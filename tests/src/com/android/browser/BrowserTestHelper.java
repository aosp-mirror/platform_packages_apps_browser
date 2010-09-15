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
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;
import android.provider.Browser.SearchColumns;
import android.test.UiThreadTest;

import java.util.ArrayList;

public class BrowserTestHelper {

    private ArrayList<ContentValues> mBookmarksBackup;

    private ArrayList<ContentValues> mSearchesBackup;

    private ContentResolver mContentResolver;

    public void backupBookmarkDatabase(ContentResolver mContentResolver) {
        // backup the current contents in database
        if (mContentResolver != null) {
            mBookmarksBackup = new ArrayList<ContentValues>();
            mSearchesBackup = new ArrayList<ContentValues>();

            this.mContentResolver = mContentResolver;
            Cursor cursor = mContentResolver.query(Browser.BOOKMARKS_URI, null, null, null, null);
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    ContentValues value = new ContentValues();

                    value.put(BookmarkColumns._ID, cursor.getInt(0));
                    value.put(BookmarkColumns.TITLE, cursor.getString(1));
                    value.put(BookmarkColumns.URL, cursor.getString(2));
                    value.put(BookmarkColumns.VISITS, cursor.getInt(3));
                    value.put(BookmarkColumns.DATE, cursor.getLong(4));
                    value.put(BookmarkColumns.CREATED, cursor.getLong(5));
                    value.put(BookmarkColumns.BOOKMARK, cursor.getInt(7));
                    value.put(BookmarkColumns.FAVICON, cursor.getBlob(8));
                    mBookmarksBackup.add(value);

                    cursor.moveToNext();
                }
            }
            cursor.close();

            cursor = mContentResolver.query(Browser.SEARCHES_URI, null, null, null, null);
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    ContentValues value = new ContentValues();

                    value.put(SearchColumns._ID, cursor.getInt(0));
                    value.put(SearchColumns.SEARCH, cursor.getString(1));
                    value.put(SearchColumns.DATE, cursor.getLong(2));
                    mSearchesBackup.add(value);

                    cursor.moveToNext();
                }
            }
            cursor.close();

            mContentResolver.delete(Browser.BOOKMARKS_URI, null, null);
            mContentResolver.delete(Browser.SEARCHES_URI, null, null);
            System.out.println("Bookmarks has been backed up.");
        } else {
            System.out.println("Couldn't back up bookmarks.");
        }
    }

    public void restoreBookmarkDatabase() {
        if (mContentResolver != null) {
            // clear all new contents added in test cases.
            mContentResolver.delete(Browser.BOOKMARKS_URI, null, null);
            mContentResolver.delete(Browser.SEARCHES_URI, null, null);

            // recover the old backup contents
            for (ContentValues value : mBookmarksBackup) {
                mContentResolver.insert(Browser.BOOKMARKS_URI, value);
            }

            for (ContentValues value : mSearchesBackup) {
                mContentResolver.insert(Browser.SEARCHES_URI, value);
            }
            System.out.println("Bookmarks has been restored.");
        } else {
            System.out.println("Nothing has been backed up.");
        }
    }

    public void prepareDatabaseWithNullTitleBookmark() {
        String nullTitle = null;

        ContentValues values = new ContentValues();
        values.put(BookmarkColumns.URL, BrowserBookmarkTest.TEST_URL);
        values.put(BookmarkColumns.TITLE, nullTitle);
        values.put(BookmarkColumns.BOOKMARK, 1);
        mContentResolver.insert(Browser.BOOKMARKS_URI, values);
    }

    @UiThreadTest
    public void deleteOnUIThread(BrowserBookmarksPage activity, int position) {
        activity.deleteBookmark(position);
    }

}
