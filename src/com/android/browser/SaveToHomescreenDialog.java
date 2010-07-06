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
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

public class SaveToHomescreenDialog extends Activity {

    private EditText    mTitle;
    private String      mUrl;
    private Bitmap      mFavicon;
    private Bitmap      mTouchIcon;

    private View.OnClickListener mOk = new View.OnClickListener() {
        public void onClick(View v) {
            if (save()) {
                finish();
            }
        }
    };

    private View.OnClickListener mCancel = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.browser_add_bookmark_const_url);
        setTitle(R.string.create_shortcut_bookmark);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                R.drawable.ic_list_bookmark);

        String title = null;
        String url = null;
        Bundle map = getIntent().getExtras();
        if (map != null) {
            title = map.getString("title");
        }

        mUrl = map.getString("url");
        mFavicon = (Bitmap)map.getParcelable("favicon");
        mTouchIcon = (Bitmap)map.getParcelable("touchIcon");

        Bitmap icon = BookmarkUtils.createIcon(this, mTouchIcon, mFavicon,
               BookmarkUtils.BookmarkIconType.ICON_HOME_SHORTCUT);
        getWindow().setFeatureDrawable(Window.FEATURE_LEFT_ICON, new BitmapDrawable(icon));

        mTitle = (EditText) findViewById(R.id.title);
        mTitle.setText(title);

        Button okButton = (Button) findViewById(R.id.OK);
        okButton.setOnClickListener(mOk);

        Button cancelButton = (Button) findViewById(R.id.cancel);
        cancelButton.setOnClickListener(mCancel);

        if (!getWindow().getDecorView().isInTouchMode()) {
            okButton.requestFocus();
        }
    }

    /**
     * Parse the data entered in the dialog and send an intent to create an
     * icon on the homescreen.
     */
    private boolean save() {
        String title = mTitle.getText().toString().trim();
        String unfilteredUrl = BrowserActivity.fixUrl(mUrl);
        if (title.length() == 0) {
            mTitle.setError(getResources().getText(R.string.bookmark_needs_title));
            return false;
        }

        String url = unfilteredUrl.trim();

        sendBroadcast(BookmarkUtils.createAddToHomeIntent(this, url, title,
                mTouchIcon, mFavicon));
        setResult(RESULT_OK);
        return true;
    }
}
