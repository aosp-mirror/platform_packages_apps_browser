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

import android.util.Log;

import android.app.Application;
import android.content.Intent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import dalvik.system.VMRuntime;

public class Browser extends Application { 

    private final static String LOGTAG = "browser";

    // Set to true to enable extra debugging.
    final static boolean DEBUG = false;
    
    // Set to true to enable verbose logging.
    final static boolean LOGV_ENABLED = DEBUG;

    // Set to true to enable extra debug logging.
    final static boolean LOGD_ENABLED = true;

    /**
     * Specifies a heap utilization ratio that works better
     * for the browser than the default ratio does.
     */
    private final static float TARGET_HEAP_UTILIZATION = 0.75f;

    public Browser() {
    }

    public void onCreate() {
        if (LOGV_ENABLED)
            Log.v(LOGTAG, "Browser.onCreate: this=" + this);
        // Fix heap utilization for better heap size characteristics.
        VMRuntime.getRuntime().setTargetHeapUtilization(
                TARGET_HEAP_UTILIZATION);
        // create CookieSyncManager with current Context
        CookieSyncManager.createInstance(this);
        // remove all expired cookies
        CookieManager.getInstance().removeExpiredCookie();
        BrowserSettings.getInstance().loadFromDb(this);
    }

    static Intent createBrowserViewIntent() {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        return intent;
    }
}

