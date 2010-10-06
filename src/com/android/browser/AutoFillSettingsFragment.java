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
 * limitations under the License.
 */

package com.android.browser;

import android.app.Fragment;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class AutoFillSettingsFragment extends Fragment {

    private static final String LOGTAG = "AutoFillSettingsFragment";

    // TODO: This will become dynamic once we support more than one profile.
    private int mProfileId = 1;

    public AutoFillSettingsFragment() {

    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.autofill_settings_fragment, container, false);

        Button saveButton = (Button)v.findViewById(R.id.autofill_profile_editor_save_button);
        saveButton.setOnClickListener(new OnClickListener() {
            public void onClick(View button) {
                View v = getView();
                EditText fullName = (EditText)v.findViewById(
                        R.id.autofill_profile_editor_name_edit);
                EditText email = (EditText)v.findViewById(
                        R.id.autofill_profile_editor_email_address_edit);
                new SaveProfileToDbTask().execute(fullName.getText().toString(),
                        email.getText().toString());
            }
        });

        // Load the profile and populate the text views in the background
        new LoadProfileFromDbTask().execute(mProfileId);

        return v;
    }

    @Override
    public void onPause() {
        AutoFillProfileDatabase db =
                AutoFillProfileDatabase.getInstance(getActivity());
        db.close();
        super.onPause();
    }

    private class SaveProfileToDbTask extends AsyncTask<String, Void, Void> {
        protected Void doInBackground(String... values) {
            AutoFillProfileDatabase db =
                    AutoFillProfileDatabase.getInstance(getActivity());
            db.addOrUpdateProfile(mProfileId, values[0], values[1]);
            return null;
        }

        protected void onPostExecute(Void result) {
            Toast.makeText(getActivity(), "Saved profile", Toast.LENGTH_SHORT).show();
        }
    }

    private static class LoadedProfileData {
        private String mFullName;
        private String mEmailAddress;

        public LoadedProfileData(String fullName, String emailAddress) {
            mFullName = fullName;
            mEmailAddress = emailAddress;
        }

        public String getFullName() { return mFullName; }
        public String getEmailAddress() { return mEmailAddress; }
    }

    private class LoadProfileFromDbTask extends AsyncTask<Integer, Void, LoadedProfileData> {
        protected LoadedProfileData doInBackground(Integer... id) {
            AutoFillProfileDatabase db = AutoFillProfileDatabase.getInstance(getActivity());
            Cursor c = db.getProfile(id[0]);
            c.moveToFirst();

            LoadedProfileData profileData = null;

            if (c.getCount() > 0) {
                String fullName = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.FULL_NAME));
                String email = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.EMAIL_ADDRESS));
                profileData =  new LoadedProfileData(fullName, email);
            }
           c.close();
           return profileData;
        }

        protected void onPostExecute(LoadedProfileData data) {
            if (data == null) {
                return;
            }

            View v = getView();
            if (v != null) {
                EditText fullName = (EditText)v.findViewById(
                        R.id.autofill_profile_editor_name_edit);
                EditText email = (EditText)v.findViewById(
                        R.id.autofill_profile_editor_email_address_edit);
                fullName.setText(data.getFullName());
                email.setText(data.getEmailAddress());
            }
        }
    }
}
