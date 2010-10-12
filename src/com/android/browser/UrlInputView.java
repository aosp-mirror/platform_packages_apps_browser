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

import com.android.browser.SuggestionsAdapter.CompletionListener;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

/**
 * url/search input view
 * handling suggestions
 */
public class UrlInputView extends AutoCompleteTextView
        implements OnFocusChangeListener, OnEditorActionListener, CompletionListener {

    private UrlInputListener   mListener;
    private InputMethodManager mInputManager;
    private SuggestionsAdapter mAdapter;
    private OnFocusChangeListener mWrappedFocusListener;
    private View mContainer;
    private boolean mLandscape;

    public UrlInputView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public UrlInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UrlInputView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mInputManager = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        setOnEditorActionListener(this);
        super.setOnFocusChangeListener(this);
        mAdapter = new SuggestionsAdapter(ctx, this);
        setAdapter(mAdapter);
        setSelectAllOnFocus(false);
        onConfigurationChanged(ctx.getResources().getConfiguration());
    }

    void setContainer(View container) {
        mContainer = container;
    }

    @Override
    protected void onConfigurationChanged(Configuration config) {
        mLandscape = (config.orientation &
                Configuration.ORIENTATION_LANDSCAPE) > 0;
        if (isPopupShowing() && (getVisibility() == View.VISIBLE)) {
            dismissDropDown();
            getFilter().filter(getText());
        }
    }

    @Override
    public void showDropDown() {
        int width = mContainer.getWidth();
        if (mLandscape && ((mAdapter.getLeftCount() == 0) ||
                (mAdapter.getRightCount() == 0))) {
            width = width / 2;
        }
        if (width != getDropDownWidth()) {
            setDropDownWidth(width);
        }
        if (getLeft() != -getDropDownHorizontalOffset()) {
            setDropDownHorizontalOffset(-getLeft());
        }
        mAdapter.setLandscapeMode(mLandscape);
        super.showDropDown();
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        // suppress selection action mode
        return null;
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener focusListener) {
        mWrappedFocusListener = focusListener;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        finishInput(getText().toString());
        return true;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            forceIme();
        } else {
            finishInput(null);
        }
        if (mWrappedFocusListener != null) {
            mWrappedFocusListener.onFocusChange(v, hasFocus);
        }
    }

    public void setUrlInputListener(UrlInputListener listener) {
        mListener = listener;
    }

    public void forceIme() {
        mInputManager.showSoftInput(this, 0);
    }

    private void finishInput(String url) {
        this.dismissDropDown();
        this.setSelection(0,0);
        mInputManager.hideSoftInputFromWindow(getWindowToken(), 0);
        if (url == null) {
            mListener.onDismiss();
        } else {
            mListener.onAction(url);
        }
    }

    // Completion Listener

    @Override
    public void onSearch(String search) {
        mListener.onEdit(search);
    }

    @Override
    public void onSelect(String url) {
        finishInput(url);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent evt) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // catch back key in order to do slightly more cleanup than usual
            finishInput(null);
            return true;
        }
        return super.onKeyPreIme(keyCode, evt);
    }

    interface UrlInputListener {

        public void onDismiss();

        public void onAction(String text);

        public void onEdit(String text);

    }

}
