/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;

import java.util.Map;
import java.util.Set;

public class BrowserPreferencesPage extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private String LOGTAG = "BrowserPreferencesPage";
    /* package */ static final String CURRENT_PAGE = "currentPage";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.browser_preferences);

        Preference e = findPreference(BrowserSettings.PREF_HOMEPAGE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getPreferenceScreen().getSharedPreferences()
                .getString(BrowserSettings.PREF_HOMEPAGE, null));
        ((BrowserHomepagePreference) e).setCurrentPage(
                getIntent().getStringExtra(CURRENT_PAGE));
        
        e = findPreference(BrowserSettings.PREF_EXTRAS_RESET_DEFAULTS);
        e.setOnPreferenceChangeListener(this);
        
        e = findPreference(BrowserSettings.PREF_TEXT_SIZE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualTextSizeName(
                getPreferenceScreen().getSharedPreferences()
                .getString(BrowserSettings.PREF_TEXT_SIZE, null)) );
        
        e = findPreference(BrowserSettings.PREF_DEFAULT_ZOOM);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getVisualDefaultZoomName(
                getPreferenceScreen().getSharedPreferences()
                .getString(BrowserSettings.PREF_DEFAULT_ZOOM, null)) );

        e = findPreference(BrowserSettings.PREF_DEFAULT_TEXT_ENCODING);
        e.setOnPreferenceChangeListener(this);

        e = findPreference(BrowserSettings.PREF_CLEAR_HISTORY);
        e.setOnPreferenceChangeListener(this);

        if (BrowserSettings.getInstance().showDebugSettings()) {
            addPreferencesFromResource(R.xml.debug_preferences);
        }

        PreferenceScreen websiteSettings = (PreferenceScreen)
            findPreference(BrowserSettings.PREF_WEBSITE_SETTINGS);
        Intent intent = new Intent(this, WebsiteSettingsActivity.class);
        websiteSettings.setIntent(intent);
    }

    /*
     * We need to set the PreferenceScreen state in onResume(), as the number of
     * origins with active features (WebStorage, Geolocation etc) could have
     * changed after calling the WebsiteSettingsActivity.
     */
    @Override
    protected void onResume() {
        super.onResume();
        final PreferenceScreen websiteSettings = (PreferenceScreen)
            findPreference(BrowserSettings.PREF_WEBSITE_SETTINGS);
        websiteSettings.setEnabled(false);
        WebStorage.getInstance().getOrigins(new ValueCallback<Map>() {
            public void onReceiveValue(Map webStorageOrigins) {
                if ((webStorageOrigins != null) && !webStorageOrigins.isEmpty()) {
                    websiteSettings.setEnabled(true);
                }
            }
        });
        GeolocationPermissions.getInstance().getOrigins(new ValueCallback<Set<String> >() {
            public void onReceiveValue(Set<String> geolocationOrigins) {
                if ((geolocationOrigins != null) && !geolocationOrigins.isEmpty()) {
                    websiteSettings.setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        // sync the shared preferences back to BrowserSettings
        BrowserSettings.getInstance().syncSharedPreferences(
                getApplicationContext(),
                getPreferenceScreen().getSharedPreferences());
    }

    public boolean onPreferenceChange(Preference pref, Object objValue) {
        if (pref.getKey().equals(BrowserSettings.PREF_EXTRAS_RESET_DEFAULTS)) {
            Boolean value = (Boolean) objValue;
            if (value.booleanValue() == true) {
                finish();
            }
        } else if (pref.getKey().equals(BrowserSettings.PREF_HOMEPAGE)) {
            String value = (String) objValue;
            boolean needUpdate = value.indexOf(' ') != -1;
            if (needUpdate) {
                value = value.trim().replace(" ", "%20");
            }
            if (value.length() != 0 && Uri.parse(value).getScheme() == null) {
                value = "http://" + value;
                needUpdate = true;
            }
            // Set the summary value.
            pref.setSummary(value);
            if (needUpdate) {
                // Update through the EditText control as it has a cached copy
                // of the string and it will handle persisting the value
                ((EditTextPreference) pref).setText(value);

                // as we update the value above, we need to return false
                // here so that setText() is not called by EditTextPref
                // with the old value.
                return false;
            } else {
                return true;
            }
        } else if (pref.getKey().equals(BrowserSettings.PREF_TEXT_SIZE)) {
            pref.setSummary(getVisualTextSizeName((String) objValue));
            return true;
        } else if (pref.getKey().equals(BrowserSettings.PREF_DEFAULT_ZOOM)) {
            pref.setSummary(getVisualDefaultZoomName((String) objValue));
            return true;
        } else if (pref.getKey().equals(
                BrowserSettings.PREF_DEFAULT_TEXT_ENCODING)) {
            pref.setSummary((String) objValue);
            return true;
        } else if (pref.getKey().equals(BrowserSettings.PREF_CLEAR_HISTORY)
                && ((Boolean) objValue).booleanValue() == true) {
            // Need to tell the browser to remove the parent/child relationship
            // between tabs
            setResult(RESULT_OK, (new Intent()).putExtra(Intent.EXTRA_TEXT,
                    pref.getKey()));
            return true;
        }
        
        return false;
    }

    private CharSequence getVisualTextSizeName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_text_size_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_text_size_values);

        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }

        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }

        return "";
    }

    private CharSequence getVisualDefaultZoomName(String enumName) {
        CharSequence[] visualNames = getResources().getTextArray(
                R.array.pref_default_zoom_choices);
        CharSequence[] enumNames = getResources().getTextArray(
                R.array.pref_default_zoom_values);

        // Sanity check
        if (visualNames.length != enumNames.length) {
            return "";
        }

        for (int i = 0; i < enumNames.length; i++) {
            if (enumNames[i].equals(enumName)) {
                return visualNames[i];
            }
        }

        return "";
    }
}
