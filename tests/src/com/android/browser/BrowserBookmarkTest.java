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

import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;
import android.test.ActivityInstrumentationTestCase2;

public class BrowserBookmarkTest extends ActivityInstrumentationTestCase2<BrowserBookmarksPage> {

    private Context mContext;

    private BrowserBookmarksPage mActivity;

    private ContentResolver mContentResolver;

    private BrowserTestHelper testHelper;

    private Instrumentation mInst;

    final static String TEST_URL = "www.google.com";

    private String nullTitle;

    public BrowserBookmarkTest() {
        super("com.android.browser", BrowserBookmarksPage.class);
        testHelper = new BrowserTestHelper();
    }

    @Override
    protected void setUp() throws Exception {
        mActivity = getActivity();
        mContext = mActivity.getApplicationContext();
        mContentResolver = mContext.getContentResolver();
        mInst = getInstrumentation();
        testHelper.backupBookmarkDatabase(mContentResolver);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        testHelper.restoreBookmarkDatabase();
        super.tearDown();
    }

    public void testAddBookmarkWithNullTitle() {
        Cursor cursor;

        cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(0, cursor.getCount());
        cursor.close();

        ContentValues values = new ContentValues();
        values.put(BookmarkColumns.URL, TEST_URL);
        values.put(BookmarkColumns.TITLE, nullTitle);
        values.put(BookmarkColumns.BOOKMARK, 1);
        mContentResolver.insert(Browser.BOOKMARKS_URI, values);

        cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();
    }

    /*
     * The codes used as inparams to TestItem comes from
     * BrowserBookmarksPage.onContextItemSelected()
     */
    public void testLoadBookmarkWithNullTitle() {
        testHelper.prepareDatabaseWithNullTitleBookmark();

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.open_context_menu_id);
        boolean result = mActivity.onContextItemSelected(item);
        assertTrue(result);
    }

    public void testEditBookmarkWithoutTitle() {
        boolean result;
        testHelper.prepareDatabaseWithNullTitleBookmark();

        IntentFilter filter = new IntentFilter("com.android.browser.AddBookmarkPage");
        ActivityMonitor monitor = new ActivityMonitor(filter, null, true);
        mInst.addMonitor(monitor);

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.edit_context_menu_id);
        result = mActivity.onContextItemSelected(item);
        assertTrue(result);
        assertTrue(mInst.checkMonitorHit(monitor, 1));
    }

    public void testAddMenuShortcutOfNullTitleBookmark() {
        boolean result;
        testHelper.prepareDatabaseWithNullTitleBookmark();

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.shortcut_context_menu_id);
        result = mActivity.onContextItemSelected(item);
        assertTrue(result);
    }

    public void testShowDeleteNullTitleBookmarkDialog() {
        boolean result = false;
        Cursor cursor;
        testHelper.prepareDatabaseWithNullTitleBookmark();

        cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.delete_context_menu_id);
        result = mActivity.onContextItemSelected(item);

        cursor = Browser.getAllBookmarks(mContentResolver);
        assertTrue(result);
        cursor.close();
    }

    public void testLoadNullTitleBookmarkInNewWindow() {
        boolean result;
        testHelper.prepareDatabaseWithNullTitleBookmark();

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.new_window_context_menu_id);
        result = mActivity.onContextItemSelected(item);
        assertTrue(result);
    }

    public void testSendNullTitleBookmark() {
        testHelper.prepareDatabaseWithNullTitleBookmark();

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        ActivityMonitor monitor = new ActivityMonitor(new IntentFilter(Intent.ACTION_CHOOSER),
                null, true);
        mInst.addMonitor(monitor);
        TestItem item = new TestItem(R.id.share_link_context_menu_id);
        mActivity.onContextItemSelected(item);
        assertTrue(mInst.checkMonitorHit(monitor, 1));
    }

    public void testCopyUrlOfNullTitleBookmark() {
        boolean result;
        testHelper.prepareDatabaseWithNullTitleBookmark();

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.copy_url_context_menu_id);
        result = mActivity.onContextItemSelected(item);
        assertTrue(result);
    }

    public void testSetNullTitleBookmarkAsHomepage() {
        boolean result;
        testHelper.prepareDatabaseWithNullTitleBookmark();

        Cursor cursor = Browser.getAllBookmarks(mContentResolver);
        assertEquals(1, cursor.getCount());
        cursor.close();

        TestItem item = new TestItem(R.id.homepage_context_menu_id);
        result = mActivity.onContextItemSelected(item);
        assertTrue(result);
    }
}
