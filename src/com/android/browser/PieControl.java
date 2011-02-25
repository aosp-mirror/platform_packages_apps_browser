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

import com.android.browser.view.PieMenu;

import android.app.Activity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.Map;

/**
 * controller for Quick Controls pie menu
 */
public class PieControl implements OnClickListener, PieMenu.PieController {

    private Activity mActivity;
    private UiController mUiController;
    private XLargeUi mUi;
    private PieMenu mPie;
    private ImageView mBack;
    private ImageView mForward;
    private ImageView mRefresh;
    private ImageView mUrl;
    private ImageView mOptions;
    private ImageView mBookmarks;
    private ImageView mNewTab;
    private ImageView mClose;

    private Map<View,Tab> mTabItems;

    boolean mNewTabMode = true;

    public PieControl(Activity activity, UiController controller, XLargeUi ui) {
        mActivity = activity;
        mUiController = controller;
        mUi = ui;
        mTabItems = new HashMap<View, Tab>();
    }

    protected void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mActivity);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            mNewTab = makeMenuView(R.drawable.ic_pie_new_tab);
            mPie.addItem(mNewTab);
            mBack = makeMenuView(R.drawable.ic_pie_back);
            mPie.addItem(mBack);
            mUrl = makeMenuView(R.drawable.ic_pie_web);
            mPie.addItem(mUrl);
            mBookmarks = makeMenuView(R.drawable.ic_pie_bookmarks);
            mPie.addItem(mBookmarks);
            mOptions = makeMenuView(R.drawable.ic_pie_more);
            mPie.addItem(mOptions);
            setClickListener(mBack,
                    mUrl,
                    mOptions,
                    mBookmarks,
                    mNewTab
                    );
            mPie.setController(this);
        }
        container.addView(mPie);
    }

    protected void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    private ImageView makeMenuView(int image) {
        ImageView item = new ImageView(mActivity);
        item.setImageResource(image);
        LayoutParams lp = new LayoutParams(48, 48);
        item.setLayoutParams(lp);
        return item;
    }

    private void setClickListener(View... views) {
        for (View view : views) {
            view.setOnClickListener(this);
        }
    }

    protected void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    @Override
    public void onClick(View v) {
        mPie.show(false);
        Tab tab = mUiController.getTabControl().getCurrentTab();
        WebView web = tab.getWebView();
        if (mBack == v) {
            web.goBack();
        } else if (mForward == v) {
            web.goForward();
        } else if (mRefresh == v) {
            if (tab.inPageLoad()) {
                web.stopLoading();
            } else {
                web.reload();
            }
        } else if (mUrl == v) {
            mUi.showTitleBarAndEdit();
        } else if (mOptions == v) {
            mActivity.openOptionsMenu();
        } else if (mBookmarks == v) {
            mUiController.bookmarksOrHistoryPicker(false);
        } else if (mNewTab == v) {
            mUiController.openTabToHomePage();
            mUi.showTitleBarAndEdit();
        } else if (mClose == v) {
            mUiController.closeCurrentTab();
        } else {
            Tab ntab = mTabItems.get(v);
            if (ntab != null) {
                mUiController.switchToTab(mUiController.getTabControl().getTabIndex(ntab));
            }
        }
    }

    @Override
    public boolean onOpen() {
        return true;
    }

}
