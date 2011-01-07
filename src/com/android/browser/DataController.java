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


package com.android.browser;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.Message;
import android.provider.BrowserContract;
import android.provider.BrowserContract.History;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DataController {
    private static final String LOGTAG = "DataController";
    // Message IDs
    private static final int HISTORY_UPDATE_VISITED = 100;
    private static final int HISTORY_UPDATE_TITLE = 101;
    public static final int QUERY_URL_IS_BOOKMARK = 200;
    private static DataController sInstance;

    private Context mContext;
    private DataControllerHandler mDataHandler;
    private Handler mCbHandler; // To respond on the UI thread

    /* package */ static interface OnQueryUrlIsBookmark {
        void onQueryUrlIsBookmark(String url, boolean isBookmark);
    }
    private static class CallbackContainer {
        Object replyTo;
        Object[] args;
    }

    private static class DCMessage {
        int what;
        Object obj;
        Object replyTo;
        DCMessage(int w, Object o) {
            what = w;
            obj = o;
        }
    }

    /* package */ static DataController getInstance(Context c) {
        if (sInstance == null) {
            sInstance = new DataController(c);
        }
        return sInstance;
    }

    private DataController(Context c) {
        mContext = c.getApplicationContext();
        mDataHandler = new DataControllerHandler();
        mDataHandler.setDaemon(true);
        mDataHandler.start();
        mCbHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                CallbackContainer cc = (CallbackContainer) msg.obj;
                switch (msg.what) {
                    case QUERY_URL_IS_BOOKMARK: {
                        OnQueryUrlIsBookmark cb = (OnQueryUrlIsBookmark) cc.replyTo;
                        String url = (String) cc.args[0];
                        boolean isBookmark = (Boolean) cc.args[1];
                        cb.onQueryUrlIsBookmark(url, isBookmark);
                        break;
                    }
                }
            }
        };
    }

    public void updateVisitedHistory(String url) {
        mDataHandler.sendMessage(HISTORY_UPDATE_VISITED, url);
    }

    public void updateHistoryTitle(String url, String title) {
        mDataHandler.sendMessage(HISTORY_UPDATE_TITLE, new String[] { url, title });
    }

    public void queryBookmarkStatus(String url, OnQueryUrlIsBookmark replyTo) {
        if (url == null || url.trim().length() == 0) {
            // null or empty url is never a bookmark
            replyTo.onQueryUrlIsBookmark(url, false);
            return;
        }
        mDataHandler.sendMessage(QUERY_URL_IS_BOOKMARK, url.trim(), replyTo);
    }

    // The standard Handler and Message classes don't allow the queue manipulation
    // we want (such as peeking). So we use our own queue.
    class DataControllerHandler extends Thread {
        private BlockingQueue<DCMessage> mMessageQueue
                = new LinkedBlockingQueue<DCMessage>();

        @Override
        public void run() {
            super.run();
            while (true) {
                try {
                    handleMessage(mMessageQueue.take());
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }

        void sendMessage(int what, Object obj) {
            DCMessage m = new DCMessage(what, obj);
            mMessageQueue.add(m);
        }

        void sendMessage(int what, Object obj, Object replyTo) {
            DCMessage m = new DCMessage(what, obj);
            m.replyTo = replyTo;
            mMessageQueue.add(m);
        }

        private void handleMessage(DCMessage msg) {
            switch (msg.what) {
            case HISTORY_UPDATE_VISITED:
                doUpdateVisitedHistory((String) msg.obj);
                break;
            case HISTORY_UPDATE_TITLE:
                String[] args = (String[]) msg.obj;
                doUpdateHistoryTitle(args[0], args[1]);
                break;
            case QUERY_URL_IS_BOOKMARK:
                // TODO: Look for identical messages in the queue and remove them
                // TODO: Also, look for partial matches and merge them (such as
                //       multiple callbacks querying the same URL)
                doQueryBookmarkStatus((String) msg.obj, msg.replyTo);
                break;
            }
        }

        private void doUpdateVisitedHistory(String url) {
            ContentResolver cr = mContext.getContentResolver();
            Cursor c = null;
            try {
                c = cr.query(History.CONTENT_URI, new String[] { History._ID, History.VISITS },
                        History.URL + "=?", new String[] { url }, null);
                if (c.moveToFirst()) {
                    ContentValues values = new ContentValues();
                    values.put(History.VISITS, c.getInt(1) + 1);
                    values.put(History.DATE_LAST_VISITED, System.currentTimeMillis());
                    cr.update(ContentUris.withAppendedId(History.CONTENT_URI, c.getLong(0)),
                            values, null, null);
                } else {
                    android.provider.Browser.truncateHistory(cr);
                    ContentValues values = new ContentValues();
                    values.put(History.URL, url);
                    values.put(History.VISITS, 1);
                    values.put(History.DATE_LAST_VISITED, System.currentTimeMillis());
                    values.put(History.TITLE, url);
                    values.put(History.DATE_CREATED, 0);
                    values.put(History.USER_ENTERED, 0);
                    cr.insert(History.CONTENT_URI, values);
                }
            } finally {
                if (c != null) c.close();
            }
        }

        private void doQueryBookmarkStatus(String url, Object replyTo) {
            ContentResolver cr = mContext.getContentResolver();
            // Check to see if the site is bookmarked
            Cursor cursor = null;
            boolean isBookmark = false;
            try {
                cursor = mContext.getContentResolver().query(
                        BookmarkUtils.getBookmarksUri(mContext),
                        new String[] { BrowserContract.Bookmarks.URL },
                        BrowserContract.Bookmarks.URL + " == ?",
                        new String[] { url },
                        null);
                isBookmark = cursor.moveToFirst();
            } catch (SQLiteException e) {
                Log.e(LOGTAG, "Error checking for bookmark: " + e);
            } finally {
                if (cursor != null) cursor.close();
            }
            CallbackContainer cc = new CallbackContainer();
            cc.replyTo = replyTo;
            cc.args = new Object[] { url, isBookmark };
            mCbHandler.obtainMessage(QUERY_URL_IS_BOOKMARK, cc).sendToTarget();
        }

        private void doUpdateHistoryTitle(String url, String title) {
            ContentResolver cr = mContext.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(History.TITLE, title);
            cr.update(History.CONTENT_URI, values, History.URL + "=?",
                    new String[] { url });
        }
    }
}
