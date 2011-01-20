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
 * limitations under the License.
 */

package com.android.browser;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.BrowserContract;

public class AccountsChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Validate that the account we are syncing to still exists
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String accountType = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_TYPE, null);
        String accountName = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
        if (accountType == null || accountName == null) {
            // Not syncing, nothing to do
            return;
        }
        Account[] accounts = AccountManager.get(context).getAccountsByType(accountType);
        for (Account a : accounts) {
            if (accountName.equals(a.name)) {
                // Still have a valid account, sweet
                return;
            }
        }
        // Account deleted - disable sync
        prefs.edit()
                .remove(BrowserBookmarksPage.PREF_ACCOUNT_TYPE)
                .remove(BrowserBookmarksPage.PREF_ACCOUNT_NAME)
                .commit();
        BrowserContract.Settings.setSyncEnabled(context, false);
        for (Account a : accounts) {
            ContentResolver.setSyncAutomatically(a, BrowserContract.AUTHORITY, false);
            ContentResolver.setIsSyncable(a, BrowserContract.AUTHORITY, 0);
        }
    }

}
