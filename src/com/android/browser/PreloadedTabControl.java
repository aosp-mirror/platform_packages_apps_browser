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

import android.app.PendingIntent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.SearchBox;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Class to manage the controlling of preloaded tab.
 */
public class PreloadedTabControl {
    private static final boolean LOGD_ENABLED = com.android.browser.Browser.LOGD_ENABLED;
    private static final String LOGTAG = "PreloadedTabControl";

    final Tab mTab;
    private String mLastQuery;
    private boolean mDestroyed;

    public PreloadedTabControl(Tab t) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "PreloadedTabControl.<init>");
        mTab = t;
    }

    private boolean maybeSetQuery(final String query, SearchBox sb) {
        if (!TextUtils.equals(mLastQuery, query)) {
            if (sb != null) {
                if (LOGD_ENABLED) Log.d(LOGTAG, "Changing searchbox query to " + query);
                sb.setVerbatim(true);
                sb.setQuery(query);
                sb.onchange(new SearchBox.SearchBoxListener() {
                    @Override
                    public void onChangeComplete(boolean called) {
                        if (mDestroyed) return;
                        if (LOGD_ENABLED) Log.d(LOGTAG, "Changed searchbox query: " + called);
                        if (called) {
                            mLastQuery = query;
                        }
                    }
                });
                return true;
            } else {
                if (LOGD_ENABLED) Log.d(LOGTAG, "Cannot set query: no searchbox interface");
            }
        }
        return false;
    }

    public void setQuery(String query) {
        maybeSetQuery(query, mTab.getWebView().getSearchBox());
    }

    public boolean searchBoxSubmit(final String query, final String fallbackUrl,
            final Map<String, String> fallbackHeaders, final PendingIntent onLoadCompleteIntent) {
        final SearchBox sb = mTab.getWebView().getSearchBox();
        if (sb == null) {
            // no searchbox, cannot submit. Fallback to regular tab creation
            if (LOGD_ENABLED) Log.d(LOGTAG, "No searchbox, cannot submit query");
            return false;
        }
        final boolean newQuery = maybeSetQuery(query, sb);
        if (LOGD_ENABLED) Log.d(LOGTAG, "Submitting query " + query);
        final String currentUrl = mTab.getUrl();
        sb.onsubmit(new SearchBox.SearchBoxListener() {
            @Override
            public void onSubmitComplete(boolean called) {
                if (mDestroyed) return;
                if (LOGD_ENABLED) Log.d(LOGTAG, "Query submitted: " + called);
                if (!called) {
                    if (LOGD_ENABLED) Log.d(LOGTAG, "Query not submitted; falling back");
                    loadUrl(fallbackUrl, fallbackHeaders);

                    // make sure that the failed, preloaded URL is cleared from the back stack
                    mTab.clearBackStackWhenItemAdded(Pattern.compile(
                            "^" + Pattern.quote(fallbackUrl) + "$"));
                    // When setting the search box query, preloadAttempted=true implies that the
                    // the query was prefetched using the searchbox API. This is the case if we
                    // the query is not new.
                    registerLoadCompleteListener(!newQuery, false, onLoadCompleteIntent);
                } else {
                    // ignore the next fragment change, to avoid leaving a blank page in the browser
                    // after the query has been submitted.
                    String currentWithoutFragment = Uri.parse(currentUrl)
                            .buildUpon()
                            .fragment(null)
                            .toString();
                    mTab.clearBackStackWhenItemAdded(
                            Pattern.compile(
                                    "^" +
                                    Pattern.quote(currentWithoutFragment) +
                                    "(\\#.*)?" +
                                    "$"));
                    registerLoadCompleteListener(!newQuery, true, onLoadCompleteIntent);
                }
            }});
        return true;
    }

    private void registerLoadCompleteListener(
            final boolean queryPreloaded,
            final boolean preloadSucceeded,
            final PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            return;
        }
        mTab.setOnPageLoadCompleteListener(null, new Tab.OnPageLoadCompleteListener(){
            @Override
            public void onPageLoadComplete() {
                IntentHandler.sendPageLoadCompletePendingIntent(mTab.mContext, pendingIntent,
                        queryPreloaded, preloadSucceeded);
            }});
    }

    public void searchBoxCancel() {
        SearchBox sb = mTab.getWebView().getSearchBox();
        if (sb != null) {
            mLastQuery = null;
            sb.oncancel(new SearchBox.SearchBoxListener(){
                @Override
                public void onCancelComplete(boolean called) {
                    if (LOGD_ENABLED) Log.d(LOGTAG, "Query cancelled: " + called);
                }
            });
        }
    }

    public void loadUrlIfChanged(String url, Map<String, String> headers) {
        String currentUrl = mTab.getUrl();
        if (!TextUtils.isEmpty(currentUrl)) {
            try {
                // remove fragment:
                currentUrl = Uri.parse(currentUrl).buildUpon().fragment(null).build().toString();
            } catch (UnsupportedOperationException e) {
                // carry on
            }
        }
        if (LOGD_ENABLED) Log.d(LOGTAG, "loadUrlIfChanged\nnew: " + url + "\nold: " +currentUrl);
        if (!TextUtils.equals(url, currentUrl)) {
            loadUrl(url, headers);
        }
    }

    public void loadUrl(String url, Map<String, String> headers) {
        if (LOGD_ENABLED) Log.d(LOGTAG, "Preloading " + url);
        mTab.loadUrl(url, headers);
    }

    public void destroy() {
        if (LOGD_ENABLED) Log.d(LOGTAG, "PreloadedTabControl.destroy");
        mDestroyed = true;
        mTab.destroy();
    }

    public Tab getTab() {
        return mTab;
    }

}
