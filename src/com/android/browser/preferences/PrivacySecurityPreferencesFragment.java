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

import com.android.browser.BrowserSettings;
import com.android.browser.GoogleAccountLogin;
import com.android.browser.R;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class PrivacySecurityPreferencesFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private BrowserSettings mSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettings = BrowserSettings.getInstance();

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.privacy_security_preferences);

        Preference e = findPreference(BrowserSettings.PREF_CLEAR_HISTORY);
        e.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        setupAutoLoginPreference();
    }

    void setupAutoLoginPreference() {
        ListPreference autologinPref = (ListPreference) findPreference(
                BrowserSettings.PREF_AUTOLOGIN_ACCOUNT);
        autologinPref.setOnPreferenceChangeListener(this);
        updateAutoLoginSummary(autologinPref);
        Account[] accounts = GoogleAccountLogin.getAccounts(getActivity());
        // +1 for disable
        CharSequence[] names = new CharSequence[accounts.length + 1];
        CharSequence[] values = new CharSequence[names.length];
        int i = 0;
        int defaultAccount = 0;
        for (Account a : accounts) {
            values[i] = names[i] = a.name;
            i++;
        }
        names[i] = getResources().getString(R.string.pref_autologin_disable);
        values[i] = "";
        autologinPref.setEntries(names);
        autologinPref.setEntryValues(values);
        autologinPref.setValue(BrowserSettings.getInstance()
                .getAutoLoginAccount(getActivity()));
    }

    private void updateAutoLoginSummary(Preference pref) {
        if (!mSettings.isAutoLoginEnabled()) {
            pref.setSummary(R.string.pref_autologin_disable);
        } else {
            String account = mSettings.getAutoLoginAccount(getActivity());
            if (account == null) {
                pref.setSummary(R.string.pref_autologin_no_account);
            } else {
                pref.setSummary(getString(R.string.pref_autologin_summary, account));
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object objValue) {
        if (pref.getKey().equals(BrowserSettings.PREF_CLEAR_HISTORY)
                && ((Boolean) objValue).booleanValue() == true) {
            // Need to tell the browser to remove the parent/child relationship
            // between tabs
            getActivity().setResult(Activity.RESULT_OK, (new Intent()).putExtra(Intent.EXTRA_TEXT,
                    pref.getKey()));
            return true;
        } else if (pref.getKey().equals(BrowserSettings.PREF_AUTOLOGIN_ACCOUNT)) {
            String account = (String) objValue;
            if (account.length() == 0) {
                // Disable
                mSettings.setAutoLoginEnabled(getActivity(), false);
            } else {
                mSettings.setAutoLoginEnabled(getActivity(), true);
            }
            mSettings.setAutoLoginAccount(getActivity(), account);
            updateAutoLoginSummary(pref);
            return true;
        }

        return false;
    }

}
