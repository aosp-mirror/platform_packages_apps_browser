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

import java.util.List;

import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.webkit.WebView;
import android.webkit.Plugin;

public class BrowserPreferencesPage extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, 
        Preference.OnPreferenceClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the XML preferences file
        addPreferencesFromResource(R.xml.browser_preferences);

        Preference e = findPreference(BrowserSettings.PREF_HOMEPAGE);
        e.setOnPreferenceChangeListener(this);
        e.setSummary(getPreferenceScreen().getSharedPreferences()
                .getString(BrowserSettings.PREF_HOMEPAGE, null));
        
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
        
        if (BrowserSettings.getInstance().showDebugSettings()) {
            addPreferencesFromResource(R.xml.debug_preferences);
        }
        
        e = findPreference(BrowserSettings.PREF_GEARS_SETTINGS);
        e.setOnPreferenceClickListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // sync the shared preferences back to BrowserSettings
        BrowserSettings.getInstance().syncSharedPreferences(
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
        }
        
        return false;
    }
    
    public boolean onPreferenceClick(Preference pref) {
        if (pref.getKey().equals(BrowserSettings.PREF_GEARS_SETTINGS)) {
            List<Plugin> loadedPlugins = WebView.getPluginList().getList();
            for(Plugin p : loadedPlugins) {
                if (p.getName().equals("gears")) {
                    p.dispatchClickEvent(this);
                    return true;
                }
            }
            
        }
        return true;
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
