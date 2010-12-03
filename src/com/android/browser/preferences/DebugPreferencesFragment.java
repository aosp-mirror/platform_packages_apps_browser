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
import com.android.browser.Controller;
import com.android.browser.R;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager.OnActivityResultListener;

import java.io.IOException;
import java.io.Serializable;

public class DebugPreferencesFragment extends PreferenceFragment
        implements OnPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.debug_preferences);

        if (BrowserSettings.getInstance().showDebugSettings()) {
            addPreferencesFromResource(R.xml.hidden_debug_preferences);
        }

        Preference e = findPreference(BrowserSettings.PREF_HARDWARE_ACCEL);
        e.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Attempt to restart
        startActivity(new Intent(BrowserActivity.ACTION_RESTART, null,
                getActivity(), BrowserActivity.class));
        return true;
    }
}
