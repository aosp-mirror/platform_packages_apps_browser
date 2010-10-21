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
import android.widget.Toast;

public class AutoFillSettingsFragment extends Fragment {

    private static final String LOGTAG = "AutoFillSettingsFragment";

    private EditText mFullNameEdit;
    private EditText mEmailEdit;
    private EditText mCompanyEdit;
    private EditText mAddressLine1Edit;
    private EditText mAddressLine2Edit;
    private EditText mCityEdit;
    private EditText mStateEdit;
    private EditText mZipEdit;
    private EditText mCountryEdit;
    private EditText mPhoneEdit;

    // For now we support just one profile so it's safe to hardcode the
    // id to 1 here. In the future this unique identifier will be set
    // dynamically.
    private int mUniqueId = 1;

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

        mFullNameEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_name_edit);
        mEmailEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_email_address_edit);
        mCompanyEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_company_name_edit);
        mAddressLine1Edit = (EditText)v.findViewById(
                R.id.autofill_profile_editor_address_line_1_edit);
        mAddressLine2Edit = (EditText)v.findViewById(
                R.id.autofill_profile_editor_address_line_2_edit);
        mCityEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_city_edit);
        mStateEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_state_edit);
        mZipEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_zip_code_edit);
        mCountryEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_country_edit);
        mPhoneEdit = (EditText)v.findViewById(R.id.autofill_profile_editor_phone_number_edit);

        Button saveButton = (Button)v.findViewById(R.id.autofill_profile_editor_save_button);
        saveButton.setOnClickListener(new OnClickListener() {
            public void onClick(View button) {
                BrowserSettings.getInstance().setAutoFillProfile(getActivity(),
                        new AutoFillProfile(
                                mUniqueId,
                                mFullNameEdit.getText().toString(),
                                mEmailEdit.getText().toString(),
                                mCompanyEdit.getText().toString(),
                                mAddressLine1Edit.getText().toString(),
                                mAddressLine2Edit.getText().toString(),
                                mCityEdit.getText().toString(),
                                mStateEdit.getText().toString(),
                                mZipEdit.getText().toString(),
                                mCountryEdit.getText().toString(),
                                mPhoneEdit.getText().toString()));
            }
        });

        Button deleteButton = (Button)v.findViewById(R.id.autofill_profile_editor_delete_button);
        deleteButton.setOnClickListener(new OnClickListener() {
            public void onClick(View button) {
                Toast.makeText(getActivity(), "TODO: Implement me", Toast.LENGTH_SHORT).show();
            }
        });

       Button cancelButton = (Button)v.findViewById(R.id.autofill_profile_editor_cancel_button);
       cancelButton.setOnClickListener(new OnClickListener() {
           public void onClick(View button) {
               getFragmentManager().popBackStack();
           }
        });

        // Populate the text boxes with any pre existing AutoFill data.
        AutoFillProfile activeProfile = BrowserSettings.getInstance().getAutoFillProfile();
        if (activeProfile != null) {
            mFullNameEdit.setText(activeProfile.getFullName());
            mEmailEdit.setText(activeProfile.getEmailAddress());
            mCompanyEdit.setText(activeProfile.getCompanyName());
            mAddressLine1Edit.setText(activeProfile.getAddressLine1());
            mAddressLine2Edit.setText(activeProfile.getAddressLine2());
            mCityEdit.setText(activeProfile.getCity());
            mStateEdit.setText(activeProfile.getState());
            mZipEdit.setText(activeProfile.getZipCode());
            mCountryEdit.setText(activeProfile.getCountry());
            mPhoneEdit.setText(activeProfile.getPhoneNumber());
        }

        return v;
    }
}
