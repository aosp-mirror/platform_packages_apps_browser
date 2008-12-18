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
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.browser.GearsPermissions.OriginPermissions;
import com.android.browser.GearsPermissions.PermissionsChangesListener;
import com.android.browser.GearsPermissions.PermissionType;

import java.util.Vector;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Gears Settings dialog
 */
class GearsSettingsDialog extends GearsBaseDialog
    implements PermissionsChangesListener {

  private static final String TAG = "GearsPermissionsDialog";
  private Vector<OriginPermissions> mSitesPermissions = null;
  private Vector<OriginPermissions> mOriginalPermissions = null;
  private Vector<OriginPermissions> mCurrentPermissions = null;

  private Vector<PermissionType> mPermissions;

  // We declare the permissions globally to simplify the code
  private final PermissionType LOCAL_STORAGE =
      new PermissionType(LOCAL_STORAGE_STRING);
  private final PermissionType LOCATION_DATA =
      new PermissionType(LOCATION_DATA_STRING);

  private boolean mChanges = false;


  public GearsSettingsDialog(Activity activity,
                             Handler handler,
                             String arguments) {
    super (activity, handler, arguments);
  }

  public void setup() {
    // First let's add the permissions' resources
    LOCAL_STORAGE.setResources(R.id.local_storage_choice,
       R.id.local_storage_allowed,
       R.id.local_storage_denied);

    LOCATION_DATA.setResources(R.id.location_data_choice,
       R.id.location_data_allowed,
       R.id.location_data_denied);

    // add the permissions to the list of permissions.
    mPermissions = new Vector<PermissionType>();
    mPermissions.add(LOCAL_STORAGE);
    mPermissions.add(LOCATION_DATA);
    OriginPermissions.setListener(this);


    inflate(R.layout.gears_dialog_settings, R.id.panel_content);
    setupDialog();
    setupButtons(0,
                 R.string.settings_button_allow,
                 R.string.settings_button_deny);

    // by default disable the allow button (it will get enabled if
    // something is changed by the user)
    View buttonView = findViewById(R.id.button_allow);
    if (buttonView != null) {
      Button button = (Button) buttonView;
      button.setEnabled(false);
    }

    View gearsVersionView = findViewById(R.id.gears_version);
    if (gearsVersionView != null) {
      TextView gearsVersion = (TextView) gearsVersionView;
      gearsVersion.setText(mGearsVersion);
    }

    // We manage the permissions using three vectors, mSitesPermissions,
    // mOriginalPermissions and mCurrentPermissions.
    // The dialog's arguments are parsed and a list of permissions is
    // generated and stored in those three vectors.
    // mOriginalPermissions is a separate copy and will not be modified;
    // mSitesPermissions contains the current permissions _only_ --
    // if an origin is removed, it is also removed from mSitesPermissions.
    // Finally, mCurrentPermissions contains the current permissions and
    // is a clone of mSitesPermissions, but removed sites aren't removed,
    // their permissions are simply set to PERMISSION_NOT_SET. This
    // allows us to easily generate the final difference between the
    // original permissions and the final permissions, while directly
    // using mSitesPermissions for the listView adapter (SettingsAdapter).

    mSitesPermissions = new Vector<OriginPermissions>();
    mOriginalPermissions = new Vector<OriginPermissions>();

    try {
      JSONObject json = new JSONObject(mDialogArguments);
      if (json.has("permissions")) {
        JSONArray jsonArray = json.getJSONArray("permissions");
        for (int i = 0; i < jsonArray.length(); i++) {
          JSONObject infos = jsonArray.getJSONObject(i);
          String name = null;
          int localStorage = PermissionType.PERMISSION_NOT_SET;
          int locationData = PermissionType.PERMISSION_NOT_SET;
          if (infos.has("name")) {
            name = infos.getString("name");
          }
          if (infos.has(LOCAL_STORAGE_STRING)) {
            JSONObject perm = infos.getJSONObject(LOCAL_STORAGE_STRING);
            if (perm.has("permissionState")) {
              localStorage = perm.getInt("permissionState");
            }
          }
          if (infos.has(LOCATION_DATA_STRING)) {
            JSONObject perm = infos.getJSONObject(LOCATION_DATA_STRING);
            if (perm.has("permissionState")) {
              locationData = perm.getInt("permissionState");
            }
          }
          OriginPermissions perms = new OriginPermissions(name);
          perms.setPermission(LOCAL_STORAGE, localStorage);
          perms.setPermission(LOCATION_DATA, locationData);

          mSitesPermissions.add(perms);
          mOriginalPermissions.add(new OriginPermissions(perms));
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception ", e);
    }
    mCurrentPermissions = (Vector<OriginPermissions>)mSitesPermissions.clone();

    View listView = findViewById(R.id.sites_list);
    if (listView != null) {
      ListView list = (ListView) listView;
      list.setAdapter(new SettingsAdapter(mActivity, mSitesPermissions));
    }
    if (mDebug) {
      printPermissions();
    }
  }

  public void setupDialog() {
    View dialogTitleView = findViewById(R.id.dialog_title);
    if (dialogTitleView != null) {
      TextView dialogTitle = (TextView) dialogTitleView;
      dialogTitle.setText(R.string.settings_title);
      dialogTitle.setVisibility(View.VISIBLE);
    }
    View dialogSubtitleView = findViewById(R.id.dialog_subtitle);
    if (dialogSubtitleView != null) {
      TextView dialogSubtitle = (TextView) dialogSubtitleView;
      dialogSubtitle.setText(R.string.settings_message);
      dialogSubtitle.setVisibility(View.VISIBLE);
    }
    View iconView = findViewById(R.id.icon);
    if (iconView != null) {
      ImageView icon = (ImageView) iconView;
      icon.setImageResource(R.drawable.gears_icon_32x32);
    }
  }

  /**
   * GearsPermissions.PermissionsChangesListener delegate
   */
  public boolean setPermission(PermissionType type, int perm) {
    if (mChanges == false) {
      signalChanges();
    }
    return mChanges;
  }

  /**
   * Controller class for binding the model (OriginPermissions) with
   * the UI.
   */
  class PermissionController {
    final static int ALLOWED_BUTTON = 1;
    final static int DENIED_BUTTON = 2;
    private int mButtonType;
    private PermissionType mPermissionType;
    private OriginPermissions mPermissions;

    PermissionController(PermissionType permissionType, int buttonType,
        OriginPermissions permissions) {
      mPermissionType = permissionType;
      mButtonType = buttonType;
      mPermissions = permissions;
    }

    public boolean isChecked() {
      boolean checked = false;

      switch (mButtonType) {
        case ALLOWED_BUTTON:
          if (mPermissions.getPermission(mPermissionType) ==
              PermissionType.PERMISSION_ALLOWED) {
            checked = true;
          } break;
        case DENIED_BUTTON:
          if (mPermissions.getPermission(mPermissionType) ==
              PermissionType.PERMISSION_DENIED) {
            checked = true;
          }
      }
      return checked;
    }

    public String print() {
        return printType() + " for " + mPermissions.getOrigin();
    }

    private String printType() {
      switch (mButtonType) {
        case ALLOWED_BUTTON:
          return "ALLOWED_BUTTON";
        case DENIED_BUTTON:
          return "DENIED_BUTTON";
      }
      return "UNKNOWN BUTTON";
    }

    public void changed(boolean isChecked) {
      if (isChecked == isChecked()) {
        return; // already set
      }

      switch (mButtonType) {
        case ALLOWED_BUTTON:
          mPermissions.setPermission(mPermissionType,
              PermissionType.PERMISSION_ALLOWED);
          break;
        case DENIED_BUTTON:
          mPermissions.setPermission(mPermissionType,
              PermissionType.PERMISSION_DENIED);
          break;
      }
    }
  }



  /**
   * Adapter class for the list view in the settings dialog
   *
   * Every row in the settings dialog display the permissions
   * for a given origin. For every type of permission
   * (location, local data...) there is two radio buttons to
   * authorize or deny the permission.
   * A remove button is also present to let the user remove
   * all the authorization of an origin in one step.
   */
  class SettingsAdapter extends ArrayAdapter {
    private Activity mContext;
    private List mItems;

    SettingsAdapter(Activity context, List items) {
      super(context, R.layout.gears_dialog_settings_row, items);
      mContext = context;
      mItems = items;
    }

    /*
     * setup the necessary listeners for the radiobuttons
     * When the buttons are clicked the permissions change.
     */
    private void createAndSetButtonListener(View buttonView,
        OriginPermissions perms, PermissionType permissionType,
        int buttonType) {
      if (buttonView == null) {
        return;
      }
      RadioButton button = (RadioButton) buttonView;

      button.setOnCheckedChangeListener(null);
      PermissionController p = new PermissionController(permissionType,
          buttonType, perms);
      button.setTag(p);

      CompoundButton.OnCheckedChangeListener listener =
          new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView,
            boolean isChecked) {
          PermissionController perm = (PermissionController)buttonView.getTag();
          perm.changed(isChecked);
        }
      };

      button.setOnCheckedChangeListener(listener);

      if (p.isChecked() != button.isChecked()) {
        button.setChecked(p.isChecked());
      }
    }

    /*
     * setup the remove button for an origin: each row has a global
     * remove button in addition to the radio buttons controlling the
     * permissions.
     */
    private void setRemoveButton(Button button, OriginPermissions perms) {
      Button.OnClickListener listener = new Button.OnClickListener() {
        public void onClick(View buttonView) {
          if (mChanges == false) {
            signalChanges();
          }
          OriginPermissions perm = (OriginPermissions) buttonView.getTag();
          perm.setPermission(LOCAL_STORAGE, PermissionType.PERMISSION_NOT_SET);
          perm.setPermission(LOCATION_DATA, PermissionType.PERMISSION_NOT_SET);
          mSitesPermissions.remove(perm);

          View view = findViewById(R.id.sites_list);
          if (view != null) {
            ListView listView = (ListView) view;
            ListAdapter listAdapter = listView.getAdapter();
            if (listAdapter != null) {
              SettingsAdapter settingsAdapter = (SettingsAdapter) listAdapter;
              settingsAdapter.notifyDataSetChanged();
            }
          }
        }
      };
      button.setTag(perms);
      button.setOnClickListener(listener);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      View row = convertView;
      if (row == null) { // no cached view, we create one
        LayoutInflater inflater = (LayoutInflater) getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        row = inflater.inflate(R.layout.gears_dialog_settings_row, null);
      }

      OriginPermissions perms = (OriginPermissions) mItems.get(position);

      View nameView = row.findViewById(R.id.origin_name);
      if (nameView != null) {
        TextView originName = (TextView) nameView;
        originName.setText(perms.getOrigin());
      }

      View removeButtonView = row.findViewById(R.id.origin_remove);
      if (removeButtonView != null) {
        Button removeButton = (Button) removeButtonView;
        setRemoveButton(removeButton, perms);
      }

      for (int i = 0; i < mPermissions.size(); i++) {
        PermissionType type = mPermissions.get(i);
        int rowRsc = type.getRowRsc();
        int allowedButtonRsc = type.getAllowedButtonRsc();
        int deniedButtonRsc = type.getDeniedButtonRsc();

        View rowView = row.findViewById(rowRsc);
        if (rowView != null) {
          int perm = perms.getPermission(type);
          if (perm != PermissionType.PERMISSION_NOT_SET) {
            createAndSetButtonListener(row.findViewById(allowedButtonRsc),
                perms, type, PermissionController.ALLOWED_BUTTON);
            createAndSetButtonListener(row.findViewById(deniedButtonRsc),
                perms, type, PermissionController.DENIED_BUTTON);
            rowView.setVisibility(View.VISIBLE);
          } else {
            rowView.setVisibility(View.GONE);
          }
        }
      }

      return row;
    }
  }

  /**
   * Utility method used in debug mode to print the list of
   * permissions (original values and current values).
   */
  public void printPermissions() {
    Log.v(TAG, "Original Permissions: ");
    for (int i = 0; i < mOriginalPermissions.size(); i++) {
      OriginPermissions p = mOriginalPermissions.get(i);
      p.print();
    }
    Log.v(TAG, "Current Permissions: ");
    for (int i = 0; i < mSitesPermissions.size(); i++) {
      OriginPermissions p = mSitesPermissions.get(i);
      p.print();
    }
  }

  /**
   * Utility method used by the settings dialog, signaling
   * the user the settings have been modified.
   * We reflect this by enabling the Allow button (disabled
   * by default).
   */
  public void signalChanges() {
    View view = findViewById(R.id.button_allow);
    if (view != null) {
      Button button = (Button) view;
      button.setEnabled(true);
    }
    mChanges = true;
  }

  /**
   * Computes the difference between the original permissions and the
   * current ones. Returns a json-formatted string.
   * It is used by the Settings dialog.
   */
  public String computeDiff(boolean modif) {
    String ret = null;
    try {
      JSONObject results = new JSONObject();
      JSONArray permissions = new JSONArray();

      for (int i = 0; modif && i < mOriginalPermissions.size(); i++) {
        OriginPermissions original = mOriginalPermissions.get(i);
        OriginPermissions current = mCurrentPermissions.get(i);
        JSONObject permission = new JSONObject();
        boolean modifications = false;

        for (int j = 0; j < mPermissions.size(); j++) {
          PermissionType type = mPermissions.get(j);

          if (current.getPermission(type) != original.getPermission(type)) {
            JSONObject state = new JSONObject();
            state.put("permissionState", current.getPermission(type));
            permission.put(type.getName(), state);
            modifications = true;
          }
        }

        if (modifications) {
          permission.put("name", current.getOrigin());
          permissions.put(permission);
        }
      }
      results.put("modifiedOrigins", permissions);
      ret = results.toString();
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception ", e);
    }
    return ret;
  }

  public String closeDialog(int closingType) {
    String ret = null;
    switch (closingType) {
      case ALWAYS_DENY:
        ret = "{\"allow\": false }";
        break;
      case ALLOW:
        ret = computeDiff(true);
        break;
      case DENY:
        ret = computeDiff(false);
        break;
    }

    if (mDebug) {
      printPermissions();
    }

    return ret;
  }

}
