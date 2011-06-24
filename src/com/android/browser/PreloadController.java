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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebView;

import java.util.List;

public class PreloadController implements WebViewController {

    private Context mContext;

    public PreloadController(Context ctx) {
        mContext = ctx;

    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public Activity getActivity() {
        return null;
    }

    @Override
    public TabControl getTabControl() {
        return null;
    }

    @Override
    public WebViewFactory getWebViewFactory() {
        return null;
    }

    @Override
    public void onSetWebView(Tab tab, WebView view) {
    }

    @Override
    public void createSubWindow(Tab tab) {
    }

    @Override
    public void onPageStarted(Tab tab, WebView view, Bitmap favicon) {
    }

    @Override
    public void onPageFinished(Tab tab) {
    }

    @Override
    public void onProgressChanged(Tab tab) {
    }

    @Override
    public void onReceivedTitle(Tab tab, String title) {
    }

    @Override
    public void onFavicon(Tab tab, WebView view, Bitmap icon) {
    }

    @Override
    public boolean shouldOverrideUrlLoading(Tab tab, WebView view, String url) {
        return false;
    }

    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        return false;
    }

    @Override
    public void onUnhandledKeyEvent(KeyEvent event) {
    }

    @Override
    public void doUpdateVisitedHistory(Tab tab, boolean isReload) {
    }

    @Override
    public void getVisitedHistory(ValueCallback<String[]> callback) {
    }

    @Override
    public void onReceivedHttpAuthRequest(Tab tab, WebView view,
                                    HttpAuthHandler handler, String host,
                                    String realm) {
    }

    @Override
    public void onDownloadStart(Tab tab, String url, String useragent,
                                    String contentDisposition, String mimeType,
                                    long contentLength) {
    }

    @Override
    public void showCustomView(Tab tab, View view, int requestedOrientation,
                                    CustomViewCallback callback) {
    }

    @Override
    public void hideCustomView() {
    }

    @Override
    public Bitmap getDefaultVideoPoster() {
        return null;
    }

    @Override
    public View getVideoLoadingProgressView() {
        return null;
    }

    @Override
    public void showSslCertificateOnError(WebView view,
                                    SslErrorHandler handler, SslError error) {
    }

    @Override
    public void onUserCanceledSsl(Tab tab) {
    }

    @Override
    public void activateVoiceSearchMode(String title, List<String> results) {
    }

    @Override
    public void revertVoiceSearchMode(Tab tab) {
    }

    @Override
    public boolean shouldShowErrorConsole() {
        return false;
    }

    @Override
    public void onUpdatedLockIcon(Tab tab) {
    }

    @Override
    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
    }

    @Override
    public void endActionMode() {
    }

    @Override
    public void attachSubWindow(Tab tab) {
    }

    @Override
    public void dismissSubWindow(Tab tab) {
    }

    @Override
    public Tab openTab(String url, boolean incognito, boolean setActive,
                                    boolean useCurrent) {
        return null;
    }

    @Override
    public Tab openTab(String url, Tab parent, boolean setActive,
                                    boolean useCurrent) {
        return null;
    }

    @Override
    public boolean switchToTab(Tab tab) {
        return false;
    }

    @Override
    public void closeTab(Tab tab) {
    }

    @Override
    public void setupAutoFill(Message message) {
    }

    @Override
    public void bookmarkedStatusHasChanged(Tab tab) {
    }

    @Override
    public void showAutoLogin(Tab tab) {
    }

    @Override
    public void hideAutoLogin(Tab tab) {
    }

}
