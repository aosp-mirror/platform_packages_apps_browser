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
import android.app.SearchManager;
import android.app.TabActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Browser;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

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

    /**
     * This class is here solely to override dispatchKeyEventPreIme, which,
     * when called on our TextView in the search bar, attempts to access its
     * SearchDialog.  It does not have one, so it crashes.
     */
    public static class CustomViewGroup extends LinearLayout {
        public CustomViewGroup(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.tabs);

        LayoutInflater factory = LayoutInflater.from(this);
        View search = factory.inflate(com.android.internal.R.layout.search_bar,
                null);
        View searchPlate = search.findViewById(
                com.android.internal.R.id.search_plate);
        // FIXME: There is some extra space at the top for some reason.
        searchPlate.setPadding(12, 0, 12, 16);
        // FIXME: Also want to remove this from the real search box in the
        // browser.
        View voiceButton = search.findViewById(
                com.android.internal.R.id.search_voice_btn);
        voiceButton.setVisibility(View.GONE);
        final TextView go = (TextView) search.findViewById(
                com.android.internal.R.id.search_go_btn);
        go.setText(R.string.search_button_text);
        View appIcon = search.findViewById(
                com.android.internal.R.id.search_app_icon);
        appIcon.setVisibility(View.GONE);

        final ViewGroup holder = (ViewGroup) findViewById(R.id.holder);
        holder.addView(search, 0);

        final TextView entryField = (TextView) search.findViewById(
                com.android.internal.R.id.search_src_text);

        String url = getIntent().getStringExtra("url");
        // Check to see if the current page is the homepage.
        // This works without calling loadFromDb because BrowserActivity has
        // already done it for us.
        if (BrowserSettings.getInstance().getHomePage().equals(url)) {
            url = null;
            entryField.setHint(R.string.search_hint);
        } else {
            entryField.setText(url);
        }
        final String pageUrl = url;
        entryField.setFocusableInTouchMode(false);
        entryField.setFocusable(false);
        go.setFocusableInTouchMode(false);
        go.setFocusable(false);
        View.OnClickListener listener = new View.OnClickListener() {
                public void onClick(View v) {
                    if (v == entryField || go == v) {
                        Bundle bundle = new Bundle();
                        bundle.putString(SearchManager.SOURCE,
                                BrowserActivity.GOOGLE_SEARCH_SOURCE_SEARCHKEY);
                        startSearch(pageUrl, true, bundle, false);
                    }
                }};
        entryField.setOnClickListener(listener);
        // FIXME: Maybe "Go" should just go, even though it is the site you
        // are already on
        go.setOnClickListener(listener);

        getTabHost().setOnTabChangedListener(this);

        Bundle extras = getIntent().getExtras();
        Resources resources = getResources();

        getIconListenerSet(getContentResolver());

        Intent bookmarksIntent = new Intent(this, BrowserBookmarksPage.class);
        bookmarksIntent.putExtras(extras);
        createTab(bookmarksIntent, R.string.tab_bookmarks, BOOKMARKS_TAB);

        Intent visitedIntent = new Intent(this, BrowserBookmarksPage.class);
        // Need to copy extras so the bookmarks activity and this one will be
        // different
        Bundle visitedExtras = new Bundle(extras);
        visitedExtras.putBoolean("mostVisited", true);
        visitedIntent.putExtras(visitedExtras);
        createTab(visitedIntent, R.string.tab_most_visited, VISITED_TAB);

        Intent historyIntent = new Intent(this, BrowserHistoryPage.class);
        historyIntent.putExtras(extras);
        createTab(historyIntent, R.string.tab_history, HISTORY_TAB);

        String defaultTab = extras.getString(STARTING_TAB);
        if (defaultTab != null) {
            getTabHost().setCurrentTab(2);
        }
    }

    private void createTab(Intent intent, int labelResId, String tab) {
        LayoutInflater factory = LayoutInflater.from(this);
        View tabHeader = factory.inflate(R.layout.tab_header, null);
        TextView textView = (TextView) tabHeader.findViewById(R.id.tab_label);
        textView.setText(labelResId);
        TabHost tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec(tab).setIndicator(tabHeader).setContent(intent));
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
