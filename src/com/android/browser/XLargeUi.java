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

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.view.ActionMode;
import android.webkit.WebView;

import java.util.List;

/**
 * Ui for xlarge screen sizes
 */
public class XLargeUi extends BaseUi implements ScrollListener {

    private static final String LOGTAG = "XLargeUi";

    private TabBar mTabBar;

    private TitleBarXLarge mTitleBar;
    private TitleBarXLarge mFakeTitleBar;

    /**
     * @param browser
     * @param controller
     */
    public XLargeUi(Activity browser, UiController controller) {
        super(browser, controller);
        mTitleBar = new TitleBarXLarge(mActivity, mUiController, this);
        mTitleBar.setProgress(100);
        mTitleBar.setEditable(false);
        mFakeTitleBar = new TitleBarXLarge(mActivity, mUiController, this);
        mFakeTitleBar.setEditable(true);
        ActionBar actionBar = mActivity.getActionBar();
        mTabBar = new TabBar(mActivity, mUiController, this);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(mTabBar);
    }

    @Override
    public void onDestroy() {
        hideFakeTitleBar();
    }

    // webview factory

    @Override
    public WebView createWebView(boolean privateBrowsing) {
        // Create a new WebView
        ScrollWebView w = new ScrollWebView(mActivity, null,
                android.R.attr.webViewStyle, privateBrowsing);
        initWebViewSettings(w);
        w.setScrollListener(this);
        w.getSettings().setDisplayZoomControls(false);
        return w;
    }

    @Override
    public WebView createSubWebView(boolean privateBrowsing) {
        ScrollWebView web = (ScrollWebView) createWebView(privateBrowsing);
        // no scroll listener for subview
        web.setScrollListener(null);
        return web;
    }

    @Override
    public void onScroll(int visibleTitleHeight) {
        mTabBar.onScroll(visibleTitleHeight);
    }

    void stopWebViewScrolling() {
        ScrollWebView web = (ScrollWebView) mUiController.getCurrentWebView();
        if (web != null) {
            web.stopScroll();
        }
    }

    // WebView callbacks

    @Override
    public void onPageStarted(Tab tab, String url, Bitmap favicon) {
        super.onPageStarted(tab, url, favicon);
        mTabBar.onPageStarted(tab, url, favicon);
    }

    @Override
    public void bookmarkedStatusHasChanged(Tab tab) {
        if (tab.inForeground()) {
            boolean isBookmark = tab.isBookmarkedSite();
            mTitleBar.setCurrentUrlIsBookmark(isBookmark);
            mFakeTitleBar.setCurrentUrlIsBookmark(isBookmark);
        }
    }

    @Override
    public void onPageFinished(Tab tab, String url) {
        mTabBar.onPageFinished(tab);
        super.onPageFinished(tab, url);
    }

    @Override
    public void onProgressChanged(Tab tab, int progress) {
        mTabBar.onProgress(tab, progress);
        if (tab.inForeground()) {
            mFakeTitleBar.setProgress(progress);
            if (progress == 100) {
                hideFakeTitleBar();
            } else {
                showFakeTitleBar();
            }
        }
    }

    @Override
    public boolean needsRestoreAllTabs() {
        return true;
    }

    @Override
    public void addTab(Tab tab) {
        mTabBar.onNewTab(tab);
    }

    @Override
    public void setActiveTab(Tab tab) {
        super.setActiveTab(tab);
        mTabBar.onSetActiveTab(tab);
    }

    @Override
    public void updateTabs(List<Tab> tabs) {
        mTabBar.updateTabs(tabs);
    }

    @Override
    public void removeTab(Tab tab) {
        super.removeTab(tab);
        mTabBar.onRemoveTab(tab);
    }

    int getTitleBarWidth() {
        if (mTitleBar != null) {
            return mTitleBar.getWidth();
        }
        return 0;
    }

    void editUrl(boolean clearInput) {
        showFakeTitleBar();
        mFakeTitleBar.onEditUrl(clearInput);
    }

    @Override
    protected void attachFakeTitleBar(WebView mainView) {
        mContentView.addView(mFakeTitleBar);
        mTabBar.onShowTitleBar();
    }

    @Override
    protected void hideFakeTitleBar() {
        if (isFakeTitleBarShowing()) {
            mContentView.removeView(mFakeTitleBar);
            mTabBar.onHideTitleBar();
        }
    }

    @Override
    protected boolean isFakeTitleBarShowing() {
        return (mFakeTitleBar.getParent() != null);
    }

    @Override
    protected TitleBarBase getFakeTitleBar() {
        return mFakeTitleBar;
    }

    @Override
    protected TitleBarBase getEmbeddedTitleBar() {
        return mTitleBar;
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (mFakeTitleBar.isEditingUrl()) {
            // hide the fake title bar when CAB is shown
            hideFakeTitleBar();
        }
    }

    @Override
    public void setUrlTitle(Tab tab, String url, String title) {
        super.setUrlTitle(tab, url, title);
        mTabBar.onUrlAndTitle(tab, url, title);
    }

    // Set the favicon in the title bar.
    @Override
    public void setFavicon(Tab tab, Bitmap icon) {
        super.setFavicon(tab, icon);
        mTabBar.onFavicon(tab, icon);
    }

    @Override
    public void showVoiceTitleBar(String title) {
        List<String> vsresults = null;
        if (getActiveTab() != null) {
            vsresults = getActiveTab().getVoiceSearchResults();
        }
        mTitleBar.setInVoiceMode(true, null);
        mTitleBar.setDisplayTitle(title);
        mFakeTitleBar.setInVoiceMode(true, vsresults);
        mFakeTitleBar.setDisplayTitle(title);
    }

    @Override
    public void revertVoiceTitleBar(Tab tab) {
        mTitleBar.setInVoiceMode(false, null);
        String url = tab.getCurrentUrl();
        mTitleBar.setDisplayTitle(url);
        mFakeTitleBar.setInVoiceMode(false, null);
        mFakeTitleBar.setDisplayTitle(url);
    }


}
