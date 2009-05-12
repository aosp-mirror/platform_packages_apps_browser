/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.preference.ListPreference;
import android.content.Context;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebStorage;

import java.util.Vector;

/**
 * Utility class to display and manage the choosen quota
 * for an origin (HTML5 WebStorage feature)
 */
class BrowserQuotaPreference extends ListPreference {

    private String TAG = "BrowserQuotaPreference";
    private OriginSettings mOrigin = null;
    private static long sOneMB = 1024 * 1024;

    // This is the constructor called by the inflater
    public BrowserQuotaPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BrowserQuotaPreference(Context context, OriginSettings origin) {
        super(context);
        mOrigin = origin;
    }

    /**
     * Find the minimum quota fitting the current usage
     * and only show larger quotas in the list
     */
    public void setQuotaList () {
        CharSequence[] entries = getEntries();
        CharSequence[] values = getEntryValues();
        Vector<CharSequence> listEntries = new Vector<CharSequence>();
        Vector<CharSequence> listValues = new Vector<CharSequence>();
        long usage = 0;
        if (mOrigin != null) {
          usage = mOrigin.getUsage();
        }
        for (int i = 0; i < values.length; i++) {
            long value = Long.parseLong(values[i].toString());
            value *= sOneMB; // the string array is expressed in MB
            if (value >= usage) {
                listEntries.add(entries[i]);
                listValues.add(values[i]);
            }
        }
        CharSequence[] newEntries = new CharSequence[listEntries.size()];
        CharSequence[] newValues = new CharSequence[listValues.size()];
        for (int i = 0; i < listEntries.size(); i++) {
            newEntries[i] = listEntries.get(i);
            newValues[i] = listValues.get(i);
        }
        setEntries(newEntries);
        setEntryValues(newValues);
        setValueIndex(0);
    }

    @Override
    protected View onCreateDialogView() {
        setQuotaList();
        return super.onCreateDialogView();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (mOrigin == null) {
            return;
        }
        if (positiveResult) {
            long quota = Long.parseLong(getValue());
            quota *= sOneMB; // getValue() is in MB
            mOrigin.setQuota(quota);
        }
    }
}
