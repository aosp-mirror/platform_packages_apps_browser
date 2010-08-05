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
 * limitations under the License
 */

package com.android.browser;

import com.android.browser.provider.BrowserContract.Bookmarks;

import android.content.Context;
import android.content.CursorLoader;

public class BookmarksLoader extends CursorLoader {
    public static final String ARG_ROOT_FOLDER = "root";

    public static final int COLUMN_INDEX_ID = 0;
    public static final int COLUMN_INDEX_URL = 1;
    public static final int COLUMN_INDEX_TITLE = 2;
    public static final int COLUMN_INDEX_FAVICON = 3;
    public static final int COLUMN_INDEX_THUMBNAIL = 4;
    public static final int COLUMN_INDEX_TOUCH_ICON = 5;
    public static final int COLUMN_INDEX_IS_FOLDER = 6;

    public static final String[] PROJECTION = new String[] {
        Bookmarks._ID, // 0
        Bookmarks.URL, // 1
        Bookmarks.TITLE, // 2
        Bookmarks.FAVICON, // 3
        Bookmarks.THUMBNAIL, // 4
        Bookmarks.TOUCH_ICON, // 5
        Bookmarks.IS_FOLDER, // 6
        Bookmarks.POSITION, // 7
    };

    public BookmarksLoader(Context context, int rootFolder) {
        super(context, Bookmarks.CONTENT_URI_DEFAULT_FOLDER, PROJECTION, null, null, null);
    }
}
