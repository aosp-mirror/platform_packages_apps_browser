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

package com.android.bookmarkprovider.tests.utils;

import com.android.bookmarkprovider.BrowserProvider2;

import java.io.File;
import java.io.FilenameFilter;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;

/**
 *  This is a replacement for ProviderTestCase2 that can handle notifyChange testing.
 *  It also has helper methods specifically for testing BrowserProvider2
 */
public abstract class BP2TestCaseHelper extends ProviderTestCase3<BrowserProvider2> {

    // Tag for potential performance impacts
    private static final String PERFTAG = "BP2-PerfCheck";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public BP2TestCaseHelper() {
        super(BrowserProvider2.class,
                BrowserContract.AUTHORITY, BrowserProvider2.LEGACY_AUTHORITY);
    }

    Uri mockInsert(Uri uri, ContentValues values) {
        return getMockContentResolver().insert(uri, values);
    }

    int mockUpdate(Uri uri, ContentValues values, String where,
            String[] selectionArgs) {
        return getMockContentResolver().update(uri, values, where, selectionArgs);
    }

    public Uri insertBookmark(String url, String title) {
        ContentValues values = new ContentValues();
        values.put(BrowserContract.Bookmarks.TITLE, title);
        values.put(BrowserContract.Bookmarks.URL, url);
        values.put(BrowserContract.Bookmarks.IS_FOLDER, 0);
        return insertBookmark(values);
    }

    public Uri insertBookmark(ContentValues values) {
        return mockInsert(Bookmarks.CONTENT_URI, values);
    }

    public boolean updateBookmark(Uri uri, String url, String title) {
        ContentValues values = new ContentValues();
        values.put(BrowserContract.Bookmarks.TITLE, title);
        values.put(BrowserContract.Bookmarks.URL, url);
        return updateBookmark(uri, values);
    }

    public boolean updateBookmark(Uri uri, ContentValues values) {
        int modifyCount = mockUpdate(uri, values, null, null);
        assertTrue("UpdatedBookmark modified too much! " + uri, modifyCount <= 1);
        return modifyCount == 1;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Delete the test databases so that subsequent runs have a clean slate
        File f = getMockContext().getDatabasePath("test");
        File dir = f.getParentFile();
        File testFiles[] = dir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                return filename.startsWith(ProviderTestCase3.FILENAME_PREFIX);
            }
        });
        for (File testFile : testFiles) {
            testFile.delete();
        }
    }
}
