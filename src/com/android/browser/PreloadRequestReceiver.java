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
package com.android.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Broadcast receiver for receiving browser preload requests
 */
public class PreloadRequestReceiver extends BroadcastReceiver {

    private final static String LOGTAG = "browser.preloader";
    private final static boolean LOGD_ENABLED = com.android.browser.Browser.LOGD_ENABLED;

    private static final String ACTION_PRELOAD = "android.intent.action.PRELOAD";
    static final String EXTRA_PRELOAD_ID = "preload_id";
    static final String EXTRA_PRELOAD_DISCARD = "preload_discard";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "received intent " + intent);
        if (BrowserSettings.getInstance().isPreloadEnabled()
                && intent.getAction().equals(ACTION_PRELOAD)) {
            handlePreload(context, intent);
        }
    }

    private void handlePreload(Context context, Intent i) {
        String url = UrlUtils.smartUrlFilter(i.getData());
        String id = i.getStringExtra(EXTRA_PRELOAD_ID);
        Map<String, String> headers = null;
        if (id == null) {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Preload request has no " + EXTRA_PRELOAD_ID);
            return;
        }
        if (i.getBooleanExtra(EXTRA_PRELOAD_DISCARD, false)) {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Got " + id + " preload discard request");
            Preloader.getInstance().discardPreload(id);
        } else {
            if (LOGD_ENABLED) Log.d(LOGTAG, "Got " + id + " preload request for " + url);
            if (url != null && url.startsWith("http")) {
                final Bundle pairs = i.getBundleExtra(Browser.EXTRA_HEADERS);
                if (pairs != null && !pairs.isEmpty()) {
                    Iterator<String> iter = pairs.keySet().iterator();
                    headers = new HashMap<String, String>();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        headers.put(key, pairs.getString(key));
                    }
                }
            }
            if (url != null) {
                Preloader.getInstance().handlePreloadRequest(id, url, headers);
            }
        }
    }

}
