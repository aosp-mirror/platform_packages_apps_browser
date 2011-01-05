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

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

public class BrowserHomepagePreference extends EditTextPreference {
    private String mCurrentPage;

    public BrowserHomepagePreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public BrowserHomepagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BrowserHomepagePreference(Context context) {
        super(context);
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView,
            EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);
        editText.setSelectAllOnFocus(true);
        // Now the EditText has a parent.  Add a button to set to the current
        // page.
        createButtons((ViewGroup) editText.getParent());
    }

    void createButtons(ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.pref_homepage_buttons, parent);
        v.findViewById(R.id.use_current).setOnClickListener(mOnClick);
        v.findViewById(R.id.use_default).setOnClickListener(mOnClick);
    }

    OnClickListener mOnClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.use_current:
                getEditText().setText(mCurrentPage);
                break;
            case R.id.use_default:
                getEditText().setText(
                        BrowserSettings.getFactoryResetHomeUrl(getContext()));
                break;
            }
        }
    };

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String url = getEditText().getText().toString().trim();
            if (url.length() > 0) {
                url = UrlUtils.smartUrlFilter(url);
            }
            getEditText().setText(url);
        }
        super.onDialogClosed(positiveResult);
    }

    /**
     * Set the current page of the browser.
     * @param currentPage This String will replace the text in the EditText
     *          when the user clicks the "Use current page" button.
     */
    public void setCurrentPage(String currentPage) {
        mCurrentPage = currentPage;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        // The dialog has its width set to wrap_content.  Change it to
        // match_parent so there is more room to type in a url.
        Window window = getDialog().getWindow();
        View decorView = window.getDecorView();
        WindowManager.LayoutParams params
                = (WindowManager.LayoutParams) decorView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        window.getWindowManager().updateViewLayout(decorView, params);
    }
}
