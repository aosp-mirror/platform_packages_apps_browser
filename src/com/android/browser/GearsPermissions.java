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

import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

/**
 * The permission mechanism works the following way:
 *
 * PermissionType allows to define a type of permission
 *   (e.g. localStorage/locationData), storing a name and a set of
 *   resource ids corresponding to the GUI resources.
 *
 * Permission defines an actual permission instance, with a type and a value.
 *
 * OriginPermissions holds an origin with a set of Permission objects
 */
class GearsPermissions {

  private static final String TAG = "GearsPermissions";

  /**
   * Defines a type of permission
   *
   * Store the permission's name (used in the json result)
   * Graphically, each permission is a label followed by two radio buttons.
   * We store the resources ids here.
   */
  public static class PermissionType {
    public static final int PERMISSION_NOT_SET = 0;
    public static final int PERMISSION_ALLOWED = 1;
    public static final int PERMISSION_DENIED = 2;

    String mName;
    int mTitleRsc;
    int mSubtitleOnRsc;
    int mSubtitleOffRsc;

    PermissionType(String name) {
      mName = name;
    }

    public void setResources(int titleRsc,
        int subtitleOnRsc, int subtitleOffRsc) {
      mTitleRsc = titleRsc;
      mSubtitleOnRsc = subtitleOnRsc;
      mSubtitleOffRsc = subtitleOffRsc;
    }

    public String getName() {
      return mName;
    }

    public int getTitleRsc() {
      return mTitleRsc;
    }

    public int getSubtitleOnRsc() {
      return mSubtitleOnRsc;
    }

    public int getSubtitleOffRsc() {
      return mSubtitleOffRsc;
    }

  }

  /**
   * Simple class to store an instance of a permission
   *
   * i.e. a permission type and a value
   * Value can be either PERMISSION_NOT_SET,
   * PERMISSION_ALLOWED or PERMISSION_DENIED
   * (defined in PermissionType).
   */
  public static class Permission {
    PermissionType mType;
    int mValue;

    Permission(PermissionType type, int value) {
      mType = type;
      mValue = value;
    }

    Permission(PermissionType type) {
      mType = type;
      mValue = 0;
    }

    public PermissionType getType() {
      return mType;
    }

    public void setValue(int value) {
      mValue = value;
    }

    public int getValue() {
      return mValue;
    }
  }

  /**
   * Interface used by the GearsNativeDialog implementation
   * to listen to changes in the permissions.
   */
  public interface PermissionsChangesListener {
    public boolean setPermission(PermissionType type, int perm);
  }

  /**
   * Holds the model for an origin -- each origin has a set of
   * permissions.
   */
  public static class OriginPermissions {
    HashMap<PermissionType, Permission> mPermissions;
    String mOrigin;
    public static PermissionsChangesListener mListener;

    public static void setListener(PermissionsChangesListener listener) {
      mListener = listener;
    }

    OriginPermissions(String anOrigin) {
      mOrigin = anOrigin;
      mPermissions = new HashMap<PermissionType, Permission>();
    }

    OriginPermissions(OriginPermissions perms) {
      mOrigin = perms.getOrigin();
      mPermissions = new HashMap<PermissionType, Permission>();
      HashMap<PermissionType, Permission> permissions = perms.getPermissions();
      Iterator<PermissionType> iterator = permissions.keySet().iterator();
      while (iterator.hasNext()) {
        Permission permission = permissions.get(iterator.next());
        int value = permission.getValue();
        setPermission(permission.getType(), value);
      }
    }

    public String getOrigin() {
      return mOrigin;
    }

    public HashMap<PermissionType, Permission> getPermissions() {
      return mPermissions;
    }

    public int getPermission(PermissionType type) {
      return mPermissions.get(type).getValue();
    }

    public void setPermission(PermissionType type, int perm) {
      if (mPermissions.get(type) == null) {
        Permission permission = new Permission(type, perm);
        mPermissions.put(type, permission);
        return;
      }

      if (mListener != null) {
        mListener.setPermission(type, perm);
      }

      mPermissions.get(type).setValue(perm);
    }

    public void print() {
      Log.v(TAG, "Permissions for " + mOrigin);
      Iterator<PermissionType> iterator = mPermissions.keySet().iterator();
      while (iterator.hasNext()) {
        Permission permission = mPermissions.get(iterator.next());
        String name = permission.getType().getName();
        int value = permission.getValue();
        Log.v(TAG, "  " + name + ": " + value);
      }
    }
  }

}
