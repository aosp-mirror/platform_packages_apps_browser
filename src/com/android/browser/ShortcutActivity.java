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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class ShortcutActivity extends Activity
    implements BookmarksPageCallbacks, OnClickListener {

    private BrowserBookmarksPage mBookmarks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: Is this needed?
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
        setContentView(R.layout.pick_bookmark);
        mBookmarks = (BrowserBookmarksPage) getFragmentManager()
                .findFragmentById(R.id.bookmarks);
        mBookmarks.setEnableContextMenu(false);
        mBookmarks.setBreadCrumbMaxVisible(2);
        mBookmarks.setBreadCrumbUseBackButton(true);
        mBookmarks.setCallbackListener(this);
        View cancel = findViewById(R.id.cancel);
        if (cancel != null) {
            cancel.setOnClickListener(this);
        }
    }

    // BookmarksPageCallbacks

    @Override
    public boolean onBookmarkSelected(Cursor c, boolean isFolder) {
        if (isFolder) {
            return false;
        }
        Intent intent = BrowserBookmarksPage.createShortcutIntent(this, c);
        setResult(RESULT_OK, intent);
        finish();
        return true;
    }

    @Override
    public boolean onOpenInNewWindow(Cursor c) {
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!mBookmarks.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onFolderChanged(int level, Uri uri) {
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.cancel:
            finish();
            break;
        }
    }
}
