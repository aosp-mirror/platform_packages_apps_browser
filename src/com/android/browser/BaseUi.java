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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/**
 * UI interface definitions
 */
public class BaseUi implements UI, WebViewFactory {

    private static final String LOGTAG = "BaseUi";

    private static final FrameLayout.LayoutParams COVER_SCREEN_PARAMS =
        new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT);

    private static final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER =
        new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        Gravity.CENTER);

    Activity mActivity;
    UiController mUiController;
    TabControl mTabControl;

    private Drawable mSecLockIcon;
    private Drawable mMixLockIcon;

    private boolean mXLargeScreenSize;
    private FrameLayout mBrowserFrameLayout;
    private FrameLayout mContentView;
    private FrameLayout mCustomViewContainer;
    private TitleBarBase mTitleBar;
    private TitleBarBase mFakeTitleBar;
    private TabBar mTabBar;

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    private CombinedBookmarkHistoryView mComboView;

    private LinearLayout mErrorConsoleContainer = null;

    private Toast mStopToast;
    private ActiveTabsPage mActiveTabsPage;

    // the default <video> poster
    private Bitmap mDefaultVideoPoster;
    // the video progress view
    private View mVideoProgressView;

    boolean mExtendedMenuOpen;
    boolean mOptionsMenuOpen;

    private boolean mActivityPaused;

    public BaseUi(Activity browser, UiController controller) {
        mActivity = browser;
        mUiController = controller;
        mTabControl = controller.getTabControl();
        Resources res = mActivity.getResources();
        mSecLockIcon = res.getDrawable(R.drawable.ic_secure);
        mMixLockIcon = res.getDrawable(R.drawable.ic_partial_secure);


        mXLargeScreenSize = (res.getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                == Configuration.SCREENLAYOUT_SIZE_XLARGE;

        FrameLayout frameLayout = (FrameLayout) mActivity.getWindow()
                .getDecorView().findViewById(android.R.id.content);
        mBrowserFrameLayout = (FrameLayout) LayoutInflater.from(mActivity)
                .inflate(R.layout.custom_screen, null);
        mContentView = (FrameLayout) mBrowserFrameLayout.findViewById(
                R.id.main_content);
        mErrorConsoleContainer = (LinearLayout) mBrowserFrameLayout
                .findViewById(R.id.error_console);
        mCustomViewContainer = (FrameLayout) mBrowserFrameLayout
                .findViewById(R.id.fullscreen_custom_content);
        frameLayout.addView(mBrowserFrameLayout, COVER_SCREEN_PARAMS);

        if (mXLargeScreenSize) {
            mTitleBar = new TitleBarXLarge(mActivity, mUiController);
            mTitleBar.setProgress(100);
            mFakeTitleBar = new TitleBarXLarge(mActivity, mUiController);
            ActionBar actionBar = mActivity.getActionBar();
            mTabBar = new TabBar(mActivity, mUiController, this);
            actionBar.setCustomNavigationMode(mTabBar);
        } else {
            mTitleBar = new TitleBar(mActivity, mUiController);
            // mTitleBar will be always be shown in the fully loaded mode on
            // phone
            mTitleBar.setProgress(100);
            mFakeTitleBar = new TitleBar(mActivity, mUiController);
        }
    }

    // webview factory

    @Override
    public WebView createWebView(boolean privateBrowsing) {
        // Create a new WebView
        ScrollWebView w = new ScrollWebView(mActivity, null,
                android.R.attr.webViewStyle, privateBrowsing);
        w.setScrollbarFadingEnabled(true);
        w.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        w.setMapTrackballToArrowKeys(false); // use trackball directly
        // Enable the built-in zoom
        w.getSettings().setBuiltInZoomControls(true);
        if (mXLargeScreenSize) {
            w.setScrollListener(mTabBar);
            w.getSettings().setDisplayZoomControls(false);
        }

        // Add this WebView to the settings observer list and update the
        // settings
        final BrowserSettings s = BrowserSettings.getInstance();
        s.addObserver(w.getSettings()).update(s, null);
        return w;
    }

    void stopWebViewScrolling() {
        ScrollWebView web = (ScrollWebView) mUiController.getCurrentWebView();
        if (web != null) {
            web.stopScroll();
        }
    }

    private void cancelStopToast() {
        if (mStopToast != null) {
            mStopToast.cancel();
            mStopToast = null;
        }
    }

    // lifecycle

    public void onPause() {
        // FIXME: This removes the active tabs page and resets the menu to
        // MAIN_MENU.  A better solution might be to do this work in onNewIntent
        // but then we would need to save it in onSaveInstanceState and restore
        // it in onCreate/onRestoreInstanceState
        if (mActiveTabsPage != null) {
            mUiController.removeActiveTabsPage(true);
        }
        cancelStopToast();
        mActivityPaused = true;
    }

    public void onResume() {
        mActivityPaused = false;
    }

    public void onDestroy() {
        hideFakeTitleBar();
    }

    public void onConfigurationChanged(Configuration config) {
    }

    // key handling

    @Override
    public boolean onBackKey() {
        if (mActiveTabsPage != null) {
            // if tab page is showing, hide it
            mUiController.removeActiveTabsPage(true);
            return true;
        }
        if (mComboView != null) {
            if (!mComboView.onBackPressed()) {
                mUiController.removeComboView();
            }
            return true;
        }
        if (mCustomView != null) {
            mUiController.hideCustomView();
            return true;
        }
        return false;
    }

    // WebView callbacks

    @Override
    public void onPageStarted(Tab tab, String url, Bitmap favicon) {
        if (mXLargeScreenSize) {
            mTabBar.onPageStarted(tab, url, favicon);
        }
        if (tab.inForeground()) {
            resetLockIcon(tab, url);
            setUrlTitle(tab, url, null);
            setFavicon(tab, favicon);
        }

    }

    @Override
    public void onPageFinished(Tab tab, String url) {
        if (mXLargeScreenSize) {
            mTabBar.onPageFinished(tab);
        }
        if (tab.inForeground()) {
            // Reset the title and icon in case we stopped a provisional load.
            resetTitleAndIcon(tab);
            // Update the lock icon image only once we are done loading
            updateLockIconToLatest(tab);
        }
    }

    @Override
    public void onPageStopped(Tab tab) {
        cancelStopToast();
        if (tab.inForeground()) {
            mStopToast = Toast
                    .makeText(mActivity, R.string.stopping, Toast.LENGTH_SHORT);
            mStopToast.show();
        }
    }

    @Override
    public void onProgressChanged(Tab tab, int progress) {
        if (mXLargeScreenSize) {
            mTabBar.onProgress(tab, progress);
        }
        if (tab.inForeground()) {
            mFakeTitleBar.setProgress(progress);
            if (progress == 100) {
                if (!mOptionsMenuOpen || !mExtendedMenuOpen) {
                    hideFakeTitleBar();
                }
            } else {
                if (!mOptionsMenuOpen || mExtendedMenuOpen) {
                    showFakeTitleBar();
                }
            }
        }
    }

    @Override
    public boolean needsRestoreAllTabs() {
        return mXLargeScreenSize;
    }

    @Override
    public void addTab(Tab tab) {
        if (mXLargeScreenSize) {
            mTabBar.onNewTab(tab);
        }
    }

    @Override
    public void setActiveTab(Tab tab) {
        Tab current = mTabControl.getCurrentTab();
        if ((tab != current) && (current != null)) {
            removeTabFromContentView(current);
        }
        attachTabToContentView(tab);
        setShouldShowErrorConsole(tab, mUiController.shouldShowErrorConsole());

        WebView view = tab.getWebView();
        view.setEmbeddedTitleBar(mTitleBar);
        if (tab.isInVoiceSearchMode()) {
            showVoiceTitleBar(tab.getVoiceDisplayTitle());
        } else {
            revertVoiceTitleBar(tab);
        }

        if (mXLargeScreenSize) {
            // Request focus on the top window.
            mTabBar.onSetActiveTab(tab);
        }
        resetTitleIconAndProgress(tab);
        updateLockIconToLatest(tab);
        tab.getTopWindow().requestFocus();
    }

    @Override
    public void updateTabs(List<Tab> tabs) {
        if (mXLargeScreenSize) {
            mTabBar.updateTabs(tabs);
        }
    }

    @Override
    public void removeTab(Tab tab) {
        if (mTabControl.getCurrentTab() == tab) {
            removeTabFromContentView(tab);
        }
        if (mXLargeScreenSize) {
            mTabBar.onRemoveTab(tab);
        }
    }

    @Override
    public void detachTab(Tab tab) {
        removeTabFromContentView(tab);
    }

    @Override
    public void attachTab(Tab tab) {
        attachTabToContentView(tab);
    }

    private void attachTabToContentView(Tab tab) {
        if (tab.getWebView() == null) {
            return;
        }
        View container = tab.getViewContainer();
        WebView mainView  = tab.getWebView();
        // Attach the WebView to the container and then attach the
        // container to the content view.
        FrameLayout wrapper =
                (FrameLayout) container.findViewById(R.id.webview_wrapper);
        ViewGroup parent = (ViewGroup) mainView.getParent();
        if (parent != wrapper) {
            if (parent != null) {
                Log.w(LOGTAG, "mMainView already has a parent in"
                        + " attachTabToContentView!");
                parent.removeView(mainView);
            }
            wrapper.addView(mainView);
        } else {
            Log.w(LOGTAG, "mMainView is already attached to wrapper in"
                    + " attachTabToContentView!");
        }
        parent = (ViewGroup) container.getParent();
        if (parent != mContentView) {
            if (parent != null) {
                Log.w(LOGTAG, "mContainer already has a parent in"
                        + " attachTabToContentView!");
                parent.removeView(container);
            }
            mContentView.addView(container, COVER_SCREEN_PARAMS);
        } else {
            Log.w(LOGTAG, "mContainer is already attached to content in"
                    + " attachTabToContentView!");
        }
        mUiController.attachSubWindow(tab);
    }

    private void removeTabFromContentView(Tab tab) {
        // Remove the container that contains the main WebView.
        WebView mainView = tab.getWebView();
        View container = tab.getViewContainer();
        if (mainView == null) {
            return;
        }
        // Remove the container from the content and then remove the
        // WebView from the container. This will trigger a focus change
        // needed by WebView.
        FrameLayout wrapper =
                (FrameLayout) container.findViewById(R.id.webview_wrapper);
        wrapper.removeView(mainView);
        mContentView.removeView(container);
        mUiController.endActionMode();
        mUiController.removeSubWindow(tab);
        ErrorConsoleView errorConsole = tab.getErrorConsole(false);
        if (errorConsole != null) {
            mErrorConsoleContainer.removeView(errorConsole);
        }
        mainView.setEmbeddedTitleBar(null);
    }

    /**
     * Remove the sub window from the content view.
     */
    @Override
    public void removeSubWindow(View subviewContainer) {
        mContentView.removeView(subviewContainer);
        mUiController.endActionMode();
    }

    /**
     * Attach the sub window to the content view.
     */
    @Override
    public void attachSubWindow(View container) {
        mContentView.addView(container, COVER_SCREEN_PARAMS);
    }

    void showFakeTitleBar() {
        if (!isFakeTitleBarShowing() && mActiveTabsPage == null &&
                !mActivityPaused) {
            WebView mainView = mUiController.getCurrentWebView();
            // if there is no current WebView, don't show the faked title bar;
            if (mainView == null) {
                return;
            }
            // Do not need to check for null, since the current tab will have
            // at least a main WebView, or we would have returned above.
            if (mUiController.isInCustomActionMode()) {
                // Do not show the fake title bar, while a custom ActionMode
                // (i.e. find or select) is showing.
                return;
            }
            if (mXLargeScreenSize) {
                mContentView.addView(mFakeTitleBar);
                mTabBar.onShowTitleBar();
            } else {
                WindowManager manager = (WindowManager)
                        mActivity.getSystemService(Context.WINDOW_SERVICE);

                // Add the title bar to the window manager so it can receive
                // touches
                // while the menu is up
                WindowManager.LayoutParams params =
                        new WindowManager.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.TYPE_APPLICATION,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP;
                boolean atTop = mainView.getScrollY() == 0;
                params.windowAnimations = atTop ? 0 : R.style.TitleBar;
                manager.addView(mFakeTitleBar, params);
            }
        }
    }

    void hideFakeTitleBar() {
        if (!isFakeTitleBarShowing()) return;
        if (mXLargeScreenSize) {
            mContentView.removeView(mFakeTitleBar);
            mTabBar.onHideTitleBar();
        } else {
            WindowManager.LayoutParams params =
                    (WindowManager.LayoutParams) mFakeTitleBar.getLayoutParams();
            WebView mainView = mUiController.getCurrentWebView();
            // Although we decided whether or not to animate based on the
            // current
            // scroll position, the scroll position may have changed since the
            // fake title bar was displayed. Make sure it has the appropriate
            // animation/lack thereof before removing.
            params.windowAnimations =
                    mainView != null && mainView.getScrollY() == 0 ?
                            0 : R.style.TitleBar;
            WindowManager manager = (WindowManager) mActivity
                    .getSystemService(Context.WINDOW_SERVICE);
            manager.updateViewLayout(mFakeTitleBar, params);
            manager.removeView(mFakeTitleBar);
        }
    }

    boolean isFakeTitleBarShowing() {
        return (mFakeTitleBar.getParent() != null);
    }

    @Override
    public void showComboView(boolean startWithHistory, Bundle extras) {
        mComboView = new CombinedBookmarkHistoryView(mActivity,
                mUiController,
                startWithHistory ?
                        CombinedBookmarkHistoryView.FRAGMENT_ID_HISTORY
                        : CombinedBookmarkHistoryView.FRAGMENT_ID_BOOKMARKS,
                extras);
        mTitleBar.setVisibility(View.GONE);
        hideFakeTitleBar();
        mContentView.addView(mComboView, COVER_SCREEN_PARAMS);
    }

    /**
     * dismiss the ComboPage
     */
    @Override
    public void hideComboView() {
        if (mComboView != null) {
            mContentView.removeView(mComboView);
            mTitleBar.setVisibility(View.VISIBLE);
            mComboView = null;
        }
    }

    @Override
    public void showCustomView(View view,
            WebChromeClient.CustomViewCallback callback) {
        // if a view already exists then immediately terminate the new one
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }

        // Add the custom view to its container.
        mCustomViewContainer.addView(view, COVER_SCREEN_GRAVITY_CENTER);
        mCustomView = view;
        mCustomViewCallback = callback;
        // Hide the content view.
        mContentView.setVisibility(View.GONE);
        // Finally show the custom view container.
        setStatusBarVisibility(false);
        mCustomViewContainer.setVisibility(View.VISIBLE);
        mCustomViewContainer.bringToFront();
    }

    @Override
    public void onHideCustomView() {
        if (mCustomView == null)
            return;

        // Hide the custom view.
        mCustomView.setVisibility(View.GONE);
        // Remove the custom view from its container.
        mCustomViewContainer.removeView(mCustomView);
        mCustomView = null;
        mCustomViewContainer.setVisibility(View.GONE);
        mCustomViewCallback.onCustomViewHidden();
        // Show the content view.
        setStatusBarVisibility(true);
        mContentView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean isCustomViewShowing() {
        return mCustomView != null;
    }

    @Override
    public void showVoiceTitleBar(String title) {
        mTitleBar.setInVoiceMode(true);
        mTitleBar.setDisplayTitle(title);
        mFakeTitleBar.setInVoiceMode(true);
        mFakeTitleBar.setDisplayTitle(title);
    }

    @Override
    public void revertVoiceTitleBar(Tab tab) {
        mTitleBar.setInVoiceMode(false);
        String url = tab.getCurrentUrl();
        mTitleBar.setDisplayTitle(url);
        mFakeTitleBar.setInVoiceMode(false);
        mFakeTitleBar.setDisplayTitle(url);
    }

    // -------------------------------------------------------------------------

    @Override
    public void resetTitleAndRevertLockIcon(Tab tab) {
        tab.revertLockIcon();
        updateLockIconToLatest(tab);
        resetTitleIconAndProgress(tab);
    }

    /**
     * Resets the lock icon. This method is called when we start a new load and
     * know the url to be loaded.
     */
    private void resetLockIcon(Tab tab, String url) {
        // Save the lock-icon state (we revert to it if the load gets cancelled)
        tab.resetLockIcon(url);
        updateLockIconImage(Tab.LOCK_ICON_UNSECURE);
    }

    /**
     * Update the lock icon to correspond to our latest state.
     */
    private void updateLockIconToLatest(Tab t) {
        if (t != null) {
            updateLockIconImage(t.getLockIconType());
        }
    }

    /**
     * Reset the title, favicon, and progress.
     */
    private void resetTitleIconAndProgress(Tab tab) {
        WebView current = tab.getWebView();
        if (current == null) {
            return;
        }
        resetTitleAndIcon(current);
        int progress = current.getProgress();
        current.getWebChromeClient().onProgressChanged(current, progress);
    }

    @Override
    public void resetTitleAndIcon(Tab tab) {
        WebView current = tab.getWebView();
        if (current != null) {
            resetTitleAndIcon(current);
        }
    }

    // Reset the title and the icon based on the given item.
    private void resetTitleAndIcon(WebView view) {
        WebHistoryItem item = view.copyBackForwardList().getCurrentItem();
        Tab tab = mTabControl.getTabFromView(view);
        if (item != null) {
            setUrlTitle(tab, item.getUrl(), item.getTitle());
            setFavicon(tab, item.getFavicon());
        } else {
            setUrlTitle(tab, null, null);
            setFavicon(tab, null);
        }
    }

    /**
     * Updates the lock-icon image in the title-bar.
     */
    private void updateLockIconImage(int lockIconType) {
        Drawable d = null;
        if (lockIconType == Tab.LOCK_ICON_SECURE) {
            d = mSecLockIcon;
        } else if (lockIconType == Tab.LOCK_ICON_MIXED) {
            d = mMixLockIcon;
        }
        mTitleBar.setLock(d);
        mFakeTitleBar.setLock(d);
    }

    // active tabs page

    public void showActiveTabsPage() {
        mActiveTabsPage = new ActiveTabsPage(mActivity, mUiController);
        mTitleBar.setVisibility(View.GONE);
        hideFakeTitleBar();
        mContentView.addView(mActiveTabsPage, COVER_SCREEN_PARAMS);
        mActiveTabsPage.requestFocus();
    }

    /**
     * Remove the active tabs page.
     * @param needToAttach If true, the active tabs page did not attach a tab
     *                     to the content view, so we need to do that here.
     */
    public void removeActiveTabsPage() {
        mContentView.removeView(mActiveTabsPage);
        mTitleBar.setVisibility(View.VISIBLE);
        mActiveTabsPage = null;
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        // hide the fake title bar when CAB is shown
        hideFakeTitleBar();
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        if (inLoad) {
            // the titlebar was removed when the CAB was shown
            // if the page is loading, show it again
            showFakeTitleBar();
        }
    }

    // menu handling callbacks

    @Override
    public void onOptionsMenuOpened() {
        mOptionsMenuOpen = true;
        // options menu opened, show fake title bar
        showFakeTitleBar();
    }

    @Override
    public void onExtendedMenuOpened() {
        // Switching the menu to expanded view, so hide the
        // title bar.
        mExtendedMenuOpen = true;
        hideFakeTitleBar();
    }

    @Override
    public void onOptionsMenuClosed(boolean inLoad) {
        mOptionsMenuOpen = false;
        if (!inLoad) {
            hideFakeTitleBar();
        }
    }

    @Override
    public void onExtendedMenuClosed(boolean inLoad) {
        mExtendedMenuOpen = false;
        if (inLoad) {
            showFakeTitleBar();
        }
    }

    @Override
    public void onContextMenuCreated(Menu menu) {
        hideFakeTitleBar();
    }

    @Override
    public void onContextMenuClosed(Menu menu, boolean inLoad) {
        if (inLoad) {
            showFakeTitleBar();
        }
    }

    @Override
    public void onScroll(boolean titleVisible) {
        if (mTabBar != null) {
            mTabBar.onScroll(titleVisible);
        }
    }

    // error console

    @Override
    public void setShouldShowErrorConsole(Tab tab, boolean flag) {
        ErrorConsoleView errorConsole = tab.getErrorConsole(true);
        if (flag) {
            // Setting the show state of the console will cause it's the layout
            // to be inflated.
            if (errorConsole.numberOfErrors() > 0) {
                errorConsole.showConsole(ErrorConsoleView.SHOW_MINIMIZED);
            } else {
                errorConsole.showConsole(ErrorConsoleView.SHOW_NONE);
            }
            if (errorConsole.getParent() != null) {
                mErrorConsoleContainer.removeView(errorConsole);
            }
            // Now we can add it to the main view.
            mErrorConsoleContainer.addView(errorConsole,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            mErrorConsoleContainer.removeView(errorConsole);
        }
    }

    private void setStatusBarVisibility(boolean visible) {
        int flag = visible ? 0 : WindowManager.LayoutParams.FLAG_FULLSCREEN;
        mActivity.getWindow().setFlags(flag,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void setUrlTitle(Tab tab, String url, String title) {
        if (TextUtils.isEmpty(title)) {
            if (TextUtils.isEmpty(url)) {
                title = mActivity.getResources()
                        .getString(R.string.title_bar_loading);
            } else {
                title = url;
            }
        }
        if (tab.isInVoiceSearchMode()) return;
        if (tab.inForeground()) {
            mTitleBar.setDisplayTitle(url);
            mFakeTitleBar.setDisplayTitle(url);
        }
        if (mXLargeScreenSize) {
            mTabBar.onUrlAndTitle(tab, url, title);
        }
    }

    // Set the favicon in the title bar.
    @Override
    public void setFavicon(Tab tab, Bitmap icon) {
        mTitleBar.setFavicon(icon);
        mFakeTitleBar.setFavicon(icon);
        if (mXLargeScreenSize) {
            mTabBar.onFavicon(tab, icon);
        }
    }
    @Override
    public boolean showsWeb() {
        return mCustomView == null && mActiveTabsPage == null
            && mComboView == null;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (!mXLargeScreenSize) {
            final MenuItem newtab = menu.findItem(R.id.new_tab_menu_id);
            newtab.setEnabled(mUiController.getTabControl().canCreateNewTab());
        }
    }

    // -------------------------------------------------------------------------
    // Helper function for WebChromeClient
    // -------------------------------------------------------------------------

    @Override
    public Bitmap getDefaultVideoPoster() {
        if (mDefaultVideoPoster == null) {
            mDefaultVideoPoster = BitmapFactory.decodeResource(
                    mActivity.getResources(), R.drawable.default_video_poster);
        }
        return mDefaultVideoPoster;
    }

    @Override
    public View getVideoLoadingProgressView() {
        if (mVideoProgressView == null) {
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            mVideoProgressView = inflater.inflate(
                    R.layout.video_loading_progress, null);
        }
        return mVideoProgressView;
    }

}
