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

import android.provider.Browser;
import android.view.LayoutInflater;
import android.view.View;

/* package */ class SelectDialog extends WebDialog {
    private View mCopyButton;
    private View mSelectAllButton;
    private View mShareButton;
    private View mFindButton;

    SelectDialog(BrowserActivity context) {
        super(context);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.browser_select, this);
        addCancel();

        mCopyButton = findViewById(R.id.copy);
        mCopyButton.setOnClickListener(mCopyListener);
        mSelectAllButton = findViewById(R.id.select_all);
        mSelectAllButton.setOnClickListener(mSelectAllListener);
        mShareButton = findViewById(R.id.share);
        mShareButton.setOnClickListener(mShareListener);
        mFindButton = findViewById(R.id.find);
        mFindButton.setOnClickListener(mFindListener);
    }

    private View.OnClickListener mCopyListener = new View.OnClickListener() {
        public void onClick(View v) {
            mWebView.copySelection();
            mBrowserActivity.closeDialogs();
        }
    };

    private View.OnClickListener mSelectAllListener = new View.OnClickListener() {
        public void onClick(View v) {
            mWebView.selectAll();
        }
    };

    private View.OnClickListener mShareListener = new View.OnClickListener() {
        public void onClick(View v) {
            String selection = mWebView.getSelection();
            Browser.sendString(mBrowserActivity, selection);
            mBrowserActivity.closeDialogs();
        }
    };

    private View.OnClickListener mFindListener = new View.OnClickListener() {
        public void onClick(View v) {
            String selection = mWebView.getSelection();
            mBrowserActivity.closeDialogs();
            mBrowserActivity.showFindDialog();
            mBrowserActivity.setFindDialogText(selection);
        }
    };

    /**
     * Called by BrowserActivity.closeDialog.  Start the animation to hide
     * the dialog, and inform the WebView that the dialog is being dismissed.
     */
    @Override
    public void dismiss() {
        super.dismiss();
        mWebView.notifySelectDialogDismissed();
    }

}
