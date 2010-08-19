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

import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/* package */ class FindDialog extends WebDialog implements TextWatcher {
    private TextView        mMatches;
    
    // Views with which the user can interact.
    private EditText        mEditText;
    private View            mNextButton;
    private View            mPrevButton;
    private View            mMatchesView;

    // When the dialog is opened up with old text, enter needs to be pressed
    // (or the text needs to be changed) before WebView.findAll can be called.
    // Once it has been called, enter should move to the next match.
    private boolean         mMatchesFound;
    private int             mNumberOfMatches;

    private View.OnClickListener mFindListener = new View.OnClickListener() {
        public void onClick(View v) {
            findNext();
        }
    };

    private View.OnClickListener mFindPreviousListener  =
            new View.OnClickListener() {
        public void onClick(View v) {
            if (mWebView == null) {
                throw new AssertionError("No WebView for FindDialog::onClick");
            }
            mWebView.findNext(false);
            updateMatchesString();
            hideSoftInput();
        }
    };

    private void disableButtons() {
        mPrevButton.setEnabled(false);
        mNextButton.setEnabled(false);
        mPrevButton.setFocusable(false);
        mNextButton.setFocusable(false);
    }

    /* package */ FindDialog(BrowserActivity context) {
        super(context);

        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.browser_find, this);

        addCancel();
        mEditText = (EditText) findViewById(R.id.edit);
        
        View button = findViewById(R.id.next);
        button.setOnClickListener(mFindListener);
        mNextButton = button;
        
        button = findViewById(R.id.previous);
        button.setOnClickListener(mFindPreviousListener);
        mPrevButton = button;
        
        mMatches = (TextView) findViewById(R.id.matches);
        mMatchesView = findViewById(R.id.matches_view);
        disableButtons();

    }

    /**
     * Called by BrowserActivity.closeDialog.  Start the animation to hide
     * the dialog, inform the WebView that the dialog is being dismissed,
     * and hide the soft keyboard.
     */
    public void dismiss() {
        super.dismiss();
        mWebView.notifyFindDialogDismissed();
        hideSoftInput();
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            KeyEvent.DispatcherState state = getKeyDispatcherState();
            if (state != null) {
                int action = event.getAction();
                if (KeyEvent.ACTION_DOWN == action
                        && event.getRepeatCount() == 0) {
                    state.startTracking(event, this);
                    return true;
                } else if (KeyEvent.ACTION_UP == action
                        && !event.isCanceled() && state.isTracking(event)) {
                    mBrowserActivity.closeDialogs();
                    return true;
                }
            }
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    && mEditText.hasFocus()) {
                if (mMatchesFound) {
                    findNext();
                } else {
                    findAll();
                    // Set the selection to the end.
                    Spannable span = (Spannable) mEditText.getText();
                    Selection.setSelection(span, span.length());
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void findNext() {
        if (mWebView == null) {
            throw new AssertionError("No WebView for FindDialog::findNext");
        }
        mWebView.findNext(true);
        updateMatchesString();
        hideSoftInput();
    }

    public void show() {
        super.show();
        // In case the matches view is showing from a previous search
        mMatchesView.setVisibility(View.INVISIBLE);
        mMatchesFound = false;
        // This text is only here to ensure that mMatches has a height.
        mMatches.setText("0");
        mEditText.requestFocus();
        Spannable span = (Spannable) mEditText.getText();
        int length = span.length();
        Selection.setSelection(span, 0, length);
        span.setSpan(this, 0, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        disableButtons();
        InputMethodManager imm = (InputMethodManager)
                mBrowserActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mEditText, 0);
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
        findAll();
    }

    private void findAll() {
        if (mWebView == null) {
            throw new AssertionError(
                    "No WebView for FindDialog::findAll");
        }
        CharSequence find = mEditText.getText();
        if (0 == find.length()) {
            disableButtons();
            mWebView.clearMatches();
            mMatchesView.setVisibility(View.INVISIBLE);
        } else {
            mMatchesView.setVisibility(View.VISIBLE);
            int found = mWebView.findAll(find.toString());
            mMatchesFound = true;
            setMatchesFound(found);
            if (found < 2) {
                disableButtons();
                if (found == 0) {
                    // Cannot use getQuantityString, which ignores the "zero"
                    // quantity.
                    // FIXME: is this fix is beyond the scope
                    // of adding touch selection to gingerbread?
                    // mMatches.setText(mBrowserActivity.getResources().getString(
                    //        R.string.no_matches));
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
        mNumberOfMatches = found;
        updateMatchesString();
    }

    public void setText(String text) {
        mEditText.setText(text);
        findAll();
    }

    private void updateMatchesString() {
        // Note: updateMatchesString is only called by methods that have already
        // checked mWebView for null.
        String template = mBrowserActivity.getResources().
                getQuantityString(R.plurals.matches_found, mNumberOfMatches,
                mWebView.findIndex() + 1, mNumberOfMatches);

        mMatches.setText(template);
    }

    public void afterTextChanged(Editable s) {
    }
}
