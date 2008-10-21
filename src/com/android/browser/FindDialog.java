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

import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/* package */ class FindDialog extends LinearLayout implements TextWatcher {
    private WebView         mWebView;
    private TextView        mMatches;
    private BrowserActivity mBrowserActivity;
    
    // Views with which the user can interact.
    private View            mOk;
    private EditText        mEditText;
    private View            mNextButton;
    private View            mPrevButton;

    // Tags for messages to be sent to the handler.
    private final static int FIND_RESPONSE  = 0;
    private final static int NUM_FOUND      = 1;

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
            // Find is disabled for version 1.0, so find methods on WebView are
            // currently private.
            //mWebView.findPrevious(mEditText.getText().toString(),
            //        mFindHandler.obtainMessage(FIND_RESPONSE));
        }
    };
    
    private Handler mFindHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (NUM_FOUND == msg.what) {
                mMatches.setText(Integer.toString(msg.arg1));
                if (0 == msg.arg1) {
                    disableButtons();
                } else {
                    mPrevButton.setFocusable(true);
                    mNextButton.setFocusable(true);
                    mPrevButton.setEnabled(true);
                    mNextButton.setEnabled(true);
                }
            }
        }
    };
    
    private void disableButtons() {
        mPrevButton.setEnabled(false);
        mNextButton.setEnabled(false);
        mPrevButton.setFocusable(false);
        mNextButton.setFocusable(false);
    }

    public void setWebView(WebView webview) {
        mWebView = webview;
    }

    /* package */ FindDialog(BrowserActivity context) {
        super(context);
        mBrowserActivity = context;
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.browser_find, this);
        
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        
        mEditText = (EditText) findViewById(R.id.edit);
        
        View button = findViewById(R.id.next);
        button.setOnClickListener(mFindListener);
        mNextButton = button;
        
        button = findViewById(R.id.previous);
        button.setOnClickListener(mFindPreviousListener);
        mPrevButton = button;
        
        button = findViewById(R.id.done);
        button.setOnClickListener(mFindCancelListener);
        mOk = button;
        
        mMatches = (TextView) findViewById(R.id.matches);
        disableButtons();
    }
    
    public void dismiss() {
        mBrowserActivity.closeFind();
        // If the nav buttons are highlighted, then there are matches
        // highlighted in the WebView, and they should be cleared.
        if (mPrevButton.isEnabled()) {
            // Find is disabled for version 1.0, so find methods on WebView are
            // currently private.
            //mWebView.clearMatches();
        }
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Make up and down find previous/next
        int code = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                if (up) {
                    dismiss();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.getMetaState() != 0) {
                    break;
                }
                if (up) {
                    mFindPreviousListener.onClick(null);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getMetaState() != 0) {
                    break;
                }
                if (up) {
                    mFindListener.onClick(null);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!mEditText.hasFocus()) {
                    break;
                }
                if (up) {
                    findNext();
                }
                return true;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        // Return true so that BrowserActivity thinks we handled it and does
        // not dismiss us.
        return true;
    }

    private void findNext() {
        if (mWebView == null) {
            throw new AssertionError("No WebView for FindDialog::findNext");
        }
        // Find is disabled for version 1.0, so find methods on WebView are
        // currently private.
        //mWebView.findNext(mEditText.getText().toString(),
        //        mFindHandler.obtainMessage(FIND_RESPONSE));
    }
    
    public void show() {
        mEditText.requestFocus();
        mEditText.setText("");
        Spannable span = (Spannable) mEditText.getText();
        span.setSpan(this, 0, span.length(), 
                     Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        mMatches.setText(R.string.zero);
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
        CharSequence find = mEditText.getText();
        if (0 == find.length()) {
            disableButtons();
            // Find is disabled for version 1.0, so find methods on WebView are
            // currently private.
            //mWebView.clearMatches();
            mMatches.setText(R.string.zero);
        } else {
            if (mWebView == null) {
                throw new AssertionError(
                        "No WebView for FindDialog::onTextChanged");
            }
            // Find is disabled for version 1.0, so find methods on WebView are
            // currently private.
            //mWebView.findAll(find.toString(),
            //        mFindHandler.obtainMessage(NUM_FOUND));
        }
    }

    public void afterTextChanged(Editable s) {
    }
}
