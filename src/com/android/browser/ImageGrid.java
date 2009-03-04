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

import android.content.Context;
import android.util.Config;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

/**
 * This class implements a Grid layout of Views for the Tab picker.
 */
class ImageGrid extends GridView implements OnItemClickListener, 
        OnCreateContextMenuListener  {
    
    private Listener     mListener;
    private ImageAdapter mAdapter;
    private boolean      mIsLive;
    private static final int SPACING = 10;
    public static final int CANCEL  = -99;
    public static final int NEW_TAB = -1;

    /**
     * Constructor
     * @param context Context to use when inflating resources.
     * @param live  TRUE if the view can accept touch or click
     * @param l     Listener to respond to clicks etc.
     */
    public ImageGrid(Context context, boolean live, Listener l) {
        super(context);

        mIsLive = live;
        if (live) {
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnItemClickListener(this);
            setOnCreateContextMenuListener(this);
        }
        mListener = l;

        mAdapter = new ImageAdapter(context, this, live);
        setAdapter(mAdapter);

        setBackgroundColor(0xFF000000);

        setVerticalSpacing(SPACING);
        setHorizontalSpacing(SPACING);
        setNumColumns(2);
        setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        setSelector(android.R.drawable.gallery_thumb);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // We always consume the BACK key even if mListener is null or the
        // ImageGrid is not "live." This prevents crashes during tab animations
        // if the user presses BACK.
        if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                (event.getKeyCode() == KeyEvent.KEYCODE_BACK)) {
            if (mListener != null && mIsLive) {
                mListener.onClick(CANCEL);
                invalidate();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    
    /**
     * Called by BrowserActivity to add a new window to the tab picker.
     * This does not happen dynamically, this only happens during view
     * setup.
     * 
     * @param v Webview of the tab to add
     * @param name Web page title
     * @param url URL of the webpage
     */
    public void add(TabControl.Tab t) {
        mAdapter.add(t);
    }

    /**
     * Called by BrowserActivity when a window has been removed from the
     * tab list.
     * 
     * @param index Window to remove, from 0 to MAX_TABS-1
     */
    public void remove(int index) {
        if (Config.DEBUG && (index < 0 || index >= TabControl.MAX_TABS)) {
            throw new AssertionError();
        }
        mAdapter.remove(index);
    }

    /**
     * Request focus to initially set to a particular tab. 
     *
     * @param startingIndex This is a Tab index from 0 - MAX_TABS-1 and does not
     *                      include the "New Tab" cell.
     */
    public void setCurrentIndex(int startingIndex) {
        if (!mAdapter.maxedOut()) {
            startingIndex++;
        }
        setSelection(startingIndex);
    }

    public Listener getListener() {
        return mListener;
    }

    public void setListener(Listener l) {
        mListener = l;
    }

    /**
     * Return true if the ImageGrid is live. This means that tabs can be chosen
     * and the menu can be invoked.
     */
    public boolean isLive() {
        return mIsLive;
    }

    /**
     * Do some internal cleanup of the ImageGrid's adapter.
     */
    public void clear() {
        mAdapter.clear();
    }

    /* (non-Javadoc)
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     */
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        if (!mAdapter.maxedOut()) {
            position--;
        }
        // Position will be -1 for the "New Tab" cell.
        if (mListener != null) {
            mListener.onClick(position);
        }
    }
    
    /* (non-Javadoc)
     * @see android.view.View.OnCreateContextMenuListener#onCreateContextMenu(android.view.ContextMenu, android.view.View, java.lang.Object)
     */
    public void onCreateContextMenu(ContextMenu menu, View v, 
            ContextMenuInfo menuInfo) {
        // Do not create the context menu if there is no listener or the Tab
        // overview is not "live."
        if (mListener == null || !mIsLive) {
            return;
        }
        AdapterView.AdapterContextMenuInfo info = 
                (AdapterView.AdapterContextMenuInfo) menuInfo;
        boolean maxed = mAdapter.maxedOut();
        if (info.position > 0 || maxed) {
            MenuInflater inflater = new MenuInflater(mContext);
            inflater.inflate(R.menu.tabscontext, menu);
            int position = info.position;
            if (!maxed) {
                position--;
            }
            menu.setHeaderTitle(mAdapter.mItems.get(position).getTitle());
        }
    }

    // convert a context menu position to an actual tab position. Since context
    // menus are not created for the "New Tab" cell, this will always return a
    // valid tab position.
    public int getContextMenuPosition(MenuItem menu) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menu.getMenuInfo();
        int pos = info.position;
        if (!mAdapter.maxedOut()) {
            pos--;
        }
        return pos;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Called when our orientation changes. Tell the adapter about the new
        // size. Compute the individual tab height by taking the grid height
        // and subtracting the SPACING. Then subtract the list padding twice
        // (once for each tab on screen) and divide the remaining height by 2.
        int tabHeight = (h - SPACING
                - 2 * (getListPaddingTop() + getListPaddingBottom())) / 2;
        mAdapter.heightChanged(tabHeight);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    /**
     * Listener to be notified by behavior of ImageGrid.
     */
    public interface Listener {
        /**
         * Called when enter is pressed on the list.
         * @param position  The index of the selected image when
         *                  enter is pressed.
         */
        void onClick(int position);

        /**
         * Called when remove is called on the grid.
         */
        void remove(int position);
    }

}
