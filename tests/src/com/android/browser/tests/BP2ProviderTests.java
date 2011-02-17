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

package com.android.browser.tests;

import com.android.browser.tests.utils.BP2TestCaseHelper;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.provider.BrowserContract.Images;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.ByteArrayOutputStream;

@SmallTest
public class BP2ProviderTests extends BP2TestCaseHelper {

    public void testUpdateImage() {
        String url = "http://stub1.com";
        insertBookmark(url, "stub 1");
        ContentValues values = new ContentValues();
        values.put(Images.URL, url);
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Config.ARGB_8888);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
        values.put(Images.THUMBNAIL, os.toByteArray());
        // Use updateBookmarks because the bookmarks URI observer should
        // be triggered, even though we aren't giving it a bookmarks URI
        assertTrue(updateBookmark(Images.CONTENT_URI, values));
    }

}
