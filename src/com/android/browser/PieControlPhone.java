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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.browser.view.PieItem;
import com.android.browser.view.PieMenu.PieView.OnLayoutListener;
import com.android.browser.view.PieStackView;

import java.util.List;

/**
 * controller for Quick Controls pie menu
 */
public class PieControlPhone extends PieControlBase implements OnClickListener {

    private PhoneUi mUi;
    private PieItem mBack;
    private PieItem mUrl;
    private PieItem mShowTabs;
    private TabAdapter mTabAdapter;

    public PieControlPhone(Activity activity, UiController controller, PhoneUi ui) {
        super(activity, controller);
        mUi = ui;
    }

    protected void populateMenu() {
        mBack = makeItem(R.drawable.ic_back_holo_dark, 1);
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
        // level 1
        mPie.addItem(mBack);
        mPie.addItem(mUrl);
        mPie.addItem(mShowTabs);
        setClickListener(this, mBack, mUrl, mShowTabs);
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
        Tab tab = mUiController.getTabControl().getCurrentTab();
        if (mBack.getView() == v) {
            tab.goBack();
        } else if (mUrl.getView() == v) {
            mUi.editUrl(false);
        } else if (mShowTabs.getView() == v) {
            mUi.showNavScreen();
        }
    }


}
