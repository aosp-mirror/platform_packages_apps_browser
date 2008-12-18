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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Gears Shortcut dialog
 */
class GearsShortcutDialog extends GearsBaseDialog {

  private static final String TAG = "GearsPermissionsDialog";

  private final String ICON_16 = "icon16x16";
  private final String ICON_32 = "icon32x32";
  private final String ICON_48 = "icon48x48";
  private final String ICON_128 = "icon128x128";

  public GearsShortcutDialog(Activity activity,
                             Handler handler,
                             String arguments) {
    super (activity, handler, arguments);
  }

  public void setup() {
    inflate(R.layout.gears_dialog_permission, R.id.panel_content);
    setupButtons(R.string.shortcut_button_alwaysdeny,
                 R.string.shortcut_button_allow,
                 R.string.shortcut_button_deny);

    View contentBorder = findViewById(R.id.content_border);
    if (contentBorder != null) {
      contentBorder.setBackgroundResource(R.color.shortcut_border);
    }
    View contentBackground = findViewById(R.id.content_background);
    if (contentBackground != null) {
      contentBackground.setBackgroundResource(R.color.shortcut_background);
    }

    try {
      JSONObject json = new JSONObject(mDialogArguments);

      String iconUrl = pickIconToRender(json);
      if (iconUrl != null) {
        downloadIcon(iconUrl);
      }

      setupDialog();

      setLabel(json, "name", R.id.origin_title);
      setLabel(json, "link", R.id.origin_subtitle);
      setLabel(json, "description", R.id.origin_message);
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception", e);
    }
  }

  public void setupDialog(TextView message, ImageView icon) {
    message.setText(R.string.shortcut_message);
    icon.setImageResource(R.drawable.gears_icon_48x48);
  }

  /**
   * Utility method to validate an icon url. Used in the
   * shortcut dialog.
   */
  boolean validIcon(JSONObject json, String name) {
    try {
      if (json.has(name)) {
        String str = json.getString(name);
        if (str.length() > 0) {
          return true;
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception", e);
    }
    return false;
  }


  /**
   * Utility method to pick the best indicated icon
   * from the dialogs' arguments. Used in the
   * shortcut dialog.
   */
  String pickIconToRender(JSONObject json) {
    try {
      if (validIcon(json, ICON_48)) { // ideal size
        mChoosenIconSize = 48;
        return json.getString(ICON_48);
      } else if (validIcon(json, ICON_32)) {
        mChoosenIconSize = 32;
        return json.getString(ICON_32);
      } else if (validIcon(json, ICON_128)) {
        mChoosenIconSize = 128;
        return json.getString(ICON_128);
      } else if (validIcon(json, ICON_16)) {
        mChoosenIconSize = 16;
        return json.getString(ICON_16);
      }
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception", e);
    }
    mChoosenIconSize = 0;
    return null;
  }

  public String closeDialog(int closingType) {
    String ret = null;
    switch (closingType) {
      case ALWAYS_DENY:
        ret = "{\"allow\": false, \"permanently\": true }";
        break;
      case ALLOW:
        ret = "{\"allow\": true, \"locations\": 0 }";
        break;
      case DENY:
        ret = null;
        break;
    }
    return ret;
  }

}
