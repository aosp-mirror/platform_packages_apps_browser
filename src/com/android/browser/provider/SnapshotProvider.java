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
package com.android.browser.provider;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.provider.BrowserContract;

import java.io.File;

/**
 * This provider is expected to be potentially flaky. It uses a database
 * stored on external storage, which could be yanked unexpectedly.
 */
public class SnapshotProvider extends ContentProvider {

    public static interface Snapshots {

        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                SnapshotProvider.AUTHORITY_URI, "snapshots");
        public static final String _ID = "_id";
        public static final String VIEWSTATE = "view_state";
        public static final String BACKGROUND = "background";
        public static final String TITLE = "title";
        public static final String URL = "url";
        public static final String FAVICON = "favicon";
        public static final String THUMBNAIL = "thumbnail";
        public static final String DATE_CREATED = "date_created";
    }

    public static final String AUTHORITY = "com.android.browser.snapshots";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    static final String TABLE_SNAPSHOTS = "snapshots";
    static final int SNAPSHOTS = 10;
    static final int SNAPSHOTS_ID = 11;
    static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    SnapshotDatabaseHelper mOpenHelper;

    static {
        URI_MATCHER.addURI(AUTHORITY, "snapshots", SNAPSHOTS);
        URI_MATCHER.addURI(AUTHORITY, "snapshots/#", SNAPSHOTS_ID);
    }

    final static class SnapshotDatabaseHelper extends SQLiteOpenHelper {

        static final String DATABASE_NAME = "snapshots.db";
        static final int DATABASE_VERSION = 2;

        public SnapshotDatabaseHelper(Context context) {
            super(context, getFullDatabaseName(context), null, DATABASE_VERSION);
        }

        static String getFullDatabaseName(Context context) {
            File dir = context.getExternalFilesDir(null);
            return new File(dir, DATABASE_NAME).getAbsolutePath();
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_SNAPSHOTS + "(" +
                    Snapshots._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Snapshots.TITLE + " TEXT," +
                    Snapshots.URL + " TEXT NOT NULL," +
                    Snapshots.DATE_CREATED + " INTEGER," +
                    Snapshots.FAVICON + " BLOB," +
                    Snapshots.THUMBNAIL + " BLOB," +
                    Snapshots.BACKGROUND + " INTEGER," +
                    Snapshots.VIEWSTATE + " BLOB NOT NULL" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("DROP TABLE " + TABLE_SNAPSHOTS);
                onCreate(db);
            }
        }

    }

    @Override
    public boolean onCreate() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        getContext().registerReceiver(mExternalStorageReceiver, filter);
        return true;
    }

    final BroadcastReceiver mExternalStorageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mOpenHelper != null) {
                try {
                    mOpenHelper.close();
                } catch (Throwable t) {
                    // We failed to close the open helper, which most likely means
                    // another thread is busy attempting to open the database
                    // or use the database. Let that thread try to gracefully
                    // deal with the error
                }
            }
        }
    };

    SQLiteDatabase getWritableDatabase() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            try {
                if (mOpenHelper == null) {
                    mOpenHelper = new SnapshotDatabaseHelper(getContext());
                }
                return mOpenHelper.getWritableDatabase();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    SQLiteDatabase getReadableDatabase() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            try {
                if (mOpenHelper == null) {
                    mOpenHelper = new SnapshotDatabaseHelper(getContext());
                }
                return mOpenHelper.getReadableDatabase();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = getReadableDatabase();
        if (db == null) {
            return null;
        }
        final int match = URI_MATCHER.match(uri);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String limit = uri.getQueryParameter(BrowserContract.PARAM_LIMIT);
        switch (match) {
        case SNAPSHOTS_ID:
            selection = DatabaseUtils.concatenateWhere(selection, "_id=?");
            selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                    new String[] { Long.toString(ContentUris.parseId(uri)) });
            // fall through
        case SNAPSHOTS:
            qb.setTables(TABLE_SNAPSHOTS);
            break;

        default:
            throw new UnsupportedOperationException("Unknown URL " + uri.toString());
        }
        try {
            Cursor cursor = qb.query(db, projection, selection, selectionArgs,
                    null, null, sortOrder, limit);
            cursor.setNotificationUri(getContext().getContentResolver(),
                    AUTHORITY_URI);
            return cursor;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return null;
        }
        int match = URI_MATCHER.match(uri);
        long id = -1;
        switch (match) {
        case SNAPSHOTS:
            try {
                id = db.insert(TABLE_SNAPSHOTS, Snapshots.TITLE, values);
            } catch (Throwable t) {
                id = -1;
            }
            break;
        default:
            throw new UnsupportedOperationException("Unknown insert URI " + uri);
        }
        if (id < 0) {
            return null;
        }
        Uri inserted = ContentUris.withAppendedId(uri, id);
        getContext().getContentResolver().notifyChange(inserted, null, false);
        return inserted;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return 0;
        }
        int match = URI_MATCHER.match(uri);
        int deleted = 0;
        switch (match) {
        case SNAPSHOTS_ID: {
            selection = DatabaseUtils.concatenateWhere(selection, TABLE_SNAPSHOTS + "._id=?");
            selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                    new String[] { Long.toString(ContentUris.parseId(uri)) });
            // fall through
        }
        case SNAPSHOTS:
            try {
                deleted = db.delete(TABLE_SNAPSHOTS, selection, selectionArgs);
            } catch (Throwable t) {
            }
            break;
        default:
            throw new UnsupportedOperationException("Unknown delete URI " + uri);
        }
        if (deleted > 0) {
            getContext().getContentResolver().notifyChange(uri, null, false);
        }
        return deleted;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("not implemented");
    }

}
