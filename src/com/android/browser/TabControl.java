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

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

class TabControl {
    // Log Tag
    private static final String LOGTAG = "TabControl";

    // next Tab ID, starting at 1
    private static long sNextId = 1;

    private static final String POSITIONS = "positions";
    private static final String CURRENT = "current";

    // Maximum number of tabs.
    private int mMaxTabs;
    // Private array of WebViews that are used as tabs.
    private ArrayList<Tab> mTabs;
    // Queue of most recently viewed tabs.
    private ArrayList<Tab> mTabQueue;
    // Current position in mTabs.
    private int mCurrentTab = -1;
    // the main browser controller
    private final Controller mController;

    private final File mThumbnailDir;

    /**
     * Construct a new TabControl object
     */
    TabControl(Controller controller) {
        mController = controller;
        mThumbnailDir = mController.getActivity()
                .getDir("thumbnails", 0);
        mMaxTabs = mController.getMaxTabs();
        mTabs = new ArrayList<Tab>(mMaxTabs);
        mTabQueue = new ArrayList<Tab>(mMaxTabs);
    }

    static long getNextId() {
        return sNextId++;
    }

    File getThumbnailDir() {
        return mThumbnailDir;
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
     * return the list of tabs
     */
    List<Tab> getTabs() {
        return mTabs;
    }

    /**
     * Return the tab at the specified position.
     * @return The Tab for the specified position or null if the tab does not
     *         exist.
     */
    Tab getTab(int position) {
        if (position >= 0 && position < mTabs.size()) {
            return mTabs.get(position);
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
     * Return the current tab position.
     * @return The current tab position
     */
    int getCurrentPosition() {
        return mCurrentTab;
    }

    /**
     * Given a Tab, find it's position
     * @param Tab to find
     * @return position of Tab or -1 if not found
     */
    int getTabPosition(Tab tab) {
        if (tab == null) {
            return -1;
        }
        return mTabs.indexOf(tab);
    }

    boolean canCreateNewTab() {
        return mMaxTabs != mTabs.size();
    }

    /**
     * Returns true if there are any incognito tabs open.
     * @return True when any incognito tabs are open, false otherwise.
     */
    boolean hasAnyOpenIncognitoTabs() {
        for (Tab tab : mTabs) {
            if (tab.getWebView() != null
                    && tab.getWebView().isPrivateBrowsingEnabled()) {
                return true;
            }
        }
        return false;
    }

    void addPreloadedTab(Tab tab) {
        tab.setId(getNextId());
        mTabs.add(tab);
        tab.setController(mController);
        mController.onSetWebView(tab, tab.getWebView());
        tab.putInBackground();
    }

    /**
     * Create a new tab.
     * @return The newly createTab or null if we have reached the maximum
     *         number of open tabs.
     */
    Tab createNewTab(boolean privateBrowsing) {
        int size = mTabs.size();
        // Return false if we have maxed out on tabs
        if (mMaxTabs == size) {
            return null;
        }
        final WebView w = createNewWebView(privateBrowsing);

        // Create a new tab and add it to the tab list
        Tab t = new Tab(mController, w);
        t.setId(getNextId());
        mTabs.add(t);
        // Initially put the tab in the background.
        t.putInBackground();
        return t;
    }

    /**
     * Create a new tab with default values for closeOnExit(false),
     * appId(null), url(null), and privateBrowsing(false).
     */
    Tab createNewTab() {
        return createNewTab(false);
    }

    SnapshotTab createSnapshotTab(long snapshotId) {
        SnapshotTab t = new SnapshotTab(mController, snapshotId);
        t.setId(getNextId());
        mTabs.add(t);
        return t;
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
            mCurrentTab = getTabPosition(current);
        }

        // destroy the tab
        t.destroy();
        // clear it's references to parent and children
        t.removeFromTree();

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
     * save the tab state:
     * current position
     * position sorted array of tab ids
     * for each tab id, save the tab state
     * @param outState
     * @param saveImages
     */
    void saveState(Bundle outState, boolean saveImages) {
        final int numTabs = getTabCount();
        if (numTabs == 0) {
            return;
        }
        long[] ids = new long[numTabs];
        int i = 0;
        for (Tab tab : mTabs) {
            ids[i++] = tab.getId();
            if (tab.saveState()) {
                outState.putBundle(Long.toString(tab.getId()),
                        tab.getSavedState(saveImages));
            }
        }
        if (!outState.isEmpty()) {
            outState.putLongArray(POSITIONS, ids);
            Tab current = getCurrentTab();
            long cid = -1;
            if (current != null) {
                cid = current.getId();
            }
            outState.putLong(CURRENT, cid);
        }
    }

    /**
     * Check if the state can be restored.  If the state can be restored, the
     * current tab id is returned.  This can be passed to restoreState below
     * in order to restore the correct tab.  Otherwise, -1 is returned and the
     * state cannot be restored.
     */
    long canRestoreState(Bundle inState, boolean restoreIncognitoTabs) {
        final long[] ids = (inState == null) ? null : inState.getLongArray(POSITIONS);
        if (ids == null) {
            return -1;
        }
        final long oldcurrent = inState.getLong(CURRENT);
        long current = -1;
        if (restoreIncognitoTabs ||
                !inState.getBundle(Long.toString(oldcurrent)).getBoolean(Tab.INCOGNITO)) {
                current = oldcurrent;
        } else {
            // pick first non incognito tab
            for (long id : ids) {
                if (!inState.getBundle(Long.toString(id)).getBoolean(Tab.INCOGNITO)) {
                    current = id;
                    break;
                }
            }
        }
        return current;
    }

    /**
     * Restore the state of all the tabs.
     * @param currentId The tab id to restore.
     * @param inState The saved state of all the tabs.
     * @param restoreIncognitoTabs Restoring private browsing tabs
     * @param restoreAll All webviews get restored, not just the current tab
     *        (this does not override handling of incognito tabs)
     * @return True if there were previous tabs that were restored. False if
     *         there was no saved state or restoring the state failed.
     */
    void restoreState(Bundle inState, long currentId,
            boolean restoreIncognitoTabs, boolean restoreAll) {
        if (currentId == -1) {
            return;
        }
        long[] ids = inState.getLongArray(POSITIONS);
        long maxId = -Long.MAX_VALUE;
        HashMap<Long, Tab> tabMap = new HashMap<Long, Tab>();
        for (long id : ids) {
            if (id > maxId) {
                maxId = id;
            }
            final String idkey = Long.toString(id);
            Bundle state = inState.getBundle(idkey);
            if (state == null || state.isEmpty()) {
                // Skip tab
                continue;
            } else if (!restoreIncognitoTabs
                    && state.getBoolean(Tab.INCOGNITO)) {
                // ignore tab
            } else if (id == currentId || restoreAll) {
                Tab t = createNewTab();
                tabMap.put(id, t);
                // Me must set the current tab before restoring the state
                // so that all the client classes are set.
                if (id == currentId) {
                    setCurrentTab(t);
                }
                if (!t.restoreState(state)) {
                    Log.w(LOGTAG, "Fail in restoreState, load home page.");
                    t.getWebView().loadUrl(BrowserSettings.getInstance()
                            .getHomePage());
                }
            } else {
                // Create a new tab and don't restore the state yet, add it
                // to the tab list
                Tab t = new Tab(mController, null);
                t.setId(id);
                tabMap.put(id, t);
                if (state != null) {
                    t.setSavedState(state);
                    // Need to maintain the app id and original url so we
                    // can possibly reuse this tab.
                    t.setAppId(state.getString(Tab.APPID));
                }
                mTabs.add(t);
                // added the tab to the front as they are not current
                mTabQueue.add(0, t);
            }
            // make sure that there is no id overlap between the restored
            // and new tabs
            sNextId = maxId + 1;

        }
        if (mCurrentTab == -1) {
            if (getTabCount() > 0) {
                setCurrentTab(getTab(0));
            } else {
                Tab t = createNewTab();
                setCurrentTab(t);
                t.getWebView().loadUrl(BrowserSettings.getInstance()
                        .getHomePage());
            }
        }
        // restore parent/child relationships
        for (long id : ids) {
            final Tab tab = tabMap.get(id);
            final Bundle b = inState.getBundle(Long.toString(id));
            if ((b != null) && (tab != null)) {
                final long parentId = b.getLong(Tab.PARENTTAB, -1);
                if (parentId != -1) {
                    final Tab parent = tabMap.get(parentId);
                    if (parent != null) {
                        parent.addChildTab(tab);
                    }
                }
            }
        }
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
                if (t != current && t != current.getParent()) {
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
        for (Tab t : mTabs) {
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
    Tab getTabFromAppId(String id) {
        if (id == null) {
            return null;
        }
        for (Tab t : mTabs) {
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
        for (Tab t : mTabs) {
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

    // This method checks if a tab matches the given url.
    private boolean tabMatchesUrl(Tab t, String url) {
        return url.equals(t.getUrl()) || url.equals(t.getOriginalUrl());
    }

    /**
     * Return the tab that matches the given url.
     * @param url The url to search for.
     */
    Tab findTabWithUrl(String url) {
        if (url == null) {
            return null;
        }
        // Check the current tab first.
        Tab currentTab = getCurrentTab();
        if (currentTab != null && tabMatchesUrl(currentTab, url)) {
            return currentTab;
        }
        // Now check all the rest.
        for (Tab tab : mTabs) {
            if (tabMatchesUrl(tab, url)) {
                return tab;
            }
        }
        return null;
    }

    /**
     * Recreate the main WebView of the given tab.
     */
    void recreateWebView(Tab t) {
        final WebView w = t.getWebView();
        if (w != null) {
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
    }

    /**
     * Creates a new WebView and registers it with the global settings.
     */
    private WebView createNewWebView() {
        return createNewWebView(false);
    }

    /**
     * Creates a new WebView and registers it with the global settings.
     * @param privateBrowsing When true, enables private browsing in the new
     *        WebView.
     */
    private WebView createNewWebView(boolean privateBrowsing) {
        return mController.getWebViewFactory().createWebView(privateBrowsing);
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
        boolean needRestore = !newTab.isSnapshot() && (mainView == null);
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
