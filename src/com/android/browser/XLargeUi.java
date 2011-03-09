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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.List;

/**
 * Ui for xlarge screen sizes
 */
public class XLargeUi extends BaseUi implements ScrollListener {

    private static final String LOGTAG = "XLargeUi";

    private ActionBar mActionBar;
    private TabBar mTabBar;

    private TitleBarXLarge mTitleBar;

    private boolean mUseQuickControls;
    private PieControl mPieControl;
    private boolean mInAnimation = false;
    private Handler mHandler;

    /**
     * @param browser
     * @param controller
     */
    public XLargeUi(Activity browser, UiController controller) {
        super(browser, controller);
        mHandler = new Handler();
        mTitleBar = new TitleBarXLarge(mActivity, mUiController, this);
        mTitleBar.setProgress(100);
        mTabBar = new TabBar(mActivity, mUiController, this);
        mActionBar = mActivity.getActionBar();
        setupActionBar();
        setUseQuickControls(BrowserSettings.getInstance().useQuickControls());
    }

    private void setupActionBar() {
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        mActionBar.setCustomView(mTabBar);
    }

    @Override
    public void showComboView(boolean startWithHistory, Bundle extras) {
        super.showComboView(startWithHistory, extras);
        if (mUseQuickControls) {
            mActionBar.show();
        }
    }

    @Override
    public void hideComboView() {
        if (isComboViewShowing()) {
            super.hideComboView();
            // ComboView changes the action bar, set it back up to what we want
            setupActionBar();
            checkTabCount();
        }
    }

    private void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        mTitleBar.setUseQuickControls(mUseQuickControls);
        if (useQuickControls) {
            checkTabCount();
            mPieControl = new PieControl(mActivity, mUiController, this);
            mPieControl.attachToContainer(mContentView);
            Tab tab = getActiveTab();
            if ((tab != null) && (tab.getWebView() != null)) {
                tab.getWebView().setEmbeddedTitleBar(null);
            }
            setTitleGravity(Gravity.BOTTOM);
        } else {
            mActivity.getActionBar().show();
            if (mPieControl != null) {
                mPieControl.removeFromContainer(mContentView);
            }
            setTitleGravity(Gravity.TOP);
            WebView web = mTabControl.getCurrentWebView();
            if (web != null) {
                web.setEmbeddedTitleBar(mTitleBar);
            }
        }
        mTabBar.setUseQuickControls(mUseQuickControls);
    }

    private void checkTabCount() {
        if (mUseQuickControls) {
            int n = mTabBar.getTabCount();
            if (n >= 2) {
                mActionBar.show();
            } else if (n == 1) {
                mHandler.post(new Runnable() {
                    public void run() {
                        mActionBar.hide();
                    }
                });
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!BrowserSettings.getInstance().useInstant()) {
            mTitleBar.clearCompletions();
        }
    }

    @Override
    public void onDestroy() {
        hideTitleBar();
    }

    // webview factory

    @Override
    public WebView createWebView(boolean privateBrowsing) {
        // Create a new WebView
        ScrollWebView w = new ScrollWebView(mActivity, null,
                android.R.attr.webViewStyle, privateBrowsing);
        initWebViewSettings(w);
        w.setScrollListener(this);
        boolean supportsMultiTouch = mActivity.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH);
        w.getSettings().setDisplayZoomControls(!supportsMultiTouch);
        w.setExpandedTileBounds(true);  // smoother scrolling
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
    public void onScroll(int visibleTitleHeight, boolean userInitiated) {
        mTabBar.onScroll(visibleTitleHeight, userInitiated);
    }

    void stopWebViewScrolling() {
        ScrollWebView web = (ScrollWebView) mUiController.getCurrentWebView();
        if (web != null) {
            web.stopScroll();
        }
    }

    // WebView callbacks

    @Override
    public void onProgressChanged(Tab tab) {
        int progress = tab.getLoadProgress();
        mTabBar.onProgress(tab, progress);
        if (tab.inForeground()) {
            mTitleBar.setProgress(progress);
            if (progress == 100) {
                if (!mTitleBar.isEditingUrl()) {
                    hideTitleBar();
                    if (mUseQuickControls) {
                        mTitleBar.setShowProgressOnly(false);
                        setTitleGravity(Gravity.BOTTOM);
                    }
                }
            } else {
                if (!isTitleBarShowing()) {
                    if (mUseQuickControls && !mTitleBar.isEditingUrl()) {
                        mTitleBar.setShowProgressOnly(true);
                        setTitleGravity(Gravity.TOP);
                    }
                    showTitleBar();
                }
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

    protected void onAddTabCompleted(Tab tab) {
        checkTabCount();
    }

    @Override
    public void setActiveTab(final Tab tab) {
        if (mInAnimation) return;
        if ((tab != mActiveTab) && (mActiveTab != null)) {
            mInAnimation = true;
            // animate between the two
            final ScrollWebView fromWV = (ScrollWebView) mActiveTab.getWebView();
            fromWV.setDrawCached(true);
            fromWV.setEmbeddedTitleBar(null);
            final ScrollWebView toWV = (ScrollWebView) tab.getWebView();
            if (!mUseQuickControls) {
                if (mTitleBar.getParent() == null) {
                    toWV.setEmbeddedTitleBar(mTitleBar);
                }
            }
            toWV.setDrawCached(true);
            attachTabToContentView(tab);
            super.setActiveTab(tab, false);
            ObjectAnimator transition = ObjectAnimator.ofFloat(
                    toWV, "alpha", 0f, 1f);
            transition.setDuration(mActivity.getResources()
                    .getInteger(R.integer.tabFadeDuration));
            transition.addListener(new AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    fromWV.setDrawCached(false);
                    toWV.setDrawCached(false);
                    setActiveTab(tab, false);
                    mInAnimation = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    fromWV.setDrawCached(false);
                    toWV.setDrawCached(false);
                    setActiveTab(tab, false);
                    mInAnimation = false;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationStart(Animator animation) {
                }
            });
            transition.start();
        } else {
            super.setActiveTab(tab, true);
            setActiveTab(tab, true);
        }
    }

    @Override
    void setActiveTab(Tab tab, boolean needsAttaching) {
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
            // check if title bar is already attached by animation
            if (mTitleBar.getParent() == null) {
                view.setEmbeddedTitleBar(mTitleBar);
            }
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
    }

    protected void onRemoveTabCompleted(Tab tab) {
        checkTabCount();
    }

    int getContentWidth() {
        if (mContentView != null) {
            return mContentView.getWidth();
        }
        return 0;
    }

    @Override
    public void editUrl(boolean clearInput) {
        if (mUiController.isInCustomActionMode()) {
            mUiController.endActionMode();
        }
        showTitleBar();
        mTitleBar.startEditingUrl(clearInput);
    }

    void showTitleBarAndEdit() {
        mTitleBar.setShowProgressOnly(false);
        showTitleBar();
        mTitleBar.startEditingUrl(false);
    }

    void stopEditingUrl() {
        mTitleBar.stopEditingUrl();
    }

    @Override
    protected void showTitleBar() {
        if (canShowTitleBar()) {
            if (mUseQuickControls) {
                mContentView.addView(mTitleBar);
            } else {
                setTitleGravity(Gravity.TOP);
            }
            super.showTitleBar();
            mTabBar.onShowTitleBar();
        }
    }

    @Override
    protected void hideTitleBar() {
        if (isTitleBarShowing()) {
            mTabBar.onHideTitleBar();
            if (mUseQuickControls) {
                mContentView.removeView(mTitleBar);
            } else {
                setTitleGravity(Gravity.NO_GRAVITY);
            }
            super.hideTitleBar();
        }
    }

    public boolean isEditingUrl() {
        return mTitleBar.isEditingUrl();
    }

    @Override
    protected TitleBarBase getTitleBar() {
        return mTitleBar;
    }

    @Override
    protected void setTitleGravity(int gravity) {
        if (mUseQuickControls) {
            FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mTitleBar.getLayoutParams();
            lp.gravity = gravity;
            mTitleBar.setLayoutParams(lp);
        } else {
            super.setTitleGravity(gravity);
        }
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (!mTitleBar.isEditingUrl()) {
            // hide the fake title bar when CAB is shown
            hideTitleBar();
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        checkTabCount();
        if (inLoad) {
            // the titlebar was removed when the CAB was shown
            // if the page is loading, show it again
            mTitleBar.setShowProgressOnly(true);
            if (!isTitleBarShowing()) {
                setTitleGravity(Gravity.TOP);
            }
            showTitleBar();
        }
    }

    @Override
    protected void updateNavigationState(Tab tab) {
        mTitleBar.updateNavigationState(tab);
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
        mTitleBar.setInVoiceMode(true, vsresults);
        mTitleBar.setDisplayTitle(title);
    }

    @Override
    public void revertVoiceTitleBar(Tab tab) {
        mTitleBar.setInVoiceMode(false, null);
        String url = tab.getUrl();
        mTitleBar.setDisplayTitle(url);
    }

    @Override
    public void showCustomView(View view, CustomViewCallback callback) {
        super.showCustomView(view, callback);
        mActivity.getActionBar().hide();
    }

    @Override
    public void onHideCustomView() {
        super.onHideCustomView();
        if (mUseQuickControls) {
            checkTabCount();
        } else {
            mActivity.getActionBar().show();
        }
    }

    @Override
    public boolean dispatchKey(int code, KeyEvent event) {
        if (mActiveTab != null) {
            WebView web = mActiveTab.getWebView();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (code) {
                    case KeyEvent.KEYCODE_TAB:
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if ((web != null) && web.hasFocus() && !mTitleBar.hasFocus()) {
                            editUrl(false);
                            return true;
                        }
                }
                boolean ctrl = event.hasModifiers(KeyEvent.META_CTRL_ON);
                if (!ctrl && isTypingKey(event) && !mTitleBar.isEditingUrl()) {
                    editUrl(true);
                    return mContentView.dispatchKeyEvent(event);
                }
            }
        }
        return false;
    }

    private boolean isTypingKey(KeyEvent evt) {
        return evt.getUnicodeChar() > 0;
    }

    TabBar getTabBar() {
        return mTabBar;
    }

    @Override
    public void registerDropdownChangeListener(DropdownChangeListener d) {
        mTitleBar.registerDropdownChangeListener(d);
    }

}
