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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
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

import com.android.browser.Tab.LockIcon;
import com.android.internal.view.menu.MenuBuilder;

import java.util.List;

/**
 * UI interface definitions
 */
public abstract class BaseUi implements UI, WebViewFactory, OnTouchListener {

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

    private static final int MSG_HIDE_TITLEBAR = 1;
    private static final int HIDE_TITLEBAR_DELAY = 1500; // in ms

    Activity mActivity;
    UiController mUiController;
    TabControl mTabControl;
    protected Tab mActiveTab;
    private InputMethodManager mInputManager;

    private Drawable mSecLockIcon;
    private Drawable mMixLockIcon;
    protected Drawable mGenericFavicon;

    private FrameLayout mBrowserFrameLayout;
    protected FrameLayout mContentView;
    protected FrameLayout mCustomViewContainer;

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private int mOriginalOrientation;

    private CombinedBookmarkHistoryView mComboView;

    private LinearLayout mErrorConsoleContainer = null;

    private Toast mStopToast;

    private float mInitialY;
    private int mTitlebarScrollTriggerSlop;

    // the default <video> poster
    private Bitmap mDefaultVideoPoster;
    // the video progress view
    private View mVideoProgressView;

    private boolean mActivityPaused;
    protected boolean mUseQuickControls;

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
        setFullscreen(BrowserSettings.getInstance().useFullscreen());
        mGenericFavicon = res.getDrawable(
                R.drawable.app_web_browser_sm);
        ViewConfiguration config = ViewConfiguration.get(browser);
        mTitlebarScrollTriggerSlop = Math.max(
                config.getScaledOverflingDistance(),
                config.getScaledOverscrollDistance());
        mTitlebarScrollTriggerSlop = Math.max(mTitlebarScrollTriggerSlop,
                config.getScaledTouchSlop());
    }

    @Override
    public WebView createWebView(boolean privateBrowsing) {
        // Create a new WebView
        BrowserWebView w = new BrowserWebView(mActivity, null,
                android.R.attr.webViewStyle, privateBrowsing);
        initWebViewSettings(w);
        return w;
    }

    @Override
    public WebView createSubWebView(boolean privateBrowsing) {
        return createWebView(privateBrowsing);
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
        boolean supportsMultiTouch = mActivity.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH);
        w.getSettings().setDisplayZoomControls(!supportsMultiTouch);
        w.setExpandedTileBounds(true);  // smoother scrolling

        // Add this WebView to the settings observer list and update the
        // settings
        final BrowserSettings s = BrowserSettings.getInstance();
        s.startManagingSettings(w.getSettings());
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

    @Override
    public boolean onMenuKey() {
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
        return true;
    }

    @Override
    public void addTab(Tab tab) {
    }

    @Override
    public void setActiveTab(final Tab tab) {
        mHandler.removeMessages(MSG_HIDE_TITLEBAR);
        if ((tab != mActiveTab) && (mActiveTab != null)) {
            removeTabFromContentView(mActiveTab);
            WebView web = mActiveTab.getWebView();
            if (web != null) {
                web.setOnTouchListener(null);
            }
        }
        mActiveTab = tab;
        WebView web = mActiveTab.getWebView();
        if (web != null && !mUseQuickControls) {
            web.setOnTouchListener(this);
        }
        attachTabToContentView(tab);
        setShouldShowErrorConsole(tab, mUiController.shouldShowErrorConsole());
        onTabDataChanged(tab);
        onProgressChanged(tab);
        boolean incognito = mActiveTab.getWebView().isPrivateBrowsingEnabled();
        getTitleBar().setIncognitoMode(incognito);
        updateAutoLogin(tab, false);
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

    protected void attachTabToContentView(Tab tab) {
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

    protected void refreshWebView() {
        WebView web = getWebView();
        if (web != null) {
            web.invalidate();
        }
    }

    public void editUrl(boolean clearInput) {
        if (mUiController.isInCustomActionMode()) {
            mUiController.endActionMode();
        }
        showTitleBar();
        getTitleBar().startEditingUrl(clearInput);
    }

    boolean canShowTitleBar() {
        return !isTitleBarShowing()
                && !isActivityPaused()
                && (getActiveTab() != null)
                && (getWebView() != null)
                && !mUiController.isInCustomActionMode();
    }

    void showTitleBar() {
        if (canShowTitleBar()) {
            getTitleBar().show();
        }
    }

    protected void hideTitleBar() {
        if (getTitleBar().isShowing()) {
            getTitleBar().hide();
        }
    }

    protected boolean isTitleBarShowing() {
        return getTitleBar().isShowing();
    }

    public boolean isEditingUrl() {
        return getTitleBar().isEditingUrl();
    }

    protected abstract TitleBarBase getTitleBar();

    protected void setTitleGravity(int gravity) {
        WebView web = getWebView();
        if (web != null) {
            web.setTitleBarGravity(gravity);
        }
    }

    @Override
    public void showVoiceTitleBar(String title, List<String> results) {
        getTitleBar().setInVoiceMode(true, results);
        getTitleBar().setDisplayTitle(title);
    }

    @Override
    public void revertVoiceTitleBar(Tab tab) {
        getTitleBar().setInVoiceMode(false, null);
        String url = tab.getUrl();
        getTitleBar().setDisplayTitle(url);
    }

    @Override
    public void registerDropdownChangeListener(DropdownChangeListener d) {
        getTitleBar().registerDropdownChangeListener(d);
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
        getTitleBar().stopEditingUrl();
        dismissIME();
        hideTitleBar();
        if (mActiveTab != null) {
            mActiveTab.putInBackground();
        }
        mComboView.setAlpha(0f);
        ObjectAnimator anim = ObjectAnimator.ofFloat(mComboView, "alpha", 0f, 1f);
        Resources res = mActivity.getResources();
        anim.setDuration(res.getInteger(R.integer.comboViewFadeInDuration));
        anim.start();
        mContentView.addView(mComboView, COVER_SCREEN_PARAMS);
    }

    public boolean isComboViewShowing() {
        return (mComboView != null);
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
    public void showCustomView(View view, int requestedOrientation,
            WebChromeClient.CustomViewCallback callback) {
        // if a view already exists then immediately terminate the new one
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }

        mOriginalOrientation = mActivity.getRequestedOrientation();

        // Add the custom view to its container.
        mCustomViewContainer.addView(view, COVER_SCREEN_GRAVITY_CENTER);
        mCustomView = view;
        mCustomViewCallback = callback;
        // Hide the content view.
        mContentView.setVisibility(View.GONE);
        // Finally show the custom view container.
        setStatusBarVisibility(false);
        mActivity.setRequestedOrientation(requestedOrientation);
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
        mActivity.setRequestedOrientation(mOriginalOrientation);
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

    @Override
    public void showAutoLogin(Tab tab) {
        updateAutoLogin(tab, true);
    }

    @Override
    public void hideAutoLogin(Tab tab) {
        updateAutoLogin(tab, true);
    }

    // -------------------------------------------------------------------------

    protected void updateNavigationState(Tab tab) {
    }

    protected void updateAutoLogin(Tab tab, boolean animate) {
        getTitleBar().updateAutoLogin(tab, animate);
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

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

    protected void captureTab(final Tab tab) {
        captureTab(tab,
                (int) mActivity.getResources()
                        .getDimension(R.dimen.qc_thumb_width),
                (int) mActivity.getResources()
                        .getDimension(R.dimen.qc_thumb_height));
    }

    protected void captureTab(final Tab tab, int width, int height) {
        if ((tab == null) || (tab.getWebView() == null)) return;
        Bitmap sshot = Controller.createScreenshot(tab, width, height);
        tab.setScreenshot(sshot);
    }

    protected WebView getWebView() {
        if (mActiveTab != null) {
            return mActiveTab.getWebView();
        } else {
            return null;
        }
    }

    protected Menu getMenu() {
        MenuBuilder menu = new MenuBuilder(mActivity);
        mActivity.getMenuInflater().inflate(R.menu.browser, menu);
        return menu;
    }

    public void setFullscreen(boolean enabled) {
        if (enabled) {
            mActivity.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            mActivity.getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    protected Drawable getFaviconDrawable(Bitmap icon) {
        Drawable[] array = new Drawable[3];
        array[0] = new PaintDrawable(Color.BLACK);
        PaintDrawable p = new PaintDrawable(Color.WHITE);
        array[1] = p;
        if (icon == null) {
            array[2] = mGenericFavicon;
        } else {
            array[2] = new BitmapDrawable(icon);
        }
        LayerDrawable d = new LayerDrawable(array);
        d.setLayerInset(1, 1, 1, 1, 1);
        d.setLayerInset(2, 2, 2, 2, 2);
        return d;
    }

    public boolean isLoading() {
        return mActiveTab != null ? mActiveTab.inPageLoad() : false;
    }

    /**
     * Suggest to the UI that the title bar can be hidden. The UI will then
     * decide whether or not to hide based off a number of factors, such
     * as if the user is editing the URL bar or if the page is loading
     */
    public void suggestHideTitleBar() {
        if (!isLoading() && !isEditingUrl()) {
            hideTitleBar();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mInitialY = event.getY();
            break;
        case MotionEvent.ACTION_MOVE:
            WebView web = (WebView) v;
            if (!isTitleBarShowing()
                    && web.getVisibleTitleHeight() == 0
                    && event.getY() > (mInitialY + mTitlebarScrollTriggerSlop)) {
                mHandler.removeMessages(MSG_HIDE_TITLEBAR);
                showTitleBar();
            } else if (event.getY() < mInitialY) {
                mInitialY = event.getY();
            }
            break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            if (isTitleBarShowing()) {
                Message msg = Message.obtain(mHandler, MSG_HIDE_TITLEBAR);
                mHandler.sendMessageDelayed(msg, HIDE_TITLEBAR_DELAY);
            }
            break;
        }
        return false;
    }

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_HIDE_TITLEBAR) {
                suggestHideTitleBar();
            }
        }
    };
}
