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

import android.text.TextUtils;
import android.util.Log;
import android.webkit.SearchBox;

import java.util.Map;

/**
 * Class to manage the controlling of preloaded tab.
 */
public class PreloadedTabControl {
    private static final boolean LOGD_ENABLED = true;//com.android.browser.Browser.LOGD_ENABLED;
    private static final String LOGTAG = "PreloadedTabControl";

    final Tab mTab;
    private String mLastQuery;
    private boolean mDestroyed;

    public PreloadedTabControl(Tab t) {
        mTab = t;
    }

    private void maybeSetQuery(String query, SearchBox sb) {
        if (!TextUtils.equals(mLastQuery, query)) {
            if (sb != null) {
                if (LOGD_ENABLED) Log.d(LOGTAG, "Changing searchbox query to " + query);
                sb.setVerbatim(true);
                sb.setQuery(query);
                sb.onchange();
                mLastQuery = query;
            } else {
                if (LOGD_ENABLED) Log.d(LOGTAG, "Cannot set query: no searchbox interface");
            }
        }
    }

    public void setQuery(String query) {
        maybeSetQuery(query, mTab.getWebView().getSearchBox());
    }

    public boolean searchBoxSubmit(final String query,
            final String fallbackUrl, final Map<String, String> fallbackHeaders) {
        final SearchBox sb = mTab.getWebView().getSearchBox();
        if (sb == null) {
            // no searchbox, cannot submit. Fallback to regular tab creation
            if (LOGD_ENABLED) Log.d(LOGTAG, "No searchbox, cannot submit query");
            return false;
        }
        sb.isSupported(new SearchBox.IsSupportedCallback() {
            @Override
            public void searchBoxIsSupported(boolean supported) {
                if (LOGD_ENABLED) Log.d(LOGTAG, "SearchBox supported: " + supported);
                if (mDestroyed) {
                    if (LOGD_ENABLED) Log.d(LOGTAG, "tab has been destroyed");
                    return;
                }
                if (supported) {
                    maybeSetQuery(query, sb);
                    if (LOGD_ENABLED) Log.d(LOGTAG, "Submitting query " + query);
                    sb.onsubmit();
                } else {
                    if (LOGD_ENABLED) Log.d(LOGTAG, "SearchBox not supported; falling back");
                    loadUrl(fallbackUrl, fallbackHeaders);
                }
                mTab.getWebView().clearHistory();
            }
        });
        return true;
    }

    public void loadUrlIfChanged(String url, Map<String, String> headers) {
        if (!TextUtils.equals(url, mTab.getUrl())) {
            loadUrl(url, headers);
        }
    }

    public void loadUrl(String url, Map<String, String> headers) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "Preloading " + url);
        mTab.loadUrl(url, headers);
    }

    public void destroy() {
        mDestroyed = true;
        mTab.destroy();
    }

    public Tab getTab() {
        return mTab;
    }

}
