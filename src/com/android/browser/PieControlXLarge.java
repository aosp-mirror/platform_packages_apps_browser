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

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.browser.UI.ComboViews;
import com.android.browser.view.PieItem;
import com.android.browser.view.PieListView;
import com.android.browser.view.PieMenu.PieView.OnLayoutListener;
import com.android.browser.view.PieStackView;

import java.util.ArrayList;
import java.util.List;

/**
 * controller for Quick Controls pie menu
 */
public class PieControlXLarge extends PieControlBase implements OnClickListener {

    private BaseUi mUi;
    private PieItem mBack;
    private PieItem mForward;
    private PieItem mRefresh;
    private PieItem mUrl;
    private PieItem mOptions;
    private PieItem mBookmarks;
    private PieItem mNewTab;
    private PieItem mClose;
    private MenuAdapter mMenuAdapter;
    private PieItem mShowTabs;
    private TabAdapter mTabAdapter;

    public PieControlXLarge(Activity activity, UiController controller, BaseUi ui) {
        super(activity, controller);
        mUiController = controller;
        mUi = ui;
    }

    @Override
    protected void attachToContainer(FrameLayout container) {
        super.attachToContainer(container);
        mPie.setUseBackground(true);
    }

    protected void populateMenu() {
        mBack = makeItem(R.drawable.ic_back_holo_dark, 1);
        mUrl = makeItem(R.drawable.ic_web_holo_dark, 1);
        mBookmarks = makeItem(R.drawable.ic_bookmarks_holo_dark, 1);
        mRefresh = makeItem(R.drawable.ic_refresh_holo_dark, 2);
        mForward = makeItem(R.drawable.ic_forward_holo_dark, 2);
        mNewTab = makeItem(R.drawable.ic_new_window_holo_dark, 2);
        mClose = makeItem(R.drawable.ic_close_window_holo_dark, 2);
        View tabs = makeTabsView();
        mShowTabs = new PieItem(tabs, 2);
        mOptions = makeItem(com.android.internal.R.drawable.ic_menu_moreoverflow_normal_holo_dark,
                                        2);
        mMenuAdapter = new MenuAdapter(mActivity, mUiController);
        mTabAdapter = new TabAdapter(mActivity, mUiController);
        PieStackView stack = new PieStackView(mActivity);
        stack.setLayoutListener(new OnLayoutListener() {
            @Override
            public void onLayout(int ax, int ay, boolean left) {
                buildTabs();
            }
        });
        stack.setOnCurrentListener(mTabAdapter);
        stack.setAdapter(mTabAdapter);
        mShowTabs.setPieView(stack);
        PieListView menuview = new PieListView(mActivity);
        menuview.setLayoutListener(new OnLayoutListener() {
            @Override
            public void onLayout(int ax, int ay, boolean left) {
                buildMenu();
            }
        });

        mOptions.setPieView(menuview);
        menuview.setAdapter(mMenuAdapter);
        setClickListener(this, mBack, mRefresh, mForward, mUrl, mBookmarks, mNewTab,
                mClose);
        // level 1
        mPie.addItem(mBack);
        mPie.addItem(mUrl);
        mPie.addItem(mBookmarks);
        // level 2
        mPie.addItem(mForward);
        mPie.addItem(mRefresh);
        mPie.addItem(mOptions);
        mPie.addItem(mShowTabs);
        mPie.addItem(mNewTab);
        mPie.addItem(mClose);
    }

    private void buildTabs() {
        final List<Tab> tabs = mUiController.getTabs();
        mUi.getActiveTab().capture();
        mTabAdapter.setTabs(tabs);
        PieStackView sym = (PieStackView) mShowTabs.getPieView();
        sym.setCurrent(mUiController.getTabControl().getCurrentPosition());
    }

    private void buildMenu() {
        Menu menu = mUi.getMenu();
        menu.setGroupVisible(R.id.NAV_MENU, false);
        mMenuAdapter.setMenu(menu);
    }

    @Override
    public void onClick(View v) {
        Tab tab = mUiController.getTabControl().getCurrentTab();
        WebView web = tab.getWebView();
        if (mBack.getView() == v) {
            tab.goBack();
        } else if (mForward.getView() == v) {
            tab.goForward();
        } else if (mRefresh.getView() == v) {
            if (tab.inPageLoad()) {
                web.stopLoading();
            } else {
                web.reload();
            }
        } else if (mUrl.getView() == v) {
            mUi.editUrl(false);
        } else if (mBookmarks.getView() == v) {
            mUiController.bookmarksOrHistoryPicker(ComboViews.Bookmarks);
        } else if (mNewTab.getView() == v) {
            mUiController.openTabToHomePage();
            mUi.editUrl(false);
        } else if (mClose.getView() == v) {
            mUiController.closeCurrentTab();
        }
    }

    private static class MenuAdapter extends BaseAdapter
            implements OnClickListener {

        List<MenuItem> mItems;
        UiController mUiController;
        LayoutInflater mInflater;

        public MenuAdapter(Context ctx, UiController ctl) {
            mUiController = ctl;
            mInflater = LayoutInflater.from(ctx);
            mItems = new ArrayList<MenuItem>();
        }

        public void setMenu(Menu menu) {
            mItems.clear();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.isEnabled() && item.isVisible()) {
                    mItems.add(item);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public MenuItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public void onClick(View v) {
            if (v.getTag() != null) {
                mUiController.onOptionsItemSelected((MenuItem) v.getTag());
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final MenuItem item = mItems.get(position);
            View view = mInflater.inflate(
                    R.layout.qc_menu_item, null);
            TextView label =
                    (TextView) view.findViewById(R.id.title);
            label.setText(item.getTitle());
            label.setTag(item);
            label.setOnClickListener(this);
            label.setLayoutParams(new LayoutParams(240, 32));
            return label;
        }

    }

}
