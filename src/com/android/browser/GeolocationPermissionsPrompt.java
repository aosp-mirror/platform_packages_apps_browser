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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.GeolocationPermissions;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class GeolocationPermissionsPrompt extends LinearLayout {
    private LinearLayout mInner;
    private TextView mMessage;
    private Button mShareButton;
    private Button mDontShareButton;
    private CheckBox mRemember;
    private GeolocationPermissions.Callback mCallback;
    private String mOrigin;

    public GeolocationPermissionsPrompt(Context context) {
        this(context, null);
    }

    public GeolocationPermissionsPrompt(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.geolocation_permissions_prompt, this);

        mInner = (LinearLayout) findViewById(R.id.inner);
        mMessage = (TextView) findViewById(R.id.message);
        mShareButton = (Button) findViewById(R.id.share_button);
        mDontShareButton = (Button) findViewById(R.id.dont_share_button);
        mRemember = (CheckBox) findViewById(R.id.remember);
        setButtonClickListeners();
    }

    /**
     * Shows the prompt for the given origin. When the user clicks on one of
     * the buttons, the supplied callback is be called.
     */
    public void show(String origin, GeolocationPermissions.Callback callback) {
        mOrigin = origin;
        mCallback = callback;
        Uri uri = Uri.parse(mOrigin);
        setMessage("http".equals(uri.getScheme()) ?  mOrigin.substring(7) : mOrigin);
        // The checkbox should always be intially checked.
        mRemember.setChecked(true);
        showDialog(true);
    }

    /**
     * Hides the prompt.
     */
    public void hide() {
        showDialog(false);
    }

    /**
     * Sets the on click listeners for the buttons.
     */
    private void setButtonClickListeners() {
        final GeolocationPermissionsPrompt me = this;
        mShareButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                me.handleButtonClick(true);
            }
        });
        mDontShareButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                me.handleButtonClick(false);
            }
        });
    }

    /**
     * Handles a click on one the buttons by invoking the callback.
     */
    private void handleButtonClick(boolean allow) {
        boolean remember = mRemember.isChecked();
        showDialog(false);
        mCallback.invoke(mOrigin, allow, remember);
    }

    /**
     * Sets the prompt's message.
     */
    private void setMessage(CharSequence origin) {
        mMessage.setText(String.format(
            getResources().getString(R.string.geolocation_permissions_prompt_message),
            origin));
    }

    /**
     * Shows or hides the prompt.
     */
    private void showDialog(boolean shown) {
        mInner.setVisibility(shown ? View.VISIBLE : View.GONE);
    }
}
