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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Environment;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.webkit.HttpAuthHandler;
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
    private final static String sStatusFile = "test_status.txt";
    private final static File sExternalStorage = Environment.getExternalStorageDirectory();

    private final static int PERF_LOOPCOUNT = 10;
    private final static int STABILITY_LOOPCOUNT = 1;
    private final static int PAGE_LOAD_TIMEOUT = 120000; // 2 minutes

    private BrowserActivity mActivity = null;
    private Instrumentation mInst = null;
    private CountDownLatch mLatch = new CountDownLatch(1);
    private RunStatus mStatus;

    public PopularUrlsTest() {
        super(BrowserActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        mInst = getInstrumentation();
        mInst.waitForIdleSync();

        mStatus = RunStatus.load();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mStatus != null) {
            mStatus.cleanUp();
        }

        super.tearDown();
    }

    static BufferedReader getInputStream() throws FileNotFoundException {
        return getInputStream(sInputFile);
    }

    static BufferedReader getInputStream(String inputFile) throws FileNotFoundException {
        String path = sExternalStorage + File.separator + inputFile;
        FileReader fileReader = new FileReader(path);
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        return bufferedReader;
    }

    OutputStreamWriter getOutputStream() throws IOException {
        return getOutputStream(sOutputFile);
    }

    OutputStreamWriter getOutputStream(String outputFile) throws IOException {
        String path = sExternalStorage + File.separator + outputFile;

        File file = new File(path);

        return new FileWriter(file, mStatus.getIsRecovery());
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

            /**
             * Ignores http auth with dummy username and password
             */
            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                    String host, String realm) {
                handler.proceed("user", "passwd");
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
        boolean timedout = !mLatch.await(PAGE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
        if (timedout) {
            Log.w(TAG, "page timeout. trying to stop.");
            // try to stop page load
            mInst.runOnMainSync(new Runnable(){
                public void run() {
                    mActivity.getTabControl().getCurrentTab().getWebView().stopLoading();
                }
            });
            // try to wait for count down latch again
            timedout = !mLatch.await(5000, TimeUnit.MILLISECONDS);
            if (timedout) {
                Log.e(TAG, "failed to stop the timedout site in 5s");
            }
        }
    }

    private static class RunStatus {
        private File mFile;
        private int iteration;
        private int page;
        private String url;
        private boolean isRecovery;

        private RunStatus(String file) throws IOException {
            mFile = new File(file);
            FileReader input = null;
            BufferedReader reader = null;
            try {
                input = new FileReader(mFile);
                isRecovery = true;
                reader = new BufferedReader(input);
                iteration = Integer.parseInt(reader.readLine());
                page = Integer.parseInt(reader.readLine());
            } catch (FileNotFoundException ex) {
                isRecovery = false;
                iteration = 0;
                page = 0;
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } finally {
                    if (input != null) {
                        input.close();
                    }
                }
            }
        }

        public static RunStatus load() throws IOException {
            return load(sStatusFile);
        }

        public static RunStatus load(String file) throws IOException {
            return new RunStatus(sExternalStorage + File.separator + file);
        }

        public void write() throws IOException {
            FileWriter output = null;
            OutputStreamWriter writer = null;
            if (mFile.exists()) {
                mFile.delete();
            }
            try {
                output = new FileWriter(mFile);
                output.write(iteration + newLine);
                output.write(page + newLine);
                output.write(url + newLine);
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                } finally {
                    if (output != null) {
                        output.close();
                    }
                }
            }
        }

        public void cleanUp() {
            if (mFile.exists()) {
                mFile.delete();
            }
        }

        public void resetPage() {
            page = 0;
        }

        public void incrementPage() {
            ++page;
        }

        public void incrementIteration() {
            ++iteration;
        }

        public int getPage() {
            return page;
        }

        public int getIteration() {
            return iteration;
        }

        public boolean getIsRecovery() {
            return isRecovery;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * Loops over a list of URLs, points the browser to each one, and records the time elapsed.
     *
     * @param input the reader from which to get the URLs.
     * @param writer the writer to which to output the results.
     * @param clearCache determines whether the cache is cleared before loading each page
     * @param loopCount the number of times to loop through the list of pages
     * @throws IOException unable to read from input or write to writer.
     * @throws InterruptedException the thread was interrupted waiting for the page to load.
     */
    void loopUrls(BufferedReader input, OutputStreamWriter writer,
            boolean clearCache, int loopCount)
            throws IOException, InterruptedException {
        Tab tab = mActivity.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();

        List<String> pages = new LinkedList<String>();

        String page;
        while (null != (page = input.readLine())) {
            pages.add(page);
        }

        Iterator<String> iterator = pages.iterator();
        for (int i = 0; i < mStatus.getPage(); ++i) {
            iterator.next();
        }

        if (mStatus.getIsRecovery()) {
            Log.e(TAG, "Recovering after crash: " + iterator.next());
        }

        while (mStatus.getIteration() < loopCount) {
            while(iterator.hasNext()) {
                page = iterator.next();
                mStatus.setUrl(page);
                mStatus.write();
                Log.i(TAG, "start: " + page);
                Uri uri = Uri.parse(page);
                if (clearCache) {
                    webView.clearCache(true);
                }
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);

                long startTime = System.currentTimeMillis();
                mInst.runOnMainSync(new Runnable() {

                    public void run() {
                        mActivity.onNewIntent(intent);
                    }

                });
                waitForLoad();
                long stopTime = System.currentTimeMillis();

                String url = webView.getUrl();
                Log.i(TAG, "finish: " + url);

                if (writer != null) {
                    writer.write(page + "|" + (stopTime - startTime) + newLine);
                    writer.flush();
                }

                mStatus.incrementPage();
            }
            mStatus.incrementIteration();
            mStatus.resetPage();
            iterator = pages.iterator();
        }
    }

    public void testLoadPerformance() throws IOException, InterruptedException {
        setUpBrowser();

        OutputStreamWriter writer = getOutputStream();
        try {
            BufferedReader bufferedReader = getInputStream();
            try {
                loopUrls(bufferedReader, writer, true, PERF_LOOPCOUNT);
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

    public void testStability() throws IOException, InterruptedException {
        setUpBrowser();

        BufferedReader bufferedReader = getInputStream();
        try {
            loopUrls(bufferedReader, null, true, STABILITY_LOOPCOUNT);
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
    }
}
