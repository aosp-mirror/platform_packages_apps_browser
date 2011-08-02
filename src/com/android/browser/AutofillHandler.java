
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Message;
import android.preference.PreferenceManager;
import android.webkit.WebSettings.AutoFillProfile;

import java.util.concurrent.CountDownLatch;

public class AutofillHandler {

    private AutoFillProfile mAutoFillProfile;
    // Default to zero. In the case no profile is set up, the initial
    // value will come from the AutoFillSettingsFragment when the user
    // creates a profile. Otherwise, we'll read the ID of the last used
    // profile from the prefs db.
    private int mAutoFillActiveProfileId;
    private static final int NO_AUTOFILL_PROFILE_SET = 0;

    private CountDownLatch mLoaded = new CountDownLatch(1);
    private Context mContext;

    public AutofillHandler(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Load settings from the browser app's database. It is performed in
     * an AsyncTask as it involves plenty of slow disk IO.
     * NOTE: Strings used for the preferences must match those specified
     * in the various preference XML files.
     */
    public void asyncLoadFromDb() {
        // Run the initial settings load in an AsyncTask as it hits the
        // disk multiple times through SharedPreferences and SQLite. We
        // need to be certain though that this has completed before we start
        // to load pages though, so in the worst case we will block waiting
        // for it to finish in BrowserActivity.onCreate().
         new LoadFromDb().start();
    }

    public void waitForLoad() {
        try {
            mLoaded.await();
        } catch (InterruptedException e) {}
    }

    private class LoadFromDb extends Thread {

        @Override
        public void run() {
            SharedPreferences p =
                    PreferenceManager.getDefaultSharedPreferences(mContext);

            // Read the last active AutoFill profile id.
            mAutoFillActiveProfileId = p.getInt(
                    PreferenceKeys.PREF_AUTOFILL_ACTIVE_PROFILE_ID,
                    mAutoFillActiveProfileId);

            // Load the autofill profile data from the database. We use a database separate
            // to the browser preference DB to make it easier to support multiple profiles
            // and switching between them.
            AutoFillProfileDatabase autoFillDb = AutoFillProfileDatabase.getInstance(mContext);
            Cursor c = autoFillDb.getProfile(mAutoFillActiveProfileId);

            if (c.getCount() > 0) {
                c.moveToFirst();

                String fullName = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.FULL_NAME));
                String email = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.EMAIL_ADDRESS));
                String company = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.COMPANY_NAME));
                String addressLine1 = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.ADDRESS_LINE_1));
                String addressLine2 = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.ADDRESS_LINE_2));
                String city = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.CITY));
                String state = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.STATE));
                String zip = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.ZIP_CODE));
                String country = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.COUNTRY));
                String phone = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.PHONE_NUMBER));
                mAutoFillProfile = new AutoFillProfile(mAutoFillActiveProfileId,
                        fullName, email, company, addressLine1, addressLine2, city,
                        state, zip, country, phone);
            }
            c.close();
            autoFillDb.close();

            mLoaded.countDown();
        }
    }

    public void setAutoFillProfile(AutoFillProfile profile, Message msg) {
        int profileId = NO_AUTOFILL_PROFILE_SET;
        if (profile != null) {
            profileId = profile.getUniqueId();
            // Update the AutoFill DB with the new profile.
            new SaveProfileToDbTask(msg).execute(profile);
        } else {
            // Delete the current profile.
            if (mAutoFillProfile != null) {
                new DeleteProfileFromDbTask(msg).execute(mAutoFillProfile.getUniqueId());
            }
        }
        // Make sure we set mAutoFillProfile before calling setActiveAutoFillProfileId
        // Calling setActiveAutoFillProfileId will trigger an update of WebViews
        // which will expect a new profile to be set
        mAutoFillProfile = profile;
        setActiveAutoFillProfileId(profileId);
    }

    public AutoFillProfile getAutoFillProfile() {
        return mAutoFillProfile;
    }

    private void setActiveAutoFillProfileId(int activeProfileId) {
        mAutoFillActiveProfileId = activeProfileId;
        Editor ed = PreferenceManager.
            getDefaultSharedPreferences(mContext).edit();
        ed.putInt(PreferenceKeys.PREF_AUTOFILL_ACTIVE_PROFILE_ID, activeProfileId);
        ed.apply();
    }

    private abstract class AutoFillProfileDbTask<T> extends AsyncTask<T, Void, Void> {
        AutoFillProfileDatabase mAutoFillProfileDb;
        Message mCompleteMessage;

        public AutoFillProfileDbTask(Message msg) {
            mCompleteMessage = msg;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (mCompleteMessage != null) {
                mCompleteMessage.sendToTarget();
            }
            mAutoFillProfileDb.close();
        }

        @Override
        abstract protected Void doInBackground(T... values);
    }


    private class SaveProfileToDbTask extends AutoFillProfileDbTask<AutoFillProfile> {
        public SaveProfileToDbTask(Message msg) {
            super(msg);
        }

        @Override
        protected Void doInBackground(AutoFillProfile... values) {
            mAutoFillProfileDb = AutoFillProfileDatabase.getInstance(mContext);
            assert mAutoFillActiveProfileId != NO_AUTOFILL_PROFILE_SET;
            AutoFillProfile newProfile = values[0];
            mAutoFillProfileDb.addOrUpdateProfile(mAutoFillActiveProfileId, newProfile);
            return null;
        }
    }

    private class DeleteProfileFromDbTask extends AutoFillProfileDbTask<Integer> {
        public DeleteProfileFromDbTask(Message msg) {
            super(msg);
        }

        @Override
        protected Void doInBackground(Integer... values) {
            mAutoFillProfileDb = AutoFillProfileDatabase.getInstance(mContext);
            int id = values[0];
            assert  id > 0;
            mAutoFillProfileDb.dropProfile(id);
            return null;
        }
    }
}
