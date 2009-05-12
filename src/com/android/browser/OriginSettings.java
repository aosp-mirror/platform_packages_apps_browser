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

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.webkit.WebStorage;

/**
 * Manage the settings for an origin.
 * We use it to keep track of the HTML5 settings, i.e. database (webstorage).
 */
class OriginSettings {

    private String TAG = "OriginSettings";
    private String mOrigin = null;
    private long mQuota = 0;
    private long mUsage = 0;
    private PreferenceScreen mInfoScreen;
    private PreferenceScreen mRootScreen;
    private PreferenceActivity mActivity;

    private static String sMBUsed = null;
    private static String sNoQuotaLeft = null;
    private static String sMBLeft = null;

    public OriginSettings(PreferenceActivity activity, String origin) {
        mOrigin = origin;
        mUsage = WebStorage.getInstance().getUsageForOrigin(mOrigin);
        mQuota = WebStorage.getInstance().getQuotaForOrigin(mOrigin);
        mActivity = activity;
        if (sMBUsed == null) {
          sMBUsed = mActivity.getString(
              R.string.webstorage_origin_summary_mb_used);
          sNoQuotaLeft = mActivity.getString(
              R.string.webstorage_origin_summary_no_quota_left);
          sMBLeft = mActivity.getString(
              R.string.webstorage_origin_summary_mb_left);
        }
    }

    public String getOrigin() {
        return mOrigin;
    }

    public long getQuota() {
        return mQuota;
    }

    public long getUsage() {
        return mUsage;
    }

    public void setScreen(PreferenceScreen screen) {
        mInfoScreen = screen;
    }

    public void setRootScreen(PreferenceScreen screen) {
        mRootScreen = screen;
    }

    private String sizeValueToString(long value) {
        float mb = (float) value / (1024.0F * 1024.0F);
        int val = (int) (mb * 10);
        float ret = (float) (val / 10.0F);
        if (ret <= 0) {
            return "0";
        }
        return String.valueOf(ret);
    }

    public void updateSummary() {
        String summary = sizeValueToString(mUsage) + " " + sMBUsed;
        if ((mQuota <= 0) || ((mQuota - mUsage) <= 0)) {
            summary += ", " + sNoQuotaLeft;
        } else {
            summary += " (" + sizeValueToString(mQuota - mUsage);
            summary += " " + sMBLeft + ")";
        }
        mInfoScreen.setSummary(summary);
        mActivity.onContentChanged();
    }

    public void setup() {
        mInfoScreen.setTitle(mOrigin);
        mInfoScreen.setKey(mOrigin);
        updateSummary();

        BrowserQuotaPreference manageSite = new BrowserQuotaPreference(mActivity, this);
        BrowserYesNoPreference clearAllData = new BrowserYesNoPreference(mActivity, this);

        manageSite.setTitle(R.string.webstorage_manage_quota_title);
        manageSite.setSummary(R.string.webstorage_manage_quota_summary);
        manageSite.setKey(BrowserSettings.PREF_MANAGE_QUOTA);
        manageSite.setEntries(R.array.webstorage_quota_entries);
        manageSite.setEntryValues(R.array.webstorage_quota_entries_values);

        clearAllData.setTitle(R.string.webstorage_clear_data_title);
        clearAllData.setSummary(R.string.webstorage_clear_data_summary);
        clearAllData.setKey(BrowserSettings.PREF_CLEAR_ALL_DATA);
        clearAllData.setDialogTitle(R.string.webstorage_clear_data_dialog_title);
        clearAllData.setDialogMessage(R.string.webstorage_clear_data_dialog_message);
        clearAllData.setDialogIcon(android.R.drawable.ic_dialog_alert);

        mInfoScreen.addPreference(manageSite);
        mInfoScreen.addPreference(clearAllData);
    }

    public void setQuota(long quota) {
        mQuota = quota;
        WebStorage.getInstance().setQuotaForOrigin(mOrigin, mQuota);
        mInfoScreen.getDialog().dismiss();
        updateSummary();
    }

    public void delete() {
        WebStorage.getInstance().deleteOrigin(mOrigin);
        mInfoScreen.removeAll();
        mRootScreen.removePreference(mInfoScreen);
        mInfoScreen.getDialog().dismiss();
        if (mRootScreen.getPreferenceCount() == 0) {
            mRootScreen.getDialog().dismiss();
            mRootScreen.setEnabled(false);
            Preference clearDatabases = mActivity.findPreference(
                    BrowserSettings.PREF_WEBSTORAGE_CLEAR_ALL);
            clearDatabases.setEnabled(false);
        }
    }
}
