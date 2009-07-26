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
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Toast;

import android.webkit.gears.NativeDialog;

import com.android.browser.GearsBaseDialog;
import com.android.browser.GearsPermissionsDialog;
import com.android.browser.GearsSettingsDialog;

/**
 * Native dialog Activity used by gears
 * TODO: rename in GearsNativeDialogActivity
 * @hide
 */
public class GearsNativeDialog extends Activity {

  private static final String TAG = "GearsNativeDialog";

  private String mDialogArguments;

  private String mGearsVersion = null;

  private boolean mDebug = false;

  private int mDialogType;
  private final int SETTINGS_DIALOG = 1;
  private final int PERMISSION_DIALOG = 2;
  private final int LOCATION_DIALOG = 3;

  private final String VERSION_STRING = "version";
  private final String SETTINGS_DIALOG_STRING = "settings_dialog";
  private final String PERMISSION_DIALOG_STRING = "permissions_dialog";
  private final String LOCATION_DIALOG_STRING = "locations_dialog";

  private boolean mDialogDismissed = false;

  GearsBaseDialog dialog;

  // Handler for callbacks to the UI thread
  final Handler mHandler = new Handler() {
    public void handleMessage(Message msg) {
      if (msg.what == GearsBaseDialog.NEW_ICON) {
        BaseAdapter adapter = (BaseAdapter) msg.obj;
        adapter.notifyDataSetChanged();
      } else if (msg.what == GearsBaseDialog.UPDATE_ICON) {
        dialog.updateIcon();
      } else if (msg.what == GearsBaseDialog.ALWAYS_DENY) {
        closeDialog(GearsBaseDialog.ALWAYS_DENY);
      } else if (msg.what == GearsBaseDialog.ALLOW) {
        closeDialog(GearsBaseDialog.ALLOW);
      } else if (msg.what == GearsBaseDialog.DENY) {
        closeDialog(GearsBaseDialog.DENY);
      }
      super.handleMessage(msg);
    }
  };

  @Override
  public void onCreate(Bundle icicle) {
    getArguments();
    if (mDialogType == SETTINGS_DIALOG) {
      setTheme(android.R.style.Theme);
    }
    super.onCreate(icicle);
    if (mDialogType != SETTINGS_DIALOG) {
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      setContentView(R.layout.gears_dialog);
    }

    switch (mDialogType) {
      case SETTINGS_DIALOG:
        dialog = new GearsSettingsDialog(this, mHandler, mDialogArguments);
        dialog.setGearsVersion(mGearsVersion);
        break;
      case PERMISSION_DIALOG:
        dialog = new GearsPermissionsDialog(this, mHandler, mDialogArguments);
        break;
      case LOCATION_DIALOG:
        dialog = new GearsPermissionsDialog(this, mHandler, mDialogArguments);
        break;
      default:
        dialog = new GearsBaseDialog(this, mHandler, mDialogArguments);
    }
    dialog.setDebug(mDebug);
    dialog.setup();
  }

  /**
   * Get the arguments for the dialog
   *
   * The dialog needs a json string as an argument, as
   * well as a dialogType. In debug mode the arguments
   * are mocked.
   */
  private void getArguments() {
    if (mDebug) {
      mDialogType = LOCATION_DIALOG +1;
      mockArguments();

      return;
    }

    Intent intent = getIntent();
    mDialogArguments = intent.getStringExtra("dialogArguments");
    String dialogTypeString = intent.getStringExtra("dialogType");
    if (dialogTypeString == null) {
      return;
    }

    if (Browser.LOGV_ENABLED) {
      Log.v(TAG, "dialogtype: " + dialogTypeString);
    }

    if (dialogTypeString.equalsIgnoreCase(SETTINGS_DIALOG_STRING)) {
      mDialogType = SETTINGS_DIALOG;
      mGearsVersion = intent.getStringExtra(VERSION_STRING);
    } else if (dialogTypeString.equalsIgnoreCase(PERMISSION_DIALOG_STRING)) {
      mDialogType = PERMISSION_DIALOG;
    } else if (dialogTypeString.equalsIgnoreCase(LOCATION_DIALOG_STRING)) {
      mDialogType = LOCATION_DIALOG;
    }
  }

  /**
   * Utility method for debugging the dialog.
   *
   * Set mock arguments.
   */
  private void mockArguments() {

    String argumentsPermissions = "{ locale: \"en-US\", "
        + "origin: \"http://www.google.com\", dialogType: \"localData\","
        + "customIcon: \"http://google-gears.googlecode.com/"
        + "svn/trunk/gears/test/manual/shortcuts/32.png\","
        + "customName: \"My Application\","
        + "customMessage: \"Press the button to enable my "
        + "application to run offline!\" };";

    String argumentsPermissions2 = "{ locale: \"en-US\", "
        + "origin: \"http://www.google.com\", dialogType: \"localData\" };";

    String argumentsLocation = "{ locale: \"en-US\", "
        + "origin: \"http://www.google.com\", dialogType: \"locationData\","
        + "customIcon: \"http://google-gears.googlecode.com/"
        + "svn/trunk/gears/test/manual/shortcuts/32.png\","
        + "customName: \"My Application\","
        + "customMessage: \"Press the button to enable my "
        + "application to run offline!\" };";

    String argumentsSettings = "{ locale: \"en-US\", permissions: [ { "
        + "name: \"http://www.google.com\", "
        + "localStorage: { permissionState: 0 }, "
        + "locationData: { permissionState: 1 } }, "
        + "{ name: \"http://www.aaronboodman.com\", "
        + "localStorage: { permissionState: 1 }, "
        + "locationData: { permissionState: 2 } }, "
        + "{ name: \"http://www.evil.org\", "
        + "localStorage: { permissionState: 2 }, "
        + "locationData: { permissionState: 2 } } ] }";

    switch (mDialogType) {
      case PERMISSION_DIALOG:
        mDialogArguments = argumentsPermissions;
        break;
      case LOCATION_DIALOG:
        mDialogArguments = argumentsLocation;
        break;
      case SETTINGS_DIALOG:
        mDialogArguments = argumentsSettings;
        break;
    }
  }

  /**
   * Close the dialog and set the return string value.
   */
  private void closeDialog(int closingType) {
    String ret = dialog.closeDialog(closingType);

    if (mDebug) {
      Log.v(TAG, "closeDialog ret value: " + ret);
    }

    NativeDialog.closeDialog(ret);
    notifyEndOfDialog();
    finish();

    // If the dialog sets a notification, we display it.
    int notification = dialog.notification();
    if (notification != 0) {
      Toast toast = Toast.makeText(this, notification, Toast.LENGTH_LONG);
      toast.setGravity(Gravity.BOTTOM, 0, 0);
      toast.show();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    // In case we reach this point without
    // notifying NativeDialog, we do it now.
    if (!mDialogDismissed) {
      notifyEndOfDialog();
    }
  }

  @Override
  public void onPause(){
    super.onPause();
    if (!mDialogDismissed) {
      closeDialog(GearsBaseDialog.CANCEL);
    }
  }

  /**
   * Signal to NativeDialog that we are done.
   */
  private void notifyEndOfDialog() {
    NativeDialog.signalFinishedDialog();
    mDialogDismissed = true;
  }

  /**
   * Intercepts the back key to immediately notify
   * NativeDialog that we are done.
   */
  public boolean dispatchKeyEvent(KeyEvent event) {
    if ((event.getKeyCode() == KeyEvent.KEYCODE_BACK)
      && (event.getAction() == KeyEvent.ACTION_DOWN)) {
      if (!dialog.handleBackButton()) {
        // if the dialog doesn't do anything with the back button
        closeDialog(GearsBaseDialog.CANCEL);
      }
      return true; // event consumed
    }
    return super.dispatchKeyEvent(event);
  }

  /**
   * If the dialog call showDialog() on ourself, we let
   * it handle the creation of this secondary dialog.
   * It is used in GearsSettingsDialog, to create the confirmation
   * dialog when the user click on "Remove this site from Gears"
   */
  @Override
  protected Dialog onCreateDialog(int id) {
    return dialog.onCreateDialog(id);
  }

}
