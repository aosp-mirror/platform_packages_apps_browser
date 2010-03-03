/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

/* package */ class FindDialog extends Dialog implements TextWatcher {
    private WebView         mWebView;
    private TextView        mMatches;
    private BrowserActivity mBrowserActivity;
    
    // Views with which the user can interact.
    private EditText        mEditText;
    private View            mNextButton;
    private View            mPrevButton;
    private View            mMatchesView;

    private View.OnClickListener mFindListener = new View.OnClickListener() {
        public void onClick(View v) {
            findNext();
        }
    };

    private View.OnClickListener mFindCancelListener  = 
            new View.OnClickListener() {
        public void onClick(View v) {
            dismiss();
        }
    };
    
    private View.OnClickListener mFindPreviousListener  = 
            new View.OnClickListener() {
        public void onClick(View v) {
            if (mWebView == null) {
                throw new AssertionError("No WebView for FindDialog::onClick");
            }
            mWebView.findNext(false);
            hideSoftInput();
        }
    };

    /*
     * Remove the soft keyboard from the screen.
     */
    private void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager)
                mBrowserActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }

    private void disableButtons() {
        mPrevButton.setEnabled(false);
        mNextButton.setEnabled(false);
        mPrevButton.setFocusable(false);
        mNextButton.setFocusable(false);
    }

    /* package */ void setWebView(WebView webview) {
        mWebView = webview;
    }

    /* package */ FindDialog(BrowserActivity context) {
        super(context, R.style.FindDialogTheme);
        mBrowserActivity = context;
        setCanceledOnTouchOutside(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window theWindow = getWindow();
        theWindow.setGravity(Gravity.BOTTOM|Gravity.FILL_HORIZONTAL);

        setContentView(R.layout.browser_find);

        theWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mEditText = (EditText) findViewById(R.id.edit);
        
        View button = findViewById(R.id.next);
        button.setOnClickListener(mFindListener);
        mNextButton = button;
        
        button = findViewById(R.id.previous);
        button.setOnClickListener(mFindPreviousListener);
        mPrevButton = button;
        
        button = findViewById(R.id.done);
        button.setOnClickListener(mFindCancelListener);
        
        mMatches = (TextView) findViewById(R.id.matches);
        mMatchesView = findViewById(R.id.matches_view);
        disableButtons();
        theWindow.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
    
    public void dismiss() {
        super.dismiss();
        mBrowserActivity.closeFind();
        mWebView.notifyFindDialogDismissed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_UP
                && mEditText.hasFocus()) {
            findNext();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void findNext() {
        if (mWebView == null) {
            throw new AssertionError("No WebView for FindDialog::findNext");
        }
        mWebView.findNext(true);
        hideSoftInput();
    }

    public void show() {
        super.show();
        mEditText.requestFocus();
        mEditText.setText("");
        Spannable span = (Spannable) mEditText.getText();
        span.setSpan(this, 0, span.length(), 
                     Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        setMatchesFound(0);
        disableButtons();
    }
    
    // TextWatcher methods
    public void beforeTextChanged(CharSequence s, 
                                  int start, 
                                  int count, 
                                  int after) {
    }
    
    public void onTextChanged(CharSequence s,  
                              int start, 
                              int before, 
                              int count) {
        if (mWebView == null) {
            throw new AssertionError(
                    "No WebView for FindDialog::onTextChanged");
        }
        CharSequence find = mEditText.getText();
        if (0 == find.length()) {
            disableButtons();
            mWebView.clearMatches();
            mMatchesView.setVisibility(View.INVISIBLE);
        } else {
            mMatchesView.setVisibility(View.VISIBLE);
            mWebView.setFindDialogHeight(
                getWindow().getDecorView().getHeight());
            int found = mWebView.findAll(find.toString());
            setMatchesFound(found);
            if (found < 2) {
                disableButtons();
                if (found == 0) {
                    setMatchesFound(0);
                }
            } else {
                mPrevButton.setFocusable(true);
                mNextButton.setFocusable(true);
                mPrevButton.setEnabled(true);
                mNextButton.setEnabled(true);
            }
        }
    }

    private void setMatchesFound(int found) {
        String template = mBrowserActivity.getResources().
                getQuantityString(R.plurals.matches_found, found, found);

        mMatches.setText(template);
    }

    public void afterTextChanged(Editable s) {
    }
}
