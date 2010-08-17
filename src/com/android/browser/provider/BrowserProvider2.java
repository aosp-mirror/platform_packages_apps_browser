/*
 * Copyright (C) 2010 he Android Open Source Project
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
 * limitations under the License
 */

package com.android.browser.provider;

import com.android.browser.R;
import com.android.internal.content.SyncStateContentProviderHelper;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;
import android.provider.BrowserContract.ChromeSyncColumns;
import android.provider.BrowserContract.History;
import android.provider.BrowserContract.Searches;
import android.provider.BrowserContract.SyncState;
import android.provider.ContactsContract.RawContacts;
import android.provider.SyncStateContract;
import android.text.TextUtils;

import java.util.HashMap;

public class BrowserProvider2 extends SQLiteContentProvider {

    static final Uri LEGACY_BROWSER_AUTHORITY_URI = Uri.parse("browser");

    static final String TABLE_BOOKMARKS = "bookmarks";
    static final String TABLE_HISTORY = "history";
    static final String TABLE_SEARCHES = "searches";
    static final String TABLE_SYNC_STATE = "syncstate";

    static final String DEFAULT_HISTORY_SORT = History.DATE_LAST_VISITED + " DESC";
    
    static final int BOOKMARKS = 1000;
    static final int BOOKMARKS_ID = 1001;
    static final int BOOKMARKS_FOLDER = 1002;
    static final int BOOKMARKS_FOLDER_ID = 1003;

    static final int HISTORY = 2000;
    static final int HISTORY_ID = 2001;

    static final int SEARCHES = 3000;
    static final int SEARCHES_ID = 3001;

    static final int SYNCSTATE = 4000;
    static final int SYNCSTATE_ID = 4001;

    static final long FIXED_ID_CHROME_ROOT = 1;
    static final long FIXED_ID_BOOKMARKS = 2;
    static final long FIXED_ID_BOOKMARKS_BAR = 3;
    static final long FIXED_ID_OTHER_BOOKMARKS = 4;

    static final String DEFAULT_BOOKMARKS_SORT_ORDER = "position ASC, _id ASC";
    
    static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static final HashMap<String, String> BOOKMARKS_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> OTHER_BOOKMARKS_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> HISTORY_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> SEARCHES_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> SYNC_STATE_PROJECTION_MAP = new HashMap<String, String>();

    static {
        final UriMatcher matcher = URI_MATCHER;
        matcher.addURI(BrowserContract.AUTHORITY, "bookmarks", BOOKMARKS);
        matcher.addURI(BrowserContract.AUTHORITY, "bookmarks/#", BOOKMARKS_ID);
        matcher.addURI(BrowserContract.AUTHORITY, "bookmarks/folder", BOOKMARKS_FOLDER);
        matcher.addURI(BrowserContract.AUTHORITY, "bookmarks/folder/#", BOOKMARKS_FOLDER_ID);
        matcher.addURI(BrowserContract.AUTHORITY, "history", HISTORY);
        matcher.addURI(BrowserContract.AUTHORITY, "history/#", HISTORY_ID);
        matcher.addURI(BrowserContract.AUTHORITY, "searches", SEARCHES);
        matcher.addURI(BrowserContract.AUTHORITY, "searches/#", SEARCHES_ID);
        matcher.addURI(BrowserContract.AUTHORITY, "syncstate", SYNCSTATE);
        matcher.addURI(BrowserContract.AUTHORITY, "syncstate/#", SYNCSTATE_ID);

        // Bookmarks
        HashMap<String, String> map = BOOKMARKS_PROJECTION_MAP;
        map.put(Bookmarks.TITLE, Bookmarks.TITLE);
        map.put(Bookmarks.URL, Bookmarks.URL);
        map.put(Bookmarks.FAVICON, Bookmarks.FAVICON);
        map.put(Bookmarks.THUMBNAIL, Bookmarks.THUMBNAIL);
        map.put(Bookmarks.TOUCH_ICON, Bookmarks.TOUCH_ICON);
        map.put(Bookmarks._ID, TABLE_BOOKMARKS + "._id AS _id");
        map.put(Bookmarks.IS_FOLDER, Bookmarks.IS_FOLDER);
        map.put(Bookmarks.PARENT, Bookmarks.PARENT);
        map.put(Bookmarks.POSITION, Bookmarks.POSITION);
        map.put(Bookmarks.IS_DELETED, Bookmarks.IS_DELETED);
        map.put(Bookmarks.ACCOUNT_NAME, Bookmarks.ACCOUNT_NAME);
        map.put(Bookmarks.ACCOUNT_TYPE, Bookmarks.ACCOUNT_TYPE);
        map.put(Bookmarks.SOURCE_ID, Bookmarks.SOURCE_ID);
        map.put(Bookmarks.VERSION, Bookmarks.VERSION);
        map.put(Bookmarks.DIRTY, Bookmarks.DIRTY);
        map.put(Bookmarks.SYNC1, Bookmarks.SYNC1);
        map.put(Bookmarks.SYNC2, Bookmarks.SYNC2);
        map.put(Bookmarks.SYNC3, Bookmarks.SYNC3);
        map.put(Bookmarks.SYNC4, Bookmarks.SYNC4);
        map.put(Bookmarks.SYNC5, Bookmarks.SYNC5);

        // Other bookmarks
        OTHER_BOOKMARKS_PROJECTION_MAP.putAll(BOOKMARKS_PROJECTION_MAP);
        OTHER_BOOKMARKS_PROJECTION_MAP.put(Bookmarks.POSITION,
                Long.toString(Long.MAX_VALUE) + " AS " + Bookmarks.POSITION);

        // History
        map = HISTORY_PROJECTION_MAP;
        map.put(History._ID, qualifyColumn(TABLE_HISTORY, History._ID));
        map.put(History.TITLE, Bookmarks.TITLE);
        map.put(History.URL, Bookmarks.URL);
        map.put(History.FAVICON, Bookmarks.FAVICON);
        map.put(History.THUMBNAIL, Bookmarks.THUMBNAIL);
        map.put(History.TOUCH_ICON, Bookmarks.TOUCH_ICON);
        map.put(History.DATE_CREATED, History.DATE_CREATED);
        map.put(History.DATE_LAST_VISITED, History.DATE_LAST_VISITED);
        map.put(History.VISITS, History.VISITS);
        map.put(History.USER_ENTERED, History.USER_ENTERED);

        // Sync state
        map = SYNC_STATE_PROJECTION_MAP;
        map.put(SyncState._ID, SyncState._ID);
        map.put(SyncState.ACCOUNT_NAME, SyncState.ACCOUNT_NAME);
        map.put(SyncState.ACCOUNT_TYPE, SyncState.ACCOUNT_TYPE);
        map.put(SyncState.DATA, SyncState.DATA);
    }

    static final String qualifyColumn(String table, String column) {
        return table + "." + column + " AS " + column;
    }
    
    DatabaseHelper mOpenHelper;
    SyncStateContentProviderHelper mSyncHelper = new SyncStateContentProviderHelper();

    final class DatabaseHelper extends SQLiteOpenHelper {
        static final String DATABASE_NAME = "browser2.db";
        static final int DATABASE_VERSION = 15;
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + "(" +
                    Bookmarks._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Bookmarks.TITLE + " TEXT," +
                    Bookmarks.URL + " TEXT," +
                    Bookmarks.FAVICON + " BLOB," +
                    Bookmarks.THUMBNAIL + " BLOB," +
                    Bookmarks.TOUCH_ICON + " BLOB," +
                    Bookmarks.IS_FOLDER + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.PARENT + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.POSITION + " INTEGER NOT NULL," +
                    Bookmarks.INSERT_AFTER + " INTEGER," +
                    Bookmarks.IS_DELETED + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.ACCOUNT_NAME + " TEXT," +
                    Bookmarks.ACCOUNT_TYPE + " TEXT," +
                    Bookmarks.SOURCE_ID + " TEXT," +
                    Bookmarks.VERSION + " INTEGER NOT NULL DEFAULT 1," +
                    Bookmarks.DIRTY + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.SYNC1 + " TEXT," +
                    Bookmarks.SYNC2 + " TEXT," +
                    Bookmarks.SYNC3 + " TEXT," +
                    Bookmarks.SYNC4 + " TEXT," +
                    Bookmarks.SYNC5 + " TEXT" +
                    ");");

            // TODO indices

            createDefaultBookmarks(db);

            db.execSQL("CREATE TABLE " + TABLE_HISTORY + "(" +
                    History._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    History.TITLE + " TEXT," +
                    History.URL + " TEXT NOT NULL," +
                    History.FAVICON + " BLOB," +
                    History.THUMBNAIL + " BLOB," +
                    History.TOUCH_ICON + " BLOB," +
                    History.DATE_CREATED + " INTEGER," +
                    History.DATE_LAST_VISITED + " INTEGER," +
                    History.VISITS + " INTEGER NOT NULL DEFAULT 0," +
                    History.USER_ENTERED + " INTEGER" +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_SEARCHES + " (" +
                    Searches._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Searches.SEARCH + " TEXT," +
                    Searches.DATE + " LONG" +
                    ");");

            mSyncHelper.createDatabase(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO write upgrade logic
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCHES);
            onCreate(db);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            mSyncHelper.onDatabaseOpened(db);
        }
        
        private void createDefaultBookmarks(SQLiteDatabase db) {
            ContentValues values = new ContentValues();
            // TODO figure out how to deal with localization for the defaults

            // Chrome sync root folder
            values.put(Bookmarks._ID, FIXED_ID_CHROME_ROOT);
            values.put(ChromeSyncColumns.SERVER_UNIQUE, ChromeSyncColumns.FOLDER_NAME_ROOT);
            values.put(Bookmarks.TITLE, "Google Chrome");
            values.put(Bookmarks.PARENT, 0);
            values.put(Bookmarks.POSITION, 0);
            values.put(Bookmarks.IS_FOLDER, true);
            values.put(Bookmarks.DIRTY, true);
            db.insertOrThrow(TABLE_BOOKMARKS, null, values);

            // Bookmarks folder
            values.put(Bookmarks._ID, FIXED_ID_BOOKMARKS);
            values.put(ChromeSyncColumns.SERVER_UNIQUE, ChromeSyncColumns.FOLDER_NAME_BOOKMARKS);
            values.put(Bookmarks.TITLE, "Bookmarks");
            values.put(Bookmarks.PARENT, FIXED_ID_CHROME_ROOT);
            values.put(Bookmarks.POSITION, 0);
            values.put(Bookmarks.IS_FOLDER, true);
            values.put(Bookmarks.DIRTY, true);
            db.insertOrThrow(TABLE_BOOKMARKS, null, values);

            // Bookmarks Bar folder
            values.clear();
            values.put(Bookmarks._ID, FIXED_ID_BOOKMARKS_BAR);
            values.put(ChromeSyncColumns.SERVER_UNIQUE,
                    ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR);
            values.put(Bookmarks.TITLE, "Bookmarks Bar");
            values.put(Bookmarks.PARENT, FIXED_ID_BOOKMARKS);
            values.put(Bookmarks.POSITION, 0);
            values.put(Bookmarks.IS_FOLDER, true);
            values.put(Bookmarks.DIRTY, true);
            db.insertOrThrow(TABLE_BOOKMARKS, null, values);

            // Other Bookmarks folder
            values.clear();
            values.put(Bookmarks._ID, FIXED_ID_OTHER_BOOKMARKS);
            values.put(ChromeSyncColumns.SERVER_UNIQUE,
                    ChromeSyncColumns.FOLDER_NAME_OTHER_BOOKMARKS);
            values.put(Bookmarks.TITLE, "Other Bookmarks");
            values.put(Bookmarks.PARENT, FIXED_ID_BOOKMARKS);
            values.put(Bookmarks.POSITION, 1000);
            values.put(Bookmarks.IS_FOLDER, true);
            values.put(Bookmarks.DIRTY, true);
            db.insertOrThrow(TABLE_BOOKMARKS, null, values);

            addDefaultBookmarks(db, FIXED_ID_BOOKMARKS_BAR);

            // TODO remove this testing code
            db.execSQL("INSERT INTO bookmarks (" +
                    Bookmarks.TITLE + ", " +
                    Bookmarks.URL + ", " +
                    Bookmarks.IS_FOLDER + "," +
                    Bookmarks.PARENT + "," +
                    Bookmarks.POSITION +
                ") VALUES (" +
                    "'Google Reader', " +
                    "'http://reader.google.com', " +
                    "0," +
                    Long.toString(FIXED_ID_OTHER_BOOKMARKS) + "," +
                    0 +
                    ");");
        }

        private void addDefaultBookmarks(SQLiteDatabase db, long parentId) {
            final CharSequence[] bookmarks = getContext().getResources().getTextArray(
                    R.array.bookmarks);
            int size = bookmarks.length;
            try {
                for (int i = 0; i < size; i = i + 2) {
                    CharSequence bookmarkDestination = replaceSystemPropertyInString(getContext(),
                            bookmarks[i + 1]);
                    db.execSQL("INSERT INTO bookmarks (" +
                            Bookmarks.TITLE + ", " +
                            Bookmarks.URL + ", " +
                            Bookmarks.IS_FOLDER + "," +
                            Bookmarks.PARENT + "," +
                            Bookmarks.POSITION +
                        ") VALUES (" +
                            "'" + bookmarks[i] + "', " +
                            "'" + bookmarkDestination + "', " +
                            "0," +
                            Long.toString(parentId) + "," +
                            Integer.toString(i) +
                            ");");
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }

        // XXX: This is a major hack to remove our dependency on gsf constants and
        // its content provider. http://b/issue?id=2425179
        private String getClientId(ContentResolver cr) {
            String ret = "android-google";
            Cursor c = null;
            try {
                c = cr.query(Uri.parse("content://com.google.settings/partner"),
                        new String[] { "value" }, "name='client_id'", null, null);
                if (c != null && c.moveToNext()) {
                    ret = c.getString(0);
                }
            } catch (RuntimeException ex) {
                // fall through to return the default
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            return ret;
        }

        private CharSequence replaceSystemPropertyInString(Context context, CharSequence srcString) {
            StringBuffer sb = new StringBuffer();
            int lastCharLoc = 0;

            final String client_id = getClientId(context.getContentResolver());

            for (int i = 0; i < srcString.length(); ++i) {
                char c = srcString.charAt(i);
                if (c == '{') {
                    sb.append(srcString.subSequence(lastCharLoc, i));
                    lastCharLoc = i;
              inner:
                    for (int j = i; j < srcString.length(); ++j) {
                        char k = srcString.charAt(j);
                        if (k == '}') {
                            String propertyKeyValue = srcString.subSequence(i + 1, j).toString();
                            if (propertyKeyValue.equals("CLIENT_ID")) {
                                sb.append(client_id);
                            } else {
                                sb.append("unknown");
                            }
                            lastCharLoc = j + 1;
                            i = j;
                            break inner;
                        }
                    }
                }
            }
            if (srcString.length() - lastCharLoc > 0) {
                // Put on the tail, if there is one
                sb.append(srcString.subSequence(lastCharLoc, srcString.length()));
            }
            return sb;
        }
    }

    @Override
    public SQLiteOpenHelper getDatabaseHelper(Context context) {
        synchronized (this) {
            if (mOpenHelper == null) {
                mOpenHelper = new DatabaseHelper(context);
            }
            return mOpenHelper;
        }
    }

    @Override
    public boolean isCallerSyncAdapter(Uri uri) {
        return uri.getBooleanQueryParameter(BrowserContract.CALLER_IS_SYNCADAPTER, false);
    }

    @Override
    public void notifyChange(boolean callerIsSyncAdapter) {
        ContentResolver resolver = getContext().getContentResolver();
        resolver.notifyChange(BrowserContract.AUTHORITY_URI, null, !callerIsSyncAdapter);
        resolver.notifyChange(LEGACY_BROWSER_AUTHORITY_URI, null, !callerIsSyncAdapter);
    }

    @Override
    public String getType(Uri uri) {
        final int match = URI_MATCHER.match(uri);
        switch (match) {
            case BOOKMARKS:
                return Bookmarks.CONTENT_TYPE;
            case BOOKMARKS_ID:
                return Bookmarks.CONTENT_ITEM_TYPE;
            case HISTORY:
                return History.CONTENT_TYPE;
            case HISTORY_ID:
                return History.CONTENT_ITEM_TYPE;
            case SEARCHES:
                return Searches.CONTENT_TYPE;
            case SEARCHES_ID:
                return Searches.CONTENT_ITEM_TYPE;
//            case SUGGEST:
//                return SearchManager.SUGGEST_MIME_TYPE;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        final int match = URI_MATCHER.match(uri);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (match) {
            case BOOKMARKS_FOLDER_ID:
            case BOOKMARKS_ID:
            case BOOKMARKS: {
                // Only show deleted bookmarks if requested to do so
                if (!uri.getBooleanQueryParameter(Bookmarks.QUERY_PARAMETER_SHOW_DELETED, false)) {
                    selection = DatabaseUtils.concatenateWhere(
                            Bookmarks.IS_DELETED + "=0", selection);
                }

                if (match == BOOKMARKS_ID) {
                    // Tack on the ID of the specific bookmark requested
                    selection = DatabaseUtils.concatenateWhere(
                            TABLE_BOOKMARKS + "." + Bookmarks._ID + "=?", selection);
                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                            new String[] { Long.toString(ContentUris.parseId(uri)) });
                } else if (match == BOOKMARKS_FOLDER_ID) {
                    // Tack on the ID of the specific folder requested
                    selection = DatabaseUtils.concatenateWhere(
                            TABLE_BOOKMARKS + "." + Bookmarks.PARENT + "=?", selection);
                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                            new String[] { Long.toString(ContentUris.parseId(uri)) });
                }

                if (TextUtils.isEmpty(sortOrder)) {
                    sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER;
                }
                
                qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
                qb.setTables(TABLE_BOOKMARKS);
                break;
            }

            case BOOKMARKS_FOLDER: {
                // Don't allow selections to be applied to the default folder
                if (!TextUtils.isEmpty(selection) || selectionArgs != null) {
                    throw new UnsupportedOperationException(
                            "selections aren't supported on this URI");
                }

                qb.setTables(TABLE_BOOKMARKS);
                qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
                String bookmarksBarQuery = qb.buildQuery(projection,
                        Bookmarks.PARENT + "=?",
                        null, null, null, null, null);

                qb.setProjectionMap(OTHER_BOOKMARKS_PROJECTION_MAP);
                String otherBookmarksQuery = qb.buildQuery(projection,
                        Bookmarks._ID + "=?",
                        null, null, null, null, null);

                String query = qb.buildUnionQuery(
                        new String[] { bookmarksBarQuery, otherBookmarksQuery },
                        DEFAULT_BOOKMARKS_SORT_ORDER, null);

                return db.rawQuery(query, new String[] {
                        Long.toString(FIXED_ID_BOOKMARKS_BAR),
                        Long.toString(FIXED_ID_OTHER_BOOKMARKS)});
            }

            case HISTORY_ID: {
                selection = DatabaseUtils.concatenateWhere(
                        TABLE_HISTORY + "._id=?", selection);
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case HISTORY: {
                if (sortOrder == null) {
                    sortOrder = DEFAULT_HISTORY_SORT;
                }
                qb.setProjectionMap(HISTORY_PROJECTION_MAP);
                qb.setTables(TABLE_HISTORY);
                break;
            }

            case SYNCSTATE: {
                return mSyncHelper.query(db, projection, selection, selectionArgs, sortOrder);
            }

            case SYNCSTATE_ID: {
                selection = appendAccountToSelection(uri, selection);
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mSyncHelper.query(db, projection, selectionWithId, selectionArgs, sortOrder);
            }
            
            default: {
                throw new UnsupportedOperationException("Unknown URL " + uri.toString());
            }
        }

        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), BrowserContract.AUTHORITY_URI);
        return cursor;
    }

    @Override
    public int deleteInTransaction(Uri uri, String selection, String[] selectionArgs,
            boolean callerIsSyncAdapter) {
        final int match = URI_MATCHER.match(uri);
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (match) {
            case BOOKMARKS_ID:
            case BOOKMARKS: {
                //TODO cascade deletes down from folders
                if (!callerIsSyncAdapter) {
                    // If the caller isn't a sync adapter just go through and update all the
                    // bookmarks to have the deleted flag set.
                    ContentValues values = new ContentValues();
                    values.put(Bookmarks.IS_DELETED, 1);
                    return updateInTransaction(uri, values, selection, selectionArgs,
                            callerIsSyncAdapter);
                } else {
                    // Sync adapters are allowed to actually delete things
                    if (match == BOOKMARKS_ID) {
                        selection = DatabaseUtils.concatenateWhere(selection,
                                TABLE_BOOKMARKS + "._id=?");
                        selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                                new String[] { Long.toString(ContentUris.parseId(uri)) });
                    }
                    return db.delete(TABLE_BOOKMARKS, selection, selectionArgs);
                }
            }

            case HISTORY_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case HISTORY: {
                return db.delete(TABLE_HISTORY, selection, selectionArgs);
            }

            case SEARCHES_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_SEARCHES + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case SEARCHES: {
                return db.delete(TABLE_SEARCHES, selection, selectionArgs);
            }

            case SYNCSTATE: {
                return mSyncHelper.delete(db, selection, selectionArgs);
            }
            case SYNCSTATE_ID: {
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mSyncHelper.delete(db, selectionWithId, selectionArgs);
            }
        }
        throw new UnsupportedOperationException("Unknown update URI " + uri);
    }

    @Override
    public Uri insertInTransaction(Uri uri, ContentValues values, boolean callerIsSyncAdapter) {
        final int match = URI_MATCHER.match(uri);
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long id = -1;
        switch (match) {
            case BOOKMARKS: {
                // Mark rows dirty if they're not coming from a sync adapater
                if (!callerIsSyncAdapter) {
                    values.put(Bookmarks.DIRTY, 1);
                }

                // If no parent is set default to the "Bookmarks Bar" folder
                if (!values.containsKey(Bookmarks.PARENT)) {
                    values.put(Bookmarks.PARENT, FIXED_ID_BOOKMARKS_BAR);
                }

                // If no position is requested put the bookmark at the beginning of the list
                if (!values.containsKey(Bookmarks.POSITION)) {
                    values.put(Bookmarks.POSITION, Long.toString(Long.MIN_VALUE));
                }

                id = db.insertOrThrow(TABLE_BOOKMARKS, Bookmarks.DIRTY, values);
                break;
            }

            case HISTORY: {
                id = db.insertOrThrow(TABLE_HISTORY, History.VISITS, values);
                break;
            }

            case SEARCHES: {
                id = db.insertOrThrow(TABLE_SEARCHES, Searches.SEARCH, values);
                break;
            }

            case SYNCSTATE: {
                id = mSyncHelper.insert(mDb, values);
                break;
            }

            default: {
                throw new UnsupportedOperationException("Unknown insert URI " + uri);
            }
        }

        if (id >= 0) {
            return ContentUris.withAppendedId(uri, id);
        } else {
            return null;
        }
    }

    @Override
    public int updateInTransaction(Uri uri, ContentValues values, String selection,
            String[] selectionArgs, boolean callerIsSyncAdapter) {
        final int match = URI_MATCHER.match(uri);
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (match) {
            case BOOKMARKS_ID: {
                // Mark the bookmark dirty if the caller isn't a sync adapter
                if (!callerIsSyncAdapter) {
                    values = new ContentValues(values);
                    values.put(Bookmarks.DIRTY, 1);
                }
                selection = DatabaseUtils.concatenateWhere(selection,
                        TABLE_BOOKMARKS + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                return db.update(TABLE_BOOKMARKS, values, selection, selectionArgs); 
            }

            case BOOKMARKS: {
                if (!callerIsSyncAdapter) {
                    values = new ContentValues(values);
                    values.put(Bookmarks.DIRTY, 1);
                }
                return updateBookmarksInTransaction(values, selection, selectionArgs);
            }

            case HISTORY_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case HISTORY: {
                return db.update(TABLE_HISTORY, values, selection, selectionArgs);
            }

            case SEARCHES_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_SEARCHES + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case SEARCHES: {
                return db.update(TABLE_SEARCHES, values, selection, selectionArgs);
            }

            case SYNCSTATE: {
                return mSyncHelper.update(mDb, values,
                        appendAccountToSelection(uri, selection), selectionArgs);
            }

            case SYNCSTATE_ID: {
                selection = appendAccountToSelection(uri, selection);
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mSyncHelper.update(mDb, values,
                        selectionWithId, selectionArgs);
            }
        }
        throw new UnsupportedOperationException("Unknown update URI " + uri);
    }

    /**
     * Does a query to find the matching bookmarks and updates each one with the provided values.
     */
    private int updateBookmarksInTransaction(ContentValues values, String selection,
            String[] selectionArgs) {
        int count = 0;
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor cursor = query(Bookmarks.CONTENT_URI, new String[] { Bookmarks._ID },
                selection, selectionArgs, null);
        try {
            String[] args = new String[1];
            while (cursor.moveToNext()) {
                args[0] = cursor.getString(0);
                count += db.update(TABLE_BOOKMARKS, values, "_id=?", args);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    private String appendAccountToSelection(Uri uri, String selection) {
        final String accountName = uri.getQueryParameter(RawContacts.ACCOUNT_NAME);
        final String accountType = uri.getQueryParameter(RawContacts.ACCOUNT_TYPE);

        final boolean partialUri = TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType);
        if (partialUri) {
            // Throw when either account is incomplete
            throw new IllegalArgumentException(
                    "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE for " + uri);
        }

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validAccount = !TextUtils.isEmpty(accountName);
        if (validAccount) {
            StringBuilder selectionSb = new StringBuilder(RawContacts.ACCOUNT_NAME + "="
                    + DatabaseUtils.sqlEscapeString(accountName) + " AND "
                    + RawContacts.ACCOUNT_TYPE + "="
                    + DatabaseUtils.sqlEscapeString(accountType));
            if (!TextUtils.isEmpty(selection)) {
                selectionSb.append(" AND (");
                selectionSb.append(selection);
                selectionSb.append(')');
            }
            return selectionSb.toString();
        } else {
            return selection;
        }
    }
}
