/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.Browser;
import android.util.Log;
import android.text.util.Regex;

public class BrowserProvider extends ContentProvider {

    private SQLiteOpenHelper mOpenHelper;
    private static final String sDatabaseName = "browser.db";
    private static final String TAG = "BrowserProvider";
    private static final String ORDER_BY = "date DESC";

    private static final String[] TABLE_NAMES = new String[] {
        "bookmarks", "searches"
    };
    private static final String[] SUGGEST_PROJECTION = new String [] {
        "0 AS " + SearchManager.SUGGEST_COLUMN_FORMAT,
        "url AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA,
        "url AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
        "title AS " + SearchManager.SUGGEST_COLUMN_TEXT_2,
        "_id"
    };
    private static final String SUGGEST_SELECTION = 
            "url LIKE ? OR url LIKE ? OR url LIKE ? OR url LIKE ?";
    private String[] SUGGEST_ARGS = new String[4];

    // make sure that these match the index of TABLE_NAMES
    private static final int URI_MATCH_BOOKMARKS = 0;
    private static final int URI_MATCH_SEARCHES = 1;
    // (id % 10) should match the table name index
    private static final int URI_MATCH_BOOKMARKS_ID = 10;
    private static final int URI_MATCH_SEARCHES_ID = 11;
    //
    private static final int URI_MATCH_SUGGEST = 20;

    private static final UriMatcher URI_MATCHER;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI("browser", TABLE_NAMES[URI_MATCH_BOOKMARKS],
                URI_MATCH_BOOKMARKS);
        URI_MATCHER.addURI("browser", TABLE_NAMES[URI_MATCH_BOOKMARKS] + "/#",
                URI_MATCH_BOOKMARKS_ID);
        URI_MATCHER.addURI("browser", TABLE_NAMES[URI_MATCH_SEARCHES],
                URI_MATCH_SEARCHES);
        URI_MATCHER.addURI("browser", TABLE_NAMES[URI_MATCH_SEARCHES] + "/#",
                URI_MATCH_SEARCHES_ID);
        URI_MATCHER.addURI("browser", SearchManager.SUGGEST_URI_PATH_QUERY,
                URI_MATCH_SUGGEST);
    }

    // 1 -> 2 add cache table
    // 2 -> 3 update history table
    // 3 -> 4 add passwords table
    // 4 -> 5 add settings table
    // 5 -> 6 ?
    // 6 -> 7 ?
    // 7 -> 8 drop proxy table
    // 8 -> 9 drop settings table
    // 9 -> 10 add form_urls and form_data
    // 10 -> 11 add searches table
    // 11 -> 12 modify cache table
    // 12 -> 13 modify cache table
    // 13 -> 14 correspond with Google Bookmarks schema
    // 14 -> 15 move couple of tables to either browser private database or webview database
    // 15 -> 17 Set it up for the SearchManager
    // 17 -> 18 Added favicon in bookmarks table for Home shortcuts
    // 18 -> 19 Remove labels table
    private static final int DATABASE_VERSION = 19;

    public BrowserProvider() {
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private Context mContext;

        public DatabaseHelper(Context context) {
            super(context, sDatabaseName, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE bookmarks (" +
                    "_id INTEGER PRIMARY KEY," +
                    "title TEXT," +
                    "url TEXT," +
                    "visits INTEGER," +
                    "date LONG," +
                    "created LONG," +
                    "description TEXT," +
                    "bookmark INTEGER," +
                    "favicon BLOB DEFAULT NULL" +
                    ");");

            final CharSequence[] bookmarks = mContext.getResources()
                    .getTextArray(R.array.bookmarks);
            int size = bookmarks.length;
            try {
                for (int i = 0; i < size; i = i + 2) {
                    db.execSQL("INSERT INTO bookmarks (title, url, visits, " +
                            "date, created, bookmark)" + " VALUES('" +
                            bookmarks[i] + "', '" + bookmarks[i + 1] + 
                            "', 0, 0, 0, 1);");
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            }

            db.execSQL("CREATE TABLE searches (" +
                    "_id INTEGER PRIMARY KEY," +
                    "search TEXT," +
                    "date LONG" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            if (oldVersion == 18) {
                db.execSQL("DROP TABLE IF EXISTS labels");
            } else {
                db.execSQL("DROP TABLE IF EXISTS bookmarks");
                db.execSQL("DROP TABLE IF EXISTS searches");
                onCreate(db);
            }
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    /*
     * Subclass AbstractCursor so we can add "Google Search"
     */
    private class MySuggestionCursor extends AbstractCursor {
        private Cursor  mCursor;
        private boolean mBeyondCursor;
        private String  mString;
        private Uri     mNotifyUri;
        private ContentResolver mContentResolver;
        private AbstractCursor.SelfContentObserver mObserver;
        private final Object mObserverLock = new Object();

        public MySuggestionCursor(Cursor c, String string) {
            mCursor = c;
            if (Regex.WEB_URL_PATTERN.matcher(string).matches()) {
                mString = "";
            } else {
                mString = string;
            }
            mBeyondCursor = false;
        }

        public boolean onMove(int oldPosition, int newPosition) {
            if (mCursor.getCount() == newPosition) {
                mBeyondCursor = true;
            } else {
                mCursor.moveToPosition(newPosition);
                mBeyondCursor = false;
            }
            return true;
        }

        public int getCount() {
            if (mString.length() > 0) {
                return mCursor.getCount() + 1;
            } else {
                return mCursor.getCount();
            }
        }

        public boolean deleteRow() {
            return !mBeyondCursor && mCursor.deleteRow();
        }

        public String[] getColumnNames() {
            return mCursor.getColumnNames();
        }

        public int getColumnCount() {
            return mCursor.getColumnCount();
        }

        public String getString(int columnIndex) {
            if (!mBeyondCursor) {
                return mCursor.getString(columnIndex);
            }
            switch (columnIndex) {
                case 2: // SearchManager.SUGGEST_COLUMN_TEXT_1
                    return "Google Search for \"" + mString + "\"";
                case 1: // SearchManager.SUGGEST_COLUMN_INTENT_DATA
                    return BrowserActivity.composeSearchUrl(mString);
                case 3: // SearchManager.SUGGEST_COLUMN_TEXT_2
                default:
                    return "";
            }
        }

        public short getShort(int columnIndex) {
            if (!mBeyondCursor) {
                return mCursor.getShort(columnIndex);
            }
            if (0 == columnIndex) {
                return 0;
            }
            return -1;
        }

        public int getInt(int columnIndex) {
            if (!mBeyondCursor) {
                return mCursor.getInt(columnIndex);
            }
            if (0 == columnIndex) {
                return 0;
            }
            return -1;
        }

        public long getLong(int columnIndex) {
            if (!mBeyondCursor) {
                return mCursor.getLong(columnIndex);
            }
            if (0 == columnIndex) {
                return 0;
            }
            return -1;
        }

        public float getFloat(int columnIndex) {
            if (!mBeyondCursor) {
                return mCursor.getFloat(columnIndex);
            }
            if (0 == columnIndex) {
                return 0f;
            }
            return -1f;
        }

        public double getDouble(int columnIndex) {
            if (!mBeyondCursor) {
                return mCursor.getDouble(columnIndex);
            }
            if (0 == columnIndex) {
                return 0.0;
            }
            return -1.0;
        }

        public boolean isNull(int columnIndex) {
            return mCursor.isNull(columnIndex);
        }

        public boolean supportsUpdates() {
            return false;
        }

        public boolean hasUpdates() {
            return false;
        }

        public boolean updateString(int columnIndex, String value) {
            return false;
        }

        public boolean updateShort(int columnIndex, short value) {
            return false;
        }

        public boolean updateInt(int columnIndex, int value) {
            return false;
        }

        public boolean updateLong(int columnIndex, long value) {
            return false;
        }

        public boolean updateFloat(int columnIndex, float value) {
            return false;
        }

        public boolean updateDouble(int columnIndex, double value) {
            return false;
        }

        // TODO Temporary change, finalize after jq's changes go in
        public void deactivate() {
            if (mCursor != null) {
                mCursor.deactivate();
            }
            super.deactivate();
        }

        public boolean requery() {
            return mCursor.requery();
        }

        // TODO Temporary change, finalize after jq's changes go in
        public void close() {
            super.close();
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }
        }

        public void registerContentObserver(ContentObserver observer) {
            super.registerContentObserver(observer);
        }

        public void unregisterContentObserver(ContentObserver observer) {
            super.unregisterContentObserver(observer);
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            super.registerDataSetObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            super.unregisterDataSetObserver(observer);
        }

        protected void onChange(boolean selfChange) {
            synchronized (mObserverLock) {
                super.onChange(selfChange);
                if (mNotifyUri != null && selfChange) {
                    mContentResolver.notifyChange(mNotifyUri, mObserver);
                }
            }
        }

        public void setNotificationUri(ContentResolver cr, Uri uri) {
            synchronized (mObserverLock) {
                if (mObserver != null) {
                    cr.unregisterContentObserver(mObserver);
                }
                mObserver = new AbstractCursor.SelfContentObserver(this);
                cr.registerContentObserver(uri, true, mObserver);
                mCursor.setNotificationUri(cr, uri);
                super.setNotificationUri(cr, uri);
                mContentResolver = cr;
                mNotifyUri = uri;
            }
        }

        public boolean getWantsAllOnMoveCalls() {
            return mCursor.getWantsAllOnMoveCalls();
        }
    }

    @Override
    public Cursor query(Uri url, String[] projectionIn, String selection,
            String[] selectionArgs, String sortOrder) 
            throws IllegalStateException {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        int match = URI_MATCHER.match(url);
        if (match == -1) {
            throw new IllegalArgumentException("Unknown URL");
        }

        if (match == URI_MATCH_SUGGEST) {
            String suggestSelection;
            String [] myArgs;
            if (selectionArgs[0] == null || selectionArgs[0].equals("")) {
                suggestSelection = null;
                myArgs = null;
            } else {
                String like = selectionArgs[0] + "%";
                SUGGEST_ARGS[0] = "http://" + like;
                SUGGEST_ARGS[1] = "http://www." + like;
                SUGGEST_ARGS[2] = "https://" + like;
                SUGGEST_ARGS[3] = "https://www." + like;
                myArgs = SUGGEST_ARGS;
                suggestSelection = SUGGEST_SELECTION;
            }
            // Suggestions are always performed with the default sort order:
            // date ASC.
            Cursor c = db.query(TABLE_NAMES[URI_MATCH_BOOKMARKS],
                    SUGGEST_PROJECTION, suggestSelection, myArgs, null, null,
                    ORDER_BY, null);
            c.setNotificationUri(getContext().getContentResolver(), url);
            return new MySuggestionCursor(c, selectionArgs[0]);
        }

        String[] projection = null;
        if (projectionIn != null && projectionIn.length > 0) {
            projection = new String[projectionIn.length + 1];
            System.arraycopy(projectionIn, 0, projection, 0, projectionIn.length);
            projection[projectionIn.length] = "_id AS _id";
        }

        StringBuilder whereClause = new StringBuilder(256);
        if (match == URI_MATCH_BOOKMARKS_ID || match == URI_MATCH_SEARCHES_ID) {
            whereClause.append("(_id = ").append(url.getPathSegments().get(1))
                    .append(")");
        }

        // Tack on the user's selection, if present
        if (selection != null && selection.length() > 0) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }

            whereClause.append('(');
            whereClause.append(selection);
            whereClause.append(')');
        }
        Cursor c = db.query(TABLE_NAMES[match % 10], projection,
                whereClause.toString(), selectionArgs, null, null, sortOrder,
                null);
        c.setNotificationUri(getContext().getContentResolver(), url);
        return c;
    }

    @Override
    public String getType(Uri url) {
        int match = URI_MATCHER.match(url);
        switch (match) {
            case URI_MATCH_BOOKMARKS:
                return "vnd.android.cursor.dir/bookmark";

            case URI_MATCH_BOOKMARKS_ID:
                return "vnd.android.cursor.item/bookmark";

            case URI_MATCH_SEARCHES:
                return "vnd.android.cursor.dir/searches";

            case URI_MATCH_SEARCHES_ID:
                return "vnd.android.cursor.item/searches";

            case URI_MATCH_SUGGEST:
                return SearchManager.SUGGEST_MIME_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URL");
        }
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int match = URI_MATCHER.match(url);
        Uri uri = null;
        switch (match) {
            case URI_MATCH_BOOKMARKS: {
                // Insert into the bookmarks table
                long rowID = db.insert(TABLE_NAMES[URI_MATCH_BOOKMARKS], "url",
                        initialValues);
                if (rowID > 0) {
                    uri = ContentUris.withAppendedId(Browser.BOOKMARKS_URI,
                            rowID);
                }
                break;
            }

            case URI_MATCH_SEARCHES: {
                // Insert into the searches table
                long rowID = db.insert(TABLE_NAMES[URI_MATCH_SEARCHES], "url",
                        initialValues);
                if (rowID > 0) {
                    uri = ContentUris.withAppendedId(Browser.SEARCHES_URI,
                            rowID);
                }
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown URL");
        }

        if (uri == null) {
            throw new IllegalArgumentException("Unknown URL");
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return uri;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int match = URI_MATCHER.match(url);
        if (match == -1 || match == URI_MATCH_SUGGEST) {
            throw new IllegalArgumentException("Unknown URL");
        }

        if (match == URI_MATCH_BOOKMARKS_ID || match == URI_MATCH_SEARCHES_ID) {
            StringBuilder sb = new StringBuilder();
            if (where != null && where.length() > 0) {
                sb.append("( ");
                sb.append(where);
                sb.append(" ) AND ");
            }
            sb.append("_id = ");
            sb.append(url.getPathSegments().get(1));
            where = sb.toString();
        }

        int count = db.delete(TABLE_NAMES[match % 10], where, whereArgs);
        getContext().getContentResolver().notifyChange(url, null);
        return count;
    }

    @Override
    public int update(Uri url, ContentValues values, String where,
            String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int match = URI_MATCHER.match(url);
        if (match == -1 || match == URI_MATCH_SUGGEST) {
            throw new IllegalArgumentException("Unknown URL");
        }

        if (match == URI_MATCH_BOOKMARKS_ID || match == URI_MATCH_SEARCHES_ID) {
            StringBuilder sb = new StringBuilder();
            if (where != null && where.length() > 0) {
                sb.append("( ");
                sb.append(where);
                sb.append(" ) AND ");
            }
            sb.append("_id = ");
            sb.append(url.getPathSegments().get(1));
            where = sb.toString();
        }

        int ret = db.update(TABLE_NAMES[match % 10], values, where, whereArgs);
        getContext().getContentResolver().notifyChange(url, null);
        return ret;
    }
}
