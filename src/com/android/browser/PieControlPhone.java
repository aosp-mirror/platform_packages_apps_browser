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

package com.android.browser;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.android.browser.UI.ComboViews;
import com.android.browser.view.PieItem;
import com.android.browser.view.PieMenu.PieView.OnLayoutListener;
import com.android.browser.view.PieStackView;

import java.util.List;

/**
 * controller for Quick Controls pie menu
 */
public class PieControlPhone extends PieControlBase implements OnClickListener,
        OnMenuItemClickListener {

    private PhoneUi mUi;
    private PieItem mUrl;
    private PieItem mShowTabs;
    private PieItem mOptions;
    private PieItem mNewTab;
    private PieItem mBookmarks;
    private TabAdapter mTabAdapter;
    private PopupMenu mPopup;

    public PieControlPhone(Activity activity, UiController controller, PhoneUi ui) {
        super(activity, controller);
        mUi = ui;
    }

    protected void populateMenu() {
        mUrl = makeItem(R.drawable.ic_web_holo_dark, 1);
        View tabs = makeTabsView();
        mShowTabs = new PieItem(tabs, 1);
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
        mOptions = makeItem(com.android.internal.R.drawable.ic_menu_moreoverflow_normal_holo_dark,
                1);

        // level 1
        mNewTab = makeItem(R.drawable.ic_new_window_holo_dark, 1);
        mBookmarks = makeItem(R.drawable.ic_bookmarks_holo_dark, 1);
        mPie.addItem(mNewTab);
        mPie.addItem(mShowTabs);
        mPie.addItem(mUrl);
        mPie.addItem(mBookmarks);
        mPie.addItem(mOptions);
        setClickListener(this, mUrl, mShowTabs, mOptions, mNewTab, mBookmarks);
        mPopup = new PopupMenu(mActivity, mUi.getTitleBar());
        Menu menu = mPopup.getMenu();
        mPopup.getMenuInflater().inflate(R.menu.browser, menu);
        mPopup.setOnMenuItemClickListener(this);
    }

    protected void showMenu() {
        mUiController.updateMenuState(mUiController.getCurrentTab(), mPopup.getMenu());
        mPopup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return mUiController.onOptionsItemSelected(item);
    }


    private void buildTabs() {
        final List<Tab> tabs = mUiController.getTabs();
        mUi.getActiveTab().capture();
        mTabAdapter.setTabs(tabs);
        PieStackView sym = (PieStackView) mShowTabs.getPieView();
        sym.setCurrent(mUiController.getTabControl().getCurrentPosition());

    }

    @Override
    public void onClick(View v) {
        if (mUrl.getView() == v) {
            mUi.editUrl(false);
        } else if (mShowTabs.getView() == v) {
            mUi.showNavScreen();
        } else if (mOptions.getView() == v) {
            showMenu();
        } else if (mNewTab.getView() == v) {
            mUiController.openTabToHomePage();
            mUi.editUrl(false);
        } else if (mBookmarks.getView() == v) {
            mUiController.bookmarksOrHistoryPicker(ComboViews.Bookmarks);
        }
    }

}
