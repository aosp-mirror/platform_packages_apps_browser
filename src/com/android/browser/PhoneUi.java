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
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.android.browser.UrlInputView.StateListener;

/**
 * Ui for regular phone screen sizes
 */
public class PhoneUi extends BaseUi {

    private static final String LOGTAG = "PhoneUi";

    private PieControlPhone mPieControl;
    private NavScreen mNavScreen;
    private NavigationBarPhone mNavigationBar;

    boolean mExtendedMenuOpen;
    boolean mOptionsMenuOpen;
    boolean mAnimating;

    /**
     * @param browser
     * @param controller
     */
    public PhoneUi(Activity browser, UiController controller) {
        super(browser, controller);
        mActivity.getActionBar().hide();
        setUseQuickControls(BrowserSettings.getInstance().useQuickControls());
        mNavigationBar = (NavigationBarPhone) mTitleBar.getNavigationBar();
    }

    @Override
    public void onDestroy() {
        hideTitleBar();
    }

    @Override
    public void editUrl(boolean clearInput) {
        if (mUseQuickControls) {
            mTitleBar.setShowProgressOnly(false);
        }
        super.editUrl(clearInput);
    }

    @Override
    public boolean onBackKey() {
        if (mNavScreen != null) {
            mNavScreen.close();
            return true;
        }
        return super.onBackKey();
    }

    @Override
    public boolean dispatchKey(int code, KeyEvent event) {
        return false;
    }

    @Override
    public void onProgressChanged(Tab tab) {
        if (tab.inForeground()) {
            int progress = tab.getLoadProgress();
            mTitleBar.setProgress(progress);
            if (progress == 100) {
                if (!mOptionsMenuOpen || !mExtendedMenuOpen) {
                    suggestHideTitleBar();
                    if (mUseQuickControls) {
                        mTitleBar.setShowProgressOnly(false);
                    }
                }
            } else {
                if (!mOptionsMenuOpen || mExtendedMenuOpen) {
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
    public void setActiveTab(final Tab tab) {
        mTitleBar.cancelTitleBarAnimation(true);
        mTitleBar.setSkipTitleBarAnimations(true);
        super.setActiveTab(tab);
        BrowserWebView view = (BrowserWebView) tab.getWebView();
        // TabControl.setCurrentTab has been called before this,
        // so the tab is guaranteed to have a webview
        if (view == null) {
            Log.e(LOGTAG, "active tab with no webview detected");
            return;
        }
        // Request focus on the top window.
        if (mUseQuickControls) {
            mPieControl.forceToTop(mContentView);
        } else {
            // check if title bar is already attached by animation
            if (mTitleBar.getParent() == null) {
                view.setEmbeddedTitleBar(mTitleBar);
            }
        }
        if (tab.isInVoiceSearchMode()) {
            showVoiceTitleBar(tab.getVoiceDisplayTitle(), tab.getVoiceSearchResults());
        } else {
            revertVoiceTitleBar(tab);
        }
        // update nav bar state
        mNavigationBar.onStateChanged(StateListener.STATE_NORMAL);
        updateLockIconToLatest(tab);
        tab.getTopWindow().requestFocus();
        mTitleBar.setSkipTitleBarAnimations(false);
    }

    /**
     * Suggest to the UI that the title bar can be hidden. The UI will then
     * decide whether or not to hide based off a number of factors, such
     * as if the user is editing the URL bar or if the page is loading
     */
    @Override
    public void suggestHideTitleBar() {
        if (!mNavigationBar.isMenuShowing()) {
            super.suggestHideTitleBar();
        }
    }

    @Override
    public void showComboView(ComboViews startWith, Bundle extras) {
        if (mNavScreen != null) {
            hideNavScreen(false);
        }
        super.showComboView(startWith, extras);
    }

    // menu handling callbacks

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuState(mActiveTab, menu);
        return true;
    }

    @Override
    public void updateMenuState(Tab tab, Menu menu) {
        menu.setGroupVisible(R.id.NAV_MENU, (mNavScreen == null));
        MenuItem bm = menu.findItem(R.id.bookmarks_menu_id);
        if (bm != null) {
            bm.setVisible(mNavScreen == null);
        }
        MenuItem nt = menu.findItem(R.id.new_tab_menu_id);
        if (nt != null) {
            nt.setVisible(mNavScreen == null);
        }
        MenuItem find = menu.findItem(R.id.find_menu_id);
        if (find != null) {
            find.setVisible(((tab != null) && !tab.isSnapshot()));
        }
        MenuItem abm = menu.findItem(R.id.add_bookmark_menu_id);
        if (abm != null) {
            abm.setVisible((tab != null) && !tab.isSnapshot());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mNavScreen != null) {
            hideNavScreen(false);
        }
        return false;
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
        if (!isEditingUrl()) {
            hideTitleBar();
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        if (inLoad) {
            if (mUseQuickControls) {
                mTitleBar.setShowProgressOnly(true);
            }
            showTitleBar();
        }
        mActivity.getActionBar().hide();
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

    @Override
    public void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        mTitleBar.setUseQuickControls(mUseQuickControls);
        if (useQuickControls) {
            mPieControl = new PieControlPhone(mActivity, mUiController, this);
            mPieControl.attachToContainer(mContentView);
            WebView web = getWebView();
            if (web != null) {
                web.setEmbeddedTitleBar(null);
                // don't show url bar on scrolling
                web.setOnTouchListener(null);
            }
        } else {
            if (mPieControl != null) {
                mPieControl.removeFromContainer(mContentView);
            }
            WebView web = getWebView();
            if (web != null) {
                web.setEmbeddedTitleBar(mTitleBar);
                // show url bar on scrolling
                web.setOnTouchListener(this);
            }
            setTitleGravity(Gravity.NO_GRAVITY);
        }
    }

    void showNavScreen() {
        mActiveTab.capture();
        detachTab(mActiveTab);
        mNavScreen = new NavScreen(mActivity, mUiController, this);
        // Add the custom view to its container.
        mCustomViewContainer.addView(mNavScreen, COVER_SCREEN_PARAMS);
        mContentView.setVisibility(View.GONE);
        mCustomViewContainer.setVisibility(View.VISIBLE);
        mCustomViewContainer.bringToFront();
        // notify accessibility manager about the screen change
        mNavScreen.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        mTabControl.setOnThumbnailUpdatedListener(mNavScreen);
    }

    void hideNavScreen(boolean animate) {
        if (mNavScreen == null) return;
        mTabControl.setOnThumbnailUpdatedListener(null);
        Tab tab = mNavScreen.getSelectedTab();
        mCustomViewContainer.removeView(mNavScreen);
        mNavScreen = null;
        mCustomViewContainer.setVisibility(View.GONE);
        mUiController.setActiveTab(tab);
        // Show the content view.
        mContentView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean needsRestoreAllTabs() {
        return false;
    }

    public void toggleNavScreen() {
        if (mNavScreen == null) {
            showNavScreen();
        } else {
            hideNavScreen(false);
        }
    }

    @Override
    public boolean shouldCaptureThumbnails() {
        return true;
    }

}
