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

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;

/* Mocked MenuItem */
public class TestItem implements MenuItem {

    int itemId;

    /*
     * The codes used as inparams to TestItem (itemId) comes from
     * BrowserBookmarksPage.onContextItemSelected()
     */
    public TestItem(int itemId) {
        this.itemId = itemId;
    }

    public int getItemId() {
        return itemId;
    }

    public ContextMenuInfo getMenuInfo() {
        int position = 0;
        int id = 0; //Not important as we always return 'itemId' in getItemId().
        AdapterContextMenuInfo menuInfo = new AdapterContextMenuInfo(null, position, id);
        return menuInfo;
    }

    /******************************************************/
    /* The rest of the methods aren't used at the moment. */

    public char getAlphabeticShortcut() {
        return 0;
    }

    public int getGroupId() {
        return 0;
    }

    public Drawable getIcon() {
        return null;
    }

    public Intent getIntent() {
        return null;
    }

    public char getNumericShortcut() {
        return 0;
    }

    public int getOrder() {
        return 0;
    }

    public SubMenu getSubMenu() {
        return null;
    }

    public CharSequence getTitle() {
        return null;
    }

    public CharSequence getTitleCondensed() {
        return null;
    }

    public boolean hasSubMenu() {
        return false;
    }

    public boolean isCheckable() {
        return false;
    }

    public boolean isChecked() {
        return false;
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean isVisible() {
        return false;
    }

    public MenuItem setAlphabeticShortcut(char alphaChar) {
        return null;
    }

    public MenuItem setCheckable(boolean checkable) {
        return null;
    }

    public MenuItem setChecked(boolean checked) {
        return null;
    }

    public MenuItem setEnabled(boolean enabled) {
        return null;
    }

    public MenuItem setIcon(Drawable icon) {
        return null;
    }

    public MenuItem setIcon(int iconRes) {
        return null;
    }

    public MenuItem setIntent(Intent intent) {
        return null;
    }

    public MenuItem setNumericShortcut(char numericChar) {
        return null;
    }

    public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
        return null;
    }

    public MenuItem setShortcut(char numericChar, char alphaChar) {
        return null;
    }

    public MenuItem setTitle(CharSequence title) {
        return null;
    }

    public MenuItem setTitle(int title) {
        return null;
    }

    public MenuItem setTitleCondensed(CharSequence title) {
        return null;
    }

    public MenuItem setVisible(boolean visible) {
        return null;
    }
    /******************************************************/
}
