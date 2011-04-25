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

import com.android.browser.BrowserBookmarksPage;
import com.android.browser.BrowserHomepagePreference;
import com.android.browser.BrowserPreferencesPage;
import com.android.browser.PreferenceKeys;
import com.android.browser.R;
import com.android.browser.widget.BookmarkThumbnailWidgetProvider;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.BrowserContract;
import android.util.Log;

public class GeneralPreferencesFragment extends PreferenceFragment
        implements OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    static final String TAG = "PersonalPreferencesFragment";

    static final String PREF_CHROME_SYNC = "sync_with_chrome";

    Preference mChromeSync;
    boolean mEnabled;
    SharedPreferences mSharedPrefs;
    Account[] mAccounts;

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
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mSharedPrefs.registerOnSharedPreferenceChangeListener(mListener);
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

        // Setup the proper state for the sync with chrome item
        mChromeSync = findPreference(PREF_CHROME_SYNC);
        refreshUi();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(mListener);
    }

    OnSharedPreferenceChangeListener mListener
            = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (BrowserBookmarksPage.PREF_ACCOUNT_NAME.equals(key)
                    || BrowserBookmarksPage.PREF_ACCOUNT_TYPE.equals(key)) {
                refreshUi();
                BookmarkThumbnailWidgetProvider.refreshWidgets(getActivity(), true);
            }
        }

    };

    private AccountManagerCallback<Bundle> mCallback =
            new AccountManagerCallback<Bundle>() {

        @Override
        public void run(AccountManagerFuture<Bundle> future) {
            try {
                Bundle bundle = future.getResult();
                String name = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);
                String type = bundle.getString(AccountManager.KEY_ACCOUNT_TYPE);
                Account account = new Account(name, type);
                mAccounts = new Account[] { account };
                ImportWizard wizard = ImportWizard.newInstance(mAccounts);
                wizard.show(getFragmentManager(), null);
            } catch (Exception ex) {
                // Canceled or failed to login, doesn't matter to us
            }
        }
    };

    OnPreferenceClickListener mAddAccount = new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            AccountManager am = AccountManager.get(getActivity());
            am.addAccount("com.google", null, null, null, getActivity(),
                    mCallback, null);
            return true;
        }
    };

    private class GetAccountsTask extends AsyncTask<Void, Void, String> {
        private Context mContext;

        GetAccountsTask(Context ctx) {
            mContext = ctx;
        }

        @Override
        protected String doInBackground(Void... unused) {
            AccountManager am = (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);
            Account[] accounts = am.getAccountsByType("com.google");
            if (accounts == null || accounts.length == 0) {
                // No Google accounts setup, don't offer Chrome sync
                if (mChromeSync != null) {
                    mChromeSync.setOnPreferenceClickListener(mAddAccount);
                }
            } else {
                // Google accounts are present.
                mAccounts = accounts;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                Bundle args = mChromeSync.getExtras();
                args.putParcelableArray("accounts", accounts);
                mEnabled = BrowserContract.Settings.isSyncEnabled(mContext);
                mChromeSync.setOnPreferenceClickListener(GeneralPreferencesFragment.this);

                if (!mEnabled) {
                    // Setup a link to the enable wizard
                    return mContext.getResources().getString(
                            R.string.pref_personal_sync_with_chrome_summary);
                } else {
                    // Chrome sync is enabled, setup a link to account switcher
                    String accountName = prefs.getString(
                            BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
                    args.putString("curAccount", accountName);
                    return accountName;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(String summary) {
            if (summary != null) {
                mChromeSync.setSummary(summary);
            }
        }
    }

    void refreshUi() {
        new GetAccountsTask(getActivity()).execute();

        PreferenceScreen autoFillSettings =
                (PreferenceScreen)findPreference(PreferenceKeys.PREF_AUTOFILL_PROFILE);
        autoFillSettings.setDependency(PreferenceKeys.PREF_AUTOFILL_ENABLED);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (mAccounts == null) {
            Log.w(TAG, "NULL accounts!");
            return true;
        }
        DialogFragment frag;
        if (mEnabled) {
            frag = new AccountChooserDialog();
            frag.setArguments(preference.getExtras());
        } else {
            frag = ImportWizard.newInstance(mAccounts);
        }
        frag.show(getFragmentManager(), null);
        return true;
    }

    public static class AccountChooserDialog extends DialogFragment
            implements DialogInterface.OnClickListener {

        AlertDialog mDialog;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            Account[] accounts = (Account[]) args.getParcelableArray("accounts");
            String curAccount = args.getString("curAccount");
            int length = accounts.length;
            int curAccountOffset = 0;
            CharSequence[] accountNames = new CharSequence[length];
            for (int i = 0; i < length; i++) {
                String name = accounts[i].name;
                if (name.equals(curAccount)) {
                    curAccountOffset = i;
                }
                accountNames[i] = name;
            }

            mDialog = new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.account_chooser_dialog_title)
                    .setSingleChoiceItems(accountNames, curAccountOffset, this)
                    .create();
            return mDialog;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String accountName = mDialog.getListView().getAdapter().getItem(which).toString();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefs.edit().putString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, accountName).apply();
            dismiss();
        }
    }
}
