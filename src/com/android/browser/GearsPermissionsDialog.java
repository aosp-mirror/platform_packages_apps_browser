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

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Gears permission dialog
 */
class GearsPermissionsDialog extends GearsBaseDialog {

  private static final String TAG = "GearsPermissionsDialog";

  private String mDialogType;

  public GearsPermissionsDialog(Activity activity,
                                Handler handler,
                                String arguments) {
    super (activity, handler, arguments);
  }

  public void setup() {
    inflate(R.layout.gears_dialog_permission, R.id.panel_content);
    setupButtons(R.string.permission_button_alwaysdeny,
                 R.string.permission_button_allow,
                 R.string.permission_button_deny);

    View contentBorder = findViewById(R.id.content_border);
    if (contentBorder != null) {
      contentBorder.setBackgroundResource(R.color.permission_border);
    }
    View contentBackground = findViewById(R.id.content_background);
    if (contentBackground != null) {
      contentBackground.setBackgroundResource(R.color.permission_background);
    }

    try {
      JSONObject json = new JSONObject(mDialogArguments);

      if (json.has("dialogType")) {
        mDialogType = json.getString("dialogType");
        setupDialog();
      }

      if (!json.has("customName")) {
        setLabel(json, "origin", R.id.origin_title);
        View titleView = findViewById(R.id.origin_title);
        if (titleView != null) {
          TextView title = (TextView) titleView;
          title.setGravity(Gravity.CENTER);
        }
      } else {
        setLabel(json, "customName", R.id.origin_title);
        setLabel(json, "origin", R.id.origin_subtitle);
        setLabel(json, "customMessage", R.id.origin_message);
      }

      if (json.has("customIcon")) {
        String iconUrl = json.getString("customIcon");
        mChoosenIconSize = 32;
        downloadIcon(iconUrl);
      }

    } catch (JSONException e) {
      Log.e(TAG, "JSON exception ", e);
    }
  }

  public void setupDialog(TextView message, ImageView icon) {
    if (mDialogType.equalsIgnoreCase(LOCAL_DATA_STRING)) {
      message.setText(R.string.query_data_message);
      icon.setImageResource(R.drawable.gears_local_data);
    } else if (mDialogType.equalsIgnoreCase(LOCATION_DATA_STRING)) {
      message.setText(R.string.location_message);
      icon.setImageResource(R.drawable.gears_location_data);
      View privacyPolicyLabel = findViewById(R.id.privacy_policy_label);
      if (privacyPolicyLabel != null) {
        privacyPolicyLabel.setVisibility(View.VISIBLE);
      }
    }
  }

  public String closeDialog(int closingType) {
    String ret = null;
    switch (closingType) {
      case ALWAYS_DENY:
        ret = "{\"allow\": false, \"permanently\": true }";
        break;
      case ALLOW:
        ret = "{\"allow\": true, \"permanently\": true }";
        break;
      case DENY:
        ret = "{\"allow\": false, \"permanently\": false }";
        break;
    }
    return ret;
  }

}
