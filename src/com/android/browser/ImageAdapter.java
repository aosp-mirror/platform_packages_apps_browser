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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Adapter used by ImageGrid.
 */
public class ImageAdapter implements ListAdapter {
    
    ArrayList<TabControl.Tab> mItems;  // Items shown in the grid
    private ArrayList<DataSetObserver> mDataObservers; // Data change listeners
    private Context mContext;  // Context to use to inflate views
    private boolean mMaxedOut;
    private ImageGrid mImageGrid;
    private boolean mIsLive;
    private int mTabHeight;

    ImageAdapter(Context context, ImageGrid grid, boolean live) {
        mContext = context;
        mIsLive = live;
        mItems = new ArrayList<TabControl.Tab>();
        mImageGrid = grid;
        mDataObservers = new ArrayList<DataSetObserver>();
    }

    void heightChanged(int newHeight) {
        mTabHeight = newHeight;
    }

    /**
     *  Whether the adapter is at its limit, determined by TabControl.MAX_TABS
     *
     *  @return True if the number of Tabs represented in this Adapter is at its
     *          maximum.
     */
    public boolean maxedOut() {
        return mMaxedOut;
    }

    /**
     * Clear the internal WebViews and remove their picture listeners.
     */
    public void clear() {
        for (TabControl.Tab t : mItems) {
            clearPictureListeners(t);
        }
        mItems.clear();
        notifyObservers();
    }

    private void clearPictureListeners(TabControl.Tab t) {
        if (t.getWebView() != null) {
            t.getWebView().setPictureListener(null);
            if (t.getSubWebView() != null) {
                t.getSubWebView().setPictureListener(null);
            }
        }
    }

    /**
     * Add a new window web page to the grid
     * 
     * @param t The tab to display
     */
    public void add(TabControl.Tab t) {
        if (mMaxedOut) {
            return;
        }
        mItems.add(t);
        notifyObservers();
        if (mItems.size() == TabControl.MAX_TABS) {
            mMaxedOut = true;
        }
    }
    
    /**
     * Remove a window from the list. At this point, the window
     * has already gone. It just needs to be removed from the screen
     * 
     * @param index window to remove
     */
    public void remove(int index) {
        if (index >= 0 && index < mItems.size()) {
            clearPictureListeners(mItems.remove(index));
            notifyObservers();
            mMaxedOut = false;
        }
    }

    /* (non-Javadoc)
     * @see android.widget.ListAdapter#areAllItemsSelectable()
     */
    public boolean areAllItemsEnabled() {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.ListAdapter#isSelectable(int)
     */
    public boolean isEnabled(int position) {
        if (position >= 0 && position <= mItems.size()) {
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getCount()
     */
    public int getCount() {
        // Include the New Window button if we have not reached the tab limit
        if (!mMaxedOut) {
            return mItems.size()+1;
        }
        return mItems.size();
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    public Object getItem(int position) {
        if (!mMaxedOut) {
            if (0 == position) {
                return null;
            }
            return mItems.get(position);
        }
        return mItems.get(position);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    public long getItemId(int position) {
        return position;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getView(int, android.view.View, 
     * android.view.ViewGroup)
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = null;
        if (convertView != null) {
            v = convertView;
        } else {
            LayoutInflater factory = LayoutInflater.from(mContext);
            v = factory.inflate(R.layout.tabitem, null);
        }
        FakeWebView img = (FakeWebView) v.findViewById(R.id.icon);
        ImageView close = (ImageView) v.findViewById(R.id.close);
        TextView tv = (TextView) v.findViewById(R.id.label);

        // position needs to be in the range of Tab indices.
        if (!mMaxedOut) {
            position--;
        }

        // Create the View for actual tabs
        if (position != ImageGrid.NEW_TAB) {
            TabControl.Tab t = mItems.get(position);
            img.setTab(t);
            tv.setText(t.getTitle());
            // Do not put the 'X' if the tab picker isn't "live" (meaning the
            // user cannot click on a tab)
            if (!mIsLive) {
                close.setVisibility(View.GONE);
            } else {
                close.setVisibility(View.VISIBLE);
                final int pos = position;
                close.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            ImageAdapter.this.confirmClose(pos);
                        }
                    });
            }
        } else {
            img.setBackgroundColor(Color.BLACK);
            img.setImageResource(R.drawable.ic_new_window);
            img.setScaleType(ImageView.ScaleType.CENTER);
            img.setPadding(0, 0, 0, 34);
            tv.setText(R.string.new_window);
            close.setVisibility(View.GONE);
        }
        ViewGroup.LayoutParams lp = img.getLayoutParams();
        if (lp.height != mTabHeight) {
            lp.height = mTabHeight;
            img.requestLayout();
        }
        return v;
    }

    /*
     * Pop a confirmation dialog to the user asking if they want to close this
     * tab.
     */
    private void confirmClose(final int position) {
        final ImageGrid.Listener l = mImageGrid.getListener();
        if (l == null) {
            return;
        }
        l.remove(position);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
     */
    public void registerDataSetObserver(DataSetObserver observer) {
        mDataObservers.add(observer);
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#hasStableIds()
     */
    public boolean hasStableIds() {
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
     */
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataObservers.remove(observer);
    }

    /**
     * Notify all the observers that a change has happened.
     */
    void notifyObservers() {
        for (DataSetObserver observer : mDataObservers) {
            observer.onChanged();
        }
    }

    public int getItemViewType(int position) {
        return 0;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public boolean isEmpty() {
        return getCount() == 0;
    }
}
