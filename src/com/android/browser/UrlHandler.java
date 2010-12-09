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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.WebView;

import java.net.URISyntaxException;

/**
 *
 */
public class UrlHandler {

    // Use in overrideUrlLoading
    /* package */ final static String SCHEME_WTAI = "wtai://wp/";
    /* package */ final static String SCHEME_WTAI_MC = "wtai://wp/mc;";
    /* package */ final static String SCHEME_WTAI_SD = "wtai://wp/sd;";
    /* package */ final static String SCHEME_WTAI_AP = "wtai://wp/ap;";

    Controller mController;
    Activity mActivity;

    private Boolean mIsProviderPresent = null;
    private Uri mRlzUri = null;

    public UrlHandler(Controller controller) {
        mController = controller;
        mActivity = mController.getActivity();
    }

    boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (view.isPrivateBrowsingEnabled()) {
            // Don't allow urls to leave the browser app when in
            // private browsing mode
            mController.loadUrl(view, url);
            return true;
        }

        if (url.startsWith(SCHEME_WTAI)) {
            // wtai://wp/mc;number
            // number=string(phone-number)
            if (url.startsWith(SCHEME_WTAI_MC)) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(WebView.SCHEME_TEL +
                        url.substring(SCHEME_WTAI_MC.length())));
                mActivity.startActivity(intent);
                // before leaving BrowserActivity, close the empty child tab.
                // If a new tab is created through JavaScript open to load this
                // url, we would like to close it as we will load this url in a
                // different Activity.
                mController.closeEmptyChildTab();
                return true;
            }
            // wtai://wp/sd;dtmf
            // dtmf=string(dialstring)
            if (url.startsWith(SCHEME_WTAI_SD)) {
                // TODO: only send when there is active voice connection
                return false;
            }
            // wtai://wp/ap;number;name
            // number=string(phone-number)
            // name=string
            if (url.startsWith(SCHEME_WTAI_AP)) {
                // TODO
                return false;
            }
        }

        // The "about:" schemes are internal to the browser; don't want these to
        // be dispatched to other apps.
        if (url.startsWith("about:")) {
            return false;
        }

        // If this is a Google search, attempt to add an RLZ string
        // (if one isn't already present).
        if (rlzProviderPresent()) {
            Uri siteUri = Uri.parse(url);
            if (needsRlzString(siteUri)) {
                // Need to look up the RLZ info from a database, so do it in an
                // AsyncTask. Although we are not overriding the URL load synchronously,
                // we guarantee that we will handle this URL load after the task executes,
                // so it's safe to just return true to WebCore now to stop its own loading.
                new RLZTask(siteUri, view).execute();
                return true;
            }
        }

        Intent intent;
        // perform generic parsing of the URI to turn it into an Intent.
        try {
            intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException ex) {
            Log.w("Browser", "Bad URI " + url + ": " + ex.getMessage());
            return false;
        }

        // check whether the intent can be resolved. If not, we will see
        // whether we can download it from the Market.
        if (mActivity.getPackageManager().resolveActivity(intent, 0) == null) {
            String packagename = intent.getPackage();
            if (packagename != null) {
                intent = new Intent(Intent.ACTION_VIEW, Uri
                        .parse("market://search?q=pname:" + packagename));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                mActivity.startActivity(intent);
                // before leaving BrowserActivity, close the empty child tab.
                // If a new tab is created through JavaScript open to load this
                // url, we would like to close it as we will load this url in a
                // different Activity.
                mController.closeEmptyChildTab();
                return true;
            } else {
                return false;
            }
        }

        // sanitize the Intent, ensuring web pages can not bypass browser
        // security (only access to BROWSABLE activities).
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        try {
            if (mActivity.startActivityIfNeeded(intent, -1)) {
                // before leaving BrowserActivity, close the empty child tab.
                // If a new tab is created through JavaScript open to load this
                // url, we would like to close it as we will load this url in a
                // different Activity.
                mController.closeEmptyChildTab();
                return true;
            }
        } catch (ActivityNotFoundException ex) {
            // ignore the error. If no application can handle the URL,
            // eg about:blank, assume the browser can handle it.
        }

        if (mController.isMenuDown()) {
            mController.openTab(url, false);
            mActivity.closeOptionsMenu();
            return true;
        }
        return false;
    }

    private class RLZTask extends AsyncTask<Void, Void, String> {
        private Uri mSiteUri;
        private WebView mWebView;

        public RLZTask(Uri uri, WebView webView) {
            mSiteUri = uri;
            mWebView = webView;
        }

        protected String doInBackground(Void... unused) {
            String result = mSiteUri.toString();
            Cursor cur = null;
            try {
                cur = mActivity.getContentResolver()
                        .query(getRlzUri(), null, null, null, null);
                if (cur != null && cur.moveToFirst() && !cur.isNull(0)) {
                    result = mSiteUri.buildUpon()
                           .appendQueryParameter("rlz", cur.getString(0))
                           .build().toString();
                }
            } finally {
                if (cur != null) {
                    cur.close();
                }
            }
            return result;
        }

        protected void onPostExecute(String result) {
            mController.loadUrl(mWebView, result);
        }
    }

    // Determine whether the RLZ provider is present on the system.
    private boolean rlzProviderPresent() {
        if (mIsProviderPresent == null) {
            PackageManager pm = mActivity.getPackageManager();
            mIsProviderPresent = pm.resolveContentProvider(
                    BrowserSettings.RLZ_PROVIDER, 0) != null;
        }
        return mIsProviderPresent;
    }

    // Retrieve the RLZ access point string and cache the URI used to
    // retrieve RLZ values.
    private Uri getRlzUri() {
        if (mRlzUri == null) {
            String ap = mActivity.getResources()
                    .getString(R.string.rlz_access_point);
            mRlzUri = Uri.withAppendedPath(BrowserSettings.RLZ_PROVIDER_URI, ap);
        }
        return mRlzUri;
    }

    // Determine if this URI appears to be for a Google search
    // and does not have an RLZ parameter.
    // Taken largely from Chrome source, src/chrome/browser/google_url_tracker.cc
    private static boolean needsRlzString(Uri uri) {
        String scheme = uri.getScheme();
        if (("http".equals(scheme) || "https".equals(scheme)) &&
            (uri.getQueryParameter("q") != null) &&
                    (uri.getQueryParameter("rlz") == null)) {
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

            // Google corp network handling.
            if (googleComponent > 0 && "corp".equals(
                    hostComponents[googleComponent - 1])) {
                return false;
            }

            return true;
        }
        return false;
    }

}
