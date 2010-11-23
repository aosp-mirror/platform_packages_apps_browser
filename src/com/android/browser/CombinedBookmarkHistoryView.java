/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebIconDatabase;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Vector;

interface BookmarksHistoryCallbacks {
    public void onUrlSelected(String url, boolean newWindow);
    public void onRemoveParentChildRelationships();
    public void onComboCanceled();
}

public class CombinedBookmarkHistoryView extends LinearLayout
        implements OnClickListener, BreadCrumbView.Controller, OnMenuItemClickListener {

    final static String STARTING_FRAGMENT = "fragment";

    final static int FRAGMENT_ID_BOOKMARKS = 1;
    final static int FRAGMENT_ID_HISTORY = 2;

    private UiController mUiController;
    private Activity mActivity;

    private Bundle mExtras;

    long mCurrentFragment;

    View mTabs;
    BreadCrumbView mCrumbs;
    TextView mTabBookmarks;
    TextView mTabHistory;
    TextView mAddBookmark;
    TextView mSelectBookmarkView;
    View mSeperateSelectAdd;

    BrowserBookmarksPage mBookmarks;
    BrowserHistoryPage mHistory;

    static class IconListenerSet implements IconListener {
        // Used to store favicons as we get them from the database
        // FIXME: We use a different method to get the Favicons in
        // BrowserBookmarksAdapter. They should probably be unified.
        private HashMap<String, Bitmap> mUrlsToIcons;
        private Vector<IconListener> mListeners;

        public IconListenerSet() {
            mUrlsToIcons = new HashMap<String, Bitmap>();
            mListeners = new Vector<IconListener>();
        }
        @Override
        public void onReceivedIcon(String url, Bitmap icon) {
            mUrlsToIcons.put(url, icon);
            for (IconListener listener : mListeners) {
                listener.onReceivedIcon(url, icon);
            }
        }
        public void addListener(IconListener listener) {
            mListeners.add(listener);
        }
        public void removeListener(IconListener listener) {
            mListeners.remove(listener);
        }
        public Bitmap getFavicon(String url) {
            return mUrlsToIcons.get(url);
        }
    }

    private static IconListenerSet sIconListenerSet;
    static IconListenerSet getIconListenerSet() {
        if (null == sIconListenerSet) {
            sIconListenerSet = new IconListenerSet();
        }
        return sIconListenerSet;
    }

    public CombinedBookmarkHistoryView(Activity activity, UiController controller,
            int startingFragment, Bundle extras) {
        super(activity);
        mUiController = controller;
        mActivity = activity;
        mExtras = extras;
        View v = LayoutInflater.from(activity).inflate(R.layout.bookmarks_history, this);
        Resources res = activity.getResources();

//        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mTabs = findViewById(R.id.tabs);
        mCrumbs = (BreadCrumbView) findViewById(R.id.crumbs);
        mCrumbs.setController(this);

        mTabBookmarks = (TextView) findViewById(R.id.bmtab);
        mTabHistory = (TextView) findViewById(R.id.historytab);
        mAddBookmark = (TextView) findViewById(R.id.addbm);
        mSeperateSelectAdd = findViewById(R.id.seperate_select_add);
        mSelectBookmarkView = (TextView) findViewById(R.id.select_bookmark_view);
        mAddBookmark.setOnClickListener(this);
        mTabHistory.setOnClickListener(this);
        mTabBookmarks.setOnClickListener(this);
        mSelectBookmarkView.setOnClickListener(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int bookmarksView =
            prefs.getInt(BrowserBookmarksPage.PREF_SELECTED_VIEW, BrowserBookmarksPage.VIEW_THUMBNAILS);
        if (bookmarksView == BrowserBookmarksPage.VIEW_THUMBNAILS) {
            mSelectBookmarkView.setText(R.string.bookmark_thumbnail_view);
        } else {
            mSelectBookmarkView.setText(R.string.bookmark_list_view);
        }
        // Start up the default fragment
        initFragments(mExtras);
        loadFragment(startingFragment, mExtras, false);

        // XXX: Must do this before launching the AsyncTask to avoid a
        // potential crash if the icon database has not been created.
        WebIconDatabase.getInstance();

        // Do this every time the view is created in case a new favicon was
        // added to the webkit db.
        (new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... v) {
                Browser.requestAllIcons(mActivity.getContentResolver(),
                        Browser.BookmarkColumns.FAVICON + " is NULL", getIconListenerSet());
                return null;
            }
        }).execute();

    }

    private void initFragments(Bundle extras) {
        mBookmarks =  BrowserBookmarksPage.newInstance(mUiController, mCrumbs, extras);
        mHistory = BrowserHistoryPage.newInstance(mUiController, extras);
    }

    private void loadFragment(int id, Bundle extras, boolean notify) {
        String fragmentClassName;
        Fragment fragment = null;
        switch (id) {
            case FRAGMENT_ID_BOOKMARKS:
                fragment = mBookmarks;
                mSeperateSelectAdd.setVisibility(View.VISIBLE);
                mSelectBookmarkView.setVisibility(View.VISIBLE);
                mCrumbs.setVisibility(View.VISIBLE);
                if (notify) {
                    mCrumbs.notifyController();
                }
                break;
            case FRAGMENT_ID_HISTORY:
                fragment = mHistory;
                mCrumbs.setVisibility(View.GONE);
                mSeperateSelectAdd.setVisibility(View.GONE);
                mSelectBookmarkView.setVisibility(View.GONE);
                break;
            default:
                throw new IllegalArgumentException();
        }
        mCurrentFragment = id;

        FragmentManager fm = mActivity.getFragmentManager();
        FragmentTransaction transaction = fm.openTransaction();
        transaction.replace(R.id.fragment, fragment);
        transaction.commit();
    }

    @Override
    public void onClick(View view) {
        if ((mTabHistory == view) && (mCurrentFragment != FRAGMENT_ID_HISTORY)) {
            loadFragment(FRAGMENT_ID_HISTORY, mExtras, false);
        } else if (mTabBookmarks == view) {
            if (mCurrentFragment != FRAGMENT_ID_BOOKMARKS) {
                loadFragment(FRAGMENT_ID_BOOKMARKS, mExtras, true);
            } else {
                mCrumbs.clear();
            }
        } else if (mAddBookmark == view) {
            mUiController.bookmarkCurrentPage(mBookmarks.getFolderId());
        } else if (mSelectBookmarkView == view) {
            PopupMenu popup = new PopupMenu(mContext, mSelectBookmarkView);
            popup.getMenuInflater().inflate(R.menu.bookmark_view,
                    popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();
        }
    }

    /**
     * BreadCrumb controller callback
     */
    @Override
    public void onTop(int level, Object data) {
        mBookmarks.onFolderChange(level, data);
    }

    /**
     * callback for back key presses
     */
    boolean onBackPressed() {
        if ((mCurrentFragment == FRAGMENT_ID_BOOKMARKS) &&
                (mCrumbs.size() > 0)) {
            mCrumbs.popView();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.list_view:
            mSelectBookmarkView.setText(R.string.bookmark_list_view);
            mBookmarks.selectView(BrowserBookmarksPage.VIEW_LIST);
            return true;
        case R.id.thumbnail_view:
            mSelectBookmarkView.setText(R.string.bookmark_thumbnail_view);
            mBookmarks.selectView(BrowserBookmarksPage.VIEW_THUMBNAILS);
            return true;
        }
        return false;
    }

}
