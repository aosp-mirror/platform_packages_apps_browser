/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Activity;
import android.app.ExpandableListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.ServiceManager;
import android.provider.Browser;
import android.text.IClipboard;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ContextMenu.ContextMenuInfo;
import android.webkit.DateSorter;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.AdapterView;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.TextView;

import java.util.List;
import java.util.Vector;

/**
 * Activity for displaying the browser's history, divided into
 * days of viewing.
 */
public class BrowserHistoryPage extends ExpandableListActivity {
    private HistoryAdapter          mAdapter;
    private DateSorter              mDateSorter;
    private boolean                 mMaxTabsOpen;

    private final static String LOGTAG = "browser";

    // Implementation of WebIconDatabase.IconListener
    private class IconReceiver implements IconListener {
        public void onReceivedIcon(String url, Bitmap icon) {
            setListAdapter(mAdapter);
        }
    }
    // Instance of IconReceiver
    private final IconReceiver mIconReceiver = new IconReceiver();

    /**
     * Report back to the calling activity to load a site.
     * @param url   Site to load.
     * @param newWindow True if the URL should be loaded in a new window
     */
    private void loadUrl(String url, boolean newWindow) {
        Intent intent = new Intent().setAction(url);
        if (newWindow) {
            Bundle b = new Bundle();
            b.putBoolean("new_window", true);
            intent.putExtras(b);
        }
        setResultToParent(RESULT_OK, intent);
        finish();
    }
    
    private void copy(CharSequence text) {
        try {
            IClipboard clip = IClipboard.Stub.asInterface(ServiceManager.getService("clipboard"));
            if (clip != null) {
                clip.setClipboardText(text);
            }
        } catch (android.os.RemoteException e) {
            Log.e(LOGTAG, "Copy failed", e);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setTitle(R.string.browser_history);
        
        mDateSorter = new DateSorter(this);

        mAdapter = new HistoryAdapter();
        setListAdapter(mAdapter);
        final ExpandableListView list = getExpandableListView();
        list.setOnCreateContextMenuListener(this);
        LayoutInflater factory = LayoutInflater.from(this);
        View v = factory.inflate(R.layout.empty_history, null);
        addContentView(v, new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
        list.setEmptyView(v);
        // Do not post the runnable if there is nothing in the list.
        if (list.getExpandableListAdapter().getGroupCount() > 0) {
            list.post(new Runnable() {
                public void run() {
                    // In case the history gets cleared before this event
                    // happens.
                    if (list.getExpandableListAdapter().getGroupCount() > 0) {
                        list.expandGroup(0);
                    }
                }
            });
        }
        mMaxTabsOpen = getIntent().getBooleanExtra("maxTabsOpen", false);
        CombinedBookmarkHistoryActivity.getIconListenerSet(getContentResolver())
                .addListener(mIconReceiver);
        
        // initialize the result to canceled, so that if the user just presses
        // back then it will have the correct result
        setResultToParent(RESULT_CANCELED, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CombinedBookmarkHistoryActivity.getIconListenerSet(getContentResolver())
                .removeListener(mIconReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.history, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.clear_history_menu_id).setVisible(Browser.canClearHistory(this.getContentResolver()));
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_history_menu_id:
                // FIXME: Need to clear the tab control in browserActivity 
                // as well
                Browser.clearHistory(getContentResolver());
                mAdapter.refreshData();
                return true;
                
            default:
                break;
        }  
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        ExpandableListContextMenuInfo i = 
            (ExpandableListContextMenuInfo) menuInfo;
        // Do not allow a context menu to come up from the group views.
        if (!(i.targetView instanceof HistoryItem)) {
            return;
        }

        // Inflate the menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.historycontext, menu);

        // Setup the header
        menu.setHeaderTitle(((HistoryItem)i.targetView).getUrl());

        // Only show open in new tab if we have not maxed out available tabs
        menu.findItem(R.id.new_window_context_menu_id).setVisible(!mMaxTabsOpen);
        
        // decide whether to show the share link option
        PackageManager pm = getPackageManager();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        ResolveInfo ri = pm.resolveActivity(send, PackageManager.MATCH_DEFAULT_ONLY);
        menu.findItem(R.id.share_link_context_menu_id).setVisible(ri != null);
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListContextMenuInfo i = 
            (ExpandableListContextMenuInfo) item.getMenuInfo();
        String url = ((HistoryItem)i.targetView).getUrl();
        String title = ((HistoryItem)i.targetView).getName();
        switch (item.getItemId()) {
            case R.id.open_context_menu_id:
                loadUrl(url, false);
                return true;
            case R.id.new_window_context_menu_id:
                loadUrl(url, true);
                return true;
            case R.id.save_to_bookmarks_menu_id:
                Browser.saveBookmark(this, title, url);
                return true;
            case R.id.share_link_context_menu_id:
                Browser.sendString(this, url);
                return true;
            case R.id.copy_context_menu_id:
                copy(url);
                return true;
            case R.id.delete_context_menu_id:
                Browser.deleteFromHistory(getContentResolver(), url);
                mAdapter.refreshData();
                return true;
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }
    
    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        if (v instanceof HistoryItem) {
            loadUrl(((HistoryItem) v).getUrl(), false);
            return true;
        }
        return false;
    }

    // This Activity is generally a sub-Activity of CombinedHistoryActivity. In
    // that situation, we need to pass our result code up to our parent.
    // However, if someone calls this Activity directly, then this has no
    // parent, and it needs to set it on itself.
    private void setResultToParent(int resultCode, Intent data) {
        Activity a = getParent() == null ? this : getParent();
        a.setResult(resultCode, data);
    }

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
            mAdapter.refreshData();
        }
    }
    
    private class HistoryAdapter implements ExpandableListAdapter {
        
        // Array for each of our bins.  Each entry represents how many items are
        // in that bin.
        int mItemMap[];
        // This is our GroupCount.  We will have at most DateSorter.DAY_COUNT
        // bins, less if the user has no items in one or more bins.
        int mNumberOfBins;
        Vector<DataSetObserver> mObservers;
        Cursor mCursor;
        
        HistoryAdapter() {
            mObservers = new Vector<DataSetObserver>();
            
            String whereClause = Browser.BookmarkColumns.VISITS + " > 0 ";
            String orderBy = Browser.BookmarkColumns.DATE + " DESC";
           
            mCursor = managedQuery(
                    Browser.BOOKMARKS_URI,
                    Browser.HISTORY_PROJECTION,
                    whereClause, null, orderBy);
            
            buildMap();
            mCursor.registerContentObserver(new ChangeObserver());
        }
        
        void refreshData() {
            mCursor.requery();
            buildMap();
            for (DataSetObserver o : mObservers) {
                o.onChanged();
            }
        }
        
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
                    long date = mCursor.getLong(Browser.HISTORY_PROJECTION_DATE_INDEX);
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

        // This translates from a group position in the Adapter to a position in
        // our array.  This is necessary because some positions in the array
        // have no history items, so we simply do not present those positions
        // to the Adapter.
        private int groupPositionToArrayPosition(int groupPosition) {
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

        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            groupPosition = groupPositionToArrayPosition(groupPosition);
            HistoryItem item;
            if (null == convertView || !(convertView instanceof HistoryItem)) {
                item = new HistoryItem(BrowserHistoryPage.this);
                // Add padding on the left so it will be indented from the
                // arrows on the group views.
                item.setPadding(item.getPaddingLeft() + 10,
                        item.getPaddingTop(),
                        item.getPaddingRight(),
                        item.getPaddingBottom());
            } else {
                item = (HistoryItem) convertView;
            }
            int index = childPosition;
            for (int i = 0; i < groupPosition; i++) {
                index += mItemMap[i];
            }
            mCursor.moveToPosition(index);
            item.setName(mCursor.getString(Browser.HISTORY_PROJECTION_TITLE_INDEX));
            String url = mCursor.getString(Browser.HISTORY_PROJECTION_URL_INDEX);
            item.setUrl(url);
            item.setFavicon(CombinedBookmarkHistoryActivity.getIconListenerSet(
                    getContentResolver()).getFavicon(url));
            item.setIsBookmark(1 ==
                    mCursor.getInt(Browser.HISTORY_PROJECTION_BOOKMARK_INDEX));
            return item;
        }
        
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            groupPosition = groupPositionToArrayPosition(groupPosition);
            TextView item;
            if (null == convertView || !(convertView instanceof TextView)) {
                LayoutInflater factory = 
                        LayoutInflater.from(BrowserHistoryPage.this);
                item = (TextView) 
                        factory.inflate(R.layout.history_header, null);
            } else {
                item = (TextView) convertView;
            }
            item.setText(mDateSorter.getLabel(groupPosition));
            return item;
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
            return mItemMap[groupPositionToArrayPosition(groupPosition)];
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
            return mCursor.getCount() == 0;
        }
    }
}
