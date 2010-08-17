/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;
import android.provider.BrowserContract;
import android.util.Log;
import android.webkit.WebIconDatabase;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 *  This class is purely to have a common place for adding/deleting bookmarks.
 */
/* package */ class Bookmarks {
    // We only want the user to be able to bookmark content that
    // the browser can handle directly.
    private static final String acceptableBookmarkSchemes[] = {
            "http:",
            "https:",
            "about:",
            "data:",
            "javascript:",
            "file:",
            "content:"
    };

    private final static String LOGTAG = "Bookmarks";
    /**
     *  Add a bookmark to the database.
     *  @param context Context of the calling Activity.  This is used to make
     *          Toast confirming that the bookmark has been added.  If the
     *          caller provides null, the Toast will not be shown.
     *  @param cr The ContentResolver being used to add the bookmark to the db.
     *  @param url URL of the website to be bookmarked.
     *  @param name Provided name for the bookmark.
     *  @param thumbnail A thumbnail for the bookmark.
     *  @param retainIcon Whether to retain the page's icon in the icon database.
     *          This will usually be <code>true</code> except when bookmarks are
     *          added by a settings restore agent.
     */
    /* package */ static void addBookmark(Context context, ContentResolver cr, String url,
            String name, Bitmap thumbnail, boolean retainIcon) {
        // Want to append to the beginning of the list
        ContentValues values = new ContentValues();
        Cursor cursor = null;
        try {
            values.put(BrowserContract.Bookmarks.TITLE, name);
            values.put(BrowserContract.Bookmarks.URL, url);
            values.put(BrowserContract.Bookmarks.IS_FOLDER, 0);
            values.put(BrowserContract.Bookmarks.THUMBNAIL,
                    bitmapToBytes(thumbnail));
            cr.insert(BrowserContract.Bookmarks.CONTENT_URI, values);
        } catch (IllegalStateException e) {
            Log.e(LOGTAG, "addBookmark", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        if (retainIcon) {
            WebIconDatabase.getInstance().retainIconForPageUrl(url);
        }
        if (context != null) {
            Toast.makeText(context, R.string.added_to_bookmarks,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     *  Remove a bookmark from the database.  If the url is a visited site, it
     *  will remain in the database, but only as a history item, and not as a
     *  bookmarked site.
     *  @param context Context of the calling Activity.  This is used to make
     *          Toast confirming that the bookmark has been removed.  If the
     *          caller provides null, the Toast will not be shown.
     *  @param cr The ContentResolver being used to remove the bookmark.
     *  @param url URL of the website to be removed.
     */
    /* package */ static void removeFromBookmarks(Context context,
            ContentResolver cr, String url, String title) {
        Cursor cursor = null;
        try {
            cursor = cr.query(
                    Browser.BOOKMARKS_URI,
                    Browser.HISTORY_PROJECTION,
                    "url = ? AND title = ?",
                    new String[] { url, title },
                    null);
            boolean first = cursor.moveToFirst();
            // Should be in the database no matter what
            if (!first) {
                throw new AssertionError("URL is not in the database! " + url
                        + " " + title);
            }
            // Remove from bookmarks
            WebIconDatabase.getInstance().releaseIconForPageUrl(url);
            Uri uri = ContentUris.withAppendedId(Browser.BOOKMARKS_URI,
                    cursor.getInt(Browser.HISTORY_PROJECTION_ID_INDEX));
            int numVisits = cursor.getInt(
                    Browser.HISTORY_PROJECTION_VISITS_INDEX);
            if (0 == numVisits) {
                cr.delete(uri, null, null);
            } else {
                // It is no longer a bookmark, but it is still a visited
                // site.
                ContentValues values = new ContentValues();
                values.put(Browser.BookmarkColumns.BOOKMARK, 0);
                try {
                    cr.update(uri, values, null, null);
                } catch (IllegalStateException e) {
                    Log.e("removeFromBookmarks", "no database!");
                }
            }
            if (context != null) {
                Toast.makeText(context, R.string.removed_from_bookmarks,
                        Toast.LENGTH_LONG).show();
            }
        } catch (IllegalStateException e) {
            Log.e(LOGTAG, "removeFromBookmarks", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static byte[] bitmapToBytes(Bitmap bm) {
        if (bm == null) {
            return null;
        }

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, os);
        return os.toByteArray();
    }

    /* package */ static boolean urlHasAcceptableScheme(String url) {
        if (url == null) {
            return false;
        }

        for (int i = 0; i < acceptableBookmarkSchemes.length; i++) {
            if (url.startsWith(acceptableBookmarkSchemes[i])) {
                return true;
            }
        }
        return false;
    }

    /* package */ static Cursor queryBookmarksForUrl(ContentResolver cr,
            String originalUrl, String url, boolean onlyBookmarks) {
        if (cr == null || url == null) {
            return null;
        }
    
        // If originalUrl is null, just set it to url.
        if (originalUrl == null) {
            originalUrl = url;
        }
    
        // Look for both the original url and the actual url. This takes in to
        // account redirects.
        String originalUrlNoQuery = Bookmarks.removeQuery(originalUrl);
        String urlNoQuery = Bookmarks.removeQuery(url);
        originalUrl = originalUrlNoQuery + '?';
        url = urlNoQuery + '?';
    
        // Use NoQuery to search for the base url (i.e. if the url is
        // http://www.yahoo.com/?rs=1, search for http://www.yahoo.com)
        // Use url to match the base url with other queries (i.e. if the url is
        // http://www.google.com/m, search for
        // http://www.google.com/m?some_query)
        final String[] selArgs = new String[] {
            originalUrlNoQuery, urlNoQuery, originalUrl, url };
        String where = BookmarkColumns.URL + " == ? OR "
                + BookmarkColumns.URL + " == ? OR "
                + BookmarkColumns.URL + " LIKE ? || '%' OR "
                + BookmarkColumns.URL + " LIKE ? || '%'";
        if (onlyBookmarks) {
            where = "(" + where + ") AND " + BookmarkColumns.BOOKMARK + " == 1";
        }
        final String[] projection =
                new String[] { Browser.BookmarkColumns._ID };
        return cr.query(Browser.BOOKMARKS_URI, projection, where, selArgs,
                null);
    }

    // Strip the query from the given url.
    static String removeQuery(String url) {
        if (url == null) {
            return null;
        }
        int query = url.indexOf('?');
        String noQuery = url;
        if (query != -1) {
            noQuery = url.substring(0, query);
        }
        return noQuery;
    }

    /**
     * Update the bookmark's favicon. This is a convenience method for updating
     * a bookmark favicon for the originalUrl and url of the passed in WebView.
     * @param cr The ContentResolver to use.
     * @param originalUrl The original url before any redirects.
     * @param url The current url.
     * @param favicon The favicon bitmap to write to the db.
     */
    /* package */ static void updateBookmarkFavicon(final ContentResolver cr,
            final String originalUrl, final String url, final Bitmap favicon) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                final Cursor c =
                        Bookmarks.queryBookmarksForUrl(cr, originalUrl, url, true);
                if (c == null) {
                    return null;
                }
                if (c.moveToFirst()) {
                    ContentValues values = new ContentValues();
                    final ByteArrayOutputStream os =
                            new ByteArrayOutputStream();
                    favicon.compress(Bitmap.CompressFormat.PNG, 100, os);
                    values.put(Browser.BookmarkColumns.FAVICON,
                            os.toByteArray());
                    do {
                        cr.update(ContentUris.withAppendedId(
                                Browser.BOOKMARKS_URI, c.getInt(0)),
                                values, null, null);
                    } while (c.moveToNext());
                }
                c.close();
                return null;
            }
        }.execute();
    }
}
