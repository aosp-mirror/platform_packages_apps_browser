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

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;
import android.service.urlrenderer.UrlRenderer;
import android.service.urlrenderer.UrlRendererService;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.android.browser.R;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class BookmarkWidgetService extends Service
        implements UrlRenderer.Callback {

    private static final String TAG = "BookmarkWidgetService";

    /** Force the bookmarks to be re-renderer. */
    public static final String UPDATE = "com.android.browser.widget.UPDATE";

    /** Change the widget to the next bookmark. */
    private static final String NEXT = "com.android.browser.widget.NEXT";

    /** Change the widget to the previous bookmark. */
    private static final String PREV = "com.android.browser.widget.PREV";

    /** Id of the current item displayed in the widget. */
    private static final String EXTRA_ID =
            "com.android.browser.widget.extra.ID";

    // XXX: Remove these magic numbers once the dimensions of the widget can be
    // queried.
    private static final int WIDTH = 306;
    private static final int HEIGHT = 386;

    // Limit the number of connection attempts.
    private static final int MAX_SERVICE_RETRY_COUNT = 5;

    // No id specified.
    private static final int NO_ID = -1;

    private static final int MSG_UPDATE = 0;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE:
                    if (mRenderer != null) {
                        queryCursorAndRender();
                    } else {
                        if (++mServiceRetryCount <= MAX_SERVICE_RETRY_COUNT) {
                            // Service is not connected, try again in a second.
                            mHandler.sendEmptyMessageDelayed(MSG_UPDATE, 1000);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mRenderer = new UrlRenderer(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            mRenderer = null;
        }
    };

    // Id -> information map storing db ids and their result.
    private final HashMap<Integer, RenderResult> mIdsToResults =
            new HashMap<Integer, RenderResult>();

    // List of ids in order
    private final ArrayList<Integer> mIdList = new ArrayList<Integer>();

    // Map of urls to ids for when a url is complete.
    private final HashMap<String, Integer> mUrlsToIds =
            new HashMap<String, Integer>();

    // The current id used by the widget during an update.
    private int mCurrentId = NO_ID;
    // Class that contacts the service on the phone to render bookmarks.
    private UrlRenderer mRenderer;
    // Number of service retries. Stop trying to connect after
    // MAX_SERVICE_RETRY_COUNT
    private int mServiceRetryCount;

    @Override
    public void onCreate() {
        bindService(new Intent(UrlRendererService.SERVICE_INTERFACE),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        unbindService(mConnection);
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();
        if (UPDATE.equals(action)) {
            mHandler.sendEmptyMessage(MSG_UPDATE);
        } else if (PREV.equals(action) && mIdList.size() > 1) {
            int prev = getPreviousId(intent);
            if (prev == NO_ID) {
                Log.d(TAG, "Could not determine previous id");
                return START_NOT_STICKY;
            }
            RenderResult res = mIdsToResults.get(prev);
            if (res != null) {
                updateWidget(res);
            }
        } else if (NEXT.equals(action) && mIdList.size() > 1) {
            int next = getNextId(intent);
            if (next == NO_ID) {
                Log.d(TAG, "Could not determine next id");
                return START_NOT_STICKY;
            }
            RenderResult res = mIdsToResults.get(next);
            if (res != null) {
                updateWidget(res);
            }
        }
        return START_STICKY;
    }

    private int getPreviousId(Intent intent) {
        int listSize = mIdList.size();
        // If the list contains 1 or fewer entries, return NO_ID so that the
        // widget does not update.
        if (listSize <= 1) {
            return NO_ID;
        }

        int curr = intent.getIntExtra(EXTRA_ID, NO_ID);
        if (curr == NO_ID) {
            return NO_ID;
        }

        // Check if the current id is the beginning of the list so we can skip
        // iterating through.
        if (mIdList.get(0) == curr) {
            return mIdList.get(listSize - 1);
        }

        // Search for the current id and remember the previous id.
        int prev = NO_ID;
        for (int id : mIdList) {
            if (id == curr) {
                break;
            }
            prev = id;
        }
        return prev;
    }

    private int getNextId(Intent intent) {
        int listSize = mIdList.size();
        // If the list contains 1 or fewer entries, return NO_ID so that the
        // widget does not update.
        if (listSize <= 1) {
            return NO_ID;
        }

        int curr = intent.getIntExtra(EXTRA_ID, NO_ID);
        if (curr == NO_ID) {
            return NO_ID;
        }

        // Check if the current id is at the end of the list so we can skip
        // iterating through.
        if (mIdList.get(listSize - 1) == curr) {
            return mIdList.get(0);
        }

        // Iterate through the ids. i is set to the current index + 1.
        int i = 1;
        for (int id : mIdList) {
            if (id == curr) {
                break;
            }
            i++;
        }
        return mIdList.get(i);
    }

    private void updateWidget(RenderResult res) {
        RemoteViews views = new RemoteViews(getPackageName(),
                R.layout.bookmarkwidget);

        Intent prev = new Intent(PREV, null, this, BookmarkWidgetService.class);
        prev.putExtra(EXTRA_ID, res.mId);
        views.setOnClickPendingIntent(R.id.previous,
                PendingIntent.getService(this, 0, prev,
                    PendingIntent.FLAG_CANCEL_CURRENT));

        Intent next = new Intent(NEXT, null, this, BookmarkWidgetService.class);
        next.putExtra(EXTRA_ID, res.mId);
        views.setOnClickPendingIntent(R.id.next,
                PendingIntent.getService(this, 0, next,
                    PendingIntent.FLAG_CANCEL_CURRENT));

        // Set the title of the bookmark. Use the url as a backup.
        String displayTitle = res.mTitle;
        if (displayTitle == null) {
            displayTitle = res.mUrl;
        }
        views.setTextViewText(R.id.title, displayTitle);

        // Set the image or revert to the progress indicator.
        if (res.mBitmap != null) {
            views.setImageViewBitmap(R.id.image, res.mBitmap);
            views.setViewVisibility(R.id.image, View.VISIBLE);
            views.setViewVisibility(R.id.progress, View.GONE);
        } else {
            views.setViewVisibility(R.id.progress, View.VISIBLE);
            views.setViewVisibility(R.id.image, View.GONE);
        }

        // Update the current id.
        mCurrentId = res.mId;

        AppWidgetManager.getInstance(this).updateAppWidget(
                new ComponentName(this, BookmarkWidgetProvider.class),
                views);
    }

    // Default WHERE clause is all bookmarks.
    private static final String QUERY_WHERE =
            BookmarkColumns.BOOKMARK + " == 1";
    private static final String[] PROJECTION = new String[] {
            BookmarkColumns._ID, BookmarkColumns.TITLE, BookmarkColumns.URL };

    // Class containing the rendering information for a specific bookmark.
    private static class RenderResult {
        final int    mId;
        final String mTitle;
        final String mUrl;
        Bitmap       mBitmap;

        RenderResult(int id, String title, String url) {
            mId = id;
            mTitle = title;
            mUrl = url;
        }
    }

    private void queryCursorAndRender() {
        // Clear the ordered list of ids and the map of ids to bitmaps.
        mIdList.clear();
        mIdsToResults.clear();

        // Look up all the bookmarks
        Cursor c = getContentResolver().query(Browser.BOOKMARKS_URI, PROJECTION,
                QUERY_WHERE, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                ArrayList<String> urls = new ArrayList<String>(c.getCount());
                boolean sawCurrentId = false;
                do {
                    int id = c.getInt(0);
                    String title = c.getString(1);
                    String url = c.getString(2);

                    // Linear list of ids to obtain the previous and next.
                    mIdList.add(id);

                    // Map the url to its db id for lookup when complete.
                    mUrlsToIds.put(url, id);

                    // Is this the current id?
                    if (mCurrentId == id) {
                        sawCurrentId = true;
                    }

                    // Store the current information to at least display the
                    // title.
                    RenderResult res = new RenderResult(id, title, url);
                    mIdsToResults.put(id, res);

                    // Add the url to our list to render.
                    urls.add(url);
                } while (c.moveToNext());

                // Request a rendering of the urls. XXX: Hard-coded dimensions
                // until the view's orientation and size can be determined. Or
                // in the future the image will be a picture that can be
                // scaled/zoomed arbitrarily.
                mRenderer.render(urls, WIDTH, HEIGHT, this);

                // Set the current id to the very first id if we did not see
                // the current id in the list (the bookmark could have been
                // deleted or this is the first update).
                if (!sawCurrentId) {
                    mCurrentId = mIdList.get(0);
                }
            }
            c.close();
        }
    }

    // UrlRenderer.Callback implementation
    public void complete(String url, ParcelFileDescriptor result) {
        int id = mUrlsToIds.get(url);
        if (id == NO_ID) {
            Log.d(TAG, "No matching id found during completion of "
                    + url);
            return;
        }

        RenderResult res = mIdsToResults.get(id);
        if (res == null) {
            Log.d(TAG, "No result found during completion of "
                    + url);
            return;
        }

        // Set the result.
        if (result != null) {
            InputStream input =
                    new ParcelFileDescriptor.AutoCloseInputStream(result);
            Bitmap orig = BitmapFactory.decodeStream(input, null, null);
            // XXX: Hard-coded scaled bitmap until I can query the image
            // dimensions.
            res.mBitmap = Bitmap.createScaledBitmap(orig, WIDTH, HEIGHT, true);
            try {
                input.close();
            } catch (IOException e) {
                // oh well...
            }
        }

        // If we are currently looking at the bookmark that just finished,
        // update the widget.
        if (mCurrentId == id) {
            updateWidget(res);
        }
    }
}
