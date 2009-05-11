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

import com.android.internal.preference.YesNoPreference;

import android.content.Context;
import android.util.AttributeSet;

class BrowserYesNoPreference extends YesNoPreference {

    // This is used for the HTML5 pref UI, where we construct
    // BrowserYesNoPreference objects on the fly and where we need
    // to save the corresponding origin.
    OriginSettings mOrigin = null;

    // This is the constructor called by the inflater
    public BrowserYesNoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BrowserYesNoPreference(Context context, OriginSettings origin) {
        super(context);
        mOrigin = origin;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            setEnabled(false);

            Context context = getContext();
            if (BrowserSettings.PREF_CLEAR_CACHE.equals(getKey())) {
                BrowserSettings.getInstance().clearCache(context);
            } else if (BrowserSettings.PREF_CLEAR_COOKIES.equals(getKey())) {
                BrowserSettings.getInstance().clearCookies(context);
            } else if (BrowserSettings.PREF_CLEAR_HISTORY.equals(getKey())) {
                BrowserSettings.getInstance().clearHistory(context);
            } else if (BrowserSettings.PREF_CLEAR_FORM_DATA.equals(getKey())) {
                BrowserSettings.getInstance().clearFormData(context);
            } else if (BrowserSettings.PREF_CLEAR_PASSWORDS.equals(getKey())) {
                BrowserSettings.getInstance().clearPasswords(context);
            } else if (BrowserSettings.PREF_CLEAR_DATABASES.equals(getKey())) {
                BrowserSettings.getInstance().clearDatabases(context);
            } else if (BrowserSettings.PREF_CLEAR_ALL_DATA.equals(getKey())) {
                if (mOrigin != null) {
                    mOrigin.delete();
                }
            } else if (BrowserSettings.PREF_EXTRAS_RESET_DEFAULTS.equals(
                    getKey())) {
                BrowserSettings.getInstance().resetDefaultPreferences(context);
                setEnabled(true);
            }
        }
    }
}
