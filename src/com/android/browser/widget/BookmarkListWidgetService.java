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

import com.android.browser.BrowserBookmarksPage;
import com.android.browser.R;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.preference.PreferenceManager;
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
import java.util.Stack;

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
            BrowserContract.Bookmarks.PARENT,
            BrowserContract.Bookmarks.POSITION};

    private Map<Integer, BookmarkFactory> mFactories;
    private Handler mUiHandler;
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
                fac.changeFolder(folderId);
                AppWidgetManager.getInstance(this).notifyAppWidgetViewDataChanged(widgetId, R.id.bookmarks_list);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mBookmarksObserver);
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

    private static class Breadcrumb {
        long mId;
        String mTitle;
        public Breadcrumb(long id, String title) {
            mId = id;
            mTitle = title;
        }
    }

    static class BookmarkFactory implements RemoteViewsService.RemoteViewsFactory {
        private List<RenderResult> mBookmarks;
        private Context mContext;
        private int mWidgetId;
        private String mAccountType;
        private String mAccountName;
        private Stack<Breadcrumb> mBreadcrumbs;

        public BookmarkFactory(Context context, int widgetId) {
            mBreadcrumbs = new Stack<Breadcrumb>();
            mContext = context;
            mWidgetId = widgetId;
        }

        void changeFolder(long folderId) {
            if (mBookmarks == null) return;

            if (!mBreadcrumbs.empty() && mBreadcrumbs.peek().mId == folderId) {
                mBreadcrumbs.pop();
                return;
            }

            for (RenderResult res : mBookmarks) {
                if (res.mId == folderId) {
                    mBreadcrumbs.push(new Breadcrumb(res.mId, res.mTitle));
                    break;
                }
            }
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
            Breadcrumb folder = mBreadcrumbs.empty() ? null : mBreadcrumbs.peek();

            RemoteViews views = new RemoteViews(
                    mContext.getPackageName(), R.layout.bookmarklistwidget_item);
            Intent fillin;
            if (res.mIsFolder) {
                long nfi = res.mId;
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
                if (folder != null && res.mId == folder.mId) {
                    views.setDrawableParameters(R.id.list_item, true, 255, -1, null, -1);
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            mAccountType = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_TYPE, null);
            mAccountName = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
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
            String where = null;
            Breadcrumb folder = mBreadcrumbs.empty() ? null : mBreadcrumbs.peek();
            Uri uri;
            if (USE_FOLDERS) {
                uri = BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER;
                if (folder != null) {
                    uri = ContentUris.withAppendedId(uri, folder.mId);
                }
            } else {
                uri = BrowserContract.Bookmarks.CONTENT_URI;
                where = Bookmarks.IS_FOLDER + " == 0";
            }
            if (!TextUtils.isEmpty(mAccountType) && !TextUtils.isEmpty(mAccountName)) {
                uri = uri.buildUpon()
                        .appendQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE, mAccountType)
                        .appendQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME, mAccountName).build();
            }
            Cursor c = null;
            try {
                c = mContext.getContentResolver().query(uri, PROJECTION,
                        where, null, null);
                if (c != null) {
                    mBookmarks = new ArrayList<RenderResult>(c.getCount() + 1);
                    if (folder != null) {
                        RenderResult res = new RenderResult(folder.mId, folder.mTitle, null);
                        res.mIsFolder = true;
                        mBookmarks.add(res);
                    }
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
                        mBookmarks.add(res);
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
        long mId;

        RenderResult(long id, String title, String url) {
            mId = id;
            mTitle = title;
            mUrl = url;
        }

    }

}
