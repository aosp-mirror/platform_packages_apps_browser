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

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.http.AndroidHttpClient;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.StringTokenizer;

public class GoogleAccountLogin extends Thread implements
        AccountManagerCallback<Bundle>, OnCancelListener {

    // Url for issuing the uber token.
    private Uri ISSUE_AUTH_TOKEN_URL = Uri.parse(
            "https://www.google.com/accounts/IssueAuthToken?service=gaia&Session=false");
    // Url for signing into a particular service.
    private final static Uri TOKEN_AUTH_URL = Uri.parse(
            "https://www.google.com/accounts/TokenAuth");
    // Google account type
    private final static String GOOGLE = "com.google";

    private final Activity mActivity;
    private final Account mAccount;
    private final WebView mWebView;
    private Runnable mRunnable;

    // SID and LSID retrieval process.
    private String mSid;
    private String mLsid;
    private int mState;  // {NONE(0), SID(1), LSID(2)}

    GoogleAccountLogin(Activity activity, String name) {
        mActivity = activity;
        mAccount = new Account(name, GOOGLE);
        mWebView = new WebView(mActivity);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                done();
            }
        });
    }

    // Thread
    @Override
    public void run() {
        String url = ISSUE_AUTH_TOKEN_URL.buildUpon()
                .appendQueryParameter("SID", mSid)
                .appendQueryParameter("LSID", mLsid)
                .build().toString();
        // Check mRunnable to see if the request has been canceled.  Otherwise
        // we might access a destroyed WebView.
        String ua = null;
        synchronized (this) {
            if (mRunnable == null) {
                return;
            }
            ua = mWebView.getSettings().getUserAgentString();
        }
        // Intentionally not using Proxy.
        AndroidHttpClient client = AndroidHttpClient.newInstance(ua);
        HttpPost request = new HttpPost(url);

        String result = null;
        try {
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                done();
                return;
            }
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                done();
                return;
            }
            result = EntityUtils.toString(entity, "UTF-8");
        } catch (Exception e) {
            request.abort();
            done();
            return;
        } finally {
            client.close();
        }
        final String newUrl = TOKEN_AUTH_URL.buildUpon()
                .appendQueryParameter("source", "android-browser")
                .appendQueryParameter("auth", result)
                .appendQueryParameter("continue",
                        BrowserSettings.getFactoryResetHomeUrl(mActivity))
                .build().toString();
        mActivity.runOnUiThread(new Runnable() {
            @Override public void run() {
                // Check mRunnable in case the request has been canceled.  This
                // is most likely not necessary as run() is the only non-UI
                // thread that calls done() but I am paranoid.
                synchronized (GoogleAccountLogin.this) {
                    if (mRunnable == null) {
                        return;
                    }
                    mWebView.loadUrl(newUrl);
                }
            }
        });
    }

    // AccountManager callbacks.
    @Override
    public void run(AccountManagerFuture<Bundle> value) {
        try {
            String id = value.getResult().getString(
                    AccountManager.KEY_AUTHTOKEN);
            switch (mState) {
                default:
                case 0:
                    throw new IllegalStateException(
                            "Impossible to get into this state");
                case 1:
                    mSid = id;
                    mState = 2;  // LSID
                    AccountManager.get(mActivity).getAuthToken(
                            mAccount, "LSID", null, mActivity, this, null);
                    break;
                case 2:
                    mLsid = id;
                    this.start();
                    break;
            }
        } catch (Exception e) {
            // For all exceptions load the original signin page.
            // TODO: toast login failed?
            done();
        }
    }

    public void startLogin(Runnable runnable) {
        mRunnable = runnable;
        mState = 1;  // SID
        AccountManager.get(mActivity).getAuthToken(
                mAccount, "SID", null, mActivity, this, null);
    }

    // Returns the account name passed in if the account exists, otherwise
    // returns the default account.
    public static String validateAccount(Context ctx, String name) {
        Account[] accounts = getAccounts(ctx);
        if (accounts.length == 0) {
            return null;
        }
        if (name != null) {
            // Make sure the account still exists.
            for (Account a : accounts) {
                if (a.name.equals(name)) {
                    return name;
                }
            }
        }
        // Return the first entry.
        return accounts[0].name;
    }

    public static Account[] getAccounts(Context ctx) {
        return AccountManager.get(ctx).getAccountsByType(GOOGLE);
    }

    // Checks for the presence of the SID cookie on google.com.
    public static boolean isLoggedIn() {
        String cookies = CookieManager.getInstance().getCookie(
                "http://www.google.com");
        if (cookies != null) {
            StringTokenizer tokenizer = new StringTokenizer(cookies, ";");
            while (tokenizer.hasMoreTokens()) {
                String cookie = tokenizer.nextToken().trim();
                if (cookie.startsWith("SID=")) {
                    return true;
                }
            }
        }
        return false;
    }

    // Used to indicate that the Browser should continue loading the main page.
    // This can happen on success, error, or timeout.
    private synchronized void done() {
        if (mRunnable != null) {
            mActivity.runOnUiThread(mRunnable);
            mRunnable = null;
            mWebView.destroy();
        }
    }

    // Called by the progress dialog on startup.
    public void onCancel(DialogInterface unused) {
        done();
    }
}
