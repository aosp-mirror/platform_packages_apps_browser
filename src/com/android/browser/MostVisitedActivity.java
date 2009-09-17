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
import android.app.ListActivity;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Browser;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewStub;

import java.util.Vector;

public class MostVisitedActivity extends ListActivity {

    private MyAdapter   mAdapter;

    // Instance of IconReceiver
    private final IconReceiver mIconReceiver = new IconReceiver();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new MyAdapter();
        CombinedBookmarkHistoryActivity.getIconListenerSet()
                .addListener(mIconReceiver);
        setListAdapter(mAdapter);
        ListView list = getListView();
        View v = new ViewStub(this, R.layout.empty_history);
        addContentView(v, new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
        list.setEmptyView(v);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CombinedBookmarkHistoryActivity.getIconListenerSet()
               .removeListener(mIconReceiver);
    }

    private class IconReceiver implements IconListener {
        public void onReceivedIcon(String url, Bitmap icon) {
            setListAdapter(mAdapter);
        }
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        TextView tv = (TextView) v.findViewById(R.id.url);
        String url = tv.getText().toString();
        loadUrl(url, false);
    }

    private void loadUrl(String url, boolean newWindow) {
        Intent intent = new Intent().setAction(url);
        if (newWindow) {
            Bundle b = new Bundle();
            b.putBoolean("new_window", true);
            intent.putExtras(b);
        }
        setResultToParent(RESULT_OK, intent);
        finish();
    }

    private class MyAdapter implements ListAdapter {
        private Vector<DataSetObserver> mObservers;
        private Cursor mCursor;
        // These correspond with projection below.
        private static final int mUrlIndex = 0;
        private static final int mTitleIndex = 1;
        private static final int mBookmarkIndex = 2;
        private static final int mFaviconIndex = 3;

        MyAdapter() {
            mObservers = new Vector<DataSetObserver>();
            String[] projection = new String[] {
                    Browser.BookmarkColumns.URL,
                    Browser.BookmarkColumns.TITLE,
                    Browser.BookmarkColumns.BOOKMARK,
                    Browser.BookmarkColumns.FAVICON };
            String whereClause = Browser.BookmarkColumns.VISITS + " != 0";
            String orderBy = Browser.BookmarkColumns.VISITS + " DESC";
            mCursor = managedQuery(Browser.BOOKMARKS_URI, projection,
                    whereClause, null, orderBy);
            mCursor.registerContentObserver(new ChangeObserver());
        }

        private class ChangeObserver extends ContentObserver {
            public ChangeObserver() {
                super(new Handler());
            }

            @Override
            public boolean deliverSelfNotifications() {
                return true;
            }

            @Override
            public void onChange(boolean selfChange) {
                MyAdapter.this.refreshData();
            }
        }

        void refreshData() {
            mCursor.requery();
            for (DataSetObserver o : mObservers) {
                o.onChanged();
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            HistoryItem item;
            if (null == convertView) {
                item = new HistoryItem(MostVisitedActivity.this);
            } else {
                item = (HistoryItem) convertView;
            }
            mCursor.moveToPosition(position);
            item.setName(mCursor.getString(mTitleIndex));
            String url = mCursor.getString(mUrlIndex);
            item.setUrl(url);
            byte[] data = mCursor.getBlob(mFaviconIndex);
            if (data != null) {
                item.setFavicon(BitmapFactory.decodeByteArray(data, 0,
                        data.length));
            } else {
                item.setFavicon(CombinedBookmarkHistoryActivity
                        .getIconListenerSet().getFavicon(url));
            }
            item.setIsBookmark(1 == mCursor.getInt(mBookmarkIndex));
            return item;
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean isEnabled(int position) {
            return true;
        }

        public int getCount() {
            return mCursor.getCount();
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return position;
        }

        // Always a HistoryItem
        public int getItemViewType(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public boolean hasStableIds() {
            return true;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mObservers.add(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mObservers.remove(observer);
        }

        public boolean isEmpty() {
            return getCount() == 0;
        }
    }

    // This Activity is generally a sub-Activity of CombinedHistoryActivity. In
    // that situation, we need to pass our result code up to our parent.
    // However, if someone calls this Activity directly, then this has no
    // parent, and it needs to set it on itself.
    private void setResultToParent(int resultCode, Intent data) {
        Activity a = getParent() == null ? this : getParent();
        a.setResult(resultCode, data);
    }
}

