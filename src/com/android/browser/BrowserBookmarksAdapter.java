/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

public class BrowserBookmarksAdapter extends CursorAdapter {
    LayoutInflater mInflater;
    int mCurrentView;

    /**
     *  Create a new BrowserBookmarksAdapter.
     */
    public BrowserBookmarksAdapter(Context context, int defaultView) {
        // Make sure to tell the CursorAdapter to avoid the observer and auto-requery
        // since the Loader will do that for us.
        super(context, null, 0);
        mInflater = LayoutInflater.from(context);
        selectView(defaultView);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (mCurrentView == BrowserBookmarksPage.VIEW_LIST) {
            bindListView(view, context, cursor);
        } else {
            bindGridView(view, context, cursor);
        }
    }

    void bindGridView(View view, Context context, Cursor cursor) {
        // We need to set this to handle rotation and other configuration change
        // events. If the padding didn't change, this is a no op.
        int padding = context.getResources()
                .getDimensionPixelSize(R.dimen.combo_horizontalSpacing);
        view.setPadding(padding, view.getPaddingTop(),
                padding, view.getPaddingBottom());
        ImageView thumb = (ImageView) view.findViewById(R.id.thumb);
        TextView tv = (TextView) view.findViewById(R.id.label);

        tv.setText(cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE));
        if (cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0) {
            // folder
            thumb.setImageResource(R.drawable.thumb_bookmark_widget_folder_holo);
            thumb.setScaleType(ScaleType.FIT_END);
            thumb.setBackgroundDrawable(null);
        } else {
            byte[] thumbData = cursor.getBlob(BookmarksLoader.COLUMN_INDEX_THUMBNAIL);
            Bitmap thumbBitmap = null;
            if (thumbData != null) {
                thumbBitmap = BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length);
            }

            thumb.setScaleType(ScaleType.CENTER_CROP);
            if (thumbBitmap == null) {
                thumb.setImageResource(R.drawable.browser_thumbnail);
            } else {
                thumb.setImageBitmap(thumbBitmap);
            }
            thumb.setBackgroundResource(R.drawable.border_thumb_bookmarks_widget_holo);
        }
    }

    void bindListView(View view, Context context, Cursor cursor) {
        ImageView favicon = (ImageView) view.findViewById(R.id.favicon);
        TextView tv = (TextView) view.findViewById(R.id.label);

        tv.setText(cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE));
        if (cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0) {
            // folder
            favicon.setImageResource(R.drawable.ic_folder_holo_dark);
            favicon.setBackgroundDrawable(null);
        } else {
            byte[] faviconData = cursor.getBlob(BookmarksLoader.COLUMN_INDEX_FAVICON);
            Bitmap faviconBitmap = null;
            if (faviconData != null) {
                faviconBitmap = BitmapFactory.decodeByteArray(faviconData, 0, faviconData.length);
            }

            if (faviconBitmap == null) {
                favicon.setImageResource(R.drawable.app_web_browser_sm);
            } else {
                favicon.setImageBitmap(faviconBitmap);
            }
            favicon.setBackgroundResource(R.drawable.bookmark_list_favicon_bg);
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (mCurrentView == BrowserBookmarksPage.VIEW_LIST) {
            return mInflater.inflate(R.layout.bookmark_list, parent, false);
        } else {
            return mInflater.inflate(R.layout.bookmark_thumbnail, parent, false);
        }
    }

    public void selectView(int view) {
        if (view != BrowserBookmarksPage.VIEW_LIST
                && view != BrowserBookmarksPage.VIEW_THUMBNAILS) {
            throw new IllegalArgumentException("Unknown view specified: " + view);
        }
        mCurrentView = view;
    }

    public int getViewMode() {
        return mCurrentView;
    }

    @Override
    public Cursor getItem(int position) {
        return (Cursor) super.getItem(position);
    }
}
