/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.browser.addbookmark;

import com.android.browser.R;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

/**
 * SpinnerAdapter used in the AddBookmarkPage to select where to save a
 * bookmark/folder.
 */
public class FolderSpinnerAdapter implements SpinnerAdapter {
    private boolean mIncludeHomeScreen;

    public static final int HOME_SCREEN = 0;
    public static final int ROOT_FOLDER = 1;
    public static final int OTHER_FOLDER = 2;

    public FolderSpinnerAdapter(boolean includeHomeScreen) {
        mIncludeHomeScreen = includeHomeScreen;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        int labelResource;
        int drawableResource;
        if (!mIncludeHomeScreen) {
            position++;
        }
        switch (position) {
            case HOME_SCREEN:
                labelResource = R.string.add_to_homescreen_menu_option;
                drawableResource = R.drawable.ic_home_holo_dark;
                break;
            case ROOT_FOLDER:
                labelResource = R.string.add_to_bookmarks_menu_option;
                drawableResource = R.drawable.ic_bookmarks_holo_dark;
                break;
            case OTHER_FOLDER:
                labelResource = R.string.add_to_other_folder_menu_option;
                drawableResource = R.drawable.ic_folder_holo_dark;
                break;
            default:
                labelResource = 0;
                drawableResource = 0;
                // assert
                break;
        }
        Context context = parent.getContext();
        LayoutInflater factory = LayoutInflater.from(context);
        TextView textView = (TextView) factory.inflate(R.layout.add_to_option, null);
        textView.setText(labelResource);
        Drawable drawable = context.getResources().getDrawable(drawableResource);
        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null,
                null, null);
        return textView;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public int getCount() {
        return mIncludeHomeScreen ? 3 : 2;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        long id = position;
        if (!mIncludeHomeScreen) {
            id++;
        }
        return id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getDropDownView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        // Never want to recycle views
        return Adapter.IGNORE_ITEM_VIEW_TYPE;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
