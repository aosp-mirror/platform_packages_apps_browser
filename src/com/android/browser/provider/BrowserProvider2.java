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
import com.android.common.content.SyncStateContentProviderHelper;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Accounts;
import android.provider.BrowserContract.Bookmarks;
import android.provider.BrowserContract.ChromeSyncColumns;
import android.provider.BrowserContract.Combined;
import android.provider.BrowserContract.History;
import android.provider.BrowserContract.Images;
import android.provider.BrowserContract.Searches;
import android.provider.BrowserContract.Settings;
import android.provider.BrowserContract.SyncState;
import android.provider.ContactsContract.RawContacts;
import android.provider.SyncStateContract;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class BrowserProvider2 extends SQLiteContentProvider {

    static final String LEGACY_AUTHORITY = "browser";
    static final Uri LEGACY_AUTHORITY_URI = new Uri.Builder().authority(LEGACY_AUTHORITY).build();

    static final String TABLE_BOOKMARKS = "bookmarks";
    static final String TABLE_HISTORY = "history";
    static final String TABLE_IMAGES = "images";
    static final String TABLE_SEARCHES = "searches";
    static final String TABLE_SYNC_STATE = "syncstate";
    static final String TABLE_SETTINGS = "settings";
    static final String VIEW_COMBINED = "combined";

    static final String TABLE_BOOKMARKS_JOIN_IMAGES = "bookmarks LEFT OUTER JOIN images " +
            "ON bookmarks.url = images." + Images.URL;
    static final String TABLE_HISTORY_JOIN_IMAGES = "history LEFT OUTER JOIN images " +
            "ON history.url = images." + Images.URL;

    static final String DEFAULT_SORT_HISTORY = History.DATE_LAST_VISITED + " DESC";

    static final String DEFAULT_SORT_SEARCHES = Searches.DATE + " DESC";

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

    static final int IMAGES = 5000;

    static final int COMBINED = 6000;
    static final int COMBINED_ID = 6001;

    static final int ACCOUNTS = 7000;

    static final int SETTINGS = 8000;

    public static final long FIXED_ID_ROOT = 1;

    // Default sort order for unsync'd bookmarks
    static final String DEFAULT_BOOKMARKS_SORT_ORDER =
            Bookmarks.IS_FOLDER + " DESC, position ASC, _id ASC";

    // Default sort order for sync'd bookmarks
    static final String DEFAULT_BOOKMARKS_SORT_ORDER_SYNC = "position ASC, _id ASC";

    static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static final HashMap<String, String> ACCOUNTS_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> BOOKMARKS_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> OTHER_BOOKMARKS_PROJECTION_MAP =
            new HashMap<String, String>();
    static final HashMap<String, String> HISTORY_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> SYNC_STATE_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> IMAGES_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> COMBINED_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> SEARCHES_PROJECTION_MAP = new HashMap<String, String>();
    static final HashMap<String, String> SETTINGS_PROJECTION_MAP = new HashMap<String, String>();

    static {
        final UriMatcher matcher = URI_MATCHER;
        final String authority = BrowserContract.AUTHORITY;
        matcher.addURI(authority, "accounts", ACCOUNTS);
        matcher.addURI(authority, "bookmarks", BOOKMARKS);
        matcher.addURI(authority, "bookmarks/#", BOOKMARKS_ID);
        matcher.addURI(authority, "bookmarks/folder", BOOKMARKS_FOLDER);
        matcher.addURI(authority, "bookmarks/folder/#", BOOKMARKS_FOLDER_ID);
        matcher.addURI(authority, "history", HISTORY);
        matcher.addURI(authority, "history/#", HISTORY_ID);
        matcher.addURI(authority, "searches", SEARCHES);
        matcher.addURI(authority, "searches/#", SEARCHES_ID);
        matcher.addURI(authority, "syncstate", SYNCSTATE);
        matcher.addURI(authority, "syncstate/#", SYNCSTATE_ID);
        matcher.addURI(authority, "images", IMAGES);
        matcher.addURI(authority, "combined", COMBINED);
        matcher.addURI(authority, "combined/#", COMBINED_ID);
        matcher.addURI(authority, "settings", SETTINGS);

        // Projection maps
        HashMap<String, String> map;

        // Accounts
        map = ACCOUNTS_PROJECTION_MAP;
        map.put(Accounts.ACCOUNT_TYPE, Accounts.ACCOUNT_TYPE);
        map.put(Accounts.ACCOUNT_NAME, Accounts.ACCOUNT_NAME);

        // Bookmarks
        map = BOOKMARKS_PROJECTION_MAP;
        map.put(Bookmarks._ID, qualifyColumn(TABLE_BOOKMARKS, Bookmarks._ID));
        map.put(Bookmarks.TITLE, Bookmarks.TITLE);
        map.put(Bookmarks.URL, Bookmarks.URL);
        map.put(Bookmarks.FAVICON, Bookmarks.FAVICON);
        map.put(Bookmarks.THUMBNAIL, Bookmarks.THUMBNAIL);
        map.put(Bookmarks.TOUCH_ICON, Bookmarks.TOUCH_ICON);
        map.put(Bookmarks.IS_FOLDER, Bookmarks.IS_FOLDER);
        map.put(Bookmarks.PARENT, Bookmarks.PARENT);
        map.put(Bookmarks.POSITION, Bookmarks.POSITION);
        map.put(Bookmarks.INSERT_AFTER, Bookmarks.INSERT_AFTER);
        map.put(Bookmarks.IS_DELETED, Bookmarks.IS_DELETED);
        map.put(Bookmarks.ACCOUNT_NAME, Bookmarks.ACCOUNT_NAME);
        map.put(Bookmarks.ACCOUNT_TYPE, Bookmarks.ACCOUNT_TYPE);
        map.put(Bookmarks.SOURCE_ID, Bookmarks.SOURCE_ID);
        map.put(Bookmarks.VERSION, Bookmarks.VERSION);
        map.put(Bookmarks.DATE_CREATED, Bookmarks.DATE_CREATED);
        map.put(Bookmarks.DATE_MODIFIED, Bookmarks.DATE_MODIFIED);
        map.put(Bookmarks.DIRTY, Bookmarks.DIRTY);
        map.put(Bookmarks.SYNC1, Bookmarks.SYNC1);
        map.put(Bookmarks.SYNC2, Bookmarks.SYNC2);
        map.put(Bookmarks.SYNC3, Bookmarks.SYNC3);
        map.put(Bookmarks.SYNC4, Bookmarks.SYNC4);
        map.put(Bookmarks.SYNC5, Bookmarks.SYNC5);
        map.put(Bookmarks.PARENT_SOURCE_ID, "(SELECT " + Bookmarks.SOURCE_ID +
                " FROM " + TABLE_BOOKMARKS + " A WHERE " +
                "A." + Bookmarks._ID + "=" + TABLE_BOOKMARKS + "." + Bookmarks.PARENT +
                ") AS " + Bookmarks.PARENT_SOURCE_ID);
        map.put(Bookmarks.INSERT_AFTER_SOURCE_ID, "(SELECT " + Bookmarks.SOURCE_ID +
                " FROM " + TABLE_BOOKMARKS + " A WHERE " +
                "A." + Bookmarks._ID + "=" + TABLE_BOOKMARKS + "." + Bookmarks.INSERT_AFTER +
                ") AS " + Bookmarks.INSERT_AFTER_SOURCE_ID);

        // Other bookmarks
        OTHER_BOOKMARKS_PROJECTION_MAP.putAll(BOOKMARKS_PROJECTION_MAP);
        OTHER_BOOKMARKS_PROJECTION_MAP.put(Bookmarks.POSITION,
                Long.toString(Long.MAX_VALUE) + " AS " + Bookmarks.POSITION);

        // History
        map = HISTORY_PROJECTION_MAP;
        map.put(History._ID, qualifyColumn(TABLE_HISTORY, History._ID));
        map.put(History.TITLE, History.TITLE);
        map.put(History.URL, History.URL);
        map.put(History.FAVICON, History.FAVICON);
        map.put(History.THUMBNAIL, History.THUMBNAIL);
        map.put(History.TOUCH_ICON, History.TOUCH_ICON);
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

        // Images
        map = IMAGES_PROJECTION_MAP;
        map.put(Images.URL, Images.URL);
        map.put(Images.FAVICON, Images.FAVICON);
        map.put(Images.THUMBNAIL, Images.THUMBNAIL);
        map.put(Images.TOUCH_ICON, Images.TOUCH_ICON);

        // Combined history half
        map = COMBINED_PROJECTION_MAP;
        map.put(Combined._ID, Combined._ID);
        map.put(Combined.TITLE, Combined.TITLE);
        map.put(Combined.URL, Combined.URL);
        map.put(Combined.DATE_CREATED, Combined.DATE_CREATED);
        map.put(Combined.DATE_LAST_VISITED, Combined.DATE_LAST_VISITED);
        map.put(Combined.IS_BOOKMARK, Combined.IS_BOOKMARK);
        map.put(Combined.VISITS, Combined.VISITS);
        map.put(Combined.FAVICON, Combined.FAVICON);
        map.put(Combined.THUMBNAIL, Combined.THUMBNAIL);
        map.put(Combined.TOUCH_ICON, Combined.TOUCH_ICON);
        map.put(Combined.USER_ENTERED, Combined.USER_ENTERED);

        // Searches
        map = SEARCHES_PROJECTION_MAP;
        map.put(Searches._ID, Searches._ID);
        map.put(Searches.SEARCH, Searches.SEARCH);
        map.put(Searches.DATE, Searches.DATE);

        // Settings
        map = SETTINGS_PROJECTION_MAP;
        map.put(Settings.KEY, Settings.KEY);
        map.put(Settings.VALUE, Settings.VALUE);
    }

    static final String bookmarkOrHistoryColumn(String column) {
        return "CASE WHEN bookmarks." + column + " IS NOT NULL THEN " +
                "bookmarks." + column + " ELSE history." + column + " END AS " + column;
    }

    static final String qualifyColumn(String table, String column) {
        return table + "." + column + " AS " + column;
    }

    DatabaseHelper mOpenHelper;
    SyncStateContentProviderHelper mSyncHelper = new SyncStateContentProviderHelper();

    final class DatabaseHelper extends SQLiteOpenHelper {
        static final String DATABASE_NAME = "browser2.db";
        static final int DATABASE_VERSION = 25;
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + "(" +
                    Bookmarks._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Bookmarks.TITLE + " TEXT," +
                    Bookmarks.URL + " TEXT," +
                    Bookmarks.IS_FOLDER + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.PARENT + " INTEGER," +
                    Bookmarks.POSITION + " INTEGER NOT NULL," +
                    Bookmarks.INSERT_AFTER + " INTEGER," +
                    Bookmarks.IS_DELETED + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.ACCOUNT_NAME + " TEXT," +
                    Bookmarks.ACCOUNT_TYPE + " TEXT," +
                    Bookmarks.SOURCE_ID + " TEXT," +
                    Bookmarks.VERSION + " INTEGER NOT NULL DEFAULT 1," +
                    Bookmarks.DATE_CREATED + " INTEGER," +
                    Bookmarks.DATE_MODIFIED + " INTEGER," +
                    Bookmarks.DIRTY + " INTEGER NOT NULL DEFAULT 0," +
                    Bookmarks.SYNC1 + " TEXT," +
                    Bookmarks.SYNC2 + " TEXT," +
                    Bookmarks.SYNC3 + " TEXT," +
                    Bookmarks.SYNC4 + " TEXT," +
                    Bookmarks.SYNC5 + " TEXT" +
                    ");");

            // TODO indices

            db.execSQL("CREATE TABLE " + TABLE_HISTORY + "(" +
                    History._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    History.TITLE + " TEXT," +
                    History.URL + " TEXT NOT NULL," +
                    History.DATE_CREATED + " INTEGER," +
                    History.DATE_LAST_VISITED + " INTEGER," +
                    History.VISITS + " INTEGER NOT NULL DEFAULT 0," +
                    History.USER_ENTERED + " INTEGER" +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_IMAGES + " (" +
                    Images.URL + " TEXT UNIQUE NOT NULL," +
                    Images.FAVICON + " BLOB," +
                    Images.THUMBNAIL + " BLOB," +
                    Images.TOUCH_ICON + " BLOB" +
                    ");");
            db.execSQL("CREATE INDEX imagesUrlIndex ON " + TABLE_IMAGES +
                    "(" + Images.URL + ")");

            db.execSQL("CREATE TABLE " + TABLE_SEARCHES + " (" +
                    Searches._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    Searches.SEARCH + " TEXT," +
                    Searches.DATE + " LONG" +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " (" +
                    Settings.KEY + " TEXT PRIMARY KEY," +
                    Settings.VALUE + " TEXT NOT NULL" +
                    ");");

            db.execSQL("CREATE VIEW " + VIEW_COMBINED + " AS " +
                "SELECT " +
                    bookmarkOrHistoryColumn(Combined._ID) + ", " +
                    bookmarkOrHistoryColumn(Combined.TITLE) + ", " +
                    qualifyColumn(TABLE_HISTORY, Combined.URL) + ", " +
                    qualifyColumn(TABLE_HISTORY, Combined.DATE_CREATED) + ", " +
                    Combined.DATE_LAST_VISITED + ", " +
                    "CASE WHEN bookmarks._id IS NOT NULL THEN 1 ELSE 0 END AS " + Combined.IS_BOOKMARK + ", " +
                    Combined.VISITS + ", " +
                    Combined.FAVICON + ", " +
                    Combined.THUMBNAIL + ", " +
                    Combined.TOUCH_ICON + ", " +
                    "NULL AS " + Combined.USER_ENTERED + " "+
                "FROM history LEFT OUTER JOIN bookmarks ON history.url = bookmarks.url LEFT OUTER JOIN images ON history.url = images.url_key " +

                "UNION ALL " +

                "SELECT " +
                    Combined._ID + ", " +
                    Combined.TITLE + ", " +
                    Combined.URL + ", " +
                    Combined.DATE_CREATED + ", " +
                    "NULL AS " + Combined.DATE_LAST_VISITED + ", "+
                    "1 AS " + Combined.IS_BOOKMARK + ", " +
                    "0 AS " + Combined.VISITS + ", "+
                    Combined.FAVICON + ", " +
                    Combined.THUMBNAIL + ", " +
                    Combined.TOUCH_ICON + ", " +
                    "NULL AS " + Combined.USER_ENTERED + " "+
                "FROM bookmarks LEFT OUTER JOIN images ON bookmarks.url = images.url_key WHERE url NOT IN (SELECT url FROM history)");

            mSyncHelper.createDatabase(db);

            createDefaultBookmarks(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO write upgrade logic
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCHES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_IMAGES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
            db.execSQL("DROP VIEW IF EXISTS " + VIEW_COMBINED);
            mSyncHelper.onAccountsChanged(db, new Account[] {}); // remove all sync info
            onCreate(db);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            mSyncHelper.onDatabaseOpened(db);
        }

        private void createDefaultBookmarks(SQLiteDatabase db) {
            ContentValues values = new ContentValues();
            // TODO figure out how to deal with localization for the defaults

            // Bookmarks folder
            values.put(Bookmarks._ID, FIXED_ID_ROOT);
            values.put(ChromeSyncColumns.SERVER_UNIQUE, ChromeSyncColumns.FOLDER_NAME_BOOKMARKS);
            values.put(Bookmarks.TITLE, "Bookmarks");
            values.putNull(Bookmarks.PARENT);
            values.put(Bookmarks.POSITION, 0);
            values.put(Bookmarks.IS_FOLDER, true);
            values.put(Bookmarks.DIRTY, true);
            db.insertOrThrow(TABLE_BOOKMARKS, null, values);

            addDefaultBookmarks(db, FIXED_ID_ROOT);
        }

        private void addDefaultBookmarks(SQLiteDatabase db, long parentId) {
            Resources res = getContext().getResources();
            final CharSequence[] bookmarks = res.getTextArray(
                    R.array.bookmarks);
            int size = bookmarks.length;
            TypedArray preloads = res.obtainTypedArray(R.array.bookmark_preloads);
            try {
                String parent = Long.toString(parentId);
                String now = Long.toString(System.currentTimeMillis());
                for (int i = 0; i < size; i = i + 2) {
                    CharSequence bookmarkDestination = replaceSystemPropertyInString(getContext(),
                            bookmarks[i + 1]);
                    db.execSQL("INSERT INTO bookmarks (" +
                            Bookmarks.TITLE + ", " +
                            Bookmarks.URL + ", " +
                            Bookmarks.IS_FOLDER + "," +
                            Bookmarks.PARENT + "," +
                            Bookmarks.POSITION + "," +
                            Bookmarks.DATE_CREATED +
                        ") VALUES (" +
                            "'" + bookmarks[i] + "', " +
                            "'" + bookmarkDestination + "', " +
                            "0," +
                            parent + "," +
                            Integer.toString(i) + "," +
                            now +
                            ");");

                    int faviconId = preloads.getResourceId(i, 0);
                    int thumbId = preloads.getResourceId(i + 1, 0);
                    byte[] thumb = null, favicon = null;
                    try {
                        thumb = readRaw(res, thumbId);
                    } catch (IOException e) {
                    }
                    try {
                        favicon = readRaw(res, faviconId);
                    } catch (IOException e) {
                    }
                    if (thumb != null || favicon != null) {
                        ContentValues imageValues = new ContentValues();
                        imageValues.put(Images.URL, bookmarkDestination.toString());
                        if (favicon != null) {
                            imageValues.put(Images.FAVICON, favicon);
                        }
                        if (thumb != null) {
                            imageValues.put(Images.THUMBNAIL, thumb);
                        }
                        db.insert(TABLE_IMAGES, Images.FAVICON, imageValues);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }

        private byte[] readRaw(Resources res, int id) throws IOException {
            InputStream is = res.openRawResource(id);
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int read;
                while ((read = is.read(buf)) > 0) {
                    bos.write(buf, 0, read);
                }
                bos.flush();
                return bos.toByteArray();
            } finally {
                is.close();
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
        resolver.notifyChange(LEGACY_AUTHORITY_URI, null, !callerIsSyncAdapter);
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
        String limit = uri.getQueryParameter(BrowserContract.PARAM_LIMIT);
        switch (match) {
            case ACCOUNTS: {
                qb.setTables(TABLE_BOOKMARKS);
                qb.setProjectionMap(ACCOUNTS_PROJECTION_MAP);
                qb.setDistinct(true);
                qb.appendWhere(Bookmarks.ACCOUNT_NAME + " IS NOT NULL");
                break;
            }

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
                    selection = DatabaseUtils.concatenateWhere(selection,
                            TABLE_BOOKMARKS + "." + Bookmarks._ID + "=?");
                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                            new String[] { Long.toString(ContentUris.parseId(uri)) });
                } else if (match == BOOKMARKS_FOLDER_ID) {
                    // Tack on the ID of the specific folder requested
                    selection = DatabaseUtils.concatenateWhere(selection,
                            TABLE_BOOKMARKS + "." + Bookmarks.PARENT + "=?");
                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                            new String[] { Long.toString(ContentUris.parseId(uri)) });
                }

                // Look for account info
                String accountType = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE);
                String accountName = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME);
                if (!TextUtils.isEmpty(accountType) && !TextUtils.isEmpty(accountName)) {
                    selection = DatabaseUtils.concatenateWhere(selection,
                            Bookmarks.ACCOUNT_TYPE + "=? AND " + Bookmarks.ACCOUNT_NAME + "=? ");
                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                            new String[] { accountType, accountName });
                }

                // Set a default sort order if one isn't specified
                if (TextUtils.isEmpty(sortOrder)) {
                    if (!TextUtils.isEmpty(accountType)
                            && !TextUtils.isEmpty(accountName)) {
                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER_SYNC;
                    } else {
                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER;
                    }
                }

                qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
                qb.setTables(TABLE_BOOKMARKS_JOIN_IMAGES);
                break;
            }

            case BOOKMARKS_FOLDER: {
                // Don't allow selections to be applied to the default folder
                if (!TextUtils.isEmpty(selection) || selectionArgs != null) {
                    throw new UnsupportedOperationException(
                            "selections aren't supported on this URI");
                }

                // Look for an account
                boolean useAccount = false;
                String accountType = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_TYPE);
                String accountName = uri.getQueryParameter(Bookmarks.PARAM_ACCOUNT_NAME);
                if (!TextUtils.isEmpty(accountType) && !TextUtils.isEmpty(accountName)) {
                    useAccount = true;
                }

                qb.setTables(TABLE_BOOKMARKS_JOIN_IMAGES);
                String[] args;
                String query;
                // Set a default sort order if one isn't specified
                if (TextUtils.isEmpty(sortOrder)) {
                    if (useAccount) {
                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER_SYNC;
                    } else {
                        sortOrder = DEFAULT_BOOKMARKS_SORT_ORDER;
                    }
                }
                if (!useAccount) {
                    qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
                    query = qb.buildQuery(projection,
                            Bookmarks.PARENT + "=? AND " + Bookmarks.IS_DELETED + "=0",
                            null, null, null, sortOrder, null);

                    args = new String[] { Long.toString(FIXED_ID_ROOT) };
                } else {
                    qb.setProjectionMap(BOOKMARKS_PROJECTION_MAP);
                    String bookmarksBarQuery = qb.buildQuery(projection,
                            Bookmarks.ACCOUNT_TYPE + "=? AND " + Bookmarks.ACCOUNT_NAME + "=? " +
                                    "AND parent = " +
                                        "(SELECT _id FROM " + TABLE_BOOKMARKS + " WHERE " +
                                        ChromeSyncColumns.SERVER_UNIQUE + "=" +
                                        "'" + ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR + "' " +
                                        "AND account_type = ? AND account_name = ?) " +
                                    "AND " + Bookmarks.IS_DELETED + "=0",
                            null, null, null, null, null);

                    qb.setProjectionMap(OTHER_BOOKMARKS_PROJECTION_MAP);
                    String otherBookmarksQuery = qb.buildQuery(projection,
                            Bookmarks.ACCOUNT_TYPE + "=? AND " + Bookmarks.ACCOUNT_NAME + "=?" +
                                    " AND " + ChromeSyncColumns.SERVER_UNIQUE + "=?",
                            null, null, null, null, null);

                    query = qb.buildUnionQuery(
                            new String[] { bookmarksBarQuery, otherBookmarksQuery },
                            sortOrder, limit);

                    args = new String[] {
                            accountType, accountName, accountType, accountName,
                            accountType, accountName, ChromeSyncColumns.FOLDER_NAME_OTHER_BOOKMARKS,
                            };
                }

                Cursor cursor = db.rawQuery(query, args);
                if (cursor != null) {
                    cursor.setNotificationUri(getContext().getContentResolver(),
                            BrowserContract.AUTHORITY_URI);
                }
                return cursor;
            }

            case HISTORY_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case HISTORY: {
                filterSearchClient(selectionArgs);
                if (sortOrder == null) {
                    sortOrder = DEFAULT_SORT_HISTORY;
                }
                qb.setProjectionMap(HISTORY_PROJECTION_MAP);
                qb.setTables(TABLE_HISTORY_JOIN_IMAGES);
                break;
            }

            case SEARCHES_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_SEARCHES + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case SEARCHES: {
                if (sortOrder == null) {
                    sortOrder = DEFAULT_SORT_SEARCHES;
                }
                qb.setTables(TABLE_SEARCHES);
                qb.setProjectionMap(SEARCHES_PROJECTION_MAP);
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

            case IMAGES: {
                qb.setTables(TABLE_IMAGES);
                qb.setProjectionMap(IMAGES_PROJECTION_MAP);
                break;
            }

            case COMBINED_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, VIEW_COMBINED + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case COMBINED: {
                qb.setTables(VIEW_COMBINED);
                qb.setProjectionMap(COMBINED_PROJECTION_MAP);
                break;
            }

            case SETTINGS: {
                qb.setTables(TABLE_SETTINGS);
                qb.setProjectionMap(SETTINGS_PROJECTION_MAP);
                break;
            }

            default: {
                throw new UnsupportedOperationException("Unknown URL " + uri.toString());
            }
        }

        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder,
                limit);
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
                    values.put(Bookmarks.DATE_MODIFIED, System.currentTimeMillis());
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
                filterSearchClient(selectionArgs);
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
                // Mark rows dirty if they're not coming from a sync adapter
                if (!callerIsSyncAdapter) {
                    long now = System.currentTimeMillis();
                    values.put(Bookmarks.DATE_CREATED, now);
                    values.put(Bookmarks.DATE_MODIFIED, now);
                    values.put(Bookmarks.DIRTY, 1);

                    // If no parent is set default to the "Bookmarks Bar" folder
                    // TODO set the parent based on the account info
                    if (!values.containsKey(Bookmarks.PARENT)) {
                        values.put(Bookmarks.PARENT, FIXED_ID_ROOT);
                    }
                }

                // If no position is requested put the bookmark at the beginning of the list
                if (!values.containsKey(Bookmarks.POSITION)) {
                    values.put(Bookmarks.POSITION, Long.toString(Long.MIN_VALUE));
                }

                // Extract out the image values so they can be inserted into the images table
                String url = values.getAsString(Bookmarks.URL);
                ContentValues imageValues = extractImageValues(values, url);
                Boolean isFolder = values.getAsBoolean(Bookmarks.IS_FOLDER);
                if ((isFolder == null || !isFolder)
                        && imageValues != null && !TextUtils.isEmpty(url)) {
                    int count = db.update(TABLE_IMAGES, imageValues, Images.URL + "=?",
                            new String[] { url });
                    if (count == 0) {
                        db.insertOrThrow(TABLE_IMAGES, Images.FAVICON, imageValues);
                    }
                }

                id = db.insertOrThrow(TABLE_BOOKMARKS, Bookmarks.DIRTY, values);
                break;
            }

            case HISTORY: {
                // If no created time is specified set it to now
                if (!values.containsKey(History.DATE_CREATED)) {
                    values.put(History.DATE_CREATED, System.currentTimeMillis());
                }
                String url = values.getAsString(History.URL);
                url = filterSearchClient(url);
                values.put(History.URL, url);

                // Extract out the image values so they can be inserted into the images table
                ContentValues imageValues = extractImageValues(values,
                        values.getAsString(History.URL));
                if (imageValues != null) {
                    db.insertOrThrow(TABLE_IMAGES, Images.FAVICON, imageValues);
                }

                id = db.insertOrThrow(TABLE_HISTORY, History.VISITS, values);
                break;
            }

            case SEARCHES: {
                id = insertSearchesInTransaction(db, values);
                break;
            }

            case SYNCSTATE: {
                id = mSyncHelper.insert(db, values);
                break;
            }

            case SETTINGS: {
                id = 0;
                insertSettingsInTransaction(db, values);
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

    private void filterSearchClient(String[] selectionArgs) {
        if (selectionArgs != null) {
            for (int i = 0; i < selectionArgs.length; i++) {
                selectionArgs[i] = filterSearchClient(selectionArgs[i]);
            }
        }
    }

    // Filters out the client=ms- param for search urls
    private String filterSearchClient(String url) {
        // remove "client" before updating it to the history so that it wont
        // show up in the auto-complete list.
        int index = url.indexOf("client=ms-");
        if (index > 0 && url.contains(".google.")) {
            int end = url.indexOf('&', index);
            if (end > 0) {
                url = url.substring(0, index)
                        .concat(url.substring(end + 1));
            } else {
                // the url.charAt(index-1) should be either '?' or '&'
                url = url.substring(0, index-1);
            }
        }
        return url;
    }

    /**
     * Searches are unique, so perform an UPSERT manually since SQLite doesn't support them.
     */
    private long insertSearchesInTransaction(SQLiteDatabase db, ContentValues values) {
        String search = values.getAsString(Searches.SEARCH);
        if (TextUtils.isEmpty(search)) {
            throw new IllegalArgumentException("Must include the SEARCH field");
        }
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_SEARCHES, new String[] { Searches._ID },
                    Searches.SEARCH + "=?", new String[] { search }, null, null, null);
            if (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                db.update(TABLE_SEARCHES, values, Searches._ID + "=?",
                        new String[] { Long.toString(id) });
                return id;
            } else {
                return db.insertOrThrow(TABLE_SEARCHES, Searches.SEARCH, values);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Settings are unique, so perform an UPSERT manually since SQLite doesn't support them.
     */
    private long insertSettingsInTransaction(SQLiteDatabase db, ContentValues values) {
        String key = values.getAsString(Settings.KEY);
        if (TextUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Must include the KEY field");
        }
        String[] keyArray = new String[] { key };
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_SETTINGS, new String[] { Settings.KEY },
                    Settings.KEY + "=?", keyArray, null, null, null);
            if (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                db.update(TABLE_SETTINGS, values, Settings.KEY + "=?", keyArray);
                return id;
            } else {
                return db.insertOrThrow(TABLE_SETTINGS, Settings.VALUE, values);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    @Override
    public int updateInTransaction(Uri uri, ContentValues values, String selection,
            String[] selectionArgs, boolean callerIsSyncAdapter) {
        final int match = URI_MATCHER.match(uri);
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        switch (match) {
            case BOOKMARKS_ID: {
                selection = DatabaseUtils.concatenateWhere(selection,
                        TABLE_BOOKMARKS + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case BOOKMARKS: {
                return updateBookmarksInTransaction(values, selection, selectionArgs,
                        callerIsSyncAdapter);
            }

            case HISTORY_ID: {
                selection = DatabaseUtils.concatenateWhere(selection, TABLE_HISTORY + "._id=?");
                selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                        new String[] { Long.toString(ContentUris.parseId(uri)) });
                // fall through
            }
            case HISTORY: {
                return updateHistoryInTransaction(values, selection, selectionArgs);
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

            case IMAGES: {
                String url = values.getAsString(Images.URL);
                if (TextUtils.isEmpty(url)) {
                    throw new IllegalArgumentException("Images.URL is required");
                }
                int count = db.update(TABLE_IMAGES, values, Images.URL + "=?",
                        new String[] { url });
                if (count == 0) {
                    db.insertOrThrow(TABLE_IMAGES, Images.FAVICON, values);
                    count = 1;
                }
                return count;
            }
        }
        throw new UnsupportedOperationException("Unknown update URI " + uri);
    }

    /**
     * Does a query to find the matching bookmarks and updates each one with the provided values.
     */
    int updateBookmarksInTransaction(ContentValues values, String selection,
            String[] selectionArgs, boolean callerIsSyncAdapter) {
        int count = 0;
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor cursor = query(Bookmarks.CONTENT_URI,
                new String[] { Bookmarks._ID, Bookmarks.VERSION, Bookmarks.URL },
                selection, selectionArgs, null);
        try {
            String[] args = new String[1];
            // Mark the bookmark dirty if the caller isn't a sync adapter
            if (!callerIsSyncAdapter) {
                values.put(Bookmarks.DATE_MODIFIED, System.currentTimeMillis());
                values.put(Bookmarks.DIRTY, 1);
            }

            boolean updatingUrl = values.containsKey(Bookmarks.URL);
            String url = null;
            if (updatingUrl) {
                url = values.getAsString(Bookmarks.URL);
            }
            ContentValues imageValues = extractImageValues(values, url);

            while (cursor.moveToNext()) {
                args[0] = cursor.getString(0);
                if (!callerIsSyncAdapter) {
                    // increase the local version for non-sync changes
                    values.put(Bookmarks.VERSION, cursor.getLong(1) + 1);
                }
                count += db.update(TABLE_BOOKMARKS, values, "_id=?", args);

                // Update the images over in their table
                if (imageValues != null) {
                    if (!updatingUrl) {
                        url = cursor.getString(2);
                        imageValues.put(Images.URL, url);
                    }

                    if (!TextUtils.isEmpty(url)) {
                        args[0] = url;
                        if (db.update(TABLE_IMAGES, imageValues, Images.URL + "=?", args) == 0) {
                            db.insert(TABLE_IMAGES, Images.FAVICON, imageValues);
                        }
                    }
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    /**
     * Does a query to find the matching bookmarks and updates each one with the provided values.
     */
    int updateHistoryInTransaction(ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        filterSearchClient(selectionArgs);
        Cursor cursor = query(History.CONTENT_URI,
                new String[] { History._ID, History.URL },
                selection, selectionArgs, null);
        try {
            String[] args = new String[1];

            boolean updatingUrl = values.containsKey(History.URL);
            String url = null;
            if (updatingUrl) {
                url = filterSearchClient(values.getAsString(History.URL));
                values.put(History.URL, url);
            }
            ContentValues imageValues = extractImageValues(values, url);

            while (cursor.moveToNext()) {
                args[0] = cursor.getString(0);
                count += db.update(TABLE_HISTORY, values, "_id=?", args);

                // Update the images over in their table
                if (imageValues != null) {
                    if (!updatingUrl) {
                        url = cursor.getString(1);
                        imageValues.put(Images.URL, url);
                    }
                    args[0] = url;
                    if (db.update(TABLE_IMAGES, imageValues, Images.URL + "=?", args) == 0) {
                        db.insert(TABLE_IMAGES, Images.FAVICON, imageValues);
                    }
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return count;
    }

    String appendAccountToSelection(Uri uri, String selection) {
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

    ContentValues extractImageValues(ContentValues values, String url) {
        ContentValues imageValues = null;
        // favicon
        if (values.containsKey(Bookmarks.FAVICON)) {
            imageValues = new ContentValues();
            imageValues.put(Images.FAVICON, values.getAsByteArray(Bookmarks.FAVICON));
            values.remove(Bookmarks.FAVICON);
        }

        // thumbnail
        if (values.containsKey(Bookmarks.THUMBNAIL)) {
            if (imageValues == null) {
                imageValues = new ContentValues();
            }
            imageValues.put(Images.THUMBNAIL, values.getAsByteArray(Bookmarks.THUMBNAIL));
            values.remove(Bookmarks.THUMBNAIL);
        }

        // touch icon
        if (values.containsKey(Bookmarks.TOUCH_ICON)) {
            if (imageValues == null) {
                imageValues = new ContentValues();
            }
            imageValues.put(Images.TOUCH_ICON, values.getAsByteArray(Bookmarks.TOUCH_ICON));
            values.remove(Bookmarks.TOUCH_ICON);
        }

        if (imageValues != null) {
            imageValues.put(Images.URL,  url);
        }
        return imageValues;
    }
}
