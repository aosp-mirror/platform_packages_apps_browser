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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.BrowserContract.History;

public class DataController {
    // Message IDs
    private static final int HISTORY_UPDATE_VISITED = 100;
    private static final int HISTORY_UPDATE_TITLE = 101;
    private static DataController sInstance;

    private Context mContext;
    private Handler mHandler;

    /* package */ static DataController getInstance(Context c) {
        if (sInstance == null) {
            sInstance = new DataController(c);
        }
        return sInstance;
    }

    private DataController(Context c) {
        mContext = c.getApplicationContext();
        HandlerThread thread = new HandlerThread("DataController");
        thread.setDaemon(true);
        thread.start();
        mHandler = new DataControllerHandler(thread.getLooper());
    }

    public void updateVisitedHistory(String url) {
        mHandler.obtainMessage(HISTORY_UPDATE_VISITED, url).sendToTarget();
    }

    public void updateHistoryTitle(String url, String title) {
        mHandler.obtainMessage(HISTORY_UPDATE_TITLE, new String[] { url, title })
                .sendToTarget();
    }

    class DataControllerHandler extends Handler {
        public DataControllerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case HISTORY_UPDATE_VISITED:
                doUpdateVisitedHistory((String) msg.obj);
                break;
            case HISTORY_UPDATE_TITLE:
                String[] args = (String[]) msg.obj;
                doUpdateHistoryTitle(args[0], args[1]);
                break;
            }
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

    private void doUpdateHistoryTitle(String url, String title) {
        ContentResolver cr = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(History.TITLE, title);
        cr.update(History.CONTENT_URI, values, History.URL + "=?",
                new String[] { url });
    }
}
