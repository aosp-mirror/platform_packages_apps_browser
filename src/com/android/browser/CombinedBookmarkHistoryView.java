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
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Browser;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.webkit.WebIconDatabase;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Vector;

interface BookmarksHistoryCallbacks {
    public void onUrlSelected(String url, boolean newWindow);
    public void onRemoveParentChildRelationships();
}

public class CombinedBookmarkHistoryView extends LinearLayout
        implements OnTouchListener, TabListener, OptionsMenuHandler {

    final static String STARTING_FRAGMENT = "fragment";

    final static int FRAGMENT_ID_BOOKMARKS = 1;
    final static int FRAGMENT_ID_HISTORY = 2;

    private UiController mUiController;
    private Activity mActivity;
    private ActionBar mActionBar;

    private Bundle mExtras;

    int mCurrentFragment;

    ActionBar.Tab mTabBookmarks;
    ActionBar.Tab mTabHistory;
    ViewGroup mBookmarksHeader;

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
        mActionBar = mActivity.getActionBar();

        View v = LayoutInflater.from(activity).inflate(R.layout.bookmarks_history, this);
        v.setOnTouchListener(this);
        Resources res = activity.getResources();

//        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mBookmarksHeader = new FrameLayout(mActivity);
        mBookmarksHeader.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL));

        // Start up the default fragment
        initFragments(mExtras);

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

        setupActionBar(startingFragment);
        mUiController.registerOptionsMenuHandler(this);
    }

    void setupActionBar(int startingFragment) {
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_USE_LOGO);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mActionBar.removeAllTabs();
        mTabBookmarks = mActionBar.newTab();
        mTabBookmarks.setText(R.string.tab_bookmarks);
        mTabBookmarks.setTabListener(this);
        mActionBar.addTab(mTabBookmarks, FRAGMENT_ID_BOOKMARKS == startingFragment);
        mTabHistory = mActionBar.newTab();
        mTabHistory.setText(R.string.tab_history);
        mTabHistory.setTabListener(this);
        mActionBar.addTab(mTabHistory, FRAGMENT_ID_HISTORY == startingFragment);
        mActionBar.setCustomView(mBookmarksHeader);

    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Resources res = mContext.getResources();
        int paddingLeftRight = (int) res.getDimension(R.dimen.combo_paddingLeftRight);
        int paddingTop = (int) res.getDimension(R.dimen.combo_paddingTop);
        findViewById(R.id.fragment).setPadding(paddingLeftRight, paddingTop,
                paddingLeftRight, 0);
    }

    private BookmarksPageCallbacks mBookmarkCallbackWrapper = new BookmarksPageCallbacks() {
        @Override
        public boolean onOpenInNewWindow(Cursor c) {
            mUiController.onUrlSelected(BrowserBookmarksPage.getUrl(c), true);
            return true;
        }

        @Override
        public boolean onBookmarkSelected(Cursor c, boolean isFolder) {
            if (isFolder) {
                return false;
            }
            mUiController.onUrlSelected(BrowserBookmarksPage.getUrl(c), false);
            return true;
        }

        @Override
        public void onFolderChanged(int level, Uri uri) {
            final int toggleFlags = ActionBar.DISPLAY_SHOW_CUSTOM
                    | ActionBar.DISPLAY_HOME_AS_UP;
            // 1 is "bookmarks" root folder
            if (level <= 1) {
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
                mActionBar.setDisplayOptions(0, toggleFlags);
            } else {
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                mActionBar.setDisplayOptions(toggleFlags, toggleFlags);
            }
        }
    };

    private void initFragments(Bundle extras) {
        mBookmarks = BrowserBookmarksPage.newInstance(mBookmarkCallbackWrapper,
                extras, mBookmarksHeader);
        mBookmarks.setBreadCrumbMaxVisible(2);
        mBookmarks.setBreadCrumbUseBackButton(false);
        mHistory = BrowserHistoryPage.newInstance(mUiController, extras);
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
            default:
                throw new IllegalArgumentException();
        }
        mCurrentFragment = id;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        FragmentManager fm = mActivity.getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        if (mCurrentFragment == FRAGMENT_ID_BOOKMARKS) {
            transaction.remove(mBookmarks);
        } else if (mCurrentFragment == FRAGMENT_ID_HISTORY) {
            transaction.remove(mHistory);
        }
        transaction.commit();
        mUiController.unregisterOptionsMenuHandler(this);
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
        }
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        // Ignore
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Handled by fragment
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Handled by fragment
        return false;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            mUiController.getUi().onBackKey();
            return true;
        case R.id.go_home:
            BrowserSettings settings = BrowserSettings.getInstance();
            mUiController.onUrlSelected(settings.getHomePage(), false);
            return true;
        case R.id.add_bookmark:
            mUiController.bookmarkCurrentPage(mBookmarks.getFolderId());
            return true;
        }

        switch (mCurrentFragment) {
        case FRAGMENT_ID_BOOKMARKS:
            return mBookmarks.onOptionsItemSelected(item);
        case FRAGMENT_ID_HISTORY:
            return mHistory.onOptionsItemSelected(item);
        }
        return false;
    }
}
