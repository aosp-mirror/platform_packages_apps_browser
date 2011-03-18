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
 * limitations under the License
 */

package com.android.browser.preferences;

import com.android.browser.BrowserActivity;
import com.android.browser.BrowserSettings;
import com.android.browser.R;
import com.android.browser.search.SearchEngine;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

public class LabPreferencesFragment extends PreferenceFragment
        implements OnPreferenceChangeListener {
    private BrowserSettings mBrowserSettings;
    private Preference useInstantPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBrowserSettings = BrowserSettings.getInstance();

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.lab_preferences);

        Preference e = findPreference(BrowserSettings.PREF_QUICK_CONTROLS);
        if (e != null) {
            e.setOnPreferenceChangeListener(this);
        }
        useInstantPref = findPreference(BrowserSettings.PREF_USE_INSTANT);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (useInstantPref != null) {
            useInstantPref.setEnabled(false);

            // Enable the "use instant" preference only if the selected
            // search engine is google.
            if (mBrowserSettings.getSearchEngine() != null) {
                final String currentName = mBrowserSettings.getSearchEngine().getName();
                if (SearchEngine.GOOGLE.equals(currentName)) {
                    useInstantPref.setEnabled(true);
                }
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Attempt to restart
        startActivity(new Intent(BrowserActivity.ACTION_RESTART, null,
                getActivity(), BrowserActivity.class));
        return true;
    }
}
