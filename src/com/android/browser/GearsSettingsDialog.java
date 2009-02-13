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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.browser.GearsPermissions.OriginPermissions;
import com.android.browser.GearsPermissions.Permission;
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
  private static final int CONFIRMATION_REMOVE_DIALOG = 1;

  // We declare the permissions globally to simplify the code
  private final PermissionType LOCAL_STORAGE =
      new PermissionType(LOCAL_STORAGE_STRING);
  private final PermissionType LOCATION_DATA =
      new PermissionType(LOCATION_DATA_STRING);

  private boolean mChanges = false;

  SettingsAdapter mListAdapter;

  public GearsSettingsDialog(Activity activity,
                             Handler handler,
                             String arguments) {
    super (activity, handler, arguments);
    activity.setContentView(R.layout.gears_settings);
  }

  public void setup() {
    // First let's add the permissions' resources
    LOCAL_STORAGE.setResources(R.string.settings_storage_title,
                               R.string.settings_storage_subtitle_on,
                               R.string.settings_storage_subtitle_off);
    LOCATION_DATA.setResources(R.string.settings_location_title,
                               R.string.settings_location_subtitle_on,
                               R.string.settings_location_subtitle_off);
    // add the permissions to the list of permissions.
    mPermissions = new Vector<PermissionType>();
    mPermissions.add(LOCAL_STORAGE);
    mPermissions.add(LOCATION_DATA);
    OriginPermissions.setListener(this);


    setupDialog();

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
      mListAdapter = new SettingsAdapter(mActivity, mSitesPermissions);
      list.setAdapter(mListAdapter);
      list.setScrollBarStyle(android.view.View.SCROLLBARS_OUTSIDE_INSET);
      list.setOnItemClickListener(mListAdapter);
    }
    if (mDebug) {
      printPermissions();
    }
  }

  private void setMainTitle() {
    String windowTitle = mActivity.getString(R.string.pref_extras_gears_settings);
    mActivity.setTitle(windowTitle);
  }

  public void setupDialog() {
    setMainTitle();
  }

  /**
   * GearsPermissions.PermissionsChangesListener delegate
   */
  public boolean setPermission(PermissionType type, int perm) {
    if (mChanges == false) {
      mChanges = true;
    }
    return mChanges;
  }

  public boolean handleBackButton() {
    return mListAdapter.backButtonPressed();
  }

  /**
   * We use this to create a confirmation dialog when the user
   * clicks on "remove this site from gears"
   */
  public Dialog onCreateDialog(int id) {
    return new AlertDialog.Builder(mActivity)
        .setTitle(R.string.settings_confirmation_remove_title)
        .setMessage(R.string.settings_confirmation_remove)
        .setPositiveButton(android.R.string.ok,
                           new AlertDialog.OnClickListener() {
          public void onClick(DialogInterface dlg, int which) {
            mListAdapter.removeCurrentSite();
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .create();
  }

  /**
   * Adapter class for the list view in the settings dialog
   *
   * We first display a list of all the origins (sites), or
   * a message saying that no permission is set if the list is empty.
   * When the user click on one of the origin, we then display
   * the list of the permissions existing for that origin.
   * Each permission can be either allowed or denied by clicking
   * on the checkbox.
   * The last row is a special case, allowing to remove the entire origin.
   */
  class SettingsAdapter extends BaseAdapter
      implements AdapterView.OnItemClickListener {
    private Activity mContext;
    private List mItems;
    private OriginPermissions mCurrentSite;
    private Vector mCurrentPermissions;
    private int MAX_ROW_HEIGHT = 64;

    SettingsAdapter(Activity context, List items) {
      mContext = context;
      mItems = items;
      mCurrentSite = null;
    }

    public int getCount() {
      if (mCurrentSite == null) {
        int size = mItems.size();
        if (size == 0) {
          return 1;
        } else {
          return size;
        }
      }
      return mCurrentPermissions.size() + 1;
    }

    public long getItemId(int position) {
      return position;
    }

    private String shortName(String url) {
        // We remove the http and https prefix
        if (url.startsWith("http://")) {
          return url.substring(7);
        }
        if (url.startsWith("https://")) {
          return url.substring(8);
        }
        return url;
    }

    public Object getItem(int position) {
      if (mCurrentSite == null) {
        if (mItems.size() == 0) {
          return null;
        } else {
          return mItems.get(position);
        }
      }
      return mCurrentPermissions.get(position);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      View row = convertView;
      if (row == null) { // no cached view, we create one
        LayoutInflater inflater = (LayoutInflater) getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        row = inflater.inflate(R.layout.gears_settings_row, null);
      }
      row.setMinimumHeight(MAX_ROW_HEIGHT);

      if (mCurrentSite == null) {
        if (mItems.size() == 0) {
          hideView(row, R.id.title);
          hideView(row, R.id.subtitle);
          hideView(row, R.id.checkbox);
          hideView(row, R.id.icon);
          setText(row, R.id.info, R.string.settings_empty);
        } else {
          hideView(row, R.id.subtitle);
          hideView(row, R.id.info);
          hideView(row, R.id.checkbox);
          OriginPermissions perms = (OriginPermissions) mItems.get(position);
          setText(row, R.id.title, shortName(perms.getOrigin()));
          showView(row, R.id.icon);
        }
      } else {
        if (position == getCount() - 1) {
          // last position: "remove this site from gears"
          hideView(row, R.id.subtitle);
          hideView(row, R.id.info);
          hideView(row, R.id.checkbox);
          hideView(row, R.id.icon);
          setText(row, R.id.title, R.string.settings_remove_site);
        } else {
          hideView(row, R.id.info);
          hideView(row, R.id.icon);
          showView(row, R.id.checkbox);

          PermissionType type =
              (PermissionType) mCurrentPermissions.get(position);
          setText(row, R.id.title, type.getTitleRsc());

          View checkboxView = row.findViewById(R.id.checkbox);
          if (checkboxView != null) {
            CheckBox checkbox = (CheckBox) checkboxView;
            int perm = mCurrentSite.getPermission(type);
            if (perm == PermissionType.PERMISSION_DENIED) {
              setText(row, R.id.subtitle, type.getSubtitleOffRsc());
              checkbox.setChecked(false);
            } else {
              setText(row, R.id.subtitle, type.getSubtitleOnRsc());
              checkbox.setChecked(true);
            }
          }
        }
      }
      return row;
    }

    public void removeCurrentSite() {
      mCurrentSite.setPermission(LOCAL_STORAGE,
                                 PermissionType.PERMISSION_NOT_SET);
      mCurrentSite.setPermission(LOCATION_DATA,
                                 PermissionType.PERMISSION_NOT_SET);
      mSitesPermissions.remove(mCurrentSite);
      mCurrentSite = null;
      setMainTitle();
      notifyDataSetChanged();
    }

    public void onItemClick(AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
      if (mItems.size() == 0) {
        return;
      }
      if (mCurrentSite == null) {
         mCurrentSite = (OriginPermissions) mItems.get(position);
         mCurrentPermissions = new Vector();
         for (int i = 0; i < mPermissions.size(); i++) {
           PermissionType type = mPermissions.get(i);
           int perm = mCurrentSite.getPermission(type);
           if (perm != PermissionType.PERMISSION_NOT_SET) {
             mCurrentPermissions.add(type);
           }
         }
         mContext.setTitle(shortName(mCurrentSite.getOrigin()));
      } else {
        if (position == getCount() - 1) { // last item (remove site)
          // Ask the user to confirm
          // If yes, removeCurrentSite() will be called via the dialog callback.
          mActivity.showDialog(CONFIRMATION_REMOVE_DIALOG);
        } else {
          PermissionType type =
              (PermissionType) mCurrentPermissions.get(position);
          if (mCurrentSite.getPermission(type) ==
              PermissionType.PERMISSION_ALLOWED) {
            mCurrentSite.setPermission(type, PermissionType.PERMISSION_DENIED);
          } else {
            mCurrentSite.setPermission(type, PermissionType.PERMISSION_ALLOWED);
          }
        }
      }
      notifyDataSetChanged();
    }

    public boolean backButtonPressed() {
      if (mCurrentSite != null) { // we intercept the back button
        mCurrentSite = null;
        setMainTitle();
        notifyDataSetChanged();
        return true;
      }
      return false;
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
    String ret = computeDiff(mChanges);

    if (mDebug) {
      printPermissions();
    }

    return ret;
  }

}
