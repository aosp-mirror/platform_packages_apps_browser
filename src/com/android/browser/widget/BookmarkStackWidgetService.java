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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.BrowserContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class BookmarkStackWidgetService extends RemoteViewsService {

    private static final String LOGTAG = "browserwidget";

    /** Force the bookmarks to be re-renderer. */
    public static final String UPDATE = "com.android.browser.widget.UPDATE";

    /** the adapter intent action */
    public static final String ADAPTER = "com.android.browser.widget.ADAPTER";

    private static final String EXTRA_ID = "_id";
    private static final String EXTRA_URL = "_url";

    private static final String[] PROJECTION = new String[] {
        BrowserContract.Bookmarks._ID,
        BrowserContract.Bookmarks.TITLE,
        BrowserContract.Bookmarks.URL,
        BrowserContract.Bookmarks.THUMBNAIL };

    private static final String WHERE_CLAUSE = BrowserContract.Bookmarks.IS_FOLDER +
            " == 0";

    // No id specified.
    private static final int NO_ID = -1;

    private static final int MSG_UPDATE = 0;

    List<RenderResult> mBookmarks;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE:
                    updateWidget();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ((intent == null) || (intent.getAction() != null) && UPDATE.equals(intent.getAction())) {
            mHandler.sendEmptyMessage(MSG_UPDATE);
        }
        return START_STICKY;
    }

    private void updateWidget() {
        RemoteViews views = new RemoteViews(getPackageName(),
                R.layout.bookmarkstackwidget);
        Intent adapter = new Intent(BookmarkStackWidgetService.ADAPTER, null,
                this, BookmarkStackWidgetService.class);
        views.setRemoteAdapter(R.id.stackwidget_stack, adapter);
        AppWidgetManager.getInstance(this).updateAppWidget(
                new ComponentName(this, BookmarkStackWidgetProvider.class),
                views);
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return mViewFactory;
    }

    RemoteViewsService.RemoteViewsFactory mViewFactory = new RemoteViewsFactory () {

        @Override
        public int getCount() {
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
            RenderResult res = mBookmarks.get(position);
            RemoteViews views = new RemoteViews(getPackageName(),
                    R.layout.bookmarkstackwidget_item);
            views.setOnClickPendingIntent(R.id.stack_item,
                    getOpenUrlPendingIntent(res.mUrl));

            // Set the title of the bookmark. Use the url as a backup.
            String displayTitle = res.mTitle;
            if (TextUtils.isEmpty(displayTitle)) {
                displayTitle = res.mUrl;
            }
            views.setTextViewText(R.id.label, displayTitle);
            if (res.mBitmap != null) {
                views.setImageViewBitmap(R.id.thumb, res.mBitmap);
                views.setViewVisibility(R.id.label, View.GONE);
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
            update();
        }

        @Override
        public void onDestroy() {
        }

        public void update() {
            mBookmarks = new ArrayList<RenderResult>();
            // Look up all the bookmarks
            Cursor c = null;
            try {
                c = getContentResolver().query(BrowserContract.Bookmarks.CONTENT_URI,
                        PROJECTION, WHERE_CLAUSE, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        int id = c.getInt(0);
                        String title = c.getString(1);
                        String url = c.getString(2);
                        RenderResult res = new RenderResult(id, title, url);
                        byte[] blob = c.getBlob(3);
                        if (blob != null) {
                            res.mBitmap = BitmapFactory.decodeByteArray(blob, 0, blob.length);
                        }
                        mBookmarks.add(res);
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(LOGTAG, "update bookmark widget", e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        @Override
        public void onDataSetChanged() {
        }
    };

    private PendingIntent getOpenUrlPendingIntent(String url) {
        Intent vi = new Intent(Intent.ACTION_VIEW);
        vi.setData(Uri.parse(url));
        return PendingIntent.getActivity(this, 0, vi, PendingIntent.FLAG_CANCEL_CURRENT);
    }


    // Class containing the rendering information for a specific bookmark.
    private static class RenderResult {
        final int mId;
        final String mTitle;
        final String mUrl;
        Bitmap mBitmap;

        RenderResult(int id, String title, String url) {
            mId = id;
            mTitle = title;
            mUrl = url;
        }

    }


}
