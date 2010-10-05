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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

public class ShortcutActivity extends Activity
    implements BookmarksHistoryCallbacks {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.openTransaction();
        Bundle extras = new Bundle();
        extras.putBoolean(BrowserBookmarksPage.EXTRA_SHORTCUT, true);
        extras.putBoolean(BrowserBookmarksPage.EXTRA_DISABLE_WINDOW, true);
        Fragment frag = BrowserBookmarksPage.newInstance(this, extras);
        transaction.add(android.R.id.content, frag);
        transaction.commit();
    }


    /**
     * handle fragment startActivity
     */
    @Override
    public void startActivityFromFragment(Fragment f, Intent intent, int requestCode) {
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
    }

    // BookmarksHistoryCallbacks

    /**
     * not used for shortcuts
     */
    @Override
    public void onRemoveParentChildRelationships() {}

    @Override
    public void onComboCanceled() {
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * not used for shortcuts
     */
    @Override
    public void onUrlSelected(String url, boolean newWindow) {}

}
