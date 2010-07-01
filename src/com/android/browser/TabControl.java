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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebBackForwardList;
import android.webkit.WebView;

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;

class TabControl {
    // Log Tag
    private static final String LOGTAG = "TabControl";
    // Maximum number of tabs.
    private static final int MAX_TABS = 8;
    // Private array of WebViews that are used as tabs.
    private ArrayList<Tab> mTabs = new ArrayList<Tab>(MAX_TABS);
    // Queue of most recently viewed tabs.
    private ArrayList<Tab> mTabQueue = new ArrayList<Tab>(MAX_TABS);
    // Current position in mTabs.
    private int mCurrentTab = -1;
    // A private instance of BrowserActivity to interface with when adding and
    // switching between tabs.
    private final BrowserActivity mActivity;
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
        return t.getWebView();
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
        return t.getTopWindow();
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
        return t.getSubWebView();
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
        if (tab == null) {
            return -1;
        }
        return mTabs.indexOf(tab);
    }

    boolean canCreateNewTab() {
        return MAX_TABS != mTabs.size();
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
        Tab t = new Tab(mActivity, w, closeOnExit, appId, url);
        mTabs.add(t);
        // Initially put the tab in the background.
        t.putInBackground();
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
     * Remove the parent child relationships from all tabs.
     */
    void removeParentChildRelationShips() {
        for (Tab tab : mTabs) {
            tab.removeFromTree();
        }
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

        // Grab the current tab before modifying the list.
        Tab current = getCurrentTab();

        // Remove t from our list of tabs.
        mTabs.remove(t);

        // Put the tab in the background only if it is the current one.
        if (current == t) {
            t.putInBackground();
            mCurrentTab = -1;
        } else {
            // If a tab that is earlier in the list gets removed, the current
            // index no longer points to the correct tab.
            mCurrentTab = getTabIndex(current);
        }

        // destroy the tab
        t.destroy();
        // clear it's references to parent and children
        t.removeFromTree();

        // The tab indices have shifted, update all the saved state so we point
        // to the correct index.
        for (Tab tab : mTabs) {
            Vector<Tab> children = tab.getChildTabs();
            if (children != null) {
                for (Tab child : children) {
                    child.setParentTab(tab);
                }
            }
        }

        // Remove it from the queue of viewed tabs.
        mTabQueue.remove(t);
        return true;
    }

    /**
     * Destroy all the tabs and subwindows
     */
    void destroy() {
        for (Tab t : mTabs) {
            t.destroy();
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


    /**
     * Save the state of all the Tabs.
     * @param outState The Bundle to save the state to.
     */
    void saveState(Bundle outState) {
        final int numTabs = getTabCount();
        outState.putInt(Tab.NUMTABS, numTabs);
        final int index = getCurrentIndex();
        outState.putInt(Tab.CURRTAB, (index >= 0 && index < numTabs) ? index : 0);
        for (int i = 0; i < numTabs; i++) {
            final Tab t = getTab(i);
            if (t.saveState()) {
                outState.putBundle(Tab.WEBVIEW + i, t.getSavedState());
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
                ? -1 : inState.getInt(Tab.NUMTABS, -1);
        if (numTabs == -1) {
            return false;
        } else {
            final int currentTab = inState.getInt(Tab.CURRTAB, -1);
            for (int i = 0; i < numTabs; i++) {
                if (i == currentTab) {
                    Tab t = createNewTab();
                    // Me must set the current tab before restoring the state
                    // so that all the client classes are set.
                    setCurrentTab(t);
                    if (!t.restoreState(inState.getBundle(Tab.WEBVIEW + i))) {
                        Log.w(LOGTAG, "Fail in restoreState, load home page.");
                        t.getWebView().loadUrl(BrowserSettings.getInstance()
                                .getHomePage());
                    }
                } else {
                    // Create a new tab and don't restore the state yet, add it
                    // to the tab list
                    Tab t = new Tab(mActivity, null, false, null, null);
                    Bundle state = inState.getBundle(Tab.WEBVIEW + i);
                    if (state != null) {
                        t.setSavedState(state);
                        t.populatePickerDataFromSavedState();
                        // Need to maintain the app id and original url so we
                        // can possibly reuse this tab.
                        t.setAppId(state.getString(Tab.APPID));
                        t.setOriginalUrl(state.getString(Tab.ORIGINALURL));
                    }
                    mTabs.add(t);
                    // added the tab to the front as they are not current
                    mTabQueue.add(0, t);
                }
            }
            // Rebuild the tree of tabs. Do this after all tabs have been
            // created/restored so that the parent tab exists.
            for (int i = 0; i < numTabs; i++) {
                final Bundle b = inState.getBundle(Tab.WEBVIEW + i);
                final Tab t = getTab(i);
                if (b != null && t != null) {
                    final int parentIndex = b.getInt(Tab.PARENTTAB, -1);
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
     * Free the memory in this order, 1) free the background tabs; 2) free the
     * WebView cache;
     */
    void freeMemory() {
        if (getTabCount() == 0) return;

        // free the least frequently used background tabs
        Vector<Tab> tabs = getHalfLeastUsedTabs(getCurrentTab());
        if (tabs.size() > 0) {
            Log.w(LOGTAG, "Free " + tabs.size() + " tabs in the browser");
            for (Tab t : tabs) {
                // store the WebView's state.
                t.saveState();
                // destroy the tab
                t.destroy();
            }
            return;
        }

        // free the WebView's unused memory (this includes the cache)
        Log.w(LOGTAG, "Free WebView's unused memory and cache");
        WebView view = getCurrentWebView();
        if (view != null) {
            view.freeMemory();
        }
    }

    private Vector<Tab> getHalfLeastUsedTabs(Tab current) {
        Vector<Tab> tabsToGo = new Vector<Tab>();

        // Don't do anything if we only have 1 tab or if the current tab is
        // null.
        if (getTabCount() == 1 || current == null) {
            return tabsToGo;
        }

        if (mTabQueue.size() == 0) {
            return tabsToGo;
        }

        // Rip through the queue starting at the beginning and tear down half of
        // available tabs which are not the current tab or the parent of the
        // current tab.
        int openTabCount = 0;
        for (Tab t : mTabQueue) {
            if (t != null && t.getWebView() != null) {
                openTabCount++;
                if (t != current && t != current.getParentTab()) {
                    tabsToGo.add(t);
                }
            }
        }

        openTabCount /= 2;
        if (tabsToGo.size() > openTabCount) {
            tabsToGo.setSize(openTabCount);
        }

        return tabsToGo;
    }

    /**
     * Show the tab that contains the given WebView.
     * @param view The WebView used to find the tab.
     */
    Tab getTabFromView(WebView view) {
        final int size = getTabCount();
        for (int i = 0; i < size; i++) {
            final Tab t = getTab(i);
            if (t.getSubWebView() == view || t.getWebView() == view) {
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
            if (id.equals(t.getAppId())) {
                return t;
            }
        }
        return null;
    }

    /**
     * Stop loading in all opened WebView including subWindows.
     */
    void stopAllLoading() {
        final int size = getTabCount();
        for (int i = 0; i < size; i++) {
            final Tab t = getTab(i);
            final WebView webview = t.getWebView();
            if (webview != null) {
                webview.stopLoading();
            }
            final WebView subview = t.getSubWebView();
            if (subview != null) {
                webview.stopLoading();
            }
        }
    }

    // This method checks if a non-app tab (one created within the browser)
    // matches the given url.
    private boolean tabMatchesUrl(Tab t, String url) {
        if (t.getAppId() != null) {
            return false;
        }
        WebView webview = t.getWebView();
        if (webview == null) {
            return false;
        } else if (url.equals(webview.getUrl())
                || url.equals(webview.getOriginalUrl())) {
            return true;
        }
        return false;
    }

    /**
     * Return the tab that has no app id associated with it and the url of the
     * tab matches the given url.
     * @param url The url to search for.
     */
    Tab findUnusedTabWithUrl(String url) {
        if (url == null) {
            return null;
        }
        // Check the current tab first.
        Tab t = getCurrentTab();
        if (t != null && tabMatchesUrl(t, url)) {
            return t;
        }
        // Now check all the rest.
        final int size = getTabCount();
        for (int i = 0; i < size; i++) {
            t = getTab(i);
            if (tabMatchesUrl(t, url)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Recreate the main WebView of the given tab. Returns true if the WebView
     * requires a load, whether it was due to the fact that it was deleted, or
     * it is because it was a voice search.
     */
    boolean recreateWebView(Tab t, BrowserActivity.UrlData urlData) {
        final String url = urlData.mUrl;
        final WebView w = t.getWebView();
        if (w != null) {
            if (url != null && url.equals(t.getOriginalUrl())
                    // Treat a voice intent as though it is a different URL,
                    // since it most likely is.
                    && urlData.mVoiceIntent == null) {
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
            t.destroy();
        }
        // Create a new WebView. If this tab is the current tab, we need to put
        // back all the clients so force it to be the current tab.
        t.setWebView(createNewWebView());
        if (getCurrentTab() == t) {
            setCurrentTab(t, true);
        }
        // Clear the saved state and picker data
        t.setSavedState(null);
        t.clearPickerData();
        // Save the new url in order to avoid deleting the WebView.
        t.setOriginalUrl(url);
        return true;
    }

    /**
     * Creates a new WebView and registers it with the global settings.
     */
    private WebView createNewWebView() {
        // Create a new WebView
        WebView w = new WebView(mActivity);
        w.setScrollbarFadingEnabled(true);
        w.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        w.setMapTrackballToArrowKeys(false); // use trackball directly
        // Enable the built-in zoom
        w.getSettings().setBuiltInZoomControls(true);
        // Add this WebView to the settings observer list and update the
        // settings
        final BrowserSettings s = BrowserSettings.getInstance();
        s.addObserver(w.getSettings()).update(s, null);

        // pick a default
        if (false) {
            MeshTracker mt = new MeshTracker(2);
            Paint paint = new Paint();
            Bitmap bm = BitmapFactory.decodeResource(mActivity.getResources(),
                                         R.drawable.pattern_carbon_fiber_dark);
            paint.setShader(new BitmapShader(bm, Shader.TileMode.REPEAT,
                                             Shader.TileMode.REPEAT));
            mt.setBGPaint(paint);
            w.setDragTracker(mt);
        }
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

    void pauseCurrentTab() {
        Tab t = getCurrentTab();
        if (t != null) {
            t.pause();
        }
    }

    void resumeCurrentTab() {
        Tab t = getCurrentTab();
        if (t != null) {
            t.resume();
        }
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
            current.putInBackground();
            mCurrentTab = -1;
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

        // Display the new current tab
        mCurrentTab = mTabs.indexOf(newTab);
        WebView mainView = newTab.getWebView();
        boolean needRestore = (mainView == null);
        if (needRestore) {
            // Same work as in createNewTab() except don't do new Tab()
            mainView = createNewWebView();
            newTab.setWebView(mainView);
        }
        newTab.putInForeground();
        if (needRestore) {
            // Have to finish setCurrentTab work before calling restoreState
            if (!newTab.restoreState(newTab.getSavedState())) {
                mainView.loadUrl(BrowserSettings.getInstance().getHomePage());
            }
        }
        return true;
    }
}
