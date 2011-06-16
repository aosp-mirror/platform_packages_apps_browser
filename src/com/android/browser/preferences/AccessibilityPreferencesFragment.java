/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.browser.PreferenceKeys;
import com.android.browser.R;

import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.View;

public class AccessibilityPreferencesFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.accessibility_preferences);

        Preference e = findPreference(PreferenceKeys.PREF_TEXT_SIZE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualTextSizeName(
                getPreferenceScreen().getSharedPreferences()
                .getString(PreferenceKeys.PREF_TEXT_SIZE, null)) );
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object objValue) {
        if (getActivity() == null) {
            // We aren't attached, so don't accept preferences changes from the
            // invisible UI.
            return false;
        }

        if (pref.getKey().equals(PreferenceKeys.PREF_TEXT_SIZE)) {
            pref.setSummary(getVisualTextSizeName((String) objValue));
            return true;
        }
        return false;
    }

    private CharSequence getVisualTextSizeName(String enumName) {
        Resources res = getActivity().getResources();
        CharSequence[] visualNames = res.getTextArray(R.array.pref_text_size_choices);
        CharSequence[] enumNames = res.getTextArray(R.array.pref_text_size_values);

        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }

        int length = enumNames.length;
        for (int i = 0; i < length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }

        return "";
    }

}