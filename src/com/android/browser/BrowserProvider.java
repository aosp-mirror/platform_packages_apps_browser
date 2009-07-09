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

import com.google.android.providers.GoogleSettings.Partner;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.Settings;
import android.server.search.SearchableInfo;
import android.text.TextUtils;
import android.text.util.Regex;
import android.util.Log;
import android.util.TypedValue;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BrowserProvider extends ContentProvider {

    private SQLiteOpenHelper mOpenHelper;
    private static final String sDatabaseName = "browser.db";
    private static final String TAG = "BrowserProvider";
    private static final String ORDER_BY = "visits DESC, date DESC";

    private static final String PICASA_URL = "http://picasaweb.google.com/m/" +
            "viewer?source=androidclient";

    private static final String[] TABLE_NAMES = new String[] {
        "bookmarks", "searches"
    };
    private static final String[] SUGGEST_PROJECTION = new String[] {
            "_id", "url", "title", "bookmark"
    };
    private static final String SUGGEST_SELECTION =
            "url LIKE ? OR url LIKE ? OR url LIKE ? OR url LIKE ?"
                + " OR title LIKE ?";
    private String[] SUGGEST_ARGS = new String[5];

    // shared suggestion array index, make sure to match COLUMNS
    private static final int SUGGEST_COLUMN_INTENT_ACTION_ID = 1;
    private static final int SUGGEST_COLUMN_INTENT_DATA_ID = 2;
    private static final int SUGGEST_COLUMN_TEXT_1_ID = 3;
    private static final int SUGGEST_COLUMN_TEXT_2_ID = 4;
    private static final int SUGGEST_COLUMN_ICON_1_ID = 5;
    private static final int SUGGEST_COLUMN_ICON_2_ID = 6;
    private static final int SUGGEST_COLUMN_QUERY_ID = 7;
    private static final int SUGGEST_COLUMN_FORMAT = 8;

    // shared suggestion columns
    private static final String[] COLUMNS = new String[] {
            "_id",
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_ICON_2,
            SearchManager.SUGGEST_COLUMN_QUERY,
            SearchManager.SUGGEST_COLUMN_FORMAT};

    private static final int MAX_SUGGESTION_SHORT_ENTRIES = 3;
    private static final int MAX_SUGGESTION_LONG_ENTRIES = 6;

    // make sure that these match the index of TABLE_NAMES
    private static final int URI_MATCH_BOOKMARKS = 0;
    private static final int URI_MATCH_SEARCHES = 1;
    // (id % 10) should match the table name index
    private static final int URI_MATCH_BOOKMARKS_ID = 10;
    private static final int URI_MATCH_SEARCHES_ID = 11;
    //
    private static final int URI_MATCH_SUGGEST = 20;
    private static final int URI_MATCH_BOOKMARKS_SUGGEST = 21;

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
        URI_MATCHER.addURI("browser",
                TABLE_NAMES[URI_MATCH_BOOKMARKS] + "/" + SearchManager.SUGGEST_URI_PATH_QUERY,
                URI_MATCH_BOOKMARKS_SUGGEST);
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

    // Regular expression which matches http://, followed by some stuff, followed by
    // optionally a trailing slash, all matched as separate groups.
    private static final Pattern STRIP_URL_PATTERN = Pattern.compile("^(http://)(.*?)(/$)?");

    private SearchManager mSearchManager;

    // The ID of the ColorStateList to be applied to urls of website suggestions, as derived from
    // the current theme. This is not set until/unless beautifyUrl is called, at which point
    // this variable caches the color value.
    private static String mSearchUrlColorId;

    public BrowserProvider() {
    }


    private static CharSequence replaceSystemPropertyInString(Context context, CharSequence srcString) {
        StringBuffer sb = new StringBuffer();
        int lastCharLoc = 0;

        final String client_id = Partner.getString(context.getContentResolver(),
                                                    Partner.CLIENT_ID, "android-google");

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
                    CharSequence bookmarkDestination = replaceSystemPropertyInString(mContext, bookmarks[i + 1]);
                    db.execSQL("INSERT INTO bookmarks (title, url, visits, " +
                            "date, created, bookmark)" + " VALUES('" +
                            bookmarks[i] + "', '" + bookmarkDestination +
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
        final Context context = getContext();
        mOpenHelper = new DatabaseHelper(context);
        // we added "picasa web album" into default bookmarks for version 19.
        // To avoid erasing the bookmark table, we added it explicitly for
        // version 18 and 19 as in the other cases, we will erase the table.
        if (DATABASE_VERSION == 18 || DATABASE_VERSION == 19) {
            SharedPreferences p = PreferenceManager
                    .getDefaultSharedPreferences(context);
            boolean fix = p.getBoolean("fix_picasa", true);
            if (fix) {
                fixPicasaBookmark();
                Editor ed = p.edit();
                ed.putBoolean("fix_picasa", false);
                ed.commit();
            }
        }
        mSearchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        mShowWebSuggestionsSettingChangeObserver
            = new ShowWebSuggestionsSettingChangeObserver();
        context.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(
                        Settings.System.SHOW_WEB_SUGGESTIONS),
                true, mShowWebSuggestionsSettingChangeObserver);
        updateShowWebSuggestions();
        return true;
    }

    /**
     * This Observer will ensure that if the user changes the system
     * setting of whether to display web suggestions, we will
     * change accordingly.
     */
    /* package */ class ShowWebSuggestionsSettingChangeObserver
            extends ContentObserver {
        public ShowWebSuggestionsSettingChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            updateShowWebSuggestions();
        }
    }

    private ShowWebSuggestionsSettingChangeObserver
            mShowWebSuggestionsSettingChangeObserver;

    // If non-null, then the system is set to show web suggestions,
    // and this is the SearchableInfo to use to get them.
    private SearchableInfo mSearchableInfo;

    /**
     * Check the system settings to see whether web suggestions are
     * allowed.  If so, store the SearchableInfo to grab suggestions
     * while the user is typing.
     */
    private void updateShowWebSuggestions() {
        mSearchableInfo = null;
        Context context = getContext();
        if (Settings.System.getInt(context.getContentResolver(),
                Settings.System.SHOW_WEB_SUGGESTIONS,
                1 /* default on */) == 1) {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            ResolveInfo info = context.getPackageManager().resolveActivity(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null) {
                ComponentName googleSearchComponent =
                        new ComponentName(info.activityInfo.packageName,
                                info.activityInfo.name);
                mSearchableInfo = mSearchManager.getSearchableInfo(
                        googleSearchComponent, false);
            }
        }
    }

    private void fixPicasaBookmark() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT _id FROM bookmarks WHERE " +
                "bookmark = 1 AND url = ?", new String[] { PICASA_URL });
        try {
            if (!cursor.moveToFirst()) {
                // set "created" so that it will be on the top of the list
                db.execSQL("INSERT INTO bookmarks (title, url, visits, " +
                        "date, created, bookmark)" + " VALUES('" +
                        getContext().getString(R.string.picasa) + "', '"
                        + PICASA_URL + "', 0, 0, " + new Date().getTime()
                        + ", 1);");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /*
     * Subclass AbstractCursor so we can combine multiple Cursors and add
     * "Google Search".
     * Here are the rules.
     * 1. We only have MAX_SUGGESTION_LONG_ENTRIES in the list plus
     *      "Google Search";
     * 2. If bookmark/history entries are less than
     *      (MAX_SUGGESTION_SHORT_ENTRIES -1), we include Google suggest.
     */
    private class MySuggestionCursor extends AbstractCursor {
        private Cursor  mHistoryCursor;
        private Cursor  mSuggestCursor;
        private int     mHistoryCount;
        private int     mSuggestionCount;
        private boolean mBeyondCursor;
        private String  mString;
        private int     mSuggestText1Id;
        private int     mSuggestText2Id;
        private int     mSuggestQueryId;

        public MySuggestionCursor(Cursor hc, Cursor sc, String string) {
            mHistoryCursor = hc;
            mSuggestCursor = sc;
            mHistoryCount = hc.getCount();
            mSuggestionCount = sc != null ? sc.getCount() : 0;
            if (mSuggestionCount > (MAX_SUGGESTION_LONG_ENTRIES - mHistoryCount)) {
                mSuggestionCount = MAX_SUGGESTION_LONG_ENTRIES - mHistoryCount;
            }
            mString = string;
            mBeyondCursor = false;

            // Some web suggest providers only give suggestions and have no description string for
            // items. The order of the result columns may be different as well. So retrieve the
            // column indices for the fields we need now and check before using below.
            if (mSuggestCursor == null) {
                mSuggestText1Id = -1;
                mSuggestText2Id = -1;
                mSuggestQueryId = -1;
            } else {
                mSuggestText1Id = mSuggestCursor.getColumnIndex(
                                SearchManager.SUGGEST_COLUMN_TEXT_1);
                mSuggestText2Id = mSuggestCursor.getColumnIndex(
                                SearchManager.SUGGEST_COLUMN_TEXT_2);
                mSuggestQueryId = mSuggestCursor.getColumnIndex(
                                SearchManager.SUGGEST_COLUMN_QUERY);
            }
        }

        @Override
        public boolean onMove(int oldPosition, int newPosition) {
            if (mHistoryCursor == null) {
                return false;
            }
            if (mHistoryCount > newPosition) {
                mHistoryCursor.moveToPosition(newPosition);
                mBeyondCursor = false;
            } else if (mHistoryCount + mSuggestionCount > newPosition) {
                mSuggestCursor.moveToPosition(newPosition - mHistoryCount);
                mBeyondCursor = false;
            } else {
                mBeyondCursor = true;
            }
            return true;
        }

        @Override
        public int getCount() {
            if (mString.length() > 0) {
                return mHistoryCount + mSuggestionCount + 1;
            } else {
                return mHistoryCount + mSuggestionCount;
            }
        }

        @Override
        public String[] getColumnNames() {
            return COLUMNS;
        }

        @Override
        public String getString(int columnIndex) {
            if ((mPos != -1 && mHistoryCursor != null)) {
                switch(columnIndex) {
                    case SUGGEST_COLUMN_INTENT_ACTION_ID:
                        if (mHistoryCount > mPos) {
                            return Intent.ACTION_VIEW;
                        } else {
                            return Intent.ACTION_SEARCH;
                        }

                    case SUGGEST_COLUMN_INTENT_DATA_ID:
                        if (mHistoryCount > mPos) {
                            return mHistoryCursor.getString(1);
                        } else {
                            return null;
                        }

                    case SUGGEST_COLUMN_TEXT_1_ID:
                        if (mHistoryCount > mPos) {
                            return getHistoryTitle();
                        } else if (!mBeyondCursor) {
                            if (mSuggestText1Id == -1) return null;
                            return mSuggestCursor.getString(mSuggestText1Id);
                        } else {
                            return mString;
                        }

                    case SUGGEST_COLUMN_TEXT_2_ID:
                        if (mHistoryCount > mPos) {
                            return getHistorySubtitle();
                        } else if (!mBeyondCursor) {
                            if (mSuggestText2Id == -1) return null;
                            return mSuggestCursor.getString(mSuggestText2Id);
                        } else {
                            return getContext().getString(R.string.search_the_web);
                        }

                    case SUGGEST_COLUMN_ICON_1_ID:
                        if (mHistoryCount > mPos) {
                            if (mHistoryCursor.getInt(3) == 1) {
                                return new Integer(
                                        R.drawable.ic_search_category_bookmark)
                                        .toString();
                            } else {
                                return new Integer(
                                        R.drawable.ic_search_category_history)
                                        .toString();
                            }
                        } else {
                            return new Integer(
                                    R.drawable.ic_search_category_suggest)
                                    .toString();
                        }

                    case SUGGEST_COLUMN_ICON_2_ID:
                        return new String("0");

                    case SUGGEST_COLUMN_QUERY_ID:
                        if (mHistoryCount > mPos) {
                            // Return the url in the intent query column. This is ignored
                            // within the browser because our searchable is set to
                            // android:searchMode="queryRewriteFromData", but it is used by
                            // global search for query rewriting.
                            return mHistoryCursor.getString(1);
                        } else if (!mBeyondCursor) {
                            if (mSuggestQueryId == -1) return null;
                            return mSuggestCursor.getString(mSuggestQueryId);
                        } else {
                            return mString;
                        }

                    case SUGGEST_COLUMN_FORMAT:
                        return "html";
                }
            }
            return null;
        }

        @Override
        public double getDouble(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(int column) {
            if ((mPos != -1) && column == 0) {
                return mPos;        // use row# as the _Id
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNull(int column) {
            throw new UnsupportedOperationException();
        }

        // TODO Temporary change, finalize after jq's changes go in
        public void deactivate() {
            if (mHistoryCursor != null) {
                mHistoryCursor.deactivate();
            }
            if (mSuggestCursor != null) {
                mSuggestCursor.deactivate();
            }
            super.deactivate();
        }

        public boolean requery() {
            return (mHistoryCursor != null ? mHistoryCursor.requery() : false) |
                    (mSuggestCursor != null ? mSuggestCursor.requery() : false);
        }

        // TODO Temporary change, finalize after jq's changes go in
        public void close() {
            super.close();
            if (mHistoryCursor != null) {
                mHistoryCursor.close();
                mHistoryCursor = null;
            }
            if (mSuggestCursor != null) {
                mSuggestCursor.close();
                mSuggestCursor = null;
            }
        }

        /**
         * Provides the title (text line 1) for a browser suggestion, which should be the
         * webpage title. If the webpage title is empty, returns the stripped url instead.
         *
         * @return the title string to use
         */
        private String getHistoryTitle() {
            String title = mHistoryCursor.getString(2 /* webpage title */);
            if (TextUtils.isEmpty(title) || TextUtils.getTrimmedLength(title) == 0) {
                title = beautifyUrl(mHistoryCursor.getString(1 /* url */));
            }
            return title;
        }

        /**
         * Provides the subtitle (text line 2) for a browser suggestion, which should be the
         * webpage url. If the webpage title is empty, then the url should go in the title
         * instead, and the subtitle should be empty, so this would return null.
         *
         * @return the subtitle string to use, or null if none
         */
        private String getHistorySubtitle() {
            String title = mHistoryCursor.getString(2 /* webpage title */);
            if (TextUtils.isEmpty(title) || TextUtils.getTrimmedLength(title) == 0) {
                return null;
            } else {
                return beautifyUrl(mHistoryCursor.getString(1 /* url */));
            }
        }

        /**
         * Strips "http://" from the beginning of a url and "/" from the end,
         * and adds html formatting to make it green.
         */
        private String beautifyUrl(String url) {
            if (mSearchUrlColorId == null) {
                // Get the color used for this purpose from the current theme.
                TypedValue colorValue = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        com.android.internal.R.attr.textColorSearchUrl, colorValue, true);
                mSearchUrlColorId = Integer.toString(colorValue.resourceId);
            }

            return "<font color=\"@" + mSearchUrlColorId + "\">" + stripUrl(url) + "</font>";
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

        if (match == URI_MATCH_SUGGEST || match == URI_MATCH_BOOKMARKS_SUGGEST) {
            String suggestSelection;
            String [] myArgs;
            if (selectionArgs[0] == null || selectionArgs[0].equals("")) {
                suggestSelection = null;
                myArgs = null;
            } else {
                String like = selectionArgs[0] + "%";
                if (selectionArgs[0].startsWith("http")) {
                    myArgs = new String[1];
                    myArgs[0] = like;
                    suggestSelection = selection;
                } else {
                    SUGGEST_ARGS[0] = "http://" + like;
                    SUGGEST_ARGS[1] = "http://www." + like;
                    SUGGEST_ARGS[2] = "https://" + like;
                    SUGGEST_ARGS[3] = "https://www." + like;
                    // To match against titles.
                    SUGGEST_ARGS[4] = like;
                    myArgs = SUGGEST_ARGS;
                    suggestSelection = SUGGEST_SELECTION;
                }
            }

            Cursor c = db.query(TABLE_NAMES[URI_MATCH_BOOKMARKS],
                    SUGGEST_PROJECTION, suggestSelection, myArgs, null, null,
                    ORDER_BY,
                    (new Integer(MAX_SUGGESTION_LONG_ENTRIES)).toString());

            if (match == URI_MATCH_BOOKMARKS_SUGGEST
                    || Regex.WEB_URL_PATTERN.matcher(selectionArgs[0]).matches()) {
                return new MySuggestionCursor(c, null, "");
            } else {
                // get Google suggest if there is still space in the list
                if (myArgs != null && myArgs.length > 1
                        && mSearchableInfo != null
                        && c.getCount() < (MAX_SUGGESTION_SHORT_ENTRIES - 1)) {
                    Cursor sc = mSearchManager.getSuggestions(mSearchableInfo, selectionArgs[0]);
                    return new MySuggestionCursor(c, sc, selectionArgs[0]);
                }
                return new MySuggestionCursor(c, null, selectionArgs[0]);
            }
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

    /**
     * Strips the provided url of preceding "http://" and any trailing "/". Does not
     * strip "https://". If the provided string cannot be stripped, the original string
     * is returned.
     *
     * TODO: Put this in TextUtils to be used by other packages doing something similar.
     *
     * @param url a url to strip, like "http://www.google.com/"
     * @return a stripped url like "www.google.com", or the original string if it could
     *         not be stripped
     */
    private static String stripUrl(String url) {
        if (url == null) return null;
        Matcher m = STRIP_URL_PATTERN.matcher(url);
        if (m.matches() && m.groupCount() == 3) {
            return m.group(2);
        } else {
            return url;
        }
    }

}
