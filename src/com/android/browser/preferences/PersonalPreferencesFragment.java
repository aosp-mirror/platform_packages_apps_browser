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
import com.android.browser.BrowserSettings;
import com.android.browser.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Bookmarks;
import android.provider.BrowserContract.ChromeSyncColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;

public class PersonalPreferencesFragment extends PreferenceFragment
        implements OnPreferenceClickListener {
    static final String TAG = "PersonalPreferencesFragment";

    static final String PREF_CHROME_SYNC = "sync_with_chrome";

    Preference mChromeSync;
    boolean mEnabled;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.personal_preferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup the proper state for the sync with chrome item
        Context context = getActivity();
        mChromeSync = findPreference(PREF_CHROME_SYNC);
        refreshUi(context);
    }

    void refreshUi(Context context) {
        AccountManager am = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Account[] accounts = am.getAccountsByType("com.google");
        if (accounts == null || accounts.length == 0) {
            // No Google accounts setup, don't offer Chrome sync
            if (mChromeSync != null) {
                getPreferenceScreen().removePreference(mChromeSync);
            }
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Bundle args = mChromeSync.getExtras();
            args.putParcelableArray("accounts", accounts);
            mEnabled = BrowserContract.Settings.isSyncEnabled(context);
            if (!mEnabled) {
                // Google accounts are present, but Chrome sync isn't enabled yet.
                // Setup a link to the enable wizard
                mChromeSync.setSummary(R.string.pref_personal_sync_with_chrome_summary);
            } else {
                // Chrome sync is enabled, setup a link to account switcher
                String accountName = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
                mChromeSync.setSummary(accountName);
                args.putString("curAccount", accountName);
            }
            mChromeSync.setOnPreferenceClickListener(this);
        }

        PreferenceScreen autoFillSettings =
                (PreferenceScreen)findPreference(BrowserSettings.PREF_AUTOFILL_PROFILE);
        autoFillSettings.setDependency(BrowserSettings.PREF_AUTOFILL_ENABLED);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        Fragment frag;
        if (mEnabled) {
            frag = new AccountChooserDialog();
        } else {
            frag = new ImportWizardDialog();
        }
        frag.setArguments(preference.getExtras());
        getFragmentManager().openTransaction()
                .add(frag, null)
                .commit();
        return true;
    }

    final class AccountChooserDialog extends DialogFragment
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
                    .setTitle("Choose account") // STOPSHIP localize
                    .setSingleChoiceItems(accountNames, curAccountOffset, this)
                    .create();
            return mDialog;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String accountName = mDialog.getListView().getAdapter().getItem(which).toString();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefs.edit().putString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, accountName).apply();
            refreshUi(getActivity());
            dismiss();
        }
    }

    final class ImportWizardDialog extends DialogFragment implements OnClickListener {
        View mRemoveButton;
        View mCancelButton;
        String mDefaultAccount;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            Dialog dialog = new Dialog(context);
            dialog.setTitle(R.string.import_bookmarks_dialog_title);
            dialog.setContentView(R.layout.import_bookmarks_dialog);
            mRemoveButton = dialog.findViewById(R.id.remove);
            mRemoveButton.setOnClickListener(this);
            mCancelButton = dialog.findViewById(R.id.cancel);
            mCancelButton.setOnClickListener(this);

            LayoutInflater inflater = dialog.getLayoutInflater();
            LinearLayout accountList = (LinearLayout) dialog.findViewById(R.id.accountList);
            Account[] accounts = (Account[]) getArguments().getParcelableArray("accounts");
            mDefaultAccount = accounts[0].name;
            int length = accounts.length;
            for (int i = 0; i < length; i++) {
                Button button = (Button) inflater.inflate(R.layout.import_bookmarks_dialog_button,
                        null);
                button.setText(context.getString(R.string.import_bookmarks_dialog_import,
                        accounts[i].name));
                button.setTag(accounts[i].name);
                button.setOnClickListener(this);
                accountList.addView(button);
            }

            return dialog;
        }

        @Override
        public void onClick(View view) {
            if (view == mCancelButton) {
                dismiss();
                return;
            }

            ContentResolver resolver = getActivity().getContentResolver();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String accountName;
            if (view == mRemoveButton) {
                // The user chose to remove their old bookmarks, delete them now
                resolver.delete(Bookmarks.CONTENT_URI,
                        Bookmarks.PARENT + "=1 AND " + Bookmarks.ACCOUNT_NAME + " IS NULL", null);
                accountName = mDefaultAccount;
            } else {
                // The user chose to migrate their old bookmarks to the account they're syncing
                accountName = view.getTag().toString();
                migrateBookmarks(resolver, accountName);
            }

            // Record the fact that we turned on sync
            BrowserContract.Settings.setSyncEnabled(getActivity(), true);
            prefs.edit()
                    .putString(BrowserBookmarksPage.PREF_ACCOUNT_TYPE, "com.google")
                    .putString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, accountName)
                    .apply();

            // Enable bookmark sync on all accounts
            Account[] accounts = (Account[]) getArguments().getParcelableArray("accounts");
            for (Account account : accounts) {
                ContentResolver.setIsSyncable(account, BrowserContract.AUTHORITY, 1);
            }

            refreshUi(getActivity());
            dismiss();
        }

        /**
         * Migrates bookmarks to the given account
         */
        void migrateBookmarks(ContentResolver resolver, String accountName) {
            Cursor cursor = null;
            try {
                // Re-parent the bookmarks in the default root folder
                cursor = resolver.query(Bookmarks.CONTENT_URI, new String[] { Bookmarks._ID },
                        Bookmarks.ACCOUNT_NAME + " =? AND " +
                            ChromeSyncColumns.SERVER_UNIQUE + " =?",
                        new String[] { accountName,
                            ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR },
                        null);
                ContentValues values = new ContentValues();
                if (cursor == null || !cursor.moveToFirst()) {
                    // The root folders don't exist for the account, create them now
                    ArrayList<ContentProviderOperation> ops =
                            new ArrayList<ContentProviderOperation>();

                    // Chrome sync root folder
                    values.clear();
                    values.put(ChromeSyncColumns.SERVER_UNIQUE, ChromeSyncColumns.FOLDER_NAME_ROOT);
                    values.put(Bookmarks.TITLE, "Google Chrome");
                    values.put(Bookmarks.POSITION, 0);
                    values.put(Bookmarks.IS_FOLDER, true);
                    values.put(Bookmarks.DIRTY, true);
                    ops.add(ContentProviderOperation.newInsert(
                            Bookmarks.CONTENT_URI.buildUpon().appendQueryParameter(
                                    BrowserContract.CALLER_IS_SYNCADAPTER, "true").build())
                            .withValues(values)
                            .build());

                    // Bookmarks folder
                    values.clear();
                    values.put(ChromeSyncColumns.SERVER_UNIQUE,
                            ChromeSyncColumns.FOLDER_NAME_BOOKMARKS);
                    values.put(Bookmarks.TITLE, "Bookmarks");
                    values.put(Bookmarks.POSITION, 0);
                    values.put(Bookmarks.IS_FOLDER, true);
                    values.put(Bookmarks.DIRTY, true);
                    ops.add(ContentProviderOperation.newInsert(Bookmarks.CONTENT_URI)
                            .withValues(values)
                            .withValueBackReference(Bookmarks.PARENT, 0)
                            .build());

                    // Bookmarks Bar folder
                    values.clear();
                    values.put(ChromeSyncColumns.SERVER_UNIQUE,
                            ChromeSyncColumns.FOLDER_NAME_BOOKMARKS_BAR);
                    values.put(Bookmarks.TITLE, "Bookmarks Bar");
                    values.put(Bookmarks.POSITION, 0);
                    values.put(Bookmarks.IS_FOLDER, true);
                    values.put(Bookmarks.DIRTY, true);
                    ops.add(ContentProviderOperation.newInsert(Bookmarks.CONTENT_URI)
                            .withValues(values)
                            .withValueBackReference(Bookmarks.PARENT, 1)
                            .build());

                    // Other Bookmarks folder
                    values.clear();
                    values.put(ChromeSyncColumns.SERVER_UNIQUE,
                            ChromeSyncColumns.FOLDER_NAME_OTHER_BOOKMARKS);
                    values.put(Bookmarks.TITLE, "Other Bookmarks");
                    values.put(Bookmarks.POSITION, 1000);
                    values.put(Bookmarks.IS_FOLDER, true);
                    values.put(Bookmarks.DIRTY, true);
                    ops.add(ContentProviderOperation.newInsert(Bookmarks.CONTENT_URI)
                            .withValues(values)
                            .withValueBackReference(Bookmarks.PARENT, 1)
                            .build());

                    // Re-parent the existing bookmarks to the newly create bookmarks bar folder
                    ops.add(ContentProviderOperation.newUpdate(Bookmarks.CONTENT_URI)
                            .withValueBackReference(Bookmarks.PARENT, 2)
                            .withSelection(Bookmarks.PARENT + "=?",
                                        new String[] { Integer.toString(1) })
                            .build());

                    // Mark all non-root folder items as belonging to the new account
                    values.clear();
                    values.put(Bookmarks.ACCOUNT_TYPE, "com.google");
                    values.put(Bookmarks.ACCOUNT_NAME, accountName);
                    ops.add(ContentProviderOperation.newUpdate(Bookmarks.CONTENT_URI)
                            .withValues(values)
                            .withSelection(Bookmarks.ACCOUNT_NAME + " IS NULL AND " +
                                    Bookmarks._ID + "<>1", null)
                            .build());
                    
                    try {
                        resolver.applyBatch(BrowserContract.AUTHORITY, ops);
                    } catch (RemoteException e) {
                        Log.e(TAG, "failed to create root folder for account " + accountName, e);
                        return;
                    } catch (OperationApplicationException e) {
                        Log.e(TAG, "failed to create root folder for account " + accountName, e);
                        return;
                    }
                } else {
                    values.put(Bookmarks.PARENT, cursor.getLong(0));
                    resolver.update(Bookmarks.CONTENT_URI, values, Bookmarks.PARENT + "=?",
                            new String[] { Integer.toString(1) });

                    // Mark all bookmarks at all levels as part of the new account
                    values.clear();
                    values.put(Bookmarks.ACCOUNT_TYPE, "com.google");
                    values.put(Bookmarks.ACCOUNT_NAME, accountName);
                    resolver.update(Bookmarks.CONTENT_URI, values,
                            Bookmarks.ACCOUNT_NAME + " IS NULL AND " + Bookmarks._ID + "<>1",
                            null);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
    }
}
