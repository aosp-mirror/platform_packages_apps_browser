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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.ContentObserver;
import android.database.DataSetObserver;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class BookmarkGridPage extends Activity {
    private final static int SPACING = 10;
    private BookmarkGrid mGridView;
    private BookmarkGridAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGridView = new BookmarkGrid(this);
        mGridView.setNumColumns(3);
        mAdapter = new BookmarkGridAdapter(this);
        mGridView.setAdapter(mAdapter);
        mGridView.setFocusable(true);
        mGridView.setFocusableInTouchMode(true);
        mGridView.setSelector(android.R.drawable.gallery_thumb);
        mGridView.setVerticalSpacing(SPACING);
        mGridView.setHorizontalSpacing(SPACING);
        setContentView(mGridView);
        mGridView.requestFocus();
    }

    private class BookmarkGrid extends GridView {
        public BookmarkGrid(Context context) {
            super(context);
        }
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            int thumbHeight = (h - 2 * (SPACING + getListPaddingTop()
                    + getListPaddingBottom())) / 3;
            mAdapter.heightChanged(thumbHeight);
            super.onSizeChanged(w, h, oldw, oldh);
        }
    }

    private class BookmarkGridAdapter implements ListAdapter {
        private ArrayList<DataSetObserver> mDataObservers;
        private Context mContext;  // Context to use to inflate views
        private Cursor  mCursor;
        private int mThumbHeight;

        public BookmarkGridAdapter(Context context) {
            mContext = context;
            mDataObservers = new ArrayList<DataSetObserver>();
            String whereClause = Browser.BookmarkColumns.BOOKMARK + " != 0";
            String orderBy = Browser.BookmarkColumns.VISITS + " DESC";
            mCursor = managedQuery(Browser.BOOKMARKS_URI,
                    Browser.HISTORY_PROJECTION, whereClause, null, orderBy);
            mCursor.registerContentObserver(new ChangeObserver());
            mGridView.setOnItemClickListener(
                    new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView parent, View v,
                        int position, long id) {
                    mCursor.moveToPosition(position);
                    String url = mCursor.getString(
                            Browser.HISTORY_PROJECTION_URL_INDEX);
                    Intent intent = (new Intent()).setAction(url);
                    getParent().setResult(RESULT_OK, intent);
                    finish();
                }});
        }

        void heightChanged(int newHeight) {
            mThumbHeight = newHeight;
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
                BookmarkGridAdapter.this.refreshData();
            }
        }

        void refreshData() {
            mCursor.requery();
            for (DataSetObserver o : mDataObservers) {
                o.onChanged();
            }
        }

        /* (non-Javadoc)
         * @see android.widget.ListAdapter#areAllItemsSelectable()
         */
        public boolean areAllItemsEnabled() {
            return true;
        }

        /* (non-Javadoc)
         * @see android.widget.ListAdapter#isSelectable(int)
         */
        public boolean isEnabled(int position) {
            if (position >= 0 && position < mCursor.getCount()) {
                return true;
            }
            return false;
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getCount()
         */
        public int getCount() {
            return mCursor.getCount();
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getItem(int)
         */
        public Object getItem(int position) {
            return null;
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getItemId(int)
         */
        public long getItemId(int position) {
            return position;
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = null;
            if (convertView != null) {
                v = convertView;
            } else {
                LayoutInflater factory = LayoutInflater.from(mContext);
                v = factory.inflate(R.layout.bookmark_thumbnail, null);
            }
            ImageView thumb = (ImageView) v.findViewById(R.id.thumb);
            TextView tv = (TextView) v.findViewById(R.id.label);

            mCursor.moveToPosition(position);
            tv.setText(mCursor.getString(
                    Browser.HISTORY_PROJECTION_TITLE_INDEX));
            byte[] data = mCursor.getBlob(
                    Browser.HISTORY_PROJECTION_THUMBNAIL_INDEX);
            if (data == null) {
                // Backup is to show the favicon
                data = mCursor.getBlob(
                        Browser.HISTORY_PROJECTION_FAVICON_INDEX);
                thumb.setScaleType(ImageView.ScaleType.CENTER);
            } else {
                thumb.setScaleType(ImageView.ScaleType.FIT_XY);
            }
            if (data != null) {
                thumb.setImageBitmap(
                        BitmapFactory.decodeByteArray(data, 0, data.length));
            } else {
                thumb.setImageResource(R.drawable.app_web_browser_sm);
                thumb.setScaleType(ImageView.ScaleType.CENTER);
            }
            ViewGroup.LayoutParams lp = thumb.getLayoutParams();
            if (lp.height != mThumbHeight) {
                lp.height = mThumbHeight;
                thumb.requestLayout();
            }
            return v;
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
         */
        public void registerDataSetObserver(DataSetObserver observer) {
            mDataObservers.add(observer);
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#hasStableIds()
         */
        public boolean hasStableIds() {
            return true;
        }

        /* (non-Javadoc)
         * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
         */
        public void unregisterDataSetObserver(DataSetObserver observer) {
            mDataObservers.remove(observer);
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public boolean isEmpty() {
            return getCount() == 0;
        }
    }
}
