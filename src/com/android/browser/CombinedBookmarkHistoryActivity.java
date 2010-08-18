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
import android.content.Intent;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Browser;
import android.view.View;
import android.webkit.WebIconDatabase;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import java.util.HashMap;
import java.util.Vector;

interface BookmarksHistoryCallbacks {
    public void onUrlSelected(String url, boolean newWindow);
    public void onRemoveParentChildRelationShips();
}

public class CombinedBookmarkHistoryActivity extends Activity
        implements BookmarksHistoryCallbacks, OnItemClickListener {
    final static String NEWTAB_MODE = "newtab_mode";
    final static String STARTING_FRAGMENT = "fragment";

    final static int FRAGMENT_ID_BOOKMARKS = 1;
    final static int FRAGMENT_ID_HISTORY = 2;

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

    /**
     * Flag to inform the browser to force the result to open in a new tab.
     */
    private boolean mNewTabMode;

    long mCurrentFragment;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookmarks_history);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        ListView list = (ListView) findViewById(android.R.id.list);
        list.setOnItemClickListener(this);
        MatrixCursor cursor = new MatrixCursor(new String[] { "name", "_id" });
        cursor.newRow().add(getString(R.string.bookmarks)).add(FRAGMENT_ID_BOOKMARKS);
        cursor.newRow().add(getString(R.string.history)).add(FRAGMENT_ID_HISTORY);
        list.setAdapter(new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1, cursor,
                new String[] { "name" }, new int[] { android.R.id.text1 }));

        int startingFragment = FRAGMENT_ID_BOOKMARKS; 
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mNewTabMode = extras.getBoolean(NEWTAB_MODE);
            startingFragment = extras.getInt(STARTING_FRAGMENT, FRAGMENT_ID_BOOKMARKS);
        }

        // Start up the default fragment
        loadFragment(startingFragment);

        // XXX: Must do this before launching the AsyncTask to avoid a
        // potential crash if the icon database has not been created.
        WebIconDatabase.getInstance();

        // Do this every time we launch the activity in case a new favicon was
        // added to the webkit db.
        (new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... v) {
                Browser.requestAllIcons(getContentResolver(),
                        Browser.BookmarkColumns.FAVICON + " is NULL", getIconListenerSet());
                return null;
            }
        }).execute();
    }

    private void loadFragment(int id) {
        String fragmentClassName;
        switch (id) {
            case FRAGMENT_ID_BOOKMARKS:
                fragmentClassName = BrowserBookmarksPage.class.getName();
                break;
            case FRAGMENT_ID_HISTORY:
                fragmentClassName = BrowserHistoryPage.class.getName();
                break;
            default:
                throw new IllegalArgumentException();
        }
        mCurrentFragment = id;

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.openTransaction();
        Fragment frag = Fragment.instantiate(this, fragmentClassName, getIntent().getExtras());
        transaction.replace(R.id.fragment, frag);
        transaction.commit();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (id == mCurrentFragment) return;
        loadFragment((int) id);
    }

    /**
     * Store extra data in the Intent to return to the calling Activity to tell
     * it to clear the parent/child relationships from all tabs.
     */
    @Override
    public void onRemoveParentChildRelationShips() {
        mExtraData = BrowserSettings.PREF_CLEAR_HISTORY;
    }

    /**
     * Custom setResult() method so that the Intent can have extra data attached
     * if necessary.
     * @param resultCode Uses same codes as Activity.setResult
     * @param data Intent returned to onActivityResult.
     */
    private void setResultFromChild(int resultCode, Intent data) {
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
        if (mNewTabMode) {
            if (mResultData == null) mResultData = new Intent();
            mResultData.putExtra(NEWTAB_MODE, true);
        }
        setResult(mResultCode, mResultData);
        super.finish();
    }

    /**
     * Report back to the calling activity to load a site.
     * @param url   Site to load.
     * @param newWindow True if the URL should be loaded in a new window
     */
    @Override
    public void onUrlSelected(String url, boolean newWindow) {
        Intent intent = new Intent().setAction(url);
        if (newWindow) {
            intent.putExtra("new_window", true);
        }
        setResultFromChild(RESULT_OK, intent);
        finish();
    }
}
