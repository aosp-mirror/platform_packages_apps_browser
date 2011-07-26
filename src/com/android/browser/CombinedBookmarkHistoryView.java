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


import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.browser.UI.ComboViews;

import java.util.HashMap;
import java.util.Vector;

public class CombinedBookmarkHistoryView extends LinearLayout
        implements OnTouchListener, TabListener {

    final static String STARTING_FRAGMENT = "fragment";

    final static int INVALID_ID = 0;
    final static int FRAGMENT_ID_BOOKMARKS = 1;
    final static int FRAGMENT_ID_HISTORY = 2;
    final static int FRAGMENT_ID_SNAPSHOTS = 3;

    private Activity mActivity;
    private ActionBar mActionBar;

    private Bundle mExtras;

    int mCurrentFragment = INVALID_ID;

    ActionBar.Tab mTabBookmarks;
    ActionBar.Tab mTabHistory;
    ActionBar.Tab mTabSnapshots;
    ViewGroup mBookmarksHeader;

    BrowserBookmarksPage mBookmarks;
    BrowserHistoryPage mHistory;
    BrowserSnapshotPage mSnapshots;
    CombinedBookmarksCallbacks mCallback;

    public static interface CombinedBookmarksCallbacks {
        void openUrl(String url);
        void openInNewTab(String... urls);
        void openSnapshot(long id);
        void close();
    }

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

    public CombinedBookmarkHistoryView(Activity activity,
            CombinedBookmarksCallbacks cb, ComboViews startingView,
            Bundle extras) {
        super(activity);
        mActivity = activity;
        mExtras = extras;
        mActionBar = mActivity.getActionBar();
        mCallback = cb;

        View v = LayoutInflater.from(activity).inflate(R.layout.bookmarks_history, this);
        v.setOnTouchListener(this);

        mBookmarksHeader = new FrameLayout(mActivity);
        mBookmarksHeader.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL));

        // Start up the default fragment
        initFragments(mExtras);

        setupActionBar(startingView);
    }

    void setupActionBar(ComboViews startingView) {
        if (BrowserActivity.isTablet(mContext)) {
            mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                    | ActionBar.DISPLAY_USE_LOGO);
        } else {
            mActionBar.setDisplayOptions(0);
        }
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mActionBar.removeAllTabs();
        mTabBookmarks = mActionBar.newTab();
        mTabBookmarks.setText(R.string.tab_bookmarks);
        mTabBookmarks.setTabListener(this);
        mActionBar.addTab(mTabBookmarks, ComboViews.Bookmarks == startingView);
        mTabHistory = mActionBar.newTab();
        mTabHistory.setText(R.string.tab_history);
        mTabHistory.setTabListener(this);
        mActionBar.addTab(mTabHistory, ComboViews.History == startingView);
        mTabSnapshots = mActionBar.newTab();
        mTabSnapshots.setText(R.string.tab_snapshots);
        mTabSnapshots.setTabListener(this);
        mActionBar.addTab(mTabSnapshots, ComboViews.Snapshots == startingView);
        mActionBar.setCustomView(mBookmarksHeader);
        mActionBar.show();
    }

    void tearDownActionBar() {
        if (mActionBar != null) {
            mActionBar.removeAllTabs();
            mTabBookmarks.setTabListener(null);
            mTabHistory.setTabListener(null);
            mTabSnapshots.setTabListener(null);
            mTabBookmarks = null;
            mTabHistory = null;
            mTabSnapshots = null;
            mActionBar = null;
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mCurrentFragment == FRAGMENT_ID_HISTORY) {
            // Warning, ugly hack below
            // This is done because history uses orientation-specific padding
            FragmentManager fm = mActivity.getFragmentManager();
            mHistory = BrowserHistoryPage.newInstance(mCallback, mHistory.getArguments());
            fm.beginTransaction().replace(R.id.fragment, mHistory).commit();
        }
    }

    private BookmarksPageCallbacks mBookmarkCallbackWrapper = new BookmarksPageCallbacks() {
        @Override
        public boolean onOpenInNewWindow(String... urls) {
            mCallback.openInNewTab(urls);
            return true;
        }

        @Override
        public boolean onBookmarkSelected(Cursor c, boolean isFolder) {
            if (isFolder) {
                return false;
            }
            mCallback.openUrl(BrowserBookmarksPage.getUrl(c));
            return true;
        }
    };

    private void initFragments(Bundle extras) {
        mBookmarks = BrowserBookmarksPage.newInstance(mBookmarkCallbackWrapper,
                extras, mBookmarksHeader);
        mHistory = BrowserHistoryPage.newInstance(mCallback, extras);
        mSnapshots = BrowserSnapshotPage.newInstance(mCallback, extras);
    }

    private void loadFragment(int id, FragmentTransaction ft) {
        if (mCurrentFragment == id) return;

        switch (id) {
            case FRAGMENT_ID_BOOKMARKS:
                ft.replace(R.id.fragment, mBookmarks);
                break;
            case FRAGMENT_ID_HISTORY:
                ft.replace(R.id.fragment, mHistory);
                break;
            case FRAGMENT_ID_SNAPSHOTS:
                ft.replace(R.id.fragment, mSnapshots);
                break;
            default:
                throw new IllegalArgumentException();
        }
        mCurrentFragment = id;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        tearDownActionBar();
        if (mCurrentFragment != INVALID_ID) {
            try {
                FragmentManager fm = mActivity.getFragmentManager();
                FragmentTransaction transaction = fm.beginTransaction();
                if (mCurrentFragment == FRAGMENT_ID_BOOKMARKS) {
                    transaction.remove(mBookmarks);
                } else if (mCurrentFragment == FRAGMENT_ID_HISTORY) {
                    transaction.remove(mHistory);
                } else if (mCurrentFragment == FRAGMENT_ID_SNAPSHOTS) {
                    transaction.remove(mSnapshots);
                }
                transaction.commit();
            } catch (IllegalStateException ex) {
                // This exception is thrown if the fragment isn't added
                // This will happen if the activity is finishing, and the
                // fragment was already removed before this view was detached
                // Aka, success!
            }
            mCurrentFragment = INVALID_ID;
        }
    }

    /**
     * callback for back key presses
     */
    boolean onBackPressed() {
        if (mCurrentFragment == FRAGMENT_ID_BOOKMARKS) {
            return mBookmarks.onBackPressed();
        }
        return false;
    }

    /**
     * capture touch events to prevent them from going to the underlying
     * WebView
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return true;
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
        // Ignore
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if (tab == mTabBookmarks) {
            loadFragment(FRAGMENT_ID_BOOKMARKS, ft);
        } else if (tab == mTabHistory) {
            loadFragment(FRAGMENT_ID_HISTORY, ft);
        } else if (tab == mTabSnapshots) {
            loadFragment(FRAGMENT_ID_SNAPSHOTS, ft);
        }
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        // Ignore
    }

}
