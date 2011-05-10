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
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

interface OnCloseTab {
    void onCloseTab(int position);
}

public class ActiveTabsPage extends LinearLayout implements OnClickListener,
        OnItemClickListener, OnCloseTab {

    private Context mContext;
    private UiController mController;
    private TabControl mTabControl;
    private View mNewTab, mNewIncognitoTab;
    private TabAdapter mAdapter;
    private AbsListView mTabsList;

    public ActiveTabsPage(Context context, UiController controller) {
        super(context);
        mContext = context;
        mController = controller;
        mTabControl = mController.getTabControl();
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_browser);
        LayoutInflater inflate = LayoutInflater.from(mContext);
        inflate.inflate(R.layout.active_tabs, this, true);
        mNewTab = findViewById(R.id.new_tab);
        mNewIncognitoTab = findViewById(R.id.new_incognito_tab);
        mNewTab.setOnClickListener(this);
        mNewIncognitoTab.setOnClickListener(this);
        int visibility = mTabControl.canCreateNewTab() ? View.VISIBLE : View.GONE;
        mNewTab.setVisibility(visibility);
        mNewIncognitoTab.setVisibility(visibility);
        mTabsList = (AbsListView) findViewById(android.R.id.list);
        mAdapter = new TabAdapter(mContext, mTabControl);
        mAdapter.setOnCloseListener(this);
        mTabsList.setAdapter(mAdapter);
        mTabsList.setOnItemClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mNewTab) {
            mController.openTabToHomePage();
        } else if (v == mNewIncognitoTab) {
            mController.openIncognitoTab();
        }
        mController.removeActiveTabsPage(false);
    }

    @Override
    public void onItemClick(
            AdapterView<?> parent, View view, int position, long id) {
        boolean needToAttach = !mController.switchToTab(position);
        mController.removeActiveTabsPage(needToAttach);
    }

    @Override
    public void onCloseTab(int position) {
        Tab tab = mTabControl.getTab(position);
        if (tab != null) {
            mController.closeTab(tab);
            if (mTabControl.getTabCount() == 0) {
                mController.openTabToHomePage();
                mController.removeActiveTabsPage(false);
            } else {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Special class to hold the close drawable.  Its sole purpose is to allow
     * the parent to be pressed without being pressed itself.  This way the line
     * of a tab can be pressed, but the close button itself is not.
     */
    public static class CloseHolder extends ImageView {
        public CloseHolder(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void setPressed(boolean pressed) {
            // If the parent is pressed, do not set to pressed.
            if (pressed && ((View) getParent()).isPressed()) {
                return;
            }
            super.setPressed(pressed);
        }
    }

    static class TabAdapter extends BaseAdapter implements OnClickListener {

        LayoutInflater mInflater;
        OnCloseTab mCloseListener;
        TabControl mTabControl;

        TabAdapter(Context context, TabControl tabs) {
            mInflater = LayoutInflater.from(context);
            mTabControl = tabs;
        }

        void setOnCloseListener(OnCloseTab listener) {
            mCloseListener = listener;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = mInflater.inflate(R.layout.tab_view, parent, false);
            }
            ImageView favicon = (ImageView) view.findViewById(R.id.favicon);
            ImageView thumbnail = (ImageView) view.findViewById(R.id.thumb);
            TextView title = (TextView) view.findViewById(R.id.label);
            Tab tab = getItem(position);

            String label = tab.getTitle();
            if (TextUtils.isEmpty(label)) {
                label = tab.getUrl();
            }
            title.setText(label);
            Bitmap thumbnailBitmap = tab.getScreenshot();
            if (thumbnailBitmap == null) {
                thumbnail.setImageResource(R.drawable.browser_thumbnail);
            } else {
                thumbnail.setImageBitmap(thumbnailBitmap);
            }
            Bitmap faviconBitmap = tab.getFavicon();
            if (tab.isPrivateBrowsingEnabled()) {
                favicon.setImageResource(R.drawable.ic_incognito_holo_dark);
            } else {
                if (faviconBitmap == null) {
                    favicon.setImageResource(R.drawable.app_web_browser_sm);
                } else {
                    favicon.setImageBitmap(faviconBitmap);
                }
            }
            View close = view.findViewById(R.id.close);
            close.setTag(position);
            close.setOnClickListener(this);
            return view;
        }

        @Override
        public void onClick(View v) {
            int position = (Integer) v.getTag();
            if (mCloseListener != null) {
                mCloseListener.onCloseTab(position);
            }
        }

        @Override
        public int getCount() {
            return mTabControl.getTabCount();
        }

        @Override
        public Tab getItem(int position) {
            return mTabControl.getTab(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
