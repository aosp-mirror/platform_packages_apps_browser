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
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class BrowserHomepagePreference extends EditTextPreference {

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
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            String url = getEditText().getText().toString();
            if (url.length() > 0
                    && !BrowserActivity.ACCEPTED_URI_SCHEMA.matcher(url)
                            .matches()) {
                int colon = url.indexOf(':');
                int space = url.indexOf(' ');
                if (colon == -1 && space == -1) {
                    // if no colon, no space, add "http://" to make it a url
                    getEditText().setText("http://" + url);
                } else {
                    // show an error dialog and change the positiveResult to
                    // false so that the bad url will not override the old url
                    new AlertDialog.Builder(this.getContext()).setMessage(
                            R.string.bookmark_url_not_valid).setPositiveButton(
                            R.string.ok, null).show();
                    positiveResult = false;
                }
            }
        }
        super.onDialogClosed(positiveResult);
    }
}
