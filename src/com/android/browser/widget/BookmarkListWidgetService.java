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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
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
            BrowserContract.Bookmarks.TOUCH_ICON,
            BrowserContract.Bookmarks.POSITION /* needed for order by */};
    private static final int BOOKMARK_INDEX_ID = 0;
    private static final int BOOKMARK_INDEX_TITLE = 1;
    private static final int BOOKMARK_INDEX_URL = 2;
    private static final int BOOKMARK_INDEX_FAVICON = 3;
    private static final int BOOKMARK_INDEX_IS_FOLDER = 4;
    private static final int BOOKMARK_INDEX_TOUCH_ICON = 5;

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
            if (mFactories != null) {
                for (BookmarkFactory fac : mFactories.values()) {
                    fac.loadData();
                }
            }
        }
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (widgetId < 0) {
            Log.w(TAG, "Missing EXTRA_APPWIDGET_ID!");
            return null;
        } else {
            BookmarkFactory fac = mFactories.get(widgetId);
            if (fac == null) {
                fac = new BookmarkFactory(getApplicationContext(), widgetId);
            }
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

    static class BookmarkFactory implements RemoteViewsService.RemoteViewsFactory,
            OnSharedPreferenceChangeListener {
        private List<RenderResult> mBookmarks;
        private Context mContext;
        private int mWidgetId;
        private String mAccountType;
        private String mAccountName;
        private Stack<Breadcrumb> mBreadcrumbs;
        private LoadBookmarksTask mLoadTask;

        public BookmarkFactory(Context context, int widgetId) {
            mBreadcrumbs = new Stack<Breadcrumb>();
            mContext = context;
            mWidgetId = widgetId;
        }

        void changeFolder(long folderId) {
            if (mBookmarks == null) return;

            if (!mBreadcrumbs.empty() && mBreadcrumbs.peek().mId == folderId) {
                mBreadcrumbs.pop();
                loadData();
                return;
            }

            for (RenderResult res : mBookmarks) {
                if (res.mId == folderId) {
                    mBreadcrumbs.push(new Breadcrumb(res.mId, res.mTitle));
                    loadData();
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
                    views.setImageViewResource(R.id.thumb, R.drawable.btn_ic_back_bookmark_widget_holo_dark);
                    views.setDrawableParameters(R.id.list_item, true, 255, -1, null, -1);
                } else {
                    views.setImageViewResource(R.id.thumb, R.drawable.ic_folder_bookmark_widget_holo_dark);
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
            prefs.registerOnSharedPreferenceChangeListener(this);
            loadData();
        }

        @Override
        public void onDestroy() {
            recycleBitmaps();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDataSetChanged() {
        }

        void loadData() {
            if (mLoadTask != null) {
                mLoadTask.cancel(false);
            }
            mLoadTask = new LoadBookmarksTask();
            mLoadTask.execute();
        }

        class LoadBookmarksTask extends AsyncTask<Void, Void, List<RenderResult>> {
            private Breadcrumb mFolder;

            @Override
            protected void onPreExecute() {
                mFolder = mBreadcrumbs.empty() ? null : mBreadcrumbs.peek();
            }

            @Override
            protected List<RenderResult> doInBackground(Void... params) {
                return loadBookmarks(mFolder);
            }

            @Override
            protected void onPostExecute(List<RenderResult> result) {
                if (!isCancelled() && result != null) {
                    recycleBitmaps();
                    mBookmarks = result;
                    AppWidgetManager.getInstance(mContext)
                            .notifyAppWidgetViewDataChanged(mWidgetId, R.id.bookmarks_list);
                }
            }
        }

        List<RenderResult> loadBookmarks(Breadcrumb folder) {
            String where = null;
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
                        .appendQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME, mAccountName)
                        .build();
            }
            Cursor c = null;
            try {
                c = mContext.getContentResolver().query(uri, PROJECTION,
                        where, null, null);
                if (c != null) {
                    ArrayList<RenderResult> bookmarks
                            = new ArrayList<RenderResult>(c.getCount() + 1);
                    if (folder != null) {
                        RenderResult res = new RenderResult(
                                folder.mId, folder.mTitle, null);
                        res.mIsFolder = true;
                        bookmarks.add(res);
                    }
                    while (c.moveToNext()) {
                        long id = c.getLong(BOOKMARK_INDEX_ID);
                        String title = c.getString(BOOKMARK_INDEX_TITLE);
                        String url = c.getString(BOOKMARK_INDEX_URL);
                        RenderResult res = new RenderResult(id, title, url);
                        res.mIsFolder = c.getInt(BOOKMARK_INDEX_IS_FOLDER) != 0;
                        if (!res.mIsFolder) {
                            byte[] blob = c.getBlob(BOOKMARK_INDEX_TOUCH_ICON);
                            if (blob == null || blob.length == 0) {
                                blob = c.getBlob(BOOKMARK_INDEX_FAVICON);
                            }
                            if (blob != null) {
                                // RemoteViews require a valid bitmap config
                                Options options = new Options();
                                options.inPreferredConfig = Config.ARGB_8888;
                                res.mBitmap = BitmapFactory.decodeByteArray(
                                        blob, 0, blob.length, options);
                            }
                        }
                        bookmarks.add(res);
                    }
                    return bookmarks;
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "update bookmark widget", e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            return null;
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

        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences prefs, String key) {
            if (BrowserBookmarksPage.PREF_ACCOUNT_TYPE.equals(key)) {
                mAccountType = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_TYPE, null);
                mBreadcrumbs.clear();
                loadData();
            }
            if (BrowserBookmarksPage.PREF_ACCOUNT_NAME.equals(key)) {
                mAccountName = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
                mBreadcrumbs.clear();
                loadData();
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
