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
 * This is a series of unit tests for the WebStorageSizeManager class.
 *
 */
@MediumTest
public class WebStorageSizeManagerUnitTests extends AndroidTestCase {

    /**
     * Test the application caches max size calculator.
     */
    public void testCalculateGlobalLimit() {
        long fileSystemSize = 78643200;  // 75 MB
        long freeSpaceSize = 25165824;  // 24 MB
        long maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(12582912, maxSize);  // 12MB

        fileSystemSize = 78643200;  // 75 MB
        freeSpaceSize = 60 * 1024 * 1024;  // 60MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(19922944, maxSize);  // 19MB

        fileSystemSize = 8589934592L;  // 8 GB
        freeSpaceSize = 4294967296L;  // 4 GB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(536870912L, maxSize);  // 512 MB

        fileSystemSize = -14;
        freeSpaceSize = 21;
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 100;
        freeSpaceSize = 101;
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~4.2 MB
        freeSpaceSize = 2560000;  // ~2.4 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(2097152, maxSize);  // 2 MB

        fileSystemSize = 4404019; // ~4.2 MB
        freeSpaceSize = 3774873;  // ~3.6 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(2097152, maxSize);  // 2 MB

        fileSystemSize = 4404019; // ~4.2 MB
        freeSpaceSize = 4404019;  // ~4.2 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(3145728, maxSize);  // 3 MB

        fileSystemSize = 1048576; // 1 MB
        freeSpaceSize = 1048575;  // 1 MB - 1 byte
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~3.6 MB
        freeSpaceSize = 2097151;  // 2 MB - 1 byte
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~3.6 MB
        freeSpaceSize = 2097151;  // 2 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);
    }
}
