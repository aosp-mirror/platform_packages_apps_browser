/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.browser;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DateSorter;
import android.widget.ExpandableListAdapter;

import java.util.Vector;

/**
 * ExpandableListAdapter which separates data into categories based on date.
 * Used for History and Downloads.
 */
public class DateSortedExpandableListAdapter implements ExpandableListAdapter {
    // Array for each of our bins.  Each entry represents how many items are
    // in that bin.
    private int mItemMap[];
    // This is our GroupCount.  We will have at most DateSorter.DAY_COUNT
    // bins, less if the user has no items in one or more bins.
    private int mNumberOfBins;
    private Vector<DataSetObserver> mObservers;
    private Cursor mCursor;
    private DateSorter mDateSorter;
    private int mDateIndex;

    private class ChangeObserver extends ContentObserver {
        public ChangeObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshData();
        }
    }

    public DateSortedExpandableListAdapter(Context context, Cursor cursor,
            int dateIndex) {
        mDateSorter = new DateSorter(context);
        mObservers = new Vector<DataSetObserver>();
        mCursor = cursor;
        cursor.registerContentObserver(new ChangeObserver());
        mDateIndex = dateIndex;
        buildMap();
    }

    /**
     * Set up the bins for determining which items belong to which groups.
     */
    private void buildMap() {
        // The cursor is sorted by date
        // The ItemMap will store the number of items in each bin.
        int array[] = new int[DateSorter.DAY_COUNT];
        // Zero out the array.
        for (int j = 0; j < DateSorter.DAY_COUNT; j++) {
            array[j] = 0;
        }
        mNumberOfBins = 0;
        int dateIndex = -1;
        if (mCursor.moveToFirst() && mCursor.getCount() > 0) {
            while (!mCursor.isAfterLast()) {
                long date = mCursor.getLong(mDateIndex);
                int index = mDateSorter.getIndex(date);
                if (index > dateIndex) {
                    mNumberOfBins++;
                    if (index == DateSorter.DAY_COUNT - 1) {
                        // We are already in the last bin, so it will
                        // include all the remaining items
                        array[index] = mCursor.getCount()
                                - mCursor.getPosition();
                        break;
                    }
                    dateIndex = index;
                }
                array[dateIndex]++;
                mCursor.moveToNext();
            }
        }
        mItemMap = array;
    }

    /**
     * Get the byte array at cursorIndex from the Cursor.  Assumes the Cursor
     * has already been moved to the correct position.  Along with
     * {@link #getInt} and {@link #getString}, these are provided so the client
     * does not need to access the Cursor directly
     * @param cursorIndex Index to query the Cursor.
     * @return corresponding byte array from the Cursor.
     */
    /* package */ byte[] getBlob(int cursorIndex) {
        return mCursor.getBlob(cursorIndex);
    }

    /**
     * Get the integer at cursorIndex from the Cursor.  Assumes the Cursor has
     * already been moved to the correct position.  Along with
     * {@link #getBlob} and {@link #getString}, these are provided so the client
     * does not need to access the Cursor directly
     * @param cursorIndex Index to query the Cursor.
     * @return corresponding integer from the Cursor.
     */
    /* package */ int getInt(int cursorIndex) {
        return mCursor.getInt(cursorIndex);
    }

    /**
     * Get the label for a group, as specified by the ExpandableList
     * @param groupPosition Position in the ExpandableList's set of groups
     * @return String label for the corresponding bin.
     */
    /* package */ String getGroupLabel(int groupPosition) {
        return mDateSorter.getLabel(groupPositionToBin(groupPosition));
    }

    /**
     * Get the String at cursorIndex from the Cursor.  Assumes the Cursor has
     * already been moved to the correct position.  Along with
     * {@link #getInt} and {@link #getInt}, these are provided so the client
     * does not need to access the Cursor directly
     * @param cursorIndex Index to query the Cursor.
     * @return corresponding String from the Cursor.
     */
    /* package */ String getString(int cursorIndex) {
        return mCursor.getString(cursorIndex);
    }

    /**
     * Translates from a group position in the ExpandableList to a bin.  This is
     * necessary because some groups have no history items, so we do not include
     * those in the ExpandableList.
     * @param groupPosition Position in the ExpandableList's set of groups
     * @return The corresponding bin that holds that group.
     */
    private int groupPositionToBin(int groupPosition) {
        if (groupPosition < 0 || groupPosition >= DateSorter.DAY_COUNT) {
            throw new AssertionError("group position out of range");
        }
        if (DateSorter.DAY_COUNT == mNumberOfBins || 0 == mNumberOfBins) {
            // In the first case, we have exactly the same number of bins
            // as our maximum possible, so there is no need to do a
            // conversion
            // The second statement is in case this method gets called when
            // the array is empty, in which case the provided groupPosition
            // will do fine.
            return groupPosition;
        }
        int arrayPosition = -1;
        while (groupPosition > -1) {
            arrayPosition++;
            if (mItemMap[arrayPosition] != 0) {
                groupPosition--;
            }
        }
        return arrayPosition;
    }

    /**
     * Move the cursor the the position indicated.
     * @param groupPosition Index of the group containing the desired item.
     * @param childPosition Index of the item within the specified group.
     * @return boolean False if the cursor is closed, so the Cursor was not
     *      moved.  True on success.
     */
    /* package */ boolean moveCursorToChildPosition(int groupPosition,
            int childPosition) {
        if (mCursor.isClosed()) return false;
        groupPosition = groupPositionToBin(groupPosition);
        int index = childPosition;
        for (int i = 0; i < groupPosition; i++) {
            index += mItemMap[i];
        }
        mCursor.moveToPosition(index);
        return true;
    }

    /* package */ void refreshData() {
        if (mCursor.isClosed()) {
            return;
        }
        mCursor.requery();
        buildMap();
        for (DataSetObserver o : mObservers) {
            o.onChanged();
        }
    }

    public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
        return null;
    }

    public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        return null;
    }

    public boolean areAllItemsEnabled() {
        return true;
    }

    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public int getGroupCount() {
        return mNumberOfBins;
    }

    public int getChildrenCount(int groupPosition) {
        return mItemMap[groupPositionToBin(groupPosition)];
    }

    public Object getGroup(int groupPosition) {
        return null;
    }

    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    public long getChildId(int groupPosition, int childPosition) {
        return (childPosition << 3) + groupPosition;
    }

    public boolean hasStableIds() {
        return true;
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        mObservers.add(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        mObservers.remove(observer);
    }

    public void onGroupExpanded(int groupPosition) {
    }

    public void onGroupCollapsed(int groupPosition) {
    }

    public long getCombinedChildId(long groupId, long childId) {
        return childId;
    }

    public long getCombinedGroupId(long groupId) {
        return groupId;
    }

    public boolean isEmpty() {
        return mCursor.isClosed() || mCursor.getCount() == 0;
    }
}
