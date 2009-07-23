/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

/**
 * This is a series of unit tests for the BrowserSettings class.
 *
 */
@MediumTest
public class BrowserSettingsUnitTests extends AndroidTestCase {

    /**
     * Test the application caches max size calculator.
     */
    public void testCalculateAppCacheMaxSize() {
        long fileSystemSize = 78643200;  // 75 MB
        long freeSpaceSize = 25165824;  // 24 MB
        long maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(6815744, maxSize);  // 6.5MB

        fileSystemSize = 78643200;  // 75 MB
        freeSpaceSize = 60 * 1024 * 1024;  // 60MB
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(9961472, maxSize);  // 9.5MB

        fileSystemSize = 8589934592L;  // 8 GB
        freeSpaceSize = 4294967296L;  // 4 GB
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(268959744L, maxSize);  // 256.5 MB

        fileSystemSize = -14;
        freeSpaceSize = 21;
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 100;
        freeSpaceSize = 101;
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~4.2 MB
        freeSpaceSize = 2560000;  // ~2.4 MB
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(1048576, maxSize);  // 1 MB

        fileSystemSize = 4404019; // ~4.2 MB
        freeSpaceSize = 3774873;  // ~3.6 MB
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(1048576, maxSize);  // 1 MB

        fileSystemSize = 4404019; // ~4.2 MB
        freeSpaceSize = 4404019;  // ~4.2 MB
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(1572864, maxSize);  // 1.5 MB

        fileSystemSize = 1048576; // 1 MB
        freeSpaceSize = 1048575;  // 1 MB - 1 byte
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 1048576; // 1 MB
        freeSpaceSize = 1048576;  // 1 MB
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(524288, maxSize);  // 512KB

        fileSystemSize = 3774873; // ~3.6 MB
        freeSpaceSize = 2097151;  // 2 MB - 1 byte
        maxSize = BrowserSettings.calculateAppCacheMaxSize(fileSystemSize, freeSpaceSize);
        assertEquals(524288, maxSize);  // 512KB
    }
}
