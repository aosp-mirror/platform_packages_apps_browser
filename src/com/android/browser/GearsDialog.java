/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;

import android.widget.LinearLayout;

public class GearsDialog extends Activity {

  private static final String TAG = "GearsDialog";

  private WebView webview;

  private String htmlContent;
  private String dialogArguments;

  private boolean dismissed = false;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    webview = new WebView(this);
    webview.getSettings().setJavaScriptEnabled(true);
    webview.addJavascriptInterface(this, "bridge");

    setContentView(webview);
    setTitle("Gears");
  }

  @Override
  public void onStart() {
    super.onStart();
    loadHTML();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    // In case we reach this point without
    // notifying GearsDialogService, we do it now.
    if (!dismissed) {
      notifyEndOfDialog();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Intent i = getIntent();
    boolean inSettings = i.getBooleanExtra("inSettings", false);
    // If we are called from the settings, we
    // dismiss ourselve upon rotation
    if (inSettings) {
      notifyEndOfDialog();
      finish();
    }
  }

  /**
   * Load the HTML content in the WebView
   */
  private void loadHTML() {
    Intent i = getIntent();
    htmlContent = i.getStringExtra("htmlContent");
    dialogArguments = i.getStringExtra("dialogArguments");
    webview.loadDataWithBaseURL("", htmlContent, "text/html", "", "");
  }

  /**
   * Signal to GearsDialogService that we are done.
   */
  private void notifyEndOfDialog() {
    GearsDialogService.signalFinishedDialog();
    dismissed = true;
  }

  /**
   * Intercepts the back key to immediately notify
   * GearsDialogService that we are done.
   */
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() ==  KeyEvent.KEYCODE_BACK && event.isDown()) {
      notifyEndOfDialog();
    }
    return super.dispatchKeyEvent(event);
  }

  /**
   * Returns a json-formatted string containing the information
   * about the site.
   * This method is accessible through the javascript bridge.
   */
  public String getDialogArguments() {
    return dialogArguments;
  }

  /**
   * Set the results string and closes the dialog.
   * This method is accessible through the javascript bridge.
   */
  public void closeDialog(String results) {
    GearsDialogService.closeDialog(results);
    notifyEndOfDialog();
    finish();
  }

  /**
   * Debug method outputting a message.
   * This method is accessible through the javascript bridge.
   */
  public void log(String msg) {
    Log.v(TAG, msg);
  }
}
