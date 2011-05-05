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

import android.net.Uri;

/**
 * Utility methods for Url manipulation
 */
public class UrlUtils {
    // Determine if this URI appears to be a Google property
    /* package */ static boolean isGoogleUri(Uri uri) {
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String[] hostComponents = host.split("\\.");
        if (hostComponents.length < 2) {
            return false;
        }

        int googleComponent = hostComponents.length - 2;
        String component = hostComponents[googleComponent];
        if (!"google".equals(component)) {
            if (hostComponents.length < 3 ||
                (!"co".equals(component) && !"com".equals(component))) {
                return false;
            }
            googleComponent = hostComponents.length - 3;
            if (!"google".equals(hostComponents[googleComponent])) {
                return false;
            }
        }
        return true;
    }
}
