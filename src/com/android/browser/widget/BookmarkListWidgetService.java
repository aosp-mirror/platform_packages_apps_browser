/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.browser.widget;

import com.android.browser.R;
import com.android.browser.provider.BrowserProvider2;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookmarkListWidgetService extends RemoteViewsService {

    static final String TAG = "BookmarkListWidgetService";
    static final boolean USE_FOLDERS = true;

    static final String ACTION_REMOVE_FACTORIES
            = "com.android.browser.widget.REMOVE_FACTORIES";
    static final String ACTION_CHANGE_FOLDER
            = "com.android.browser.widget.CHANGE_FOLDER";

    private static final String[] PROJECTION = new String[] {
            BrowserContract.Bookmarks._ID,
            BrowserContract.Bookmarks.TITLE,
            BrowserContract.Bookmarks.URL,
            BrowserContract.Bookmarks.FAVICON,
            BrowserContract.Bookmarks.IS_FOLDER,
            BrowserContract.Bookmarks.PARENT};

    // Ordering merged with DEFAULT_BOOKMARK_SORT_ORDER from BrowserProvider2
    private static final String ORDER_BY_CLAUSE =
        Bookmarks.IS_FOLDER + " DESC, position ASC, _id ASC";

    private Map<Integer, BookmarkFactory> mFactories;
    private Handler mUiHandler;
    private HandlerThread mBackgroundThread;
    private BookmarksObserver mBookmarksObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        mFactories = new HashMap<Integer, BookmarkFactory>();
        mUiHandler = new Handler();
        mBookmarksObserver = new BookmarksObserver(mUiHandler);
        getContentResolver().registerContentObserver(
                BrowserContract.AUTHORITY_URI, true, mBookmarksObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Intent view = new Intent(intent);
            view.setComponent(null);
            startActivity(view);
        } else if (ACTION_REMOVE_FACTORIES.equals(action)) {
            int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (ids != null) {
                for (int id : ids) {
                    mFactories.remove(id);
                }
            }
        } else if (ACTION_CHANGE_FOLDER.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            long folderId = intent.getLongExtra(Bookmarks._ID, -1);
            BookmarkFactory fac = mFactories.get(widgetId);
            if (fac != null && folderId >= 0) {
                fac.setFolder(folderId);
                AppWidgetManager.getInstance(this).notifyAppWidgetViewDataChanged(widgetId, R.id.bookmarks_list);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mBookmarksObserver);
        mBackgroundThread.quit();
    }

    private class BookmarksObserver extends ContentObserver {
        public BookmarksObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            // Update all the bookmark widgets
            sendBroadcast(new Intent(
                    BookmarkListWidgetProvider.ACTION_BOOKMARK_APPWIDGET_UPDATE,
                    null, BookmarkListWidgetService.this,
                    BookmarkListWidgetProvider.class));
        }
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (widgetId < 0) {
            Log.w(TAG, "Missing EXTRA_APPWIDGET_ID!");
            return null;
        } else {
            BookmarkFactory fac = new BookmarkFactory(this, widgetId);
            mFactories.put(widgetId, fac);
            return fac;
        }
    }

    static class BookmarkFactory implements RemoteViewsService.RemoteViewsFactory {
        private List<RenderResult> mBookmarks;
        private Context mContext;
        private int mWidgetId;
        private long mFolderId = BrowserProvider2.FIXED_ID_ROOT;

        public BookmarkFactory(Context context, int widgetId) {
            mContext = context;
            mWidgetId = widgetId;
        }

        void setFolder(long folderId) {
            mFolderId = folderId;
        }

        @Override
        public int getCount() {
            if (mBookmarks == null)
                return 0;
            return mBookmarks.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= getCount()) {
                return null;
            }

            RenderResult res = mBookmarks.get(position);

            RemoteViews views = new RemoteViews(
                    mContext.getPackageName(), R.layout.bookmarklistwidget_item);
            Intent fillin;
            if (res.mIsFolder) {
                long nfi = res.mId;
                if (nfi == mFolderId) nfi = res.mParentId;
                fillin = new Intent(ACTION_CHANGE_FOLDER, null,
                        mContext, BookmarkListWidgetService.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
                        .putExtra(Bookmarks._ID, nfi);
            } else {
                fillin = new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse(res.mUrl))
                        .addCategory(Intent.CATEGORY_BROWSABLE);
            }
            views.setOnClickFillInIntent(R.id.list_item, fillin);
            // Set the title of the bookmark. Use the url as a backup.
            String displayTitle = res.mTitle;
            if (TextUtils.isEmpty(displayTitle)) {
                // The browser always requires a title for bookmarks, but jic...
                displayTitle = res.mUrl;
            }
            views.setTextViewText(R.id.label, displayTitle);
            views.setDrawableParameters(R.id.list_item, true, 0, -1, null, -1);
            if (res.mIsFolder) {
                if (res.mId == mFolderId) {
                    views.setDrawableParameters(R.id.list_item, true, 140, -1, null, -1);
                    views.setImageViewResource(R.id.thumb, R.drawable.ic_back_normal);
                } else {
                    views.setImageViewResource(R.id.thumb, R.drawable.ic_folder);
                }
            } else {
                if (res.mBitmap != null) {
                    views.setImageViewBitmap(R.id.thumb, res.mBitmap);
                } else {
                    views.setImageViewResource(R.id.thumb,
                            R.drawable.browser_thumbnail);
                }
            }
            return views;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDestroy() {
            recycleBitmaps();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        void loadData() {
            // Reset identity since this could be an IPC call
            long token = Binder.clearCallingIdentity();
            update();
            Binder.restoreCallingIdentity(token);
        }

        void update() {
            recycleBitmaps();
            String where;
            if (USE_FOLDERS) {
                where = String.format("%s == %d", Bookmarks.PARENT, mFolderId);
                if (mFolderId != BrowserProvider2.FIXED_ID_ROOT) {
                    where = String.format("%s OR %s == %d", where,
                        Bookmarks._ID, mFolderId);
                }
            } else {
                where = Bookmarks.IS_FOLDER + " == 0";
            }
            Cursor c = null;
            try {
                c = mContext.getContentResolver().query(
                        BrowserContract.Bookmarks.CONTENT_URI, PROJECTION,
                        where, null, ORDER_BY_CLAUSE);
                if (c != null) {
                    mBookmarks = new ArrayList<RenderResult>(c.getCount());
                    while (c.moveToNext()) {
                        long id = c.getLong(0);
                        String title = c.getString(1);
                        String url = c.getString(2);
                        RenderResult res = new RenderResult(id, title, url);
                        byte[] blob = c.getBlob(3);
                        if (blob != null) {
                            // RemoteViews require a valid bitmap config
                            Options options = new Options();
                            options.inPreferredConfig = Config.ARGB_8888;
                            res.mBitmap = BitmapFactory.decodeByteArray(
                                    blob, 0, blob.length, options);
                        }
                        res.mIsFolder = c.getInt(4) != 0;
                        res.mParentId = c.getLong(5);
                        if (res.mId == mFolderId) {
                            // Make sure this is first
                            mBookmarks.add(0, res);
                        } else {
                            mBookmarks.add(res);
                        }
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "update bookmark widget", e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        private void recycleBitmaps() {
            // Do a bit of house cleaning for the system
            if (mBookmarks != null) {
                for (RenderResult res : mBookmarks) {
                    if (res.mBitmap != null) {
                        res.mBitmap.recycle();
                        res.mBitmap = null;
                    }
                }
            }
        }
    }

    // Class containing the rendering information for a specific bookmark.
    private static class RenderResult {
        final String mTitle;
        final String mUrl;
        Bitmap mBitmap;
        boolean mIsFolder;
        long mParentId;
        long mId;

        RenderResult(long id, String title, String url) {
            mId = id;
            mTitle = title;
            mUrl = url;
        }

    }

}
