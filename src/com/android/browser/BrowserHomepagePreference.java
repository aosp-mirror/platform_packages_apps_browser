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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.AttributeSet;

public class BrowserHomepagePreference extends EditTextPreference {
    private String mCurrentPage;
    private AlertDialog mSetHomepageTo;

    public BrowserHomepagePreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        createSetHomepageToDialog();
    }

    public BrowserHomepagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        createSetHomepageToDialog();
    }

    public BrowserHomepagePreference(Context context) {
        super(context);
        createSetHomepageToDialog();
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView,
            EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);
        // Now the EditText has a parent.  Add a button to set to the current
        // page.
        ViewGroup parent = (ViewGroup) editText.getParent();
        Button button = new Button(getContext());
        button.setText(R.string.pref_set_homepage_to);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mSetHomepageTo.show();
            }
        });
        if (parent instanceof LinearLayout) {
            ((LinearLayout) parent).setGravity(Gravity.CENTER_HORIZONTAL);
        }
        parent.addView(button, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void createSetHomepageToDialog() {
        Context context = getContext();
        CharSequence[] setToChoices = new CharSequence[] {
                context.getText(R.string.pref_use_current),
                context.getText(R.string.pref_use_blank),
                context.getText(R.string.pref_use_default),
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.pref_set_homepage_to);
        builder.setItems(setToChoices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    getEditText().setText(mCurrentPage);
                } else if (which == 1) {
                    getEditText().setText("about:blank");
                } else if (which == 2) {
                    getEditText().setText(BrowserSettings
                            .getFactoryResetHomeUrl(getContext()));
                }
            }
        });
        mSetHomepageTo = builder.create();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String url = getEditText().getText().toString();
            if (!BrowserActivity.ACCEPTED_URI_SCHEMA.matcher(url).matches()) {
                int colon = url.indexOf(':');
                int space = url.indexOf(' ');
                if (colon == -1 && space == -1 && url.length() > 0) {
                    // if no colon, no space, add "http://" to make it a url
                    getEditText().setText("http://" + url);
                } else {
                    // show an error toast and change the positiveResult to
                    // false so that the bad url will not override the old url
                    Toast.makeText(getContext(), R.string.bookmark_url_not_valid,
                            Toast.LENGTH_SHORT).show();
                    positiveResult = false;
                }
            }
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
