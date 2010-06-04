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

import android.content.Context;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.LinearLayout;

/* package */ class WebDialog extends LinearLayout {
    protected WebView         mWebView;
    protected BrowserActivity mBrowserActivity;
    private boolean           mIsVisible;

    /* package */ WebDialog(BrowserActivity context) {
        super(context);
        mBrowserActivity = context;
    }

    /* dialogs that have cancel buttons can optionally share code by including a
     * view with an id of 'done'.
     */
    protected void addCancel() {
        View button = findViewById(R.id.done);
        if (button != null) button.setOnClickListener(mCancelListener);
    }

    private View.OnClickListener mCancelListener = new View.OnClickListener() {
        public void onClick(View v) {
            mBrowserActivity.closeDialogs();
        }
    };

    protected void dismiss() {
        startAnimation(AnimationUtils.loadAnimation(mBrowserActivity,
                R.anim.dialog_exit));
        mIsVisible = false;
    }

    /*
     * Remove the soft keyboard from the screen.
     */
    protected void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager)
                mBrowserActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mWebView.getWindowToken(), 0);
    }

    protected boolean isVisible() {
        return mIsVisible;
    }

    /* package */ void setWebView(WebView webview) {
        mWebView = webview;
    }

    protected void show() {
        startAnimation(AnimationUtils.loadAnimation(mBrowserActivity,
            R.anim.dialog_enter));
        mIsVisible = true;
    }

}
