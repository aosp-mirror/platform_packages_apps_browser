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

import com.android.browser.ScrollWebView.ScrollListener;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TabBar extends LinearLayout
        implements ScrollListener, OnClickListener {

    private static final int PROGRESS_MAX = 100;

    private Activity mActivity;
    private UiController mUiController;
    private TabControl mTabControl;
    private BaseUi mUi;

    private final int mTabWidthSelected;
    private final int mTabWidthUnselected;

    private TabScrollView mTabs;

    private ImageButton mNewTab;
    private int mButtonWidth;

    private Map<Tab, TabViewData> mTabMap;

    private boolean mUserRequestedUrlbar;
    private boolean mTitleVisible;
    private boolean mShowUrlMode;
    private boolean mHasReceivedTitle;

    private Drawable mGenericFavicon;
    private String mLoadingText;

    public TabBar(Activity activity, UiController controller, BaseUi ui) {
        super(activity);
        mActivity = activity;
        mUiController = controller;
        mTabControl = mUiController.getTabControl();
        mUi = ui;
        Resources res = activity.getResources();
        mTabWidthSelected = (int) res.getDimension(R.dimen.tab_width_selected);
        mTabWidthUnselected = (int) res.getDimension(R.dimen.tab_width_unselected);

        mTabMap = new HashMap<Tab, TabViewData>();
        Resources resources = activity.getResources();
        LayoutInflater factory = LayoutInflater.from(activity);
        factory.inflate(R.layout.tab_bar, this);
        mTabs = (TabScrollView) findViewById(R.id.tabs);
        mNewTab = (ImageButton) findViewById(R.id.newtab);
        mNewTab.setOnClickListener(this);
        mGenericFavicon = res.getDrawable(R.drawable.app_web_browser_sm);
        mLoadingText = res.getString(R.string.title_bar_loading);

        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.

        updateTabs(mUiController.getTabs());

        mUserRequestedUrlbar = false;
        mTitleVisible = true;
        mButtonWidth = -1;
    }

    void updateTabs(List<Tab> tabs) {
        mTabs.clearTabs();
        mTabMap.clear();
        for (Tab tab : tabs) {
            TabViewData data = buildTab(tab);
            TabView tv = buildView(data);
        }
        mTabs.setSelectedTab(mTabControl.getCurrentIndex());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mButtonWidth == -1) {
            mButtonWidth = mNewTab.getMeasuredWidth();
        }
        int sw = mTabs.getMeasuredWidth();
        int w = right-left;
        if (w-sw < mButtonWidth) {
            sw = w - mButtonWidth;
        }
        mTabs.layout(0, 0, sw, bottom-top );
        mNewTab.layout(sw, 0, sw+mButtonWidth, bottom-top);
    }

    public void onClick(View view) {
        mUi.hideComboView();
        if (mNewTab == view) {
            mUiController.openTabToHomePage();
        } else if (mTabs.getSelectedTab() == view) {
            if (mUi.isFakeTitleBarShowing() && !isLoading()) {
                mUi.hideFakeTitleBar();
            } else {
                showUrlBar();
            }
        } else {
            int ix = mTabs.getChildIndex(view);
            if (ix >= 0) {
                mTabs.setSelectedTab(ix);
                mUiController.switchToTab(ix);
            }
        }
    }

    private void showUrlBar() {
        mUi.stopWebViewScrolling();
        mUi.showFakeTitleBar();
        mUserRequestedUrlbar = true;
    }

    private void setShowUrlMode(boolean showUrl) {
        mShowUrlMode = showUrl;
    }

    // callback after fake titlebar is shown
    void onShowTitleBar() {
        setShowUrlMode(false);
    }

    // callback after fake titlebar is hidden
    void onHideTitleBar() {
        setShowUrlMode(!mTitleVisible);
        Tab tab = mTabControl.getCurrentTab();
        tab.getWebView().requestFocus();
        mUserRequestedUrlbar = false;
    }

    // webview scroll listener

    @Override
    public void onScroll(boolean titleVisible) {
        // isLoading is using the current tab, which initially might not be set yet
        if (mTabControl.getCurrentTab() != null) {
            mTitleVisible = titleVisible;
            if (!mShowUrlMode && !mTitleVisible && !isLoading()) {
                if (mUserRequestedUrlbar) {
                    mUi.hideFakeTitleBar();
                } else {
                    setShowUrlMode(true);
                }
            } else if (mTitleVisible && !isLoading()) {
                if (mShowUrlMode) {
                    setShowUrlMode(false);
                }
            }
        }
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mActivity.onCreateContextMenu(menu, this, null);
    }

    private TabViewData buildTab(Tab tab) {
        TabViewData data = new TabViewData(tab);
        mTabMap.put(tab, data);
        return data;
    }

    private TabView buildView(final TabViewData data) {
        TabView tv = new TabView(mActivity, data);
        tv.setTag(data);
        tv.setOnClickListener(this);
        mTabs.addTab(tv);
        return tv;
    }

    /**
     * View used in the tab bar
     */
    class TabView extends LinearLayout implements OnClickListener {

        TabViewData mTabData;
        View mTabContent;
        TextView mTitle;
        View mIncognito;
        ImageView mIconView;
        ImageView mLock;
        ImageView mClose;
        boolean mSelected;
        boolean mInLoad;

        /**
         * @param context
         */
        public TabView(Context context, TabViewData tab) {
            super(context);
            mTabData = tab;
            setGravity(Gravity.CENTER_VERTICAL);
            setOrientation(LinearLayout.HORIZONTAL);
            setBackgroundResource(R.drawable.tab_background);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            mTabContent = inflater.inflate(R.layout.tab_title, this, true);
            mTitle = (TextView) mTabContent.findViewById(R.id.title);
            mIconView = (ImageView) mTabContent.findViewById(R.id.favicon);
            mLock = (ImageView) mTabContent.findViewById(R.id.lock);
            mClose = (ImageView) mTabContent.findViewById(R.id.close);
            mClose.setOnClickListener(this);
            mIncognito = mTabContent.findViewById(R.id.incognito);
            mSelected = false;
            mInLoad = false;
            // update the status
            updateFromData();
        }

        @Override
        public void onClick(View v) {
            if (v == mClose) {
                closeTab();
            }
        }

        private void updateFromData() {
            mTabData.mTabView = this;
            if (mTabData.mUrl != null) {
                setDisplayTitle(mTabData.mUrl);
            }
            if (mTabData.mTitle != null) {
                setDisplayTitle(mTabData.mTitle);
            }
            setProgress(mTabData.mProgress);
            if (mTabData.mIcon != null) {
                setFavicon(mTabData.mIcon);
            }
            if (mTabData.mLock != null) {
                setLock(mTabData.mLock);
            }
            if (mTabData.mTab != null) {
                mIncognito.setVisibility(
                        mTabData.mTab.isPrivateBrowsingEnabled() ?
                        View.VISIBLE : View.GONE);
            }
        }

        @Override
        public void setActivated(boolean selected) {
            mSelected = selected;
            mClose.setVisibility(mSelected ? View.VISIBLE : View.GONE);
            mTitle.setTextAppearance(mActivity, mSelected ?
                    R.style.TabTitleSelected : R.style.TabTitleUnselected);
            setHorizontalFadingEdgeEnabled(!mSelected);
            setFadingEdgeLength(50);
            super.setActivated(selected);
            setLayoutParams(new LayoutParams(selected ?
                    mTabWidthSelected : mTabWidthUnselected,
                    LayoutParams.MATCH_PARENT));
        }

        void setDisplayTitle(String title) {
            mTitle.setText(title);
        }

        void setFavicon(Drawable d) {
            mIconView.setImageDrawable(d);
        }

        void setLock(Drawable d) {
            if (null == d) {
                mLock.setVisibility(View.GONE);
            } else {
                mLock.setImageDrawable(d);
                mLock.setVisibility(View.VISIBLE);
            }
        }

        void setProgress(int newProgress) {
            if (newProgress >= PROGRESS_MAX) {
                mInLoad = false;
            } else {
                if (!mInLoad && getWindowToken() != null) {
                    mInLoad = true;
                }
            }
        }

        private void closeTab() {
            if (mTabData.mTab == mTabControl.getCurrentTab()) {
                mUiController.closeCurrentTab();
            } else {
                mUiController.closeTab(mTabData.mTab);
            }
        }

    }

    /**
     * Store tab state within the title bar
     */
    class TabViewData {

        Tab mTab;
        TabView mTabView;
        int mProgress;
        Drawable mIcon;
        Drawable mLock;
        String mTitle;
        String mUrl;

        TabViewData(Tab tab) {
            mTab = tab;
            WebView web = tab.getWebView();
            if (web != null) {
                setUrlAndTitle(web.getUrl(), web.getTitle());
            }
        }

        void setUrlAndTitle(String url, String title) {
            mUrl = url;
            mTitle = title;
            if (mTabView != null) {
                if (title != null) {
                    mTabView.setDisplayTitle(title);
                } else if (url != null) {
                    mTabView.setDisplayTitle(UrlUtils.stripUrl(url));
                }
            }
        }

        void setProgress(int newProgress) {
            mProgress = newProgress;
            if (mTabView != null) {
                mTabView.setProgress(mProgress);
            }
        }

        void setFavicon(Bitmap icon) {
            Drawable[] array = new Drawable[3];
            array[0] = new PaintDrawable(Color.BLACK);
            array[1] = new PaintDrawable(Color.WHITE);
            if (icon == null) {
                array[2] = mGenericFavicon;
            } else {
                array[2] = new BitmapDrawable(icon);
            }
            LayerDrawable d = new LayerDrawable(array);
            d.setLayerInset(1, 1, 1, 1, 1);
            d.setLayerInset(2, 2, 2, 2, 2);
            mIcon = d;
            if (mTabView != null) {
                mTabView.setFavicon(mIcon);
            }
        }

    }

    // TabChangeListener implementation

    public void onSetActiveTab(Tab tab) {
        mTabs.setSelectedTab(mTabControl.getTabIndex(tab));
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setProgress(tvd.mProgress);
            // update the scroll state
            WebView webview = tab.getWebView();
            onScroll(webview.getVisibleTitleHeight() > 0);
        }
    }

    public void onFavicon(Tab tab, Bitmap favicon) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setFavicon(favicon);
        }
    }

    public void onNewTab(Tab tab) {
        TabViewData tvd = buildTab(tab);
        buildView(tvd);
    }

    public void onProgress(Tab tab, int progress) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setProgress(progress);
        }
    }

    public void onRemoveTab(Tab tab) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            TabView tv = tvd.mTabView;
            if (tv != null) {
                mTabs.removeTab(tv);
            }
        }
        mTabMap.remove(tab);
    }

    public void onUrlAndTitle(Tab tab, String url, String title) {
        mHasReceivedTitle = true;
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setUrlAndTitle(url, title);
        }
    }

    public void onPageFinished(Tab tab) {
        if (!mHasReceivedTitle) {
            TabViewData tvd = mTabMap.get(tab);
            if (tvd != null) {
                tvd.setUrlAndTitle(tvd.mUrl, null);
            }
        }
    }

    public void onPageStarted(Tab tab, String url, Bitmap favicon) {
        mHasReceivedTitle = false;
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setFavicon(favicon);
            tvd.setUrlAndTitle(url, mLoadingText);
        }
    }

    private boolean isLoading() {
        TabViewData tvd = mTabMap.get(mTabControl.getCurrentTab());
        if ((tvd != null) && (tvd.mTabView != null)) {
            return tvd.mTabView.mInLoad;
        } else {
            return false;
        }
    }

}
