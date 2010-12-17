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
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import java.util.List;

/**
 * Ui for xlarge screen sizes
 */
public class XLargeUi extends BaseUi implements ScrollListener {

    private static final String LOGTAG = "XLargeUi";

    private ActionBar mActionBar;
    private TabBar mTabBar;

    private TitleBarXLarge mTitleBar;
    private TitleBarXLarge mFakeTitleBar;

    private boolean mUseQuickControls;
    private PieControl mPieControl;

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
        mTabBar = new TabBar(mActivity, mUiController, this);
        mActionBar = mActivity.getActionBar();
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        mActionBar.setCustomView(mTabBar);
        setUseQuickControls(BrowserSettings.getInstance().useQuickControls());
    }

    private void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        if (useQuickControls) {
            checkTabCount();
            mPieControl = new PieControl(mActivity, mUiController, this);
            mPieControl.attachToContainer(mContentView);
            setFakeTitleBarGravity(Gravity.BOTTOM);

            // remove embedded title bar if present
            WebView web = mTabControl.getCurrentWebView();
            if ((web != null) && (web.getVisibleTitleHeight() > 0)) {
                web.setEmbeddedTitleBar(null);
            }
        } else {
            mActivity.getActionBar().show();
            if (mPieControl != null) {
                mPieControl.removeFromContainer(mContentView);
            }
            setFakeTitleBarGravity(Gravity.TOP);
            // remove embedded title bar if present
            WebView web = mTabControl.getCurrentWebView();
            if ((web != null) && (web.getVisibleTitleHeight() == 0)) {
                web.setEmbeddedTitleBar(mTitleBar);
            }
        }
        mTabBar.setUseQuickControls(mUseQuickControls);
        mFakeTitleBar.setUseQuickControls(mUseQuickControls);
    }

    private void checkTabCount() {
        if (mUseQuickControls) {
            int n = mTabBar.getTabCount();
            if (n >= 2) {
                mActivity.getActionBar().show();
            } else if (n == 1) {
                mActivity.getActionBar().hide();
            }
        }
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
    public void bookmarkedStatusHasChanged(Tab tab) {
        if (tab.inForeground()) {
            boolean isBookmark = tab.isBookmarkedSite();
            mTitleBar.setCurrentUrlIsBookmark(isBookmark);
            mFakeTitleBar.setCurrentUrlIsBookmark(isBookmark);
        }
    }

    @Override
    public void onProgressChanged(Tab tab) {
        int progress = tab.getLoadProgress();
        mTabBar.onProgress(tab, progress);
        if (tab.inForeground()) {
            mFakeTitleBar.setProgress(progress);
            if (progress == 100) {
                if (!mFakeTitleBar.isEditingUrl()) {
                    hideFakeTitleBar();
                    if (mUseQuickControls) {
                        mFakeTitleBar.setShowProgressOnly(false);
                        setFakeTitleBarGravity(Gravity.BOTTOM);
                    }
                }
            } else {
                if (mUseQuickControls && !mFakeTitleBar.isEditingUrl()) {
                    mFakeTitleBar.setShowProgressOnly(true);
                    if (!isFakeTitleBarShowing()) {
                        setFakeTitleBarGravity(Gravity.TOP);
                    }
                }
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
        checkTabCount();
    }

    @Override
    public void setActiveTab(Tab tab) {
        super.setActiveTab(tab);
        ScrollWebView view = (ScrollWebView) tab.getWebView();
        // TabControl.setCurrentTab has been called before this,
        // so the tab is guaranteed to have a webview
        if (view == null) {
            Log.e(LOGTAG, "active tab with no webview detected");
            return;
        }
        // Request focus on the top window.
        if (mUseQuickControls) {
            mPieControl.forceToTop(mContentView);
            view.setScrollListener(null);
            mTabBar.showTitleBarIndicator(false);
        } else {
            view.setEmbeddedTitleBar(mTitleBar);
            view.setScrollListener(this);
        }
        mTabBar.onSetActiveTab(tab);
        if (tab.isInVoiceSearchMode()) {
            showVoiceTitleBar(tab.getVoiceDisplayTitle());
        } else {
            revertVoiceTitleBar(tab);
        }
        updateLockIconToLatest(tab);
        tab.getTopWindow().requestFocus();
    }

    @Override
    public void updateTabs(List<Tab> tabs) {
        mTabBar.updateTabs(tabs);
        checkTabCount();
    }

    @Override
    public void removeTab(Tab tab) {
        super.removeTab(tab);
        mTabBar.onRemoveTab(tab);
        checkTabCount();
    }

    int getContentWidth() {
        if (mContentView != null) {
            return mContentView.getWidth();
        }
        return 0;
    }

    void editUrl(boolean clearInput) {
        showFakeTitleBar();
        mFakeTitleBar.onEditUrl(clearInput);
    }

    void setFakeTitleBarGravity(int gravity) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                mFakeTitleBar.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
        }
        lp.gravity = gravity;
        mFakeTitleBar.setLayoutParams(lp);
    }

    void showFakeTitleBarAndEdit() {
        mFakeTitleBar.setShowProgressOnly(false);
        setFakeTitleBarGravity(Gravity.BOTTOM);
        showFakeTitleBar();
        mFakeTitleBar.onEditUrl(false);
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
        if (!mFakeTitleBar.isEditingUrl()) {
            // hide the fake title bar when CAB is shown
            hideFakeTitleBar();
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        checkTabCount();
        if (inLoad) {
            // the titlebar was removed when the CAB was shown
            // if the page is loading, show it again
            mFakeTitleBar.setShowProgressOnly(true);
            if (!isFakeTitleBarShowing()) {
                setFakeTitleBarGravity(Gravity.TOP);
            }
            showFakeTitleBar();
        }
    }

    @Override
    public void setUrlTitle(Tab tab) {
        super.setUrlTitle(tab);
        mTabBar.onUrlAndTitle(tab, tab.getUrl(), tab.getTitle());
    }

    // Set the favicon in the title bar.
    @Override
    public void setFavicon(Tab tab) {
        super.setFavicon(tab);
        mTabBar.onFavicon(tab, tab.getFavicon());
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
        String url = tab.getUrl();
        mTitleBar.setDisplayTitle(url);
        mFakeTitleBar.setInVoiceMode(false, null);
        mFakeTitleBar.setDisplayTitle(url);
    }


}
