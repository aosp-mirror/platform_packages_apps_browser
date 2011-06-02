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

import android.app.Instrumentation;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.webkit.ClientCertRequestHandler;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Adds a JavaScript interface to the webview and calls functions on it to verify variables
 * are passed from JS to Java correctly.
 * To run this test, execute:
 * adb shell am instrument -w -e class com.android.browser.JNIBindingsTestApp#testJNIBindings \
 *     com.android.browser.tests/android.test.InstrumentationTestRunner
 */
public class JNIBindingsTestApp extends ActivityInstrumentationTestCase2<BrowserActivity> {

    private final static String TAG = "JNIBindingsTest";

    private static final String SDCARD_BINDINGS_TEST_HTML = "/sdcard/bindings_test.html";

    private static final int MSG_WEBKIT_DATA_READY = 101;

    private BrowserActivity mActivity = null;
    private Controller mController = null;
    private Instrumentation mInst = null;

    private boolean mTestDone = false;
    private String mWebKitResult;

    private String mExpectedWebKitResult = "Running JNI Bindings test...\n" +
            "testPrimitiveTypes passed!\n" +
            "testObjectTypes passed!\n" +
            "testArray passed!\n" +
            "testObjectArray passed!\n" +
            "testObjectMembers passed!\n" +
            "testJSPrimitivesToStringsInJava passed!\n" +
            "testJavaReturnTypes passed!\n" +
            "getIfaceProperties passed!\n" +
            "testParameterTypeMismatch passed!\n";


    private class GetWebKitDataThread extends Thread {
        private JNIBindingsTestApp mTestApp;
        private WebView mWebView;
        private Handler mHandler;

        GetWebKitDataThread(JNIBindingsTestApp testApp, WebView webView) {
            mTestApp = testApp;
            mWebView = webView;
        }

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_WEBKIT_DATA_READY: {
                            mTestApp.setWebKitResult((String)msg.obj);
                            Looper.myLooper().quit();
                        }
                        default: super.handleMessage(msg); break;
                    }
                }
            };
            mWebView.documentAsText(mHandler.obtainMessage(MSG_WEBKIT_DATA_READY, 1, 0));
            Looper.loop();
        }
    }

    public synchronized void setWebKitResult(String result) {
       mWebKitResult = result;
       notify();
    }

    public JNIBindingsTestApp() {
        super(BrowserActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        mController = mActivity.getController();
        mInst = getInstrumentation();
        mInst.waitForIdleSync();

        extractAsset();
    }

    @Override
    protected void tearDown() throws Exception {
        removeAsset();
        super.tearDown();
    }

    protected void extractAsset() throws IOException {
        InputStream in = getInstrumentation().getContext().getAssets().open("bindings_test.html");
        OutputStream out = new FileOutputStream(SDCARD_BINDINGS_TEST_HTML);

        byte[] buf = new byte[2048];
        int len;

        while ((len = in.read(buf)) >= 0 ) {
            out.write(buf, 0, len);
        }
        out.close();
        in.close();
    }

    protected void removeAsset(){
        File fileToDelete = new File(SDCARD_BINDINGS_TEST_HTML);
        fileToDelete.delete();
    }

    /**
     * Gets the browser ready for testing by starting the application
     * and wrapping the WebView's helper clients.
     */
    void setUpBrowser() {
        Tab tab = mController.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();
        webView.addJavascriptInterface(new JNIBindingsTest(this), "JNIBindingsTest");

        webView.setWebChromeClient(new TestWebChromeClient(webView.getWebChromeClient()) {

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
             * Ignores and logs SSL client certificate requests.
             */
            @Override
            public void onReceivedClientCertRequest(WebView view, ClientCertRequestHandler handler,
                    String host_and_port) {
                Log.w(TAG, "SSL client certificate request: " + host_and_port);
                handler.cancel();
            }

        });
    }

    public synchronized void notifyComplete() {
        mTestDone = true;
        notify();
    }

    public void testJNIBindings() {
        setUpBrowser();

        Tab tab = mController.getTabControl().getCurrentTab();
        WebView webView = tab.getWebView();
        webView.loadUrl("file://" + SDCARD_BINDINGS_TEST_HTML);
        synchronized(this) {
            while(!mTestDone) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            }
        }

        // Now the tests are complete grab the DOM content and compare to the reference.
        GetWebKitDataThread getWKData = new GetWebKitDataThread(this, webView);
        mWebKitResult = null;
        getWKData.start();

        synchronized(this) {
            while(mWebKitResult == null) {
                try {
                    wait();
                } catch (InterruptedException e) {}
            }
        }

        Log.v(TAG, "WebKit result:");
        Log.v(TAG, mWebKitResult);
        assertEquals("Bindings test failed! See logcat for more details!", mExpectedWebKitResult,
                mWebKitResult);
    }
}
