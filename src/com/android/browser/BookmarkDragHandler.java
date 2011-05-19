/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnDragListener;

public class BookmarkDragHandler {

    public static interface BookmarkDragController {
        boolean startDrag(Cursor item);
    }

    public static interface BookmarkDragAdapter {
        void setBookmarkDragHandler(BookmarkDragHandler handler);
        Cursor getItemForView(View v);
    }

    static class BookmarkDragState {
        long id;
        long parent;
    }

    static final String BOOKMARK_DRAG_LABEL = "com.android.browser.BOOKMARK_LABEL";

    private Context mContext;
    private BookmarkDragController mDragController;
    private BookmarkDragAdapter mDragAdapter;

    public BookmarkDragHandler(Context context, BookmarkDragController controller,
            BookmarkDragAdapter adapter) {
        mContext = context;
        mDragController = controller;
        mDragAdapter = adapter;
        mDragAdapter.setBookmarkDragHandler(this);
    }

    public boolean startDrag(View view, Cursor item, long id) {
        if (!mDragController.startDrag(item)) {
            return false;
        }
        Uri uri = ContentUris.withAppendedId(
                BrowserContract.Bookmarks.CONTENT_URI, id);
        ClipData data = ClipData.newRawUri(BOOKMARK_DRAG_LABEL, uri);
        BookmarkDragState state = new BookmarkDragState();
        state.id = id;
        state.parent = item.getLong(BookmarksLoader.COLUMN_INDEX_PARENT);
        view.startDrag(data, new View.DragShadowBuilder(view), state, 0);
        return true;
    }

    public void registerBookmarkDragHandler(View view) {
        view.setOnDragListener(mBookmarkDragListener);
    }

    private OnDragListener mBookmarkDragListener = new OnDragListener() {

        @Override
        public boolean onDrag(View v, DragEvent event) {
            Cursor c = mDragAdapter.getItemForView(v);
            BookmarkDragState state = (BookmarkDragState) event.getLocalState();
            switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DROP:
                long id = c.getLong(BookmarksLoader.COLUMN_INDEX_ID);
                if (id == state.id) {
                    // We dropped onto ourselves, show the context menu
                    v.showContextMenu();
                    return false;
                }
                long parent = c.getLong(BookmarksLoader.COLUMN_INDEX_PARENT);
                if (isFolder(c)) {
                    parent = c.getLong(BookmarksLoader.COLUMN_INDEX_ID);
                }
                if (parent != state.parent) {
                    ContentResolver cr = mContext.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(Bookmarks.PARENT, parent);
                    Uri uri = event.getClipData().getItemAt(0).getUri();
                    cr.update(uri, values, null, null);
                }
                break;
            }
            return false;
        }
    };

    static boolean isFolder(Cursor c) {
        return c.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;
    }
}
