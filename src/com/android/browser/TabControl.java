/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Picture;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.webkit.HttpAuthHandler;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Vector;

class TabControl {
    // Log Tag
    private static final String LOGTAG = "TabControl";
    // Maximum number of tabs.
    static final int MAX_TABS = 8;
    // Static instance of an empty callback.
    private static final WebViewClient mEmptyClient =
            new WebViewClient();
    // Instance of BackgroundChromeClient for background tabs.
    private final BackgroundChromeClient mBackgroundChromeClient =
            new BackgroundChromeClient();
    // Private array of WebViews that are used as tabs.
    private ArrayList<Tab> mTabs = new ArrayList<Tab>(MAX_TABS);
    // Queue of most recently viewed tabs.
    private ArrayList<Tab> mTabQueue = new ArrayList<Tab>(MAX_TABS);
    // Current position in mTabs.
    private int mCurrentTab = -1;
    // A private instance of BrowserActivity to interface with when adding and
    // switching between tabs.
    private final BrowserActivity mActivity;
    // Inflation service for making subwindows.
    private final LayoutInflater mInflateService;
    // Subclass of WebViewClient used in subwindows to notify the main
    // WebViewClient of certain WebView activities.
    private class SubWindowClient extends WebViewClient {
        // The main WebViewClient.
        private final WebViewClient mClient;

        SubWindowClient(WebViewClient client) {
            mClient = client;
        }
        @Override
        public void doUpdateVisitedHistory(WebView view, String url,
                boolean isReload) {
            mClient.doUpdateVisitedHistory(view, url, isReload);
        }
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return mClient.shouldOverrideUrlLoading(view, url);
        }
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler,
                SslError error) {
            mClient.onReceivedSslError(view, handler, error);
        }
        @Override
        public void onReceivedHttpAuthRequest(WebView view,
                HttpAuthHandler handler, String host, String realm) {
            mClient.onReceivedHttpAuthRequest(view, handler, host, realm);
        }
        @Override
        public void onFormResubmission(WebView view, Message dontResend,
                Message resend) {
            mClient.onFormResubmission(view, dontResend, resend);
        }
        @Override
        public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            mClient.onReceivedError(view, errorCode, description, failingUrl);
        }
    }
    // Subclass of WebChromeClient to display javascript dialogs.
    private class SubWindowChromeClient extends WebChromeClient {
        // This subwindow's tab.
        private final Tab mTab;
        // The main WebChromeClient.
        private final WebChromeClient mClient;

        SubWindowChromeClient(Tab t, WebChromeClient client) {
            mTab = t;
            mClient = client;
        }
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            mClient.onProgressChanged(view, newProgress);
        }
        @Override
        public boolean onCreateWindow(WebView view, boolean dialog,
                boolean userGesture, android.os.Message resultMsg) {
            return mClient.onCreateWindow(view, dialog, userGesture, resultMsg);
        }
        @Override
        public void onCloseWindow(WebView window) {
            if (Browser.DEBUG && window != mTab.mSubView) {
                throw new AssertionError("Can't close the window");
            }
            mActivity.dismissSubWindow(mTab);
        }
    }
    // Background WebChromeClient for focusing tabs
    private class BackgroundChromeClient extends WebChromeClient {
        @Override
        public void onRequestFocus(WebView view) {
            Tab t = getTabFromView(view);
            if (t != getCurrentTab()) {
                mActivity.showTab(t);
            }
        }
    }

    // Extra saved information for displaying the tab in the picker.
    public static class PickerData {
        String  mUrl;
        String  mTitle;
        float   mScale;
        int     mScrollX;
        int     mScrollY;
        int     mWidth;
        Picture mPicture;
        // This can be null. When a new picture comes in, this view should be
        // invalidated to show the new picture.
        FakeWebView mFakeWebView;
    }

    /**
     * Private class for maintaining Tabs with a main WebView and a subwindow.
     */
    public class Tab implements WebView.PictureListener {
        // Main WebView
        private WebView mMainView;
        // Subwindow WebView
        private WebView mSubView;
        // Subwindow container
        private View mSubViewContainer;
        // Subwindow callback
        private SubWindowClient mSubViewClient;
        // Subwindow chrome callback
        private SubWindowChromeClient mSubViewChromeClient;
        // Saved bundle for when we are running low on memory. It contains the
        // information needed to restore the WebView if the user goes back to
        // the tab.
        private Bundle mSavedState;
        // Data used when displaying the tab in the picker.
        private PickerData mPickerData;

        // Parent Tab. This is the Tab that created this Tab, or null
        // if the Tab was created by the UI
        private Tab mParentTab;
        // Tab that constructed by this Tab. This is used when this
        // Tab is destroyed, it clears all mParentTab values in the 
        // children.
        private Vector<Tab> mChildTabs;

        private Boolean mCloseOnExit;
        // Application identifier used to find tabs that another application
        // wants to reuse.
        private String mAppId;
        // Keep the original url around to avoid killing the old WebView if the
        // url has not changed.
        private String mOriginalUrl;

        // Construct a new tab
        private Tab(WebView w, boolean closeOnExit, String appId, String url) {
            mMainView = w;
            mCloseOnExit = closeOnExit;
            mAppId = appId;
            mOriginalUrl = url;
        }

        /**
         * Return the top window of this tab; either the subwindow if it is not
         * null or the main window.
         * @return The top window of this tab.
         */
        public WebView getTopWindow() {
            if (mSubView != null) {
                return mSubView;
            }
            return mMainView;
        }

        /**
         * Return the main window of this tab. Note: if a tab is freed in the
         * background, this can return null. It is only guaranteed to be 
         * non-null for the current tab.
         * @return The main WebView of this tab.
         */
        public WebView getWebView() {
            return mMainView;
        }

        /**
         * Return the subwindow of this tab or null if there is no subwindow.
         * @return The subwindow of this tab or null.
         */
        public WebView getSubWebView() {
            return mSubView;
        }

        /**
         * Return the subwindow container of this tab or null if there is no
         * subwindow.
         * @return The subwindow's container View.
         */
        public View getSubWebViewContainer() {
            return mSubViewContainer;
        }

        /**
         * Get the url of this tab.  Valid after calling populatePickerData, but
         * before calling wipePickerData, or if the webview has been destroyed.
         * 
         * @return The WebView's url or null.
         */
        public String getUrl() {
            if (mPickerData != null) {
                return mPickerData.mUrl;
            }
            return null;
        }

        /**
         * Get the title of this tab.  Valid after calling populatePickerData, 
         * but before calling wipePickerData, or if the webview has been 
         * destroyed.  If the url has no title, use the url instead.
         * 
         * @return The WebView's title (or url) or null.
         */
        public String getTitle() {
            if (mPickerData != null) {
                return mPickerData.mTitle;
            }
            return null;
        }

        /**
         * Returns the picker data.
         */
        public PickerData getPickerData() {
            return mPickerData;
        }

        private void setParentTab(Tab parent) {
            mParentTab = parent;
            // This tab may have been freed due to low memory. If that is the
            // case, the parent tab index is already saved. If we are changing
            // that index (most likely due to removing the parent tab) we must
            // update the parent tab index in the saved Bundle.
            if (mSavedState != null) {
                if (parent == null) {
                    mSavedState.remove(PARENTTAB);
                } else {
                    mSavedState.putInt(PARENTTAB, getTabIndex(parent));
                }
            }
        }
        
        /**
         * When a Tab is created through the content of another Tab, then 
         * we associate the Tabs. 
         * @param child the Tab that was created from this Tab
         */
        public void addChildTab(Tab child) {
            if (mChildTabs == null) {
                mChildTabs = new Vector<Tab>();
            }
            mChildTabs.add(child);
            child.setParentTab(this);
        }
        
        private void removeFromTree() {
            // detach the children
            if (mChildTabs != null) {
                for(Tab t : mChildTabs) {
                    t.setParentTab(null);
                }
            }
            
            // Find myself in my parent list
            if (mParentTab != null) {
                mParentTab.mChildTabs.remove(this);
            }
        }
        
        /**
         * If this Tab was created through another Tab, then this method
         * returns that Tab.
         * @return the Tab parent or null
         */
        public Tab getParentTab() {
            return mParentTab;
        }

        /**
         * Return whether this tab should be closed when it is backing out of
         * the first page.
         * @return TRUE if this tab should be closed when exit.
         */
        public boolean closeOnExit() {
            return mCloseOnExit;
        }

        public void onNewPicture(WebView view, Picture p) {
            if (mPickerData == null) {
                return;
            }

            mPickerData.mPicture = p;
            // Tell the FakeWebView to redraw.
            if (mPickerData.mFakeWebView != null) {
                mPickerData.mFakeWebView.invalidate();
            }
        }
    };

    // Directory to store thumbnails for each WebView.
    private final File mThumbnailDir;

    /**
     * Construct a new TabControl object that interfaces with the given
     * BrowserActivity instance.
     * @param activity A BrowserActivity instance that TabControl will interface
     *                 with.
     */
    TabControl(BrowserActivity activity) {
        mActivity = activity;
        mInflateService =
                ((LayoutInflater) activity.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE));
        mThumbnailDir = activity.getDir("thumbnails", 0);
    }

    File getThumbnailDir() {
        return mThumbnailDir;
    }

    BrowserActivity getBrowserActivity() {
        return mActivity;
    }

    /**
     * Return the current tab's main WebView. This will always return the main
     * WebView for a given tab and not a subwindow.
     * @return The current tab's WebView.
     */
    WebView getCurrentWebView() {
        Tab t = getTab(mCurrentTab);
        if (t == null) {
            return null;
        }
        return t.mMainView;
    }

    /**
     * Return the current tab's top-level WebView. This can return a subwindow
     * if one exists.
     * @return The top-level WebView of the current tab.
     */
    WebView getCurrentTopWebView() {
        Tab t = getTab(mCurrentTab);
        if (t == null) {
            return null;
        }
        return t.mSubView != null ? t.mSubView : t.mMainView;
    }

    /**
     * Return the current tab's subwindow if it exists.
     * @return The subwindow of the current tab or null if it doesn't exist.
     */
    WebView getCurrentSubWindow() {
        Tab t = getTab(mCurrentTab);
        if (t == null) {
            return null;
        }
        return t.mSubView;
    }

    /**
     * Return the tab at the specified index.
     * @return The Tab for the specified index or null if the tab does not
     *         exist.
     */
    Tab getTab(int index) {
        if (index >= 0 && index < mTabs.size()) {
            return mTabs.get(index);
        }
        return null;
    }

    /**
     * Return the current tab.
     * @return The current tab.
     */
    Tab getCurrentTab() {
        return getTab(mCurrentTab);
    }

    /**
     * Return the current tab index.
     * @return The current tab index
     */
    int getCurrentIndex() {
        return mCurrentTab;
    }
    
    /**
     * Given a Tab, find it's index
     * @param Tab to find
     * @return index of Tab or -1 if not found
     */
    int getTabIndex(Tab tab) {
        return mTabs.indexOf(tab);
    }

    /**
     * Create a new tab.
     * @return The newly createTab or null if we have reached the maximum
     *         number of open tabs.
     */
    Tab createNewTab(boolean closeOnExit, String appId, String url) {
        int size = mTabs.size();
        // Return false if we have maxed out on tabs
        if (MAX_TABS == size) {
            return null;
        }
        final WebView w = createNewWebView();
        // Create a new tab and add it to the tab list
        Tab t = new Tab(w, closeOnExit, appId, url);
        mTabs.add(t);
        // Initially put the tab in the background.
        putTabInBackground(t);
        return t;
    }

    /**
     * Create a new tab with default values for closeOnExit(false),
     * appId(null), and url(null).
     */
    Tab createNewTab() {
        return createNewTab(false, null, null);
    }

    /**
     * Remove the tab from the list. If the tab is the current tab shown, the
     * last created tab will be shown.
     * @param t The tab to be removed.
     */
    boolean removeTab(Tab t) {
        if (t == null) {
            return false;
        }
        // Only remove the tab if it is the current one.
        if (getCurrentTab() == t) {
            putTabInBackground(t);
        }

        // Only destroy the WebView if it still exists.
        if (t.mMainView != null) {
            // Take down the sub window.
            dismissSubWindow(t);
            // Remove the WebView's settings from the BrowserSettings list of
            // observers.
            BrowserSettings.getInstance().deleteObserver(
                    t.mMainView.getSettings());
            // Destroy the main view and subview
            t.mMainView.destroy();
            t.mMainView = null;
        }
        // clear it's references to parent and children
        t.removeFromTree();
        
        // Remove it from our list of tabs.
        mTabs.remove(t);

        // The tab indices have shifted, update all the saved state so we point
        // to the correct index.
        for (Tab tab : mTabs) {
            if (tab.mChildTabs != null) {
                for (Tab child : tab.mChildTabs) {
                    child.setParentTab(tab);
                }
            }
        }


        // This tab may have been pushed in to the background and then closed.
        // If the saved state contains a picture file, delete the file.
        if (t.mSavedState != null) {
            if (t.mSavedState.containsKey(CURRPICTURE)) {
                new File(t.mSavedState.getString(CURRPICTURE)).delete();
            }
        }

        // Remove it from the queue of viewed tabs.
        mTabQueue.remove(t);
        mCurrentTab = -1;
        return true;
    }

    /**
     * Clear the back/forward list for all the current tabs.
     */
    void clearHistory() {
        int size = getTabCount();
        for (int i = 0; i < size; i++) {
            Tab t = mTabs.get(i);
            // TODO: if a tab is freed due to low memory, its history is not
            // cleared here.
            if (t.mMainView != null) {
                t.mMainView.clearHistory();
            }
            if (t.mSubView != null) {
                t.mSubView.clearHistory();
            }
        }
    }

    /**
     * Destroy all the tabs and subwindows
     */
    void destroy() {
        BrowserSettings s = BrowserSettings.getInstance();
        for (Tab t : mTabs) {
            if (t.mMainView != null) {
                dismissSubWindow(t);
                s.deleteObserver(t.mMainView.getSettings());
                t.mMainView.destroy();
                t.mMainView = null;
            }
        }
        mTabs.clear();
        mTabQueue.clear();
    }

    /**
     * Returns the number of tabs created.
     * @return The number of tabs created.
     */
    int getTabCount() {
        return mTabs.size();
    }

    // Used for saving and restoring each Tab
    private static final String WEBVIEW = "webview";
    private static final String NUMTABS = "numTabs";
    private static final String CURRTAB = "currentTab";
    private static final String CURRURL = "currentUrl";
    private static final String CURRTITLE = "currentTitle";
    private static final String CURRWIDTH = "currentWidth";
    private static final String CURRPICTURE = "currentPicture";
    private static final String CLOSEONEXIT = "closeonexit";
    private static final String PARENTTAB = "parentTab";
    private static final String APPID = "appid";
    private static final String ORIGINALURL = "originalUrl";

    /**
     * Save the state of all the Tabs.
     * @param outState The Bundle to save the state to.
     */
    void saveState(Bundle outState) {
        final int numTabs = getTabCount();
        outState.putInt(NUMTABS, numTabs);
        final int index = getCurrentIndex();
        outState.putInt(CURRTAB, (index >= 0 && index < numTabs) ? index : 0);
        for (int i = 0; i < numTabs; i++) {
            final Tab t = getTab(i);
            if (saveState(t)) {
                outState.putBundle(WEBVIEW + i, t.mSavedState);
            }
        }
    }

    /**
     * Restore the state of all the tabs.
     * @param inState The saved state of all the tabs.
     * @return True if there were previous tabs that were restored. False if
     *         there was no saved state or restoring the state failed.
     */
    boolean restoreState(Bundle inState) {
        final int numTabs = (inState == null)
                ? -1 : inState.getInt(NUMTABS, -1);
        if (numTabs == -1) {
            return false;
        } else {
            final int currentTab = inState.getInt(CURRTAB, -1);
            for (int i = 0; i < numTabs; i++) {
                if (i == currentTab) {
                    Tab t = createNewTab();
                    // Me must set the current tab before restoring the state
                    // so that all the client classes are set.
                    setCurrentTab(t);
                    if (!restoreState(inState.getBundle(WEBVIEW + i), t)) {
                        Log.w(LOGTAG, "Fail in restoreState, load home page.");
                        t.mMainView.loadUrl(BrowserSettings.getInstance()
                                .getHomePage());
                    }
                } else {
                    // Create a new tab and don't restore the state yet, add it
                    // to the tab list
                    Tab t = new Tab(null, false, null, null);
                    t.mSavedState = inState.getBundle(WEBVIEW + i);
                    if (t.mSavedState != null) {
                        populatePickerDataFromSavedState(t);
                        // Need to maintain the app id and original url so we
                        // can possibly reuse this tab.
                        t.mAppId = t.mSavedState.getString(APPID);
                        t.mOriginalUrl = t.mSavedState.getString(ORIGINALURL);
                    }
                    mTabs.add(t);
                    mTabQueue.add(t);
                }
            }
            // Rebuild the tree of tabs. Do this after all tabs have been
            // created/restored so that the parent tab exists.
            for (int i = 0; i < numTabs; i++) {
                final Bundle b = inState.getBundle(WEBVIEW + i);
                final Tab t = getTab(i);
                if (b != null && t != null) {
                    final int parentIndex = b.getInt(PARENTTAB, -1);
                    if (parentIndex != -1) {
                        final Tab parent = getTab(parentIndex);
                        if (parent != null) {
                            parent.addChildTab(t);
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Free the memory in this order, 1) free the background tab; 2) free the
     * WebView cache;
     */
    void freeMemory() {
        // free the least frequently used background tab
        Tab t = getLeastUsedTab();
        if (t != null) {
            Log.w(LOGTAG, "Free a tab in the browser");
            freeTab(t);
            // force a gc
            System.gc();
            return;
        }

        // free the WebView cache
        Log.w(LOGTAG, "Free WebView cache");
        WebView view = getCurrentWebView();
        if (view != null) {
            view.clearCache(false);
        }
        // force a gc
        System.gc();
    }

    private Tab getLeastUsedTab() {
        // Don't do anything if we only have 1 tab.
        if (getTabCount() == 1) {
            return null;
        }

        // Rip through the queue starting at the beginning and teardown the
        // next available tab.
        Tab t = null;
        int i = 0;
        final int queueSize = mTabQueue.size();
        if (queueSize == 0) {
            return null;
        }
        do {
            t = mTabQueue.get(i++);
        } while (i < queueSize && t != null && t.mMainView == null);

        // Don't do anything if the last remaining tab is the current one or if
        // the last tab has been freed already.
        if (t == getCurrentTab() || t.mMainView == null) {
            return null;
        }

        return t;
    }

    private void freeTab(Tab t) {
        // Store the WebView's state.
        saveState(t);

        // Tear down the tab.
        dismissSubWindow(t);
        // Remove the WebView's settings from the BrowserSettings list of
        // observers.
        BrowserSettings.getInstance().deleteObserver(t.mMainView.getSettings());
        t.mMainView.destroy();
        t.mMainView = null;
    }

    /**
     * Create a new subwindow unless a subwindow already exists.
     * @return True if a new subwindow was created. False if one already exists.
     */
    void createSubWindow() {
        Tab t = getTab(mCurrentTab);
        if (t != null && t.mSubView == null) {
            final View v = mInflateService.inflate(R.layout.browser_subwindow, null);
            final WebView w = (WebView) v.findViewById(R.id.webview);
            w.setMapTrackballToArrowKeys(false); // use trackball directly
            final SubWindowClient subClient =
                    new SubWindowClient(mActivity.getWebViewClient());
            final SubWindowChromeClient subChromeClient =
                    new SubWindowChromeClient(t,
                            mActivity.getWebChromeClient());
            w.setWebViewClient(subClient);
            w.setWebChromeClient(subChromeClient);
            w.setDownloadListener(mActivity);
            w.setOnCreateContextMenuListener(mActivity);
            final BrowserSettings s = BrowserSettings.getInstance();
            s.addObserver(w.getSettings()).update(s, null);
            t.mSubView = w;
            t.mSubViewClient = subClient;
            t.mSubViewChromeClient = subChromeClient;
            // FIXME: I really hate having to know the name of the view
            // containing the webview.
            t.mSubViewContainer = v.findViewById(R.id.subwindow_container);
            final ImageButton cancel =
                    (ImageButton) v.findViewById(R.id.subwindow_close);
            cancel.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        subChromeClient.onCloseWindow(w);
                    }
                });
        }
    }

    /**
     * Show the tab that contains the given WebView.
     * @param view The WebView used to find the tab.
     */
    Tab getTabFromView(WebView view) {
        final int size = getTabCount();
        for (int i = 0; i < size; i++) {
            final Tab t = getTab(i);
            if (t.mSubView == view || t.mMainView == view) {
                return t;
            }
        }
        return null;
    }

    /**
     * Return the tab with the matching application id.
     * @param id The application identifier.
     */
    Tab getTabFromId(String id) {
        if (id == null) {
            return null;
        }
        final int size = getTabCount();
        for (int i = 0; i < size; i++) {
            final Tab t = getTab(i);
            if (id.equals(t.mAppId)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Recreate the main WebView of the given tab. Returns true if the WebView
     * was deleted.
     */
    boolean recreateWebView(Tab t, String url) {
        final WebView w = t.mMainView;
        if (w != null) {
            if (url != null && url.equals(t.mOriginalUrl)) {
                // The original url matches the current url. Just go back to the
                // first history item so we can load it faster than if we
                // rebuilt the WebView.
                final WebBackForwardList list = w.copyBackForwardList();
                if (list != null) {
                    w.goBackOrForward(-list.getCurrentIndex());
                    w.clearHistory(); // maintains the current page.
                    return false;
                }
            }
            // Remove the settings object from the global settings and destroy
            // the WebView.
            BrowserSettings.getInstance().deleteObserver(
                    t.mMainView.getSettings());
            t.mMainView.destroy();
        }
        // Create a new WebView. If this tab is the current tab, we need to put
        // back all the clients so force it to be the current tab.
        t.mMainView = createNewWebView();
        if (getCurrentTab() == t) {
            setCurrentTab(t, true);
        }
        // Clear the saved state except for the app id and close-on-exit
        // values.
        t.mSavedState = null;
        t.mPickerData = null;
        // Save the new url in order to avoid deleting the WebView.
        t.mOriginalUrl = url;
        return true;
    }

    /**
     * Creates a new WebView and registers it with the global settings.
     */
    private WebView createNewWebView() {
        // Create a new WebView
        WebView w = new WebView(mActivity);
        w.setMapTrackballToArrowKeys(false); // use trackball directly
        // Enable the built-in zoom
        w.getSettings().setBuiltInZoomControls(true);
        // Add this WebView to the settings observer list and update the
        // settings
        final BrowserSettings s = BrowserSettings.getInstance();
        s.addObserver(w.getSettings()).update(s, null);
        return w;
    }

    /**
     * Put the current tab in the background and set newTab as the current tab.
     * @param newTab The new tab. If newTab is null, the current tab is not
     *               set.
     */
    boolean setCurrentTab(Tab newTab) {
        return setCurrentTab(newTab, false);
    }

    /**
     * If force is true, this method skips the check for newTab == current.
     */
    private boolean setCurrentTab(Tab newTab, boolean force) {
        Tab current = getTab(mCurrentTab);
        if (current == newTab && !force) {
            return true;
        }
        if (current != null) {
            // Remove the current WebView and the container of the subwindow
            putTabInBackground(current);
        }

        if (newTab == null) {
            return false;
        }

        // Move the newTab to the end of the queue
        int index = mTabQueue.indexOf(newTab);
        if (index != -1) {
            mTabQueue.remove(index);
        }
        mTabQueue.add(newTab);

        WebView mainView;
        WebView subView;

        // Display the new current tab
        mCurrentTab = mTabs.indexOf(newTab);
        mainView = newTab.mMainView;
        boolean needRestore = (mainView == null);
        if (needRestore) {
            // Same work as in createNewTab() except don't do new Tab()
            newTab.mMainView = mainView = createNewWebView();
        }
        mainView.setWebViewClient(mActivity.getWebViewClient());
        mainView.setWebChromeClient(mActivity.getWebChromeClient());
        mainView.setOnCreateContextMenuListener(mActivity);
        mainView.setDownloadListener(mActivity);
        // Add the subwindow if it exists
        if (newTab.mSubViewContainer != null) {
            subView = newTab.mSubView;
            subView.setWebViewClient(newTab.mSubViewClient);
            subView.setWebChromeClient(newTab.mSubViewChromeClient);
            subView.setOnCreateContextMenuListener(mActivity);
            subView.setDownloadListener(mActivity);
        }
        if (needRestore) {
            // Have to finish setCurrentTab work before calling restoreState
            if (!restoreState(newTab.mSavedState, newTab)) {
                mainView.loadUrl(BrowserSettings.getInstance().getHomePage());
            }
        }
        return true;
    }

    /*
     * Put the tab in the background using all the empty/background clients.
     */
    private void putTabInBackground(Tab t) {
        WebView mainView = t.mMainView;
        // Set an empty callback so that default actions are not triggered.
        mainView.setWebViewClient(mEmptyClient);
        mainView.setWebChromeClient(mBackgroundChromeClient);
        mainView.setOnCreateContextMenuListener(null);
        // Leave the DownloadManager attached so that downloads can start in
        // a non-active window. This can happen when going to a site that does
        // a redirect after a period of time. The user could have switched to
        // another tab while waiting for the download to start.
        mainView.setDownloadListener(mActivity);
        WebView subView = t.mSubView;
        if (subView != null) {
            // Set an empty callback so that default actions are not triggered.
            subView.setWebViewClient(mEmptyClient);
            subView.setWebChromeClient(mBackgroundChromeClient);
            subView.setOnCreateContextMenuListener(null);
            subView.setDownloadListener(mActivity);
        }
    }

    /*
     * Dismiss the subwindow for the given tab.
     */
    void dismissSubWindow(Tab t) {
        if (t != null && t.mSubView != null) {
            BrowserSettings.getInstance().deleteObserver(
                    t.mSubView.getSettings());
            t.mSubView.destroy();
            t.mSubView = null;
            t.mSubViewContainer = null;
        }
    }

    /**
     * Ensure that Tab t has data to display in the tab picker.
     * @param  t   Tab to populate.
     */
    /* package */ void populatePickerData(Tab t) {
        if (t == null) {
            return;
        }

        // mMainView == null indicates that the tab has been freed.
        if (t.mMainView == null) {
            populatePickerDataFromSavedState(t);
            return;
        }

        // FIXME: The only place we cared about subwindow was for 
        // bookmarking (i.e. not when saving state). Was this deliberate?
        final WebBackForwardList list = t.mMainView.copyBackForwardList();
        final WebHistoryItem item =
                list != null ? list.getCurrentItem() : null;
        populatePickerData(t, item);

        // This method is only called during the tab picker creation. At this
        // point we need to listen for new pictures since the WebView is still
        // active.
        final WebView w = t.getTopWindow();
        w.setPictureListener(t);
        // Capture the picture here instead of populatePickerData since it can
        // be called when saving the state of a tab.
        t.mPickerData.mPicture = w.capturePicture();
    }

    // Create the PickerData and populate it using the saved state of the tab.
    private void populatePickerDataFromSavedState(Tab t) {
        if (t.mSavedState == null) {
            return;
        }

        final PickerData data = new PickerData();
        final Bundle state = t.mSavedState;
        data.mUrl = state.getString(CURRURL);
        data.mTitle = state.getString(CURRTITLE);
        data.mWidth = state.getInt(CURRWIDTH, 0);
        // XXX: These keys are from WebView.savePicture so if they change, this
        // will break.
        data.mScale = state.getFloat("scale", 1.0f);
        data.mScrollX = state.getInt("scrollX", 0);
        data.mScrollY = state.getInt("scrollY", 0);

        if (state.containsKey(CURRPICTURE)) {
            final File f = new File(t.mSavedState.getString(CURRPICTURE));
            try {
                final FileInputStream in = new FileInputStream(f);
                data.mPicture = Picture.createFromStream(in);
                in.close();
            } catch (Exception ex) {
                // Ignore any problems with inflating the picture. We just
                // won't draw anything.
            }
        }

        // Set the tab's picker data.
        t.mPickerData = data;
    }

    // Populate the picker data using the given history item and the current
    // top WebView.
    private void populatePickerData(Tab t, WebHistoryItem item) {
        final PickerData data = new PickerData();
        if (item != null) {
            data.mUrl = item.getUrl();
            data.mTitle = item.getTitle();
            if (data.mTitle == null) {
                data.mTitle = data.mUrl;
            }
        }
        // We want to display the top window in the tab picker but use the url
        // and title of the main window.
        final WebView w = t.getTopWindow();
        data.mWidth = w.getWidth();
        data.mScale = w.getScale();
        data.mScrollX = w.getScrollX();
        data.mScrollY = w.getScrollY();
        t.mPickerData = data;
    }
    
    /**
     * Clean up the data for all tabs.
     */
    /* package */ void wipeAllPickerData() {
        int size = getTabCount();
        for (int i = 0; i < size; i++) {
            final Tab t = getTab(i);
            if (t != null && t.mSavedState == null) {
                t.mPickerData = null;
            }
            if (t.mMainView != null) {
                // Clear the picture listeners.
                t.mMainView.setPictureListener(null);
                if (t.mSubView != null) {
                    t.mSubView.setPictureListener(null);
                }
            }
        }
    }

    /*
     * Save the state for an individual tab.
     */
    private boolean saveState(Tab t) {
        if (t != null) {
            final WebView w = t.mMainView;
            // If the WebView is null it means we ran low on memory and we
            // already stored the saved state in mSavedState.
            if (w == null) {
                return true;
            }
            final Bundle b = new Bundle();
            final WebBackForwardList list = w.saveState(b);
            if (list != null) {
                final File f = new File(mThumbnailDir, w.hashCode()
                        + "_pic.save");
                if (w.savePicture(b, f)) {
                    b.putString(CURRPICTURE, f.getPath());
                }
            }

            // Store some extra info for displaying the tab in the picker.
            final WebHistoryItem item =
                    list != null ? list.getCurrentItem() : null;
            populatePickerData(t, item);

            // XXX: WebView.savePicture stores the scale and scroll positions
            // in the bundle so we don't have to do it here.
            final PickerData data = t.mPickerData;
            if (data.mUrl != null) {
                b.putString(CURRURL, data.mUrl);
            }
            if (data.mTitle != null) {
                b.putString(CURRTITLE, data.mTitle);
            }
            b.putInt(CURRWIDTH, data.mWidth);
            b.putBoolean(CLOSEONEXIT, t.mCloseOnExit);
            if (t.mAppId != null) {
                b.putString(APPID, t.mAppId);
            }
            if (t.mOriginalUrl != null) {
                b.putString(ORIGINALURL, t.mOriginalUrl);
            }

            // Remember the parent tab so the relationship can be restored.
            if (t.mParentTab != null) {
                b.putInt(PARENTTAB, getTabIndex(t.mParentTab));
            }

            // Remember the saved state.
            t.mSavedState = b;
            return true;
        }
        return false;
    }

    /*
     * Restore the state of the tab.
     */
    private boolean restoreState(Bundle b, Tab t) {
        if (b == null) {
            return false;
        }
        // Restore the internal state even if the WebView fails to restore.
        // This will maintain the app id, original url and close-on-exit values.
        t.mSavedState = null;
        t.mPickerData = null;
        t.mCloseOnExit = b.getBoolean(CLOSEONEXIT);
        t.mAppId = b.getString(APPID);
        t.mOriginalUrl = b.getString(ORIGINALURL);

        final WebView w = t.mMainView;
        final WebBackForwardList list = w.restoreState(b);
        if (list == null) {
            return false;
        }
        if (b.containsKey(CURRPICTURE)) {
            final File f = new File(b.getString(CURRPICTURE));
            w.restorePicture(b, f);
            f.delete();
        }
        return true;
    }
}
