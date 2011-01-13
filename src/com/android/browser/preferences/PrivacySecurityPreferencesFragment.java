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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
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
        e = findPreference(BrowserSettings.PREF_AUTOLOGIN);
        e.setOnPreferenceChangeListener(this);
        updateAutoLoginSummary((CheckBoxPreference) e);
    }

    private void updateAutoLoginSummary(CheckBoxPreference pref) {
        String account = mSettings.getAutoLoginAccount(getActivity());
        if (account == null) {
            pref.setChecked(false);
            pref.setEnabled(false);
            pref.setSummary(R.string.pref_autologin_no_account);
        } else {
            pref.setSummary(getString(R.string.pref_autologin_summary, account));
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
        } else if (pref.getKey().equals(BrowserSettings.PREF_AUTOLOGIN)) {
            boolean val = ((Boolean) objValue).booleanValue();
            if (val) {
                selectAccount((CheckBoxPreference) pref);
                return false;
            }
            return true;
        }

        return false;
    }

    class AccountCallback implements OnClickListener {
        private final Account[] mAccounts;
        private final CheckBoxPreference mPref;

        public AccountCallback(Account[] accounts, CheckBoxPreference pref) {
            mAccounts = accounts;
            mPref = pref;
        }

        public void onClick(DialogInterface d, int which) {
            saveAutoLoginAccount(mPref, mAccounts[which].name);
            d.dismiss();
        }
    }

    private void saveAutoLoginAccount(CheckBoxPreference pref, String name) {
        mSettings.setAutoLoginAccount(getActivity(), name);
        pref.setChecked(true);
        updateAutoLoginSummary(pref);
    }

    private void selectAccount(CheckBoxPreference pref) {
        Account[] accounts = GoogleAccountLogin.getAccounts(getActivity());
        if (accounts.length == 0) {
            mSettings.setAutoLoginAccount(getActivity(), null);
            updateAutoLoginSummary(pref);
            return;
        } else if (accounts.length == 1) {
            // No need for a dialog with one account.
            saveAutoLoginAccount(pref, accounts[0].name);
            return;
        }

        String account = mSettings.getAutoLoginAccount(getActivity());
        CharSequence[] names = new CharSequence[accounts.length];
        int i = 0;
        int defaultAccount = 0;
        for (Account a : accounts) {
            if (a.name.equals(account)) {
                defaultAccount = i;
            }
            names[i++] = a.name;
        }

        AccountCallback callback =
                new AccountCallback(accounts, pref);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.pref_autologin_title)
                .setSingleChoiceItems(names, defaultAccount, callback)
                .setCancelable(true)
                .show();
    }
}
