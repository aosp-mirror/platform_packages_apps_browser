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
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Browser;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.view.Window;

import java.util.HashMap;
import java.util.Vector;

public class CombinedBookmarkHistoryActivity extends TabActivity
        implements TabHost.OnTabChangeListener {
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
        public Bitmap getFavicon(String url) {
            return (Bitmap) mUrlsToIcons.get(url);
        }
    }
    private static IconListenerSet sIconListenerSet;
    static IconListenerSet getIconListenerSet(ContentResolver cr) {
        if (null == sIconListenerSet) {
            sIconListenerSet = new IconListenerSet();
            Browser.requestAllIcons(cr, null, sIconListenerSet);
        }
        return sIconListenerSet;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.tabs);
        TabHost tabHost = getTabHost();
        tabHost.setOnTabChangedListener(this);

        Bundle extras = getIntent().getExtras();
        Resources resources = getResources();

        getIconListenerSet(getContentResolver());
        Intent bookmarksIntent = new Intent(this, BrowserBookmarksPage.class);
        bookmarksIntent.putExtras(extras);
        tabHost.addTab(tabHost.newTabSpec(BOOKMARKS_TAB)
                .setIndicator(resources.getString(R.string.tab_bookmarks),
                resources.getDrawable(R.drawable.browser_bookmark_tab))
                .setContent(bookmarksIntent));

        Intent visitedIntent = new Intent(this, MostVisitedActivity.class);
        visitedIntent.putExtras(extras);
        tabHost.addTab(tabHost.newTabSpec(VISITED_TAB)
                .setIndicator(resources.getString(R.string.tab_most_visited),
                resources.getDrawable(R.drawable.browser_visited_tab))
                .setContent(visitedIntent));

        Intent historyIntent = new Intent(this, BrowserHistoryPage.class);
        historyIntent.putExtras(extras);
        tabHost.addTab(tabHost.newTabSpec(HISTORY_TAB)
                .setIndicator(resources.getString(R.string.tab_history),
                resources.getDrawable(R.drawable.
                browser_history_tab)).setContent(historyIntent));

        String defaultTab = extras.getString(STARTING_TAB);
        if (defaultTab != null) {
            tabHost.setCurrentTab(2);
        }
    }

    // Copied from DialTacts Activity
    /** {@inheritDoc} */
    public void onTabChanged(String tabId) {
        Activity activity = getLocalActivityManager().getActivity(tabId);
        if (activity != null) {
            activity.onWindowFocusChanged(true);
        }
    }

    
}
