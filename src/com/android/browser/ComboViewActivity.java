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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.android.browser.CombinedBookmarkHistoryView.CombinedBookmarksCallbacks;
import com.android.browser.UI.ComboViews;

public class ComboViewActivity extends Activity implements CombinedBookmarksCallbacks {

    public static final String EXTRA_COMBO_ARGS = "combo_args";
    public static final String EXTRA_INITIAL_VIEW = "initial_view";

    public static final String EXTRA_OPEN_SNAPSHOT = "snapshot_id";
    public static final String EXTRA_OPEN_ALL = "open_all";
    public static final String EXTRA_CURRENT_URL = "url";
    public static final String EXTRA_BOOKMARK_PAGE = "create_bookmark";

    private CombinedBookmarkHistoryView mComboView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Bundle extras = getIntent().getExtras();
        Bundle args = extras.getBundle(EXTRA_COMBO_ARGS);
        String svStr = extras.getString(EXTRA_INITIAL_VIEW, null);
        ComboViews startingView = svStr != null
                ? ComboViews.valueOf(svStr)
                : ComboViews.Bookmarks;
        mComboView = new CombinedBookmarkHistoryView(this, this,
                startingView, args);
        setContentView(mComboView);
    }

    @Override
    public void openUrl(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void openInNewTab(String... urls) {
        Intent i = new Intent();
        i.putExtra(EXTRA_OPEN_ALL, urls);
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void close() {
        finish();
    }

    @Override
    public void openSnapshot(long id) {
        Intent i = new Intent();
        i.putExtra(EXTRA_OPEN_SNAPSHOT, id);
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (!mComboView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.combined, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.preferences_menu_id) {
            String url = getIntent().getStringExtra(EXTRA_CURRENT_URL);
            Intent intent = new Intent(this, BrowserPreferencesPage.class);
            intent.putExtra(BrowserPreferencesPage.CURRENT_PAGE, url);
            startActivityForResult(intent, Controller.PREFERENCES_PAGE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
