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
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Browser;
import android.webkit.WebIconDatabase;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.TabHost;

import java.util.HashMap;
import java.util.Vector;

public class CombinedBookmarkHistoryActivity extends TabActivity
        implements TabHost.OnTabChangeListener {
    /**
     * Used to inform BrowserActivity to remove the parent/child relationships
     * from all the tabs.
     */
    private String mExtraData;
    /**
     * Intent to be passed to calling Activity when finished.  Keep a pointer to
     * it locally so mExtraData can be added.
     */
    private Intent mResultData;
    /**
     * Result code to pass back to calling Activity when finished.
     */
    private int mResultCode;

    /* package */ static String BOOKMARKS_TAB = "bookmark";
    /* package */ static String VISITED_TAB = "visited";
    /* package */ static String HISTORY_TAB = "history";
    /* package */ static String STARTING_TAB = "tab";

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
            return (Bitmap) mUrlsToIcons.get(url);
        }
    }
    private static IconListenerSet sIconListenerSet;
    static IconListenerSet getIconListenerSet() {
        if (null == sIconListenerSet) {
            sIconListenerSet = new IconListenerSet();
        }
        return sIconListenerSet;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tabs);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        getTabHost().setOnTabChangedListener(this);

        Bundle extras = getIntent().getExtras();

        Intent bookmarksIntent = new Intent(this, BrowserBookmarksPage.class);
        if (extras != null) {
            bookmarksIntent.putExtras(extras);
        }
        createTab(bookmarksIntent, R.string.tab_bookmarks,
                R.drawable.browser_bookmark_tab, BOOKMARKS_TAB);

        Intent visitedIntent = new Intent(this, BrowserBookmarksPage.class);
        // Need to copy extras so the bookmarks activity and this one will be
        // different
        Bundle visitedExtras = extras == null ? new Bundle() : new Bundle(extras);
        visitedExtras.putBoolean("mostVisited", true);
        visitedIntent.putExtras(visitedExtras);
        createTab(visitedIntent, R.string.tab_most_visited,
                R.drawable.browser_visited_tab, VISITED_TAB);

        Intent historyIntent = new Intent(this, BrowserHistoryPage.class);
        String defaultTab = null;
        if (extras != null) {
            historyIntent.putExtras(extras);
            defaultTab = extras.getString(STARTING_TAB);
        }
        createTab(historyIntent, R.string.tab_history,
                R.drawable.browser_history_tab, HISTORY_TAB);

        if (defaultTab != null) {
            getTabHost().setCurrentTab(2);
        }

        // XXX: Must do this before launching the AsyncTask to avoid a
        // potential crash if the icon database has not been created.
        WebIconDatabase.getInstance();
        // Do this every time we launch the activity in case a new favicon was
        // added to the webkit db.
        (new AsyncTask<Void, Void, Void>() {
            public Void doInBackground(Void... v) {
                Browser.requestAllIcons(getContentResolver(),
                    Browser.BookmarkColumns.FAVICON + " is NULL",
                    getIconListenerSet());
                return null;
            }
        }).execute();
    }

    private void createTab(Intent intent, int labelResId, int iconResId,
            String tab) {
        Resources resources = getResources();
        TabHost tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec(tab).setIndicator(
                resources.getText(labelResId), resources.getDrawable(iconResId))
                .setContent(intent));
    }
    // Copied from DialTacts Activity
    /** {@inheritDoc} */
    public void onTabChanged(String tabId) {
        Activity activity = getLocalActivityManager().getActivity(tabId);
        if (activity != null) {
            activity.onWindowFocusChanged(true);
        }
    }

    /**
     * Store extra data in the Intent to return to the calling Activity to tell
     * it to clear the parent/child relationships from all tabs.
     */
    /* package */ void removeParentChildRelationShips() {
        mExtraData = BrowserSettings.PREF_CLEAR_HISTORY;
    }

    /**
     * Custom setResult() method so that the Intent can have extra data attached
     * if necessary.
     * @param resultCode Uses same codes as Activity.setResult
     * @param data Intent returned to onActivityResult.
     */
    /* package */ void setResultFromChild(int resultCode, Intent data) {
        mResultCode = resultCode;
        mResultData = data;
    }

    @Override
    public void finish() {
        if (mExtraData != null) {
            mResultCode = RESULT_OK;
            if (mResultData == null) mResultData = new Intent();
            mResultData.putExtra(Intent.EXTRA_TEXT, mExtraData);
        }
        setResult(mResultCode, mResultData);
        super.finish();
    }
}
