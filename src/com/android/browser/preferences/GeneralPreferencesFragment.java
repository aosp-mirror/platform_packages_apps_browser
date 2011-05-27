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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.android.browser.BrowserHomepagePreference;
import com.android.browser.BrowserPreferencesPage;
import com.android.browser.PreferenceKeys;
import com.android.browser.R;

public class GeneralPreferencesFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    static final String TAG = "PersonalPreferencesFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.general_preferences);

        Preference e = findPreference(PreferenceKeys.PREF_HOMEPAGE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getPreferenceScreen().getSharedPreferences()
                .getString(PreferenceKeys.PREF_HOMEPAGE, null));
        ((BrowserHomepagePreference) e).setCurrentPage(
                getActivity().getIntent().getStringExtra(BrowserPreferencesPage.CURRENT_PAGE));
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object objValue) {
        if (getActivity() == null) {
            // We aren't attached, so don't accept preferences changes from the
            // invisible UI.
            Log.w("PageContentPreferencesFragment", "onPreferenceChange called from detached fragment!");
            return false;
        }

        if (pref.getKey().equals(PreferenceKeys.PREF_HOMEPAGE)) {
            pref.setSummary((String) objValue);
            return true;
        }

        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshUi();
    }

    void refreshUi() {
        PreferenceScreen autoFillSettings =
                (PreferenceScreen)findPreference(PreferenceKeys.PREF_AUTOFILL_PROFILE);
        autoFillSettings.setDependency(PreferenceKeys.PREF_AUTOFILL_ENABLED);
    }
}
