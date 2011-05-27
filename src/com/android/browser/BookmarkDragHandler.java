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

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BrowserContract;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewGroup;

public class BookmarkDragHandler implements Callback {

    public static interface BookmarkDragController {
        boolean startDrag(Cursor item);

        ViewGroup getActionModeView(ActionMode mode, BookmarkDragState state);
        void actionItemClicked(View v, BookmarkDragState state);
    }

    public static interface BookmarkDragAdapter {
        void setBookmarkDragHandler(BookmarkDragHandler handler);
        Cursor getItemForView(View v);
    }

    public static class BookmarkDragState {
        public long id;
        public long parent;
        public Object extraState;
    }

    static final String BOOKMARK_DRAG_LABEL = "com.android.browser.BOOKMARK_LABEL";

    private Activity mActivity;
    private BookmarkDragController mDragController;
    private BookmarkDragAdapter mDragAdapter;
    private ActionMode mActionMode;
    private BookmarkDragState mDragState;

    public BookmarkDragHandler(Activity activity, BookmarkDragController controller,
            BookmarkDragAdapter adapter) {
        mActivity = activity;
        mDragController = controller;
        mDragAdapter = adapter;
        mDragAdapter.setBookmarkDragHandler(this);
    }

    public boolean startDrag(View view, Cursor item, long id, Object extraState) {
        if (!mDragController.startDrag(item)) {
            return false;
        }
        Uri uri = ContentUris.withAppendedId(
                BrowserContract.Bookmarks.CONTENT_URI, id);
        ClipData data = ClipData.newRawUri(BOOKMARK_DRAG_LABEL, uri);
        BookmarkDragState state = new BookmarkDragState();
        state.id = id;
        state.parent = item.getLong(BookmarksLoader.COLUMN_INDEX_PARENT);
        state.extraState = extraState;
        mDragState = state;
        view.startDrag(data, new View.DragShadowBuilder(view), state, 0);
        mActionMode = view.startActionMode(this);
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
                    ContentResolver cr = mActivity.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(BrowserContract.Bookmarks.PARENT, parent);
                    Uri uri = event.getClipData().getItemAt(0).getUri();
                    cr.update(uri, values, null, null);
                }
                break;
            }
            return false;
        }
    };

    private OnDragListener mActionModeDragListener = new OnDragListener() {

        @Override
        public boolean onDrag(View v, DragEvent event) {
            BookmarkDragState state = (BookmarkDragState) event.getLocalState();
            switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DROP:
                mDragController.actionItemClicked(v, state);
                // fall through
            case DragEvent.ACTION_DRAG_ENDED:
                if (mActionMode != null) {
                    mActionMode.finish();
                    mActionMode = null;
                }
                return true;
            }
            return false;
        }
    };

    static boolean isFolder(Cursor c) {
        return c.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        ViewGroup view = mDragController.getActionModeView(mode, mDragState);
        int count = view.getChildCount();
        for (int i = 0; i < count; i++) {
            view.getChildAt(i).setOnDragListener(mActionModeDragListener);
        }
        mode.setCustomView(view);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }

}
