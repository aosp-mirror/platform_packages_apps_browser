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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.res.Resources;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Browser;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebIconDatabase;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import java.util.HashMap;
import java.util.Vector;

interface BookmarksHistoryCallbacks {
    public void onUrlSelected(String url, boolean newWindow);
    public void onRemoveParentChildRelationships();
    public void onComboCanceled();
}

public class CombinedBookmarkHistoryView extends LinearLayout
        implements OnItemClickListener {

    final static String STARTING_FRAGMENT = "fragment";

    final static int FRAGMENT_ID_BOOKMARKS = 1;
    final static int FRAGMENT_ID_HISTORY = 2;

    private BrowserActivity mBrowserActivity;

    private Bundle mExtras;

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

    public CombinedBookmarkHistoryView(Context context, int startingFragment, Bundle extras) {
        super(context);
        mBrowserActivity = (BrowserActivity) context;
        mExtras = extras;
        View v = LayoutInflater.from(context).inflate(R.layout.bookmarks_history, this);
        Resources res = context.getResources();

//        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        ListView list = (ListView) findViewById(android.R.id.list);
        list.setOnItemClickListener(this);
        MatrixCursor cursor = new MatrixCursor(new String[] { "name", "_id" });
        cursor.newRow().add(res.getString(R.string.bookmarks)).add(FRAGMENT_ID_BOOKMARKS);
        cursor.newRow().add(res.getString(R.string.history)).add(FRAGMENT_ID_HISTORY);
        list.setAdapter(new SimpleCursorAdapter(context,
                android.R.layout.simple_list_item_1, cursor,
                new String[] { "name" }, new int[] { android.R.id.text1 }));

        // Start up the default fragment
        loadFragment(startingFragment, mExtras);

        // XXX: Must do this before launching the AsyncTask to avoid a
        // potential crash if the icon database has not been created.
        WebIconDatabase.getInstance();

        // Do this every time the view is created in case a new favicon was
        // added to the webkit db.
        (new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... v) {
                Browser.requestAllIcons(mBrowserActivity.getContentResolver(),
                        Browser.BookmarkColumns.FAVICON + " is NULL", getIconListenerSet());
                return null;
            }
        }).execute();

    }

    private void loadFragment(int id, Bundle extras) {
        String fragmentClassName;
        Fragment fragment = null;
        switch (id) {
            case FRAGMENT_ID_BOOKMARKS:
                fragment = BrowserBookmarksPage.newInstance(mBrowserActivity, extras);
                break;
            case FRAGMENT_ID_HISTORY:
                fragment = BrowserHistoryPage.newInstance(mBrowserActivity, extras);
                break;
            default:
                throw new IllegalArgumentException();
        }
        mCurrentFragment = id;

        FragmentManager fm = mBrowserActivity.getFragmentManager();
        FragmentTransaction transaction = fm.openTransaction();
        transaction.replace(R.id.fragment, fragment);
        transaction.commit();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (id == mCurrentFragment) return;
        loadFragment((int) id, mExtras);
    }

}
