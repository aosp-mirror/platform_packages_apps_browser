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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.CountDownLatch;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Environment;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;

/**
 *
 * Iterates over a list of URLs from a file and outputs the time to load each.
 */
public class PopularUrlsTest extends ActivityInstrumentationTestCase2<BrowserActivity> {

    private final static String TAG = "PopularUrlsTest";
    private final static String newLine = System.getProperty("line.separator");
    private final static String sInputFile = "popular_urls.txt";
    private final static String sOutputFile = "test_output.txt";
    private final static File sExternalStorage = Environment.getExternalStorageDirectory();
    private BrowserActivity mActivity = null;
    private Instrumentation mInst = null;
    private CountDownLatch mLatch = new CountDownLatch(1);

    public PopularUrlsTest() {
        super("com.android.browser", BrowserActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        mInst = getInstrumentation();
        mInst.waitForIdleSync();
    }

    static BufferedReader getInputStream() throws FileNotFoundException {
        String path = sExternalStorage + File.separator + sInputFile;
        FileReader fileReader = new FileReader(path);
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        return bufferedReader;
    }

    static OutputStreamWriter getOutputStream() throws IOException {
        String path = sExternalStorage + File.separator + sOutputFile;

        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }

        return new FileWriter(file);
    }

    /**
     * Gets the browser ready for testing by starting the application
     * and wrapping the WebView's helper clients.
     */
    void setUpBrowser() {
        Tab tab = mActivity.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();

        webView.setWebChromeClient(new TestWebChromeClient(webView.getWebChromeClient()) {

            /**
             * Reset the latch whenever page progress reaches 100%.
             */
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress >= 100) {
                    resetLatch();
                }
            }

            /**
             * Dismisses and logs Javascript alerts.
             */
            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                    JsResult result) {
                String logMsg = String.format("JS Alert '%s' received from %s", message, url);
                Log.w(TAG, logMsg);
                result.confirm();

                return true;
            }

            /**
             * Confirms and logs Javascript alerts.
             */
            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                    JsResult result) {
                String logMsg = String.format("JS Confirmation '%s' received from %s",
                        message, url);
                Log.w(TAG, logMsg);
                result.confirm();

                return true;
            }

            /**
             * Confirms and logs Javascript alerts, providing the default value.
             */
            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                    String defaultValue, JsPromptResult result) {
                String logMsg = String.format("JS Prompt '%s' received from %s; " +
                        "Giving default value '%s'", message, url, defaultValue);
                Log.w(TAG, logMsg);
                result.confirm(defaultValue);

                return true;
            }
        });

        webView.setWebViewClient(new TestWebViewClient(webView.getWebViewClient()) {

            /**
             * Bypasses and logs errors.
             */
            @Override
            public void onReceivedError(WebView view, int errorCode,
                    String description, String failingUrl) {
                String message = String.format("Error '%s' (%d) loading url: %s",
                        description, errorCode, failingUrl);
                Log.w(TAG, message);
            }

            /**
             * Ignores and logs SSL errors.
             */
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                    SslError error) {
                Log.w(TAG, "SSL error: " + error);
                handler.proceed();
            }

        });
    }

    void resetLatch() {
        CountDownLatch temp = mLatch;
        mLatch = new CountDownLatch(1);
        if (temp != null) {
            // Notify existing latch that it's done.
            while (temp.getCount() > 0) {
                temp.countDown();
            }
        }
    }

    void waitForLoad() throws InterruptedException {
        mLatch.await();
    }

    /**
     * Loops over a list of URLs, points the browser to each one, and records the time elapsed.
     *
     * @param input the reader from which to get the URLs.
     * @param writer the writer to which to output the results.
     * @throws IOException unable to read from input or write to writer.
     * @throws InterruptedException the thread was interrupted waiting for the page to load.
     */
    void loopUrls(BufferedReader input, OutputStreamWriter writer)
            throws IOException, InterruptedException {
        Tab tab = mActivity.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();
        String page;

        while (null != (page = input.readLine())) {
            Uri uri = Uri.parse(page);
            webView.clearCache(true);
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            writer.write(uri.toString());

            long startTime = System.nanoTime();
            mInst.runOnMainSync(new Runnable() {

                public void run() {
                    mActivity.onNewIntent(intent);
                }

            });
            waitForLoad();
            long stopTime = System.nanoTime();

            String url = webView.getUrl();
            Log.i(TAG, "Loaded url: " + url);
            writer.write("|" + (stopTime - startTime) + newLine);
        }
    }

    public void testLoadPerformance() throws IOException, InterruptedException {
        setUpBrowser();

        OutputStreamWriter writer = getOutputStream();
        try {
            BufferedReader bufferedReader = getInputStream();
            try {
                loopUrls(bufferedReader, writer);
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}

