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

import com.android.browser.Tab.LockIcon;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/**
 * UI interface definitions
 */
public abstract class BaseUi implements UI, WebViewFactory {

    private static final String LOGTAG = "BaseUi";

    protected static final FrameLayout.LayoutParams COVER_SCREEN_PARAMS =
        new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT);

    protected static final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER =
        new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        Gravity.CENTER);

    Activity mActivity;
    UiController mUiController;
    TabControl mTabControl;
    private Tab mActiveTab;
    private InputMethodManager mInputManager;

    private Drawable mSecLockIcon;
    private Drawable mMixLockIcon;

    private FrameLayout mBrowserFrameLayout;
    protected FrameLayout mContentView;
    private FrameLayout mCustomViewContainer;

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    private CombinedBookmarkHistoryView mComboView;

    private LinearLayout mErrorConsoleContainer = null;

    private Toast mStopToast;

    private boolean mTitleShowing;

    // the default <video> poster
    private Bitmap mDefaultVideoPoster;
    // the video progress view
    private View mVideoProgressView;

    private boolean mActivityPaused;

    public BaseUi(Activity browser, UiController controller) {
        mActivity = browser;
        mUiController = controller;
        mTabControl = controller.getTabControl();
        Resources res = mActivity.getResources();
        mInputManager = (InputMethodManager)
                browser.getSystemService(Activity.INPUT_METHOD_SERVICE);
        mSecLockIcon = res.getDrawable(R.drawable.ic_secure_holo_dark);
        mMixLockIcon = res.getDrawable(R.drawable.ic_partial_secure);

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
        mTitleShowing = false;
    }

    /**
     * common webview initialization
     * @param w the webview to initialize
     */
    protected void initWebViewSettings(WebView w) {
        w.setScrollbarFadingEnabled(true);
        w.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        w.setMapTrackballToArrowKeys(false); // use trackball directly
        // Enable the built-in zoom
        w.getSettings().setBuiltInZoomControls(true);

        // Add this WebView to the settings observer list and update the
        // settings
        final BrowserSettings s = BrowserSettings.getInstance();
        s.addObserver(w.getSettings()).update(s, null);
    }

    private void cancelStopToast() {
        if (mStopToast != null) {
            mStopToast.cancel();
            mStopToast = null;
        }
    }

    // lifecycle

    public void onPause() {
        if (isCustomViewShowing()) {
            onHideCustomView();
        }
        cancelStopToast();
        mActivityPaused = true;
    }

    public void onResume() {
        mActivityPaused = false;
    }

    protected boolean isActivityPaused() {
        return mActivityPaused;
    }

    public void onConfigurationChanged(Configuration config) {
    }

    public abstract void editUrl(boolean clearInput);

    // key handling

    @Override
    public boolean onBackKey() {
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

    // Tab callbacks
    @Override
    public void onTabDataChanged(Tab tab) {
        setUrlTitle(tab);
        setFavicon(tab);
        updateLockIconToLatest(tab);
        updateNavigationState(tab);
    }

    @Override
    public void bookmarkedStatusHasChanged(Tab tab) {
        if (tab.inForeground()) {
            boolean isBookmark = tab.isBookmarkedSite();
            getTitleBar().setCurrentUrlIsBookmark(isBookmark);
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
    public boolean needsRestoreAllTabs() {
        return false;
    }

    @Override
    public void addTab(Tab tab) {
    }

    @Override
    public void setActiveTab(Tab tab) {
        if ((tab != mActiveTab) && (mActiveTab != null)) {
            removeTabFromContentView(mActiveTab);
        }
        mActiveTab = tab;
        attachTabToContentView(tab);
        setShouldShowErrorConsole(tab, mUiController.shouldShowErrorConsole());
        onTabDataChanged(tab);
        onProgressChanged(tab);
        boolean incognito = mActiveTab.getWebView().isPrivateBrowsingEnabled();
        getTitleBar().setIncognitoMode(incognito);
    }

    Tab getActiveTab() {
        return mActiveTab;
    }

    @Override
    public void updateTabs(List<Tab> tabs) {
    }

    @Override
    public void removeTab(Tab tab) {
        if (mActiveTab == tab) {
            removeTabFromContentView(tab);
            mActiveTab = null;
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
        if ((tab == null) || (tab.getWebView() == null)) {
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
        mainView.setNextFocusUpId(R.id.url_focused);
        mainView.setNextFocusDownId(R.id.url_focused);
        mUiController.attachSubWindow(tab);
    }

    private void removeTabFromContentView(Tab tab) {
        hideTitleBar();
        // Remove the container that contains the main WebView.
        WebView mainView = tab.getWebView();
        View container = tab.getViewContainer();
        if (mainView == null) {
            return;
        }
        // Remove the container from the content and then remove the
        // WebView from the container. This will trigger a focus change
        // needed by WebView.
        mainView.setEmbeddedTitleBar(null);
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
    }

    @Override
    public void onSetWebView(Tab tab, WebView webView) {
        View container = tab.getViewContainer();
        if (container == null) {
            // The tab consists of a container view, which contains the main
            // WebView, as well as any other UI elements associated with the tab.
            container = mActivity.getLayoutInflater().inflate(R.layout.tab,
                    null);
            tab.setViewContainer(container);
        }
        if (tab.getWebView() != webView) {
            // Just remove the old one.
            FrameLayout wrapper =
                    (FrameLayout) container.findViewById(R.id.webview_wrapper);
            wrapper.removeView(tab.getWebView());
        }
    }

    /**
     * create a sub window container and webview for the tab
     * Note: this methods operates through side-effects for now
     * it sets both the subView and subViewContainer for the given tab
     * @param tab tab to create the sub window for
     * @param subView webview to be set as a subwindow for the tab
     */
    @Override
    public void createSubWindow(Tab tab, WebView subView) {
        View subViewContainer = mActivity.getLayoutInflater().inflate(
                R.layout.browser_subwindow, null);
        ViewGroup inner = (ViewGroup) subViewContainer
                .findViewById(R.id.inner_container);
        inner.addView(subView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        final ImageButton cancel = (ImageButton) subViewContainer
                .findViewById(R.id.subwindow_close);
        final WebView cancelSubView = subView;
        cancel.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                cancelSubView.getWebChromeClient().onCloseWindow(cancelSubView);
            }
        });
        tab.setSubWebView(subView);
        tab.setSubViewContainer(subViewContainer);
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
        if (container.getParent() != null) {
            // already attached, remove first
            ((ViewGroup) container.getParent()).removeView(container);
        }
        mContentView.addView(container, COVER_SCREEN_PARAMS);
    }

    boolean canShowTitleBar() {
        return !isTitleBarShowing()
                && !isActivityPaused()
                && (getActiveTab() != null)
                && (getActiveTab().getWebView() != null)
                && !mUiController.isInCustomActionMode();
    }

    void showTitleBar() {
        mTitleShowing = true;
    }

    protected void hideTitleBar() {
        mTitleShowing = false;
    }

    protected boolean isTitleBarShowing() {
        return mTitleShowing;
    }

    protected abstract TitleBarBase getTitleBar();

    protected void setTitleGravity(int gravity) {
        getTitleBar().setTitleGravity(gravity);
        Tab tab = getActiveTab();
        if ((tab != null) && (tab.getWebView() != null)) {
            tab.getWebView().setTitleBarGravity(gravity);
        }
    }

    @Override
    public void showVoiceTitleBar(String title) {
        getTitleBar().setInVoiceMode(true);
        getTitleBar().setDisplayTitle(title);
    }

    @Override
    public void revertVoiceTitleBar(Tab tab) {
        getTitleBar().setInVoiceMode(false);
        String url = tab.getUrl();
        getTitleBar().setDisplayTitle(url);
    }

    @Override
    public void showComboView(boolean startWithHistory, Bundle extras) {
        if (mComboView != null) {
            return;
        }
        mComboView = new CombinedBookmarkHistoryView(mActivity,
                mUiController,
                startWithHistory ?
                        CombinedBookmarkHistoryView.FRAGMENT_ID_HISTORY
                        : CombinedBookmarkHistoryView.FRAGMENT_ID_BOOKMARKS,
                extras);
        FrameLayout wrapper =
            (FrameLayout) mContentView.findViewById(R.id.webview_wrapper);
        wrapper.setVisibility(View.GONE);
        hideTitleBar();
        dismissIME();
        if (mActiveTab != null) {
            WebView web = mActiveTab.getWebView();
            mActiveTab.putInBackground();
        }
        mContentView.addView(mComboView, COVER_SCREEN_PARAMS);
    }

    /**
     * dismiss the ComboPage
     */
    @Override
    public void hideComboView() {
        if (mComboView != null) {
            mContentView.removeView(mComboView);
            FrameLayout wrapper =
                (FrameLayout) mContentView.findViewById(R.id.webview_wrapper);
            wrapper.setVisibility(View.VISIBLE);
            mComboView = null;
        }
        if (mActiveTab != null) {
            mActiveTab.putInForeground();
        }
        mActivity.invalidateOptionsMenu();
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

    protected void dismissIME() {
        if (mInputManager.isActive()) {
            mInputManager.hideSoftInputFromWindow(mContentView.getWindowToken(),
                    0);
        }
    }

    @Override
    public boolean showsWeb() {
        return mCustomView == null
            && mComboView == null;
    }

    // -------------------------------------------------------------------------

    protected void updateNavigationState(Tab tab) {
    }

    /**
     * Update the lock icon to correspond to our latest state.
     */
    protected void updateLockIconToLatest(Tab t) {
        if (t != null && t.inForeground()) {
            updateLockIconImage(t.getLockIconType());
        }
    }

    /**
     * Updates the lock-icon image in the title-bar.
     */
    private void updateLockIconImage(LockIcon lockIconType) {
        Drawable d = null;
        if (lockIconType == LockIcon.LOCK_ICON_SECURE) {
            d = mSecLockIcon;
        } else if (lockIconType == LockIcon.LOCK_ICON_MIXED) {
            d = mMixLockIcon;
        }
        getTitleBar().setLock(d);
    }

    protected void setUrlTitle(Tab tab) {
        String url = tab.getUrl();
        String title = tab.getTitle();
        if (TextUtils.isEmpty(title)) {
            title = url;
        }
        if (tab.isInVoiceSearchMode()) return;
        if (tab.inForeground()) {
            getTitleBar().setDisplayTitle(url);
        }
    }

    // Set the favicon in the title bar.
    protected void setFavicon(Tab tab) {
        if (tab.inForeground()) {
            Bitmap icon = tab.getFavicon();
            getTitleBar().setFavicon(icon);
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        if (inLoad) {
            // the titlebar was removed when the CAB was shown
            // if the page is loading, show it again
            showTitleBar();
        }
    }

    // active tabs page

    public void showActiveTabsPage() {
    }

    /**
     * Remove the active tabs page.
     */
    public void removeActiveTabsPage() {
    }

    // menu handling callbacks

    @Override
    public void onOptionsMenuOpened() {
    }

    @Override
    public void onExtendedMenuOpened() {
    }

    @Override
    public void onOptionsMenuClosed(boolean inLoad) {
    }

    @Override
    public void onExtendedMenuClosed(boolean inLoad) {
    }

    @Override
    public void onContextMenuCreated(Menu menu) {
    }

    @Override
    public void onContextMenuClosed(Menu menu, boolean inLoad) {
    }

    // error console

    @Override
    public void setShouldShowErrorConsole(Tab tab, boolean flag) {
        if (tab == null) return;
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
        WindowManager.LayoutParams params = mActivity.getWindow().getAttributes();
        params.systemUiVisibility = visible ? View.STATUS_BAR_VISIBLE : View.STATUS_BAR_HIDDEN;
        mActivity.getWindow().setAttributes(params);
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

    @Override
    public void showMaxTabsWarning() {
        Toast warning = Toast.makeText(mActivity,
                mActivity.getString(R.string.max_tabs_warning),
                Toast.LENGTH_SHORT);
        warning.show();
    }

}
