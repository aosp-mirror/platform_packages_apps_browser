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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.webkit.WebSettings.AutoFillProfile;
import android.widget.Button;
import android.widget.EditText;

public class AutoFillSettingsFragment extends Fragment {

    private static final String LOGTAG = "AutoFillSettingsFragment";

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
                BrowserSettings.getInstance().setAutoFillProfile(getActivity(),
                        new AutoFillProfile(
                                fullName.getText().toString(),
                                email.getText().toString()));
            }
        });

        // Populate the text boxes with any pre existing AutoFill data.
        EditText fullName = (EditText)v.findViewById(
                R.id.autofill_profile_editor_name_edit);
        EditText email = (EditText)v.findViewById(
                R.id.autofill_profile_editor_email_address_edit);
        AutoFillProfile activeProfile = BrowserSettings.getInstance().getAutoFillProfile();
        fullName.setText(activeProfile.getFullName());
        email.setText(activeProfile.getEmailAddress());

        return v;
    }

    @Override
    public void onPause() {
        AutoFillProfileDatabase db =
                AutoFillProfileDatabase.getInstance(getActivity());
        db.close();
        super.onPause();
    }
}
