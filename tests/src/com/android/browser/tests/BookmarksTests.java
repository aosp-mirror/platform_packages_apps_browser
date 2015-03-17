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

package com.android.bookmarkprovider.tests;

import com.android.bookmarkprovider.tests.utils.BP2TestCaseHelper;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.BrowserContract.Combined;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Extends from BP2TestCaseHelper for the helper methods
 * and to get the mock database
 */
@SmallTest
public class BookmarksTests extends BP2TestCaseHelper {
    private static final String QUERY_BOOKMARKS_WHERE =
            Combined.URL + " == ? OR " +
            Combined.URL + " == ?";

    private static Cursor queryCombinedForUrl(ContentResolver cr,
            String originalUrl, String url) {
        if (cr == null || url == null) {
            return null;
        }

        // If originalUrl is null, just set it to url.
        if (originalUrl == null) {
            originalUrl = url;
        }

        // Look for both the original url and the actual url. This takes in to
        // account redirects.

        final String[] selArgs = new String[] { originalUrl, url };
        final String[] projection = new String[] { Combined.URL };
        return cr.query(Combined.CONTENT_URI, projection, QUERY_BOOKMARKS_WHERE, selArgs, null);
    }

    public void testQueryCombinedForUrl() {
        // First, add some bookmarks
        assertNotNull(insertBookmark(
                "http://google.com/search?q=test", "Test search"));
        assertNotNull(insertBookmark(
                "http://google.com/search?q=mustang", "Mustang search"));
        assertNotNull(insertBookmark(
                "http://google.com/search?q=aliens", "Aliens search"));
        ContentResolver cr = getMockContentResolver();

        Cursor c = null;
        try {
            // First, search for a match
            String url = "http://google.com/search?q=test";
            c = queryCombinedForUrl(cr, null, url);
            assertEquals(1, c.getCount());
            assertTrue(c.moveToFirst());
            assertEquals(url, c.getString(0));
            c.close();

            // Next, search for no match
            url = "http://google.com/search";
            c = queryCombinedForUrl(cr, null, url);
            assertEquals(0, c.getCount());
            assertFalse(c.moveToFirst());
            c.close();
        } finally {
            if (c != null) c.close();
        }
    }
}
