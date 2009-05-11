/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Permission dialog for HTML5
 * @hide
 */
public class PermissionDialog extends Activity {

    private static final String TAG = "PermissionDialog";
    public static final String PARAM_ORIGIN = "origin";
    public static final String PARAM_QUOTA = "quota";

    private String mWebStorageOrigin;
    private long mWebStorageQuota = 0;
    private int mNotification = 0;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getParameters();
        setupDialog();
    }

    private void getParameters() {
        Intent intent = getIntent();
        mWebStorageOrigin = intent.getStringExtra(PARAM_ORIGIN);
        mWebStorageQuota = intent.getLongExtra(PARAM_QUOTA, 0);
    }

    private void setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.permission_dialog);

        setIcon(R.id.icon, android.R.drawable.ic_popup_disk_full);
        setText(R.id.dialog_title, R.string.query_storage_quota_prompt);
        setText(R.id.dialog_message, R.string.query_storage_quota_message);
        setCharSequence(R.id.origin, mWebStorageOrigin);

        setupButton(R.id.button_allow, R.string.permission_button_allow,
            new View.OnClickListener() {
                public void onClick(View v) { allow(); }
            });
        setupButton(R.id.button_alwaysdeny, R.string.permission_button_alwaysdeny,
            new View.OnClickListener() {
                public void onClick(View v) { alwaysdeny(); }
            });
        setupButton(R.id.button_deny, R.string.permission_button_deny,
            new View.OnClickListener() {
                public void onClick(View v) { deny(); }
            });
    }

    private void setText(int viewID, int stringID) {
        setCharSequence(viewID, getString(stringID));
    }

    private void setCharSequence(int viewID, CharSequence string) {
        View view = findViewById(viewID);
        if (view == null) {
            return;
        }
        view.setVisibility(View.VISIBLE);
        TextView textView = (TextView) view;
        textView.setText(string);
    }

    private void setIcon(int viewID, int imageID) {
        View view = findViewById(viewID);
        if (view == null) {
            return;
        }
        view.setVisibility(View.VISIBLE);
        ImageView icon = (ImageView) view;
        icon.setImageResource(imageID);
    }

    private void setupButton(int viewID, int stringID,
                             View.OnClickListener listener) {
        View view = findViewById(viewID);
        if (view == null) {
            return;
        }
        setText(viewID, stringID);
        view.setOnClickListener(listener);
    }

    private void useNextQuota() {
        CharSequence[] values = getResources().getTextArray(
            R.array.webstorage_quota_entries_values);
        for (int i=0; i<values.length; i++) {
            long value = Long.parseLong(values[i].toString());
            value *= (1024 * 1024); // the string array is expressed in MB
            if (value > mWebStorageQuota) {
                mWebStorageQuota = value;
                break;
            }
        }
    }

    private void allow() {
        // If somehow there is no "next quota" in the ladder,
        // we'll add 1MB anyway.
        mWebStorageQuota += 1024*1024;
        useNextQuota();
        mNotification = R.string.webstorage_notification;
        closeDialog();
    }

    private void alwaysdeny() {
        // Setting the quota to 0 will prevent any new data to be
        // added, but the existing data will not be deleted.
        mWebStorageQuota = 0;
        mNotification = R.string.webstorage_notification;
        closeDialog();
    }

    private void deny() {
        closeDialog();
    }

    private void closeDialog() {
        Intent intent = new Intent();
        intent.putExtra(PARAM_QUOTA, mWebStorageQuota);
        setResult(RESULT_OK, intent);
        showToast();
        finish();
    }

    private void showToast() {
        if (mNotification != 0) {
            Toast toast = Toast.makeText(this, mNotification, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            toast.show();
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK)
              && (event.getAction() == KeyEvent.ACTION_DOWN)) {
            closeDialog();
            return true; // event consumed
        }
        return super.dispatchKeyEvent(event);
    }

}
