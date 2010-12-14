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

import com.android.browser.preferences.DebugPreferencesFragment;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import java.util.List;

public class BrowserPreferencesPage extends PreferenceActivity {

    public static final String CURRENT_PAGE = "currentPage";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(
                    ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);

        if (BrowserSettings.DEV_BUILD || BrowserSettings.getInstance().showDebugSettings()) {
            Header debug = new Header();
            debug.title = getText(R.string.pref_development_title);
            debug.fragment = DebugPreferencesFragment.class.getName();
            target.add(debug);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // sync the shared preferences back to BrowserSettings
        BrowserSettings.getInstance().syncSharedPreferences(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(this));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getFragmentManager().countBackStackEntries() > 0) {
                    getFragmentManager().popBackStack();
                } else {
                    finish();
                }
                return true;
        }

        return false;
    }
}
