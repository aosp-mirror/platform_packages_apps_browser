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
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;

/**
 * Ui for regular phone screen sizes
 */
public class PhoneUi extends BaseUi {

    private static final String LOGTAG = "PhoneUi";

    private TitleBar mTitleBar;
    private ActiveTabsPage mActiveTabsPage;

    boolean mExtendedMenuOpen;
    boolean mOptionsMenuOpen;

    /**
     * @param browser
     * @param controller
     */
    public PhoneUi(Activity browser, UiController controller) {
        super(browser, controller);
        mTitleBar = new TitleBar(mActivity, mUiController);
        // mTitleBar will be always be shown in the fully loaded mode on
        // phone
        mTitleBar.setProgress(100);
        mActivity.getActionBar().hide();
    }

    @Override
    public void hideComboView() {
        super.hideComboView();
        mActivity.getActionBar().hide();
    }

    // webview factory

    @Override
    public WebView createWebView(boolean privateBrowsing) {
        // Create a new WebView
        WebView w = new WebView(mActivity, null,
                android.R.attr.webViewStyle, privateBrowsing);
        initWebViewSettings(w);
        return w;
    }

    @Override
    public WebView createSubWebView(boolean privateBrowsing) {
        WebView web = createWebView(privateBrowsing);
        return web;
    }

    // lifecycle

    @Override
    public void onPause() {
        // FIXME: This removes the active tabs page and resets the menu to
        // MAIN_MENU.  A better solution might be to do this work in onNewIntent
        // but then we would need to save it in onSaveInstanceState and restore
        // it in onCreate/onRestoreInstanceState
        if (mActiveTabsPage != null) {
            mUiController.removeActiveTabsPage(true);
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        hideTitleBar();
    }

    @Override
    public boolean onBackKey() {
        if (mActiveTabsPage != null) {
            // if tab page is showing, hide it
            mUiController.removeActiveTabsPage(true);
            return true;
        }
        return super.onBackKey();
    }

    @Override
    public void onProgressChanged(Tab tab) {
        if (tab.inForeground()) {
            int progress = tab.getLoadProgress();
            mTitleBar.setProgress(progress);
            if (progress == 100) {
                if (!mOptionsMenuOpen || !mExtendedMenuOpen) {
                    hideTitleBar();
                }
            } else {
                if (!mOptionsMenuOpen || mExtendedMenuOpen) {
                    showTitleBar();
                }
            }
        }
    }

    @Override
    public void setActiveTab(Tab tab) {
        super.setActiveTab(tab);
        WebView view = tab.getWebView();
        // TabControl.setCurrentTab has been called before this,
        // so the tab is guaranteed to have a webview
        if (view == null) {
            Log.e(LOGTAG, "active tab with no webview detected");
            return;
        }
        view.setEmbeddedTitleBar(getTitleBar());
        if (tab.isInVoiceSearchMode()) {
            showVoiceTitleBar(tab.getVoiceDisplayTitle());
        } else {
            revertVoiceTitleBar(tab);
        }
        tab.getTopWindow().requestFocus();
    }

    private void attachTitleBar(WebView mainView) {
        if (mainView != null) {
            mainView.setEmbeddedTitleBar(null);
        }
        WindowManager manager = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        // Add the title bar to the window manager so it can receive
        // touches while the menu is up
        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP;
        boolean atTop = mainView.getScrollY() == 0;
        params.windowAnimations = atTop ? 0 : R.style.TitleBar;
        manager.addView(mTitleBar, params);
        super.showTitleBar();
    }

    @Override
    void showTitleBar() {
        if (canShowTitleBar()) {
            WebView mainView = mUiController.getCurrentWebView();
            attachTitleBar(mainView);
            super.showTitleBar();
        }
    }

    @Override
    protected void hideTitleBar() {
        if (isTitleBarShowing()) {
            WindowManager.LayoutParams params =
                    (WindowManager.LayoutParams) mTitleBar.getLayoutParams();
            WebView mainView = mUiController.getCurrentWebView();
            // Although we decided whether or not to animate based on the
            // current
            // scroll position, the scroll position may have changed since the
            // fake title bar was displayed. Make sure it has the appropriate
            // animation/lack thereof before removing.
            params.windowAnimations =
                    mainView != null && mainView.getScrollY() == 0 ? 0 : R.style.TitleBar;
            WindowManager manager =
                    (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
            manager.updateViewLayout(mTitleBar, params);
            manager.removeView(mTitleBar);
            if (mainView != null) {
                mainView.setEmbeddedTitleBar(mTitleBar);
            }
            super.hideTitleBar();
        }
    }

    @Override
    protected TitleBarBase getTitleBar() {
        return mTitleBar;
    }

    // active tabs page

    @Override
    public void showActiveTabsPage() {
        mActiveTabsPage = new ActiveTabsPage(mActivity, mUiController);
        mTitleBar.setVisibility(View.GONE);
        hideTitleBar();
        mContentView.addView(mActiveTabsPage, COVER_SCREEN_PARAMS);
        mActiveTabsPage.requestFocus();
    }

    /**
     * Remove the active tabs page.
     */
    @Override
    public void removeActiveTabsPage() {
        mContentView.removeView(mActiveTabsPage);
        mTitleBar.setVisibility(View.VISIBLE);
        mActiveTabsPage = null;
    }

    @Override
    public boolean showsWeb() {
        return super.showsWeb() && mActiveTabsPage == null;
    }

    // menu handling callbacks

    @Override
    public void onOptionsMenuOpened() {
        mOptionsMenuOpen = true;
        // options menu opened, show fake title bar
        showTitleBar();
    }

    @Override
    public void onExtendedMenuOpened() {
        // Switching the menu to expanded view, so hide the
        // title bar.
        mExtendedMenuOpen = true;
        hideTitleBar();
    }

    @Override
    public void onOptionsMenuClosed(boolean inLoad) {
        mOptionsMenuOpen = false;
        if (!inLoad) {
            hideTitleBar();
        }
    }

    @Override
    public void onExtendedMenuClosed(boolean inLoad) {
        mExtendedMenuOpen = false;
        showTitleBar();
    }

    @Override
    public void onContextMenuCreated(Menu menu) {
        hideTitleBar();
    }

    @Override
    public void onContextMenuClosed(Menu menu, boolean inLoad) {
        if (inLoad) {
            showTitleBar();
        }
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        hideTitleBar();
    }

    @Override
    public boolean dispatchKey(int code, KeyEvent event) {
        return false;
    }

}
