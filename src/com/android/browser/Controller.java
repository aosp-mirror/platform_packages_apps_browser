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

import com.android.browser.IntentHandler.UrlData;
import com.android.browser.UI.DropdownChangeListener;
import com.android.browser.search.SearchEngine;
import com.android.common.Search;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceActivity;
import android.provider.Browser;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Images;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

/**
 * Controller for browser
 */
public class Controller
        implements WebViewController, UiController {

    private static final String LOGTAG = "Controller";
    private static final String SEND_APP_ID_EXTRA =
        "android.speech.extras.SEND_APPLICATION_ID_EXTRA";
    private static final String INCOGNITO_URI = "browser:incognito";


    // public message ids
    public final static int LOAD_URL = 1001;
    public final static int STOP_LOAD = 1002;

    // Message Ids
    private static final int FOCUS_NODE_HREF = 102;
    private static final int RELEASE_WAKELOCK = 107;

    static final int UPDATE_BOOKMARK_THUMBNAIL = 108;

    private static final int OPEN_BOOKMARKS = 201;

    private static final int EMPTY_MENU = -1;

    // activity requestCode
    final static int PREFERENCES_PAGE = 3;
    final static int FILE_SELECTED = 4;
    final static int AUTOFILL_SETUP = 5;

    private final static int WAKELOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes

    // As the ids are dynamically created, we can't guarantee that they will
    // be in sequence, so this static array maps ids to a window number.
    final static private int[] WINDOW_SHORTCUT_ID_ARRAY =
    { R.id.window_one_menu_id, R.id.window_two_menu_id,
      R.id.window_three_menu_id, R.id.window_four_menu_id,
      R.id.window_five_menu_id, R.id.window_six_menu_id,
      R.id.window_seven_menu_id, R.id.window_eight_menu_id };

    // "source" parameter for Google search through search key
    final static String GOOGLE_SEARCH_SOURCE_SEARCHKEY = "browser-key";
    // "source" parameter for Google search through simplily type
    final static String GOOGLE_SEARCH_SOURCE_TYPE = "browser-type";

    // "no-crash-recovery" parameter in intetnt to suppress crash recovery
    final static String NO_CRASH_RECOVERY = "no-crash-recovery";

    private Activity mActivity;
    private UI mUi;
    private TabControl mTabControl;
    private BrowserSettings mSettings;
    private WebViewFactory mFactory;
    private OptionsMenuHandler mOptionsMenuHandler = null;

    private WakeLock mWakeLock;

    private UrlHandler mUrlHandler;
    private UploadHandler mUploadHandler;
    private IntentHandler mIntentHandler;
    private PageDialogsHandler mPageDialogsHandler;
    private NetworkStateHandler mNetworkHandler;

    private Message mAutoFillSetupMessage;

    private boolean mShouldShowErrorConsole;

    private SystemAllowGeolocationOrigins mSystemAllowGeolocationOrigins;

    // FIXME, temp address onPrepareMenu performance problem.
    // When we move everything out of view, we should rewrite this.
    private int mCurrentMenuState = 0;
    private int mMenuState = R.id.MAIN_MENU;
    private int mOldMenuState = EMPTY_MENU;
    private Menu mCachedMenu;

    private boolean mMenuIsDown;

    // For select and find, we keep track of the ActionMode so that
    // finish() can be called as desired.
    private ActionMode mActionMode;

    /**
     * Only meaningful when mOptionsMenuOpen is true.  This variable keeps track
     * of whether the configuration has changed.  The first onMenuOpened call
     * after a configuration change is simply a reopening of the same menu
     * (i.e. mIconView did not change).
     */
    private boolean mConfigChanged;

    /**
     * Keeps track of whether the options menu is open. This is important in
     * determining whether to show or hide the title bar overlay
     */
    private boolean mOptionsMenuOpen;

    /**
     * Whether or not the options menu is in its bigger, popup menu form. When
     * true, we want the title bar overlay to be gone. When false, we do not.
     * Only meaningful if mOptionsMenuOpen is true.
     */
    private boolean mExtendedMenuOpen;

    private boolean mInLoad;

    private boolean mActivityPaused = true;
    private boolean mLoadStopped;

    private Handler mHandler;
    // Checks to see when the bookmarks database has changed, and updates the
    // Tabs' notion of whether they represent bookmarked sites.
    private ContentObserver mBookmarksObserver;
    private DataController mDataController;
    private CrashRecoveryHandler mCrashRecoveryHandler;

    private static class ClearThumbnails extends AsyncTask<File, Void, Void> {
        @Override
        public Void doInBackground(File... files) {
            if (files != null) {
                for (File f : files) {
                    if (!f.delete()) {
                        Log.e(LOGTAG, f.getPath() + " was not deleted");
                    }
                }
            }
            return null;
        }
    }

    public Controller(Activity browser) {
        mActivity = browser;
        mSettings = BrowserSettings.getInstance();
        mDataController = DataController.getInstance(mActivity);
        mTabControl = new TabControl(this);
        mSettings.setController(this);
        mCrashRecoveryHandler = new CrashRecoveryHandler(this);

        mUrlHandler = new UrlHandler(this);
        mIntentHandler = new IntentHandler(mActivity, this);
        mPageDialogsHandler = new PageDialogsHandler(mActivity, this);

        PowerManager pm = (PowerManager) mActivity
                .getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Browser");

        startHandler();
        mBookmarksObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                int size = mTabControl.getTabCount();
                for (int i = 0; i < size; i++) {
                    mTabControl.getTab(i).updateBookmarkedStatus();
                }
            }

        };
        browser.getContentResolver().registerContentObserver(
                BrowserContract.Bookmarks.CONTENT_URI, true, mBookmarksObserver);

        mNetworkHandler = new NetworkStateHandler(mActivity, this);
        // Start watching the default geolocation permissions
        mSystemAllowGeolocationOrigins =
                new SystemAllowGeolocationOrigins(mActivity.getApplicationContext());
        mSystemAllowGeolocationOrigins.start();

        retainIconsOnStartup();
    }

    void start(final Bundle icicle, final Intent intent) {
        boolean noCrashRecovery = intent.getBooleanExtra(NO_CRASH_RECOVERY, false);
        if (icicle != null || noCrashRecovery) {
            mCrashRecoveryHandler.clearState();
            doStart(icicle, intent);
        } else {
            mCrashRecoveryHandler.startRecovery(intent);
        }
    }

    void doStart(final Bundle icicle, final Intent intent) {
        // Unless the last browser usage was within 24 hours, destroy any
        // remaining incognito tabs.

        Calendar lastActiveDate = icicle != null ?
                (Calendar) icicle.getSerializable("lastActiveDate") : null;
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);

        final boolean restoreIncognitoTabs = !(lastActiveDate == null
            || lastActiveDate.before(yesterday)
            || lastActiveDate.after(today));

        // Find out if we will restore any state and remember the tab.
        final int currentTab =
                mTabControl.canRestoreState(icicle, restoreIncognitoTabs);

        if (currentTab == -1) {
            // Not able to restore so we go ahead and clear session cookies.  We
            // must do this before trying to login the user as we don't want to
            // clear any session cookies set during login.
            CookieManager.getInstance().removeSessionCookie();
        }

        GoogleAccountLogin.startLoginIfNeeded(mActivity,
                new Runnable() {
                    @Override public void run() {
                        onPreloginFinished(icicle, intent, currentTab, restoreIncognitoTabs);
                    }
                });
    }

    private void onPreloginFinished(Bundle icicle, Intent intent, int currentTab,
            boolean restoreIncognitoTabs) {
        if (currentTab == -1) {
            final Bundle extra = intent.getExtras();
            // Create an initial tab.
            // If the intent is ACTION_VIEW and data is not null, the Browser is
            // invoked to view the content by another application. In this case,
            // the tab will be close when exit.
            UrlData urlData = mIntentHandler.getUrlDataFromIntent(intent);
            Tab t = null;
            if (urlData.isEmpty()) {
                t = openTabToHomePage();
            } else {
                t = openTab(urlData);
            }
            if (t != null) {
                t.setAppId(intent.getStringExtra(Browser.EXTRA_APPLICATION_ID));
            }
            WebView webView = t.getWebView();
            if (extra != null) {
                int scale = extra.getInt(Browser.INITIAL_ZOOM_LEVEL, 0);
                if (scale > 0 && scale <= 1000) {
                    webView.setInitialScale(scale);
                }
            }
        } else {
            mTabControl.restoreState(icicle, currentTab, restoreIncognitoTabs,
                    mUi.needsRestoreAllTabs());
            mUi.updateTabs(mTabControl.getTabs());
            // TabControl.restoreState() will create a new tab even if
            // restoring the state fails.
            setActiveTab(mTabControl.getCurrentTab());
        }
        // clear up the thumbnail directory, which is no longer used;
        // ideally this should only be run once after an upgrade from
        // a previous version of the browser
        new ClearThumbnails().execute(mTabControl.getThumbnailDir()
                .listFiles());
        // Read JavaScript flags if it exists.
        String jsFlags = getSettings().getJsEngineFlags();
        if (jsFlags.trim().length() != 0) {
            getCurrentWebView().setJsFlags(jsFlags);
        }
        if (BrowserActivity.ACTION_SHOW_BOOKMARKS.equals(intent.getAction())) {
            bookmarksOrHistoryPicker(false);
        }
    }

    void setWebViewFactory(WebViewFactory factory) {
        mFactory = factory;
    }

    @Override
    public WebViewFactory getWebViewFactory() {
        return mFactory;
    }

    @Override
    public void onSetWebView(Tab tab, WebView view) {
        mUi.onSetWebView(tab, view);
    }

    @Override
    public void createSubWindow(Tab tab) {
        endActionMode();
        WebView mainView = tab.getWebView();
        WebView subView = mFactory.createWebView((mainView == null)
                ? false
                : mainView.isPrivateBrowsingEnabled());
        mUi.createSubWindow(tab, subView);
    }

    @Override
    public Activity getActivity() {
        return mActivity;
    }

    void setUi(UI ui) {
        mUi = ui;
    }

    BrowserSettings getSettings() {
        return mSettings;
    }

    IntentHandler getIntentHandler() {
        return mIntentHandler;
    }

    @Override
    public UI getUi() {
        return mUi;
    }

    int getMaxTabs() {
        return mActivity.getResources().getInteger(R.integer.max_tabs);
    }

    @Override
    public TabControl getTabControl() {
        return mTabControl;
    }

    @Override
    public List<Tab> getTabs() {
        return mTabControl.getTabs();
    }

    // Open the icon database and retain all the icons for visited sites.
    // This is done on a background thread so as not to stall startup.
    private void retainIconsOnStartup() {
        // WebIconDatabase needs to be retrieved on the UI thread so that if
        // it has not been created successfully yet the Handler is started on the
        // UI thread.
        new RetainIconsOnStartupTask(WebIconDatabase.getInstance()).execute();
    }

    private class RetainIconsOnStartupTask extends AsyncTask<Void, Void, Void> {
        private WebIconDatabase mDb;

        public RetainIconsOnStartupTask(WebIconDatabase db) {
            mDb = db;
        }

        @Override
        protected Void doInBackground(Void... unused) {
            mDb.open(mActivity.getDir("icons", 0).getPath());
            Cursor c = null;
            try {
                c = Browser.getAllBookmarks(mActivity.getContentResolver());
                if (c.moveToFirst()) {
                    int urlIndex = c.getColumnIndex(Browser.BookmarkColumns.URL);
                    do {
                        String url = c.getString(urlIndex);
                        mDb.retainIconForPageUrl(url);
                    } while (c.moveToNext());
                }
            } catch (IllegalStateException e) {
                Log.e(LOGTAG, "retainIconsOnStartup", e);
            } finally {
                if (c != null) c.close();
            }

            return null;
        }
    }

    private void startHandler() {
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case OPEN_BOOKMARKS:
                        bookmarksOrHistoryPicker(false);
                        break;
                    case FOCUS_NODE_HREF:
                    {
                        String url = (String) msg.getData().get("url");
                        String title = (String) msg.getData().get("title");
                        String src = (String) msg.getData().get("src");
                        if (url == "") url = src; // use image if no anchor
                        if (TextUtils.isEmpty(url)) {
                            break;
                        }
                        HashMap focusNodeMap = (HashMap) msg.obj;
                        WebView view = (WebView) focusNodeMap.get("webview");
                        // Only apply the action if the top window did not change.
                        if (getCurrentTopWebView() != view) {
                            break;
                        }
                        switch (msg.arg1) {
                            case R.id.open_context_menu_id:
                                loadUrlFromContext(getCurrentTopWebView(), url);
                                break;
                            case R.id.view_image_context_menu_id:
                                loadUrlFromContext(getCurrentTopWebView(), src);
                                break;
                            case R.id.open_newtab_context_menu_id:
                                final Tab parent = mTabControl.getCurrentTab();
                                final Tab newTab =
                                        openTab(url,
                                                (parent != null)
                                                && parent.isPrivateBrowsingEnabled(),
                                                !mSettings.openInBackground(),
                                                true);
                                if (newTab != null && newTab != parent) {
                                    parent.addChildTab(newTab);
                                }
                                break;
                            case R.id.copy_link_context_menu_id:
                                copy(url);
                                break;
                            case R.id.save_link_context_menu_id:
                            case R.id.download_context_menu_id:
                                DownloadHandler.onDownloadStartNoStream(
                                        mActivity, url, null, null, null,
                                        view.isPrivateBrowsingEnabled());
                                break;
                        }
                        break;
                    }

                    case LOAD_URL:
                        loadUrlFromContext(getCurrentTopWebView(), (String) msg.obj);
                        break;

                    case STOP_LOAD:
                        stopLoading();
                        break;

                    case RELEASE_WAKELOCK:
                        if (mWakeLock.isHeld()) {
                            mWakeLock.release();
                            // if we reach here, Browser should be still in the
                            // background loading after WAKELOCK_TIMEOUT (5-min).
                            // To avoid burning the battery, stop loading.
                            mTabControl.stopAllLoading();
                        }
                        break;

                    case UPDATE_BOOKMARK_THUMBNAIL:
                        Tab tab = (Tab) msg.obj;
                        if (tab != null) {
                            updateScreenshot(tab);
                        }
                        break;
                }
            }
        };

    }

    @Override
    public void shareCurrentPage() {
        shareCurrentPage(mTabControl.getCurrentTab());
    }

    private void shareCurrentPage(Tab tab) {
        if (tab != null) {
            sharePage(mActivity, tab.getTitle(),
                    tab.getUrl(), tab.getFavicon(),
                    createScreenshot(tab.getWebView(),
                            getDesiredThumbnailWidth(mActivity),
                            getDesiredThumbnailHeight(mActivity)));
        }
    }

    /**
     * Share a page, providing the title, url, favicon, and a screenshot.  Uses
     * an {@link Intent} to launch the Activity chooser.
     * @param c Context used to launch a new Activity.
     * @param title Title of the page.  Stored in the Intent with
     *          {@link Intent#EXTRA_SUBJECT}
     * @param url URL of the page.  Stored in the Intent with
     *          {@link Intent#EXTRA_TEXT}
     * @param favicon Bitmap of the favicon for the page.  Stored in the Intent
     *          with {@link Browser#EXTRA_SHARE_FAVICON}
     * @param screenshot Bitmap of a screenshot of the page.  Stored in the
     *          Intent with {@link Browser#EXTRA_SHARE_SCREENSHOT}
     */
    static final void sharePage(Context c, String title, String url,
            Bitmap favicon, Bitmap screenshot) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, url);
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Browser.EXTRA_SHARE_FAVICON, favicon);
        send.putExtra(Browser.EXTRA_SHARE_SCREENSHOT, screenshot);
        try {
            c.startActivity(Intent.createChooser(send, c.getString(
                    R.string.choosertitle_sharevia)));
        } catch(android.content.ActivityNotFoundException ex) {
            // if no app handles it, do nothing
        }
    }

    private void copy(CharSequence text) {
        ClipboardManager cm = (ClipboardManager) mActivity
                .getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setText(text);
    }

    // lifecycle

    protected void onConfgurationChanged(Configuration config) {
        mConfigChanged = true;
        if (mPageDialogsHandler != null) {
            mPageDialogsHandler.onConfigurationChanged(config);
        }
        mUi.onConfigurationChanged(config);
    }

    @Override
    public void handleNewIntent(Intent intent) {
        mIntentHandler.onNewIntent(intent);
    }

    protected void onPause() {
        if (mUi.isCustomViewShowing()) {
            hideCustomView();
        }
        if (mActivityPaused) {
            Log.e(LOGTAG, "BrowserActivity is already paused.");
            return;
        }
        mActivityPaused = true;
        Tab tab = mTabControl.getCurrentTab();
        if (tab != null) {
            tab.pause();
            if (!pauseWebViewTimers(tab)) {
                mWakeLock.acquire();
                mHandler.sendMessageDelayed(mHandler
                        .obtainMessage(RELEASE_WAKELOCK), WAKELOCK_TIMEOUT);
            }
        }
        mUi.onPause();
        mNetworkHandler.onPause();

        WebView.disablePlatformNotifications();
        mCrashRecoveryHandler.clearState();
    }

    void onSaveInstanceState(Bundle outState) {
        // the default implementation requires each view to have an id. As the
        // browser handles the state itself and it doesn't use id for the views,
        // don't call the default implementation. Otherwise it will trigger the
        // warning like this, "couldn't save which view has focus because the
        // focused view XXX has no id".

        // Save all the tabs
        mTabControl.saveState(outState);
        // Save time so that we know how old incognito tabs (if any) are.
        outState.putSerializable("lastActiveDate", Calendar.getInstance());
    }

    void onResume() {
        if (!mActivityPaused) {
            Log.e(LOGTAG, "BrowserActivity is already resumed.");
            return;
        }
        mActivityPaused = false;
        Tab current = mTabControl.getCurrentTab();
        if (current != null) {
            current.resume();
            resumeWebViewTimers(current);
        }
        if (mWakeLock.isHeld()) {
            mHandler.removeMessages(RELEASE_WAKELOCK);
            mWakeLock.release();
        }
        mUi.onResume();
        mNetworkHandler.onResume();
        WebView.enablePlatformNotifications();
    }

    /**
     * resume all WebView timers using the WebView instance of the given tab
     * @param tab guaranteed non-null
     */
    private void resumeWebViewTimers(Tab tab) {
        boolean inLoad = tab.inPageLoad();
        if ((!mActivityPaused && !inLoad) || (mActivityPaused && inLoad)) {
            CookieSyncManager.getInstance().startSync();
            WebView w = tab.getWebView();
            if (w != null) {
                w.resumeTimers();
            }
        }
    }

    /**
     * Pause all WebView timers using the WebView of the given tab
     * @param tab
     * @return true if the timers are paused or tab is null
     */
    private boolean pauseWebViewTimers(Tab tab) {
        if (tab == null) {
            return true;
        } else if (!tab.inPageLoad()) {
            CookieSyncManager.getInstance().stopSync();
            WebView w = getCurrentWebView();
            if (w != null) {
                w.pauseTimers();
            }
            return true;
        }
        return false;
    }

    void onDestroy() {
        if (mUploadHandler != null && !mUploadHandler.handled()) {
            mUploadHandler.onResult(Activity.RESULT_CANCELED, null);
            mUploadHandler = null;
        }
        if (mTabControl == null) return;
        mUi.onDestroy();
        // Remove the current tab and sub window
        Tab t = mTabControl.getCurrentTab();
        if (t != null) {
            dismissSubWindow(t);
            removeTab(t);
        }
        mActivity.getContentResolver().unregisterContentObserver(mBookmarksObserver);
        // Destroy all the tabs
        mTabControl.destroy();
        WebIconDatabase.getInstance().close();
        // Stop watching the default geolocation permissions
        mSystemAllowGeolocationOrigins.stop();
        mSystemAllowGeolocationOrigins = null;
    }

    protected boolean isActivityPaused() {
        return mActivityPaused;
    }

    protected void onLowMemory() {
        mTabControl.freeMemory();
    }

    @Override
    public boolean shouldShowErrorConsole() {
        return mShouldShowErrorConsole;
    }

    protected void setShouldShowErrorConsole(boolean show) {
        if (show == mShouldShowErrorConsole) {
            // Nothing to do.
            return;
        }
        mShouldShowErrorConsole = show;
        Tab t = mTabControl.getCurrentTab();
        if (t == null) {
            // There is no current tab so we cannot toggle the error console
            return;
        }
        mUi.setShouldShowErrorConsole(t, show);
    }

    @Override
    public void stopLoading() {
        mLoadStopped = true;
        Tab tab = mTabControl.getCurrentTab();
        WebView w = getCurrentTopWebView();
        w.stopLoading();
        mUi.onPageStopped(tab);
    }

    boolean didUserStopLoading() {
        return mLoadStopped;
    }

    // WebViewController

    @Override
    public void onPageStarted(Tab tab, WebView view, Bitmap favicon) {

        // We've started to load a new page. If there was a pending message
        // to save a screenshot then we will now take the new page and save
        // an incorrect screenshot. Therefore, remove any pending thumbnail
        // messages from the queue.
        mHandler.removeMessages(Controller.UPDATE_BOOKMARK_THUMBNAIL,
                tab);

        // reset sync timer to avoid sync starts during loading a page
        CookieSyncManager.getInstance().resetSync();

        if (!mNetworkHandler.isNetworkUp()) {
            view.setNetworkAvailable(false);
        }

        // when BrowserActivity just starts, onPageStarted may be called before
        // onResume as it is triggered from onCreate. Call resumeWebViewTimers
        // to start the timer. As we won't switch tabs while an activity is in
        // pause state, we can ensure calling resume and pause in pair.
        if (mActivityPaused) {
            resumeWebViewTimers(tab);
        }
        mLoadStopped = false;
        if (!mNetworkHandler.isNetworkUp()) {
            mNetworkHandler.createAndShowNetworkDialog();
        }
        endActionMode();

        mUi.onTabDataChanged(tab);

        String url = tab.getUrl();
        // update the bookmark database for favicon
        maybeUpdateFavicon(tab, null, url, favicon);

        Performance.tracePageStart(url);

        // Performance probe
        if (false) {
            Performance.onPageStarted();
        }

    }

    @Override
    public void onPageFinished(Tab tab) {
        mUi.onTabDataChanged(tab);
        if (!tab.isPrivateBrowsingEnabled()
                && !TextUtils.isEmpty(tab.getUrl())) {
            if (tab.inForeground() && !didUserStopLoading()
                    || !tab.inForeground()) {
                // Only update the bookmark screenshot if the user did not
                // cancel the load early.
                mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        UPDATE_BOOKMARK_THUMBNAIL, 0, 0, tab),
                        500);
            }
        }
        // pause the WebView timer and release the wake lock if it is finished
        // while BrowserActivity is in pause state.
        if (mActivityPaused && pauseWebViewTimers(tab)) {
            if (mWakeLock.isHeld()) {
                mHandler.removeMessages(RELEASE_WAKELOCK);
                mWakeLock.release();
            }
        }
        // Performance probe
        if (false) {
            Performance.onPageFinished(tab.getUrl());
         }

        Performance.tracePageFinished();
    }

    @Override
    public void onProgressChanged(Tab tab) {
        int newProgress = tab.getLoadProgress();

        if (newProgress == 100) {
            CookieSyncManager.getInstance().sync();
            // onProgressChanged() may continue to be called after the main
            // frame has finished loading, as any remaining sub frames continue
            // to load. We'll only get called once though with newProgress as
            // 100 when everything is loaded. (onPageFinished is called once
            // when the main frame completes loading regardless of the state of
            // any sub frames so calls to onProgressChanges may continue after
            // onPageFinished has executed)
            if (mInLoad) {
                mInLoad = false;
                updateInLoadMenuItems(mCachedMenu);
            }
        } else {
            if (!mInLoad) {
                // onPageFinished may have already been called but a subframe is
                // still loading and updating the progress. Reset mInLoad and
                // update the menu items.
                mInLoad = true;
                updateInLoadMenuItems(mCachedMenu);
            }
        }
        mUi.onProgressChanged(tab);
    }

    @Override
    public void onUpdatedLockIcon(Tab tab) {
        mUi.onTabDataChanged(tab);
    }

    @Override
    public void onReceivedTitle(Tab tab, final String title) {
        mUi.onTabDataChanged(tab);
        final String pageUrl = tab.getOriginalUrl();
        if (TextUtils.isEmpty(pageUrl) || pageUrl.length()
                >= SQLiteDatabase.SQLITE_MAX_LIKE_PATTERN_LENGTH) {
            return;
        }
        // Update the title in the history database if not in private browsing mode
        if (!tab.isPrivateBrowsingEnabled()) {
            mDataController.updateHistoryTitle(pageUrl, title);
        }
    }

    @Override
    public void onFavicon(Tab tab, WebView view, Bitmap icon) {
        mUi.onTabDataChanged(tab);
        maybeUpdateFavicon(tab, view.getOriginalUrl(), view.getUrl(), icon);
    }

    @Override
    public boolean shouldOverrideUrlLoading(Tab tab, WebView view, String url) {
        return mUrlHandler.shouldOverrideUrlLoading(tab, view, url);
    }

    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        if (mMenuIsDown) {
            // only check shortcut key when MENU is held
            return mActivity.getWindow().isShortcutKey(event.getKeyCode(),
                    event);
        } else {
            return false;
        }
    }

    @Override
    public void onUnhandledKeyEvent(KeyEvent event) {
        if (!isActivityPaused()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mActivity.onKeyDown(event.getKeyCode(), event);
            } else {
                mActivity.onKeyUp(event.getKeyCode(), event);
            }
        }
    }

    @Override
    public void doUpdateVisitedHistory(Tab tab, boolean isReload) {
        // Don't save anything in private browsing mode
        if (tab.isPrivateBrowsingEnabled()) return;
        String url = tab.getOriginalUrl();

        if (TextUtils.isEmpty(url)
                || url.regionMatches(true, 0, "about:", 0, 6)) {
            return;
        }
        mDataController.updateVisitedHistory(url);
        WebIconDatabase.getInstance().retainIconForPageUrl(url);
        if (!mActivityPaused) {
            // Since we clear the state in onPause, don't backup the current
            // state if we are already paused
            mCrashRecoveryHandler.backupState();
        }
    }

    @Override
    public void getVisitedHistory(final ValueCallback<String[]> callback) {
        AsyncTask<Void, Void, String[]> task =
                new AsyncTask<Void, Void, String[]>() {
            @Override
            public String[] doInBackground(Void... unused) {
                return Browser.getVisitedHistory(mActivity.getContentResolver());
            }
            @Override
            public void onPostExecute(String[] result) {
                callback.onReceiveValue(result);
            }
        };
        task.execute();
    }

    @Override
    public void onReceivedHttpAuthRequest(Tab tab, WebView view,
            final HttpAuthHandler handler, final String host,
            final String realm) {
        String username = null;
        String password = null;

        boolean reuseHttpAuthUsernamePassword
                = handler.useHttpAuthUsernamePassword();

        if (reuseHttpAuthUsernamePassword && view != null) {
            String[] credentials = view.getHttpAuthUsernamePassword(host, realm);
            if (credentials != null && credentials.length == 2) {
                username = credentials[0];
                password = credentials[1];
            }
        }

        if (username != null && password != null) {
            handler.proceed(username, password);
        } else {
            if (tab.inForeground()) {
                mPageDialogsHandler.showHttpAuthentication(tab, handler, host, realm);
            } else {
                handler.cancel();
            }
        }
    }

    @Override
    public void onDownloadStart(Tab tab, String url, String userAgent,
            String contentDisposition, String mimetype, long contentLength) {
        WebView w = tab.getWebView();
        DownloadHandler.onDownloadStart(mActivity, url, userAgent,
                contentDisposition, mimetype, w.isPrivateBrowsingEnabled());
        if (w.copyBackForwardList().getSize() == 0) {
            // This Tab was opened for the sole purpose of downloading a
            // file. Remove it.
            if (tab == mTabControl.getCurrentTab()) {
                // In this case, the Tab is still on top.
                goBackOnePageOrQuit();
            } else {
                // In this case, it is not.
                closeTab(tab);
            }
        }
    }

    @Override
    public Bitmap getDefaultVideoPoster() {
        return mUi.getDefaultVideoPoster();
    }

    @Override
    public View getVideoLoadingProgressView() {
        return mUi.getVideoLoadingProgressView();
    }

    @Override
    public void showSslCertificateOnError(WebView view, SslErrorHandler handler,
            SslError error) {
        mPageDialogsHandler.showSSLCertificateOnError(view, handler, error);
    }

    @Override
    public void showAutoLogin(Tab tab) {
        assert tab.inForeground();
        // Update the title bar to show the auto-login request.
        mUi.showAutoLogin(tab);
    }

    @Override
    public void hideAutoLogin(Tab tab) {
        assert tab.inForeground();
        mUi.hideAutoLogin(tab);
    }

    // helper method

    /*
     * Update the favorites icon if the private browsing isn't enabled and the
     * icon is valid.
     */
    private void maybeUpdateFavicon(Tab tab, final String originalUrl,
            final String url, Bitmap favicon) {
        if (favicon == null) {
            return;
        }
        if (!tab.isPrivateBrowsingEnabled()) {
            Bookmarks.updateFavicon(mActivity
                    .getContentResolver(), originalUrl, url, favicon);
        }
    }

    @Override
    public void bookmarkedStatusHasChanged(Tab tab) {
        // TODO: Switch to using onTabDataChanged after b/3262950 is fixed
        mUi.bookmarkedStatusHasChanged(tab);
    }

    // end WebViewController

    protected void pageUp() {
        getCurrentTopWebView().pageUp(false);
    }

    protected void pageDown() {
        getCurrentTopWebView().pageDown(false);
    }

    // callback from phone title bar
    public void editUrl() {
        if (mOptionsMenuOpen) mActivity.closeOptionsMenu();
        mUi.editUrl(false);
    }

    public void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                mActivity.getComponentName().flattenToString());
        intent.putExtra(SEND_APP_ID_EXTRA, false);
        intent.putExtra(RecognizerIntent.EXTRA_WEB_SEARCH_ONLY, true);
        mActivity.startActivity(intent);
    }

    @Override
    public void activateVoiceSearchMode(String title, List<String> results) {
        mUi.showVoiceTitleBar(title, results);
    }

    public void revertVoiceSearchMode(Tab tab) {
        mUi.revertVoiceTitleBar(tab);
    }

    public boolean supportsVoiceSearch() {
        SearchEngine searchEngine = getSettings().getSearchEngine();
        return (searchEngine != null && searchEngine.supportsVoiceSearch());
    }

    public void showCustomView(Tab tab, View view,
            WebChromeClient.CustomViewCallback callback) {
        if (tab.inForeground()) {
            if (mUi.isCustomViewShowing()) {
                callback.onCustomViewHidden();
                return;
            }
            mUi.showCustomView(view, callback);
            // Save the menu state and set it to empty while the custom
            // view is showing.
            mOldMenuState = mMenuState;
            mMenuState = EMPTY_MENU;
            mActivity.invalidateOptionsMenu();
        }
    }

    @Override
    public void hideCustomView() {
        if (mUi.isCustomViewShowing()) {
            mUi.onHideCustomView();
            // Reset the old menu state.
            mMenuState = mOldMenuState;
            mOldMenuState = EMPTY_MENU;
            mActivity.invalidateOptionsMenu();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode,
            Intent intent) {
        if (getCurrentTopWebView() == null) return;
        switch (requestCode) {
            case PREFERENCES_PAGE:
                if (resultCode == Activity.RESULT_OK && intent != null) {
                    String action = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (PreferenceKeys.PREF_PRIVACY_CLEAR_HISTORY.equals(action)) {
                        mTabControl.removeParentChildRelationShips();
                    }
                }
                break;
            case FILE_SELECTED:
                // Chose a file from the file picker.
                if (null == mUploadHandler) break;
                mUploadHandler.onResult(resultCode, intent);
                break;
            case AUTOFILL_SETUP:
                // Determine whether a profile was actually set up or not
                // and if so, send the message back to the WebTextView to
                // fill the form with the new profile.
                if (getSettings().getAutoFillProfile() != null) {
                    mAutoFillSetupMessage.sendToTarget();
                    mAutoFillSetupMessage = null;
                }
                break;
            default:
                break;
        }
        getCurrentTopWebView().requestFocus();
    }

    /**
     * Open the Go page.
     * @param startWithHistory If true, open starting on the history tab.
     *                         Otherwise, start with the bookmarks tab.
     */
    @Override
    public void bookmarksOrHistoryPicker(boolean startWithHistory) {
        if (mTabControl.getCurrentWebView() == null) {
            return;
        }
        // clear action mode
        if (isInCustomActionMode()) {
            endActionMode();
        }
        Bundle extras = new Bundle();
        // Disable opening in a new window if we have maxed out the windows
        extras.putBoolean(BrowserBookmarksPage.EXTRA_DISABLE_WINDOW,
                !mTabControl.canCreateNewTab());
        mUi.showComboView(startWithHistory, extras);
    }

    // combo view callbacks

    /**
     * callback from ComboPage when clear history is requested
     */
    public void onRemoveParentChildRelationships() {
        mTabControl.removeParentChildRelationShips();
    }

    /**
     * callback from ComboPage when bookmark/history selection
     */
    @Override
    public void onUrlSelected(String url, boolean newTab) {
        removeComboView();
        if (!TextUtils.isEmpty(url)) {
            if (newTab) {
                final Tab parent = mTabControl.getCurrentTab();
                openTab(url,
                        (parent != null) && parent.isPrivateBrowsingEnabled(),
                        !mSettings.openInBackground(),
                        true);
            } else {
                final Tab currentTab = mTabControl.getCurrentTab();
                dismissSubWindow(currentTab);
                loadUrl(getCurrentTopWebView(), url);
            }
        }
    }

    /**
     * dismiss the ComboPage
     */
    @Override
    public void removeComboView() {
        mUi.hideComboView();
    }

    // active tabs page handling

    protected void showActiveTabsPage() {
        mMenuState = EMPTY_MENU;
        mUi.showActiveTabsPage();
    }

    /**
     * Remove the active tabs page.
     * @param needToAttach If true, the active tabs page did not attach a tab
     *                     to the content view, so we need to do that here.
     */
    @Override
    public void removeActiveTabsPage(boolean needToAttach) {
        mMenuState = R.id.MAIN_MENU;
        mUi.removeActiveTabsPage();
        if (needToAttach) {
            setActiveTab(mTabControl.getCurrentTab());
        }
        getCurrentTopWebView().requestFocus();
    }

    // key handling
    protected void onBackKey() {
        if (!mUi.onBackKey()) {
            WebView subwindow = mTabControl.getCurrentSubWindow();
            if (subwindow != null) {
                if (subwindow.canGoBack()) {
                    subwindow.goBack();
                } else {
                    dismissSubWindow(mTabControl.getCurrentTab());
                }
            } else {
                goBackOnePageOrQuit();
            }
        }
    }

    // menu handling and state
    // TODO: maybe put into separate handler

    protected boolean onCreateOptionsMenu(Menu menu) {
        if (mOptionsMenuHandler != null) {
            return mOptionsMenuHandler.onCreateOptionsMenu(menu);
        }

        if (mMenuState == EMPTY_MENU) {
            return false;
        }
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.browser, menu);
        updateInLoadMenuItems(menu);
        // hold on to the menu reference here; it is used by the page callbacks
        // to update the menu based on loading state
        mCachedMenu = menu;
        return true;
    }

    protected void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        if (v instanceof TitleBarBase) {
            return;
        }
        if (!(v instanceof WebView)) {
            return;
        }
        final WebView webview = (WebView) v;
        WebView.HitTestResult result = webview.getHitTestResult();
        if (result == null) {
            return;
        }

        int type = result.getType();
        if (type == WebView.HitTestResult.UNKNOWN_TYPE) {
            Log.w(LOGTAG,
                    "We should not show context menu when nothing is touched");
            return;
        }
        if (type == WebView.HitTestResult.EDIT_TEXT_TYPE) {
            // let TextView handles context menu
            return;
        }

        // Note, http://b/issue?id=1106666 is requesting that
        // an inflated menu can be used again. This is not available
        // yet, so inflate each time (yuk!)
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.browsercontext, menu);

        // Show the correct menu group
        final String extra = result.getExtra();
        menu.setGroupVisible(R.id.PHONE_MENU,
                type == WebView.HitTestResult.PHONE_TYPE);
        menu.setGroupVisible(R.id.EMAIL_MENU,
                type == WebView.HitTestResult.EMAIL_TYPE);
        menu.setGroupVisible(R.id.GEO_MENU,
                type == WebView.HitTestResult.GEO_TYPE);
        menu.setGroupVisible(R.id.IMAGE_MENU,
                type == WebView.HitTestResult.IMAGE_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE);
        menu.setGroupVisible(R.id.ANCHOR_MENU,
                type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE);
        boolean hitText = type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                || type == WebView.HitTestResult.PHONE_TYPE
                || type == WebView.HitTestResult.EMAIL_TYPE
                || type == WebView.HitTestResult.GEO_TYPE;
        menu.setGroupVisible(R.id.SELECT_TEXT_MENU, hitText);
        if (hitText) {
            menu.findItem(R.id.select_text_menu_id)
                    .setOnMenuItemClickListener(new SelectText(webview));
        }
        // Setup custom handling depending on the type
        switch (type) {
            case WebView.HitTestResult.PHONE_TYPE:
                menu.setHeaderTitle(Uri.decode(extra));
                menu.findItem(R.id.dial_context_menu_id).setIntent(
                        new Intent(Intent.ACTION_VIEW, Uri
                                .parse(WebView.SCHEME_TEL + extra)));
                Intent addIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                addIntent.putExtra(Insert.PHONE, Uri.decode(extra));
                addIntent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                menu.findItem(R.id.add_contact_context_menu_id).setIntent(
                        addIntent);
                menu.findItem(R.id.copy_phone_context_menu_id)
                        .setOnMenuItemClickListener(
                        new Copy(extra));
                break;

            case WebView.HitTestResult.EMAIL_TYPE:
                menu.setHeaderTitle(extra);
                menu.findItem(R.id.email_context_menu_id).setIntent(
                        new Intent(Intent.ACTION_VIEW, Uri
                                .parse(WebView.SCHEME_MAILTO + extra)));
                menu.findItem(R.id.copy_mail_context_menu_id)
                        .setOnMenuItemClickListener(
                        new Copy(extra));
                break;

            case WebView.HitTestResult.GEO_TYPE:
                menu.setHeaderTitle(extra);
                menu.findItem(R.id.map_context_menu_id).setIntent(
                        new Intent(Intent.ACTION_VIEW, Uri
                                .parse(WebView.SCHEME_GEO
                                        + URLEncoder.encode(extra))));
                menu.findItem(R.id.copy_geo_context_menu_id)
                        .setOnMenuItemClickListener(
                        new Copy(extra));
                break;

            case WebView.HitTestResult.SRC_ANCHOR_TYPE:
            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                menu.setHeaderTitle(extra);
                // decide whether to show the open link in new tab option
                boolean showNewTab = mTabControl.canCreateNewTab();
                MenuItem newTabItem
                        = menu.findItem(R.id.open_newtab_context_menu_id);
                newTabItem.setTitle(getSettings().openInBackground()
                        ? R.string.contextmenu_openlink_newwindow_background
                        : R.string.contextmenu_openlink_newwindow);
                newTabItem.setVisible(showNewTab);
                if (showNewTab) {
                    if (WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE == type) {
                        newTabItem.setOnMenuItemClickListener(
                                new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        final HashMap<String, WebView> hrefMap =
                                                new HashMap<String, WebView>();
                                        hrefMap.put("webview", webview);
                                        final Message msg = mHandler.obtainMessage(
                                                FOCUS_NODE_HREF,
                                                R.id.open_newtab_context_menu_id,
                                                0, hrefMap);
                                        webview.requestFocusNodeHref(msg);
                                        return true;
                                    }
                                });
                    } else {
                        newTabItem.setOnMenuItemClickListener(
                                new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        final Tab parent = mTabControl.getCurrentTab();
                                        final Tab newTab =
                                                openTab(extra,
                                                        (parent != null)
                                                        && parent.isPrivateBrowsingEnabled(),
                                                        !mSettings.openInBackground(),
                                                        true);
                                        if (newTab != parent) {
                                            parent.addChildTab(newTab);
                                        }
                                        return true;
                                    }
                                });
                    }
                }
                if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                    break;
                }
                // otherwise fall through to handle image part
            case WebView.HitTestResult.IMAGE_TYPE:
                if (type == WebView.HitTestResult.IMAGE_TYPE) {
                    menu.setHeaderTitle(extra);
                }
                menu.findItem(R.id.view_image_context_menu_id).setIntent(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(extra)));
                menu.findItem(R.id.download_context_menu_id).
                        setOnMenuItemClickListener(
                                new Download(mActivity, extra, webview.isPrivateBrowsingEnabled()));
                menu.findItem(R.id.set_wallpaper_context_menu_id).
                        setOnMenuItemClickListener(new WallpaperHandler(mActivity,
                                extra));
                break;

            default:
                Log.w(LOGTAG, "We should not get here.");
                break;
        }
        //update the ui
        mUi.onContextMenuCreated(menu);
    }

    /**
     * As the menu can be open when loading state changes
     * we must manually update the state of the stop/reload menu
     * item
     */
    private void updateInLoadMenuItems(Menu menu) {
        if (menu == null) {
            return;
        }
        MenuItem dest = menu.findItem(R.id.stop_reload_menu_id);
        MenuItem src = mInLoad ?
                menu.findItem(R.id.stop_menu_id):
                menu.findItem(R.id.reload_menu_id);
        if (src != null) {
            dest.setIcon(src.getIcon());
            dest.setTitle(src.getTitle());
        }
    }

    boolean onPrepareOptionsMenu(Menu menu) {
        if (mOptionsMenuHandler != null) {
            return mOptionsMenuHandler.onPrepareOptionsMenu(menu);
        }
        // Note: setVisible will decide whether an item is visible; while
        // setEnabled() will decide whether an item is enabled, which also means
        // whether the matching shortcut key will function.
        switch (mMenuState) {
            case EMPTY_MENU:
                if (mCurrentMenuState != mMenuState) {
                    menu.setGroupVisible(R.id.MAIN_MENU, false);
                    menu.setGroupEnabled(R.id.MAIN_MENU, false);
                    menu.setGroupEnabled(R.id.MAIN_SHORTCUT_MENU, false);
                }
                break;
            default:
                if (mCurrentMenuState != mMenuState) {
                    menu.setGroupVisible(R.id.MAIN_MENU, true);
                    menu.setGroupEnabled(R.id.MAIN_MENU, true);
                    menu.setGroupEnabled(R.id.MAIN_SHORTCUT_MENU, true);
                }
                final WebView w = getCurrentTopWebView();
                boolean canGoBack = false;
                boolean canGoForward = false;
                boolean isHome = false;
                if (w != null) {
                    canGoBack = w.canGoBack();
                    canGoForward = w.canGoForward();
                    isHome = mSettings.getHomePage().equals(w.getUrl());
                }
                final MenuItem back = menu.findItem(R.id.back_menu_id);
                back.setEnabled(canGoBack);

                final MenuItem home = menu.findItem(R.id.homepage_menu_id);
                home.setEnabled(!isHome);

                final MenuItem forward = menu.findItem(R.id.forward_menu_id);
                forward.setEnabled(canGoForward);

                // decide whether to show the share link option
                PackageManager pm = mActivity.getPackageManager();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                ResolveInfo ri = pm.resolveActivity(send,
                        PackageManager.MATCH_DEFAULT_ONLY);
                menu.findItem(R.id.share_page_menu_id).setVisible(ri != null);

                boolean isNavDump = mSettings.enableNavDump();
                final MenuItem nav = menu.findItem(R.id.dump_nav_menu_id);
                nav.setVisible(isNavDump);
                nav.setEnabled(isNavDump);

                boolean showDebugSettings = mSettings.isDebugEnabled();
                final MenuItem counter = menu.findItem(R.id.dump_counters_menu_id);
                counter.setVisible(showDebugSettings);
                counter.setEnabled(showDebugSettings);

                final MenuItem newtab = menu.findItem(R.id.new_tab_menu_id);
                newtab.setEnabled(getTabControl().canCreateNewTab());

                MenuItem archive = menu.findItem(R.id.save_webarchive_menu_id);
                Tab tab = getTabControl().getCurrentTab();
                String url = tab != null ? tab.getUrl() : null;
                archive.setVisible(!TextUtils.isEmpty(url)
                        && !url.endsWith(".webarchivexml"));
                break;
        }
        mCurrentMenuState = mMenuState;
        return mUi.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (mOptionsMenuHandler != null &&
                mOptionsMenuHandler.onOptionsItemSelected(item)) {
            return true;
        }

        if (item.getGroupId() != R.id.CONTEXT_MENU) {
            // menu remains active, so ensure comboview is dismissed
            // if main menu option is selected
            removeComboView();
        }
        if (null == getCurrentTopWebView()) {
            return false;
        }
        if (mMenuIsDown) {
            // The shortcut action consumes the MENU. Even if it is still down,
            // it won't trigger the next shortcut action. In the case of the
            // shortcut action triggering a new activity, like Bookmarks, we
            // won't get onKeyUp for MENU. So it is important to reset it here.
            mMenuIsDown = false;
        }
        switch (item.getItemId()) {
            // -- Main menu
            case R.id.new_tab_menu_id:
                openTabToHomePage();
                break;

            case R.id.incognito_menu_id:
                openIncognitoTab();
                break;

            case R.id.goto_menu_id:
                editUrl();
                break;

            case R.id.bookmarks_menu_id:
                bookmarksOrHistoryPicker(false);
                break;

            case R.id.active_tabs_menu_id:
                showActiveTabsPage();
                break;

            case R.id.add_bookmark_menu_id:
                bookmarkCurrentPage(AddBookmarkPage.DEFAULT_FOLDER_ID, false);
                break;

            case R.id.stop_reload_menu_id:
                if (mInLoad) {
                    stopLoading();
                } else {
                    getCurrentTopWebView().reload();
                }
                break;

            case R.id.back_menu_id:
                getCurrentTopWebView().goBack();
                break;

            case R.id.forward_menu_id:
                getCurrentTopWebView().goForward();
                break;

            case R.id.close_menu_id:
                // Close the subwindow if it exists.
                if (mTabControl.getCurrentSubWindow() != null) {
                    dismissSubWindow(mTabControl.getCurrentTab());
                    break;
                }
                closeCurrentTab();
                break;

            case R.id.homepage_menu_id:
                Tab current = mTabControl.getCurrentTab();
                if (current != null) {
                    dismissSubWindow(current);
                    loadUrl(current.getWebView(), mSettings.getHomePage());
                }
                break;

            case R.id.preferences_menu_id:
                Intent intent = new Intent(mActivity, BrowserPreferencesPage.class);
                intent.putExtra(BrowserPreferencesPage.CURRENT_PAGE,
                        getCurrentTopWebView().getUrl());
                mActivity.startActivityForResult(intent, PREFERENCES_PAGE);
                break;

            case R.id.find_menu_id:
                getCurrentTopWebView().showFindDialog(null, true);
                break;

            case R.id.save_webarchive_menu_id:
                String state = Environment.getExternalStorageState();
                if (!Environment.MEDIA_MOUNTED.equals(state)) {
                    Log.e(LOGTAG, "External storage not mounted");
                    Toast.makeText(mActivity, R.string.webarchive_failed,
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                final String directory = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS) + File.separator;
                File dir = new File(directory);
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(LOGTAG, "Save as Web Archive: mkdirs for " + directory + " failed!");
                    Toast.makeText(mActivity, R.string.webarchive_failed,
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                final WebView topWebView = getCurrentTopWebView();
                final String title = topWebView.getTitle();
                final String url = topWebView.getUrl();
                topWebView.saveWebArchive(directory, true,
                        new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(final String value) {
                        if (value != null) {
                            File file = new File(value);
                            final long length = file.length();
                            if (file.exists() && length > 0) {
                                Toast.makeText(mActivity, R.string.webarchive_saved,
                                        Toast.LENGTH_SHORT).show();
                                final DownloadManager manager = (DownloadManager) mActivity
                                        .getSystemService(Context.DOWNLOAD_SERVICE);
                                new Thread("Add WebArchive to download manager") {
                                    @Override
                                    public void run() {
                                        manager.addCompletedDownload(
                                                null == title ? value : title,
                                                value, true, "application/x-webarchive-xml",
                                                value, length, true);
                                    }
                                }.start();
                                return;
                            }
                        }
                        DownloadHandler.onDownloadStartNoStream(mActivity,
                                url, null, null, null, topWebView.isPrivateBrowsingEnabled());
                    }
                });
                break;

            case R.id.page_info_menu_id:
                mPageDialogsHandler.showPageInfo(mTabControl.getCurrentTab(),
                        false);
                break;

            case R.id.classic_history_menu_id:
                bookmarksOrHistoryPicker(true);
                break;

            case R.id.title_bar_share_page_url:
            case R.id.share_page_menu_id:
                Tab currentTab = mTabControl.getCurrentTab();
                if (null == currentTab) {
                    return false;
                }
                shareCurrentPage(currentTab);
                break;

            case R.id.dump_nav_menu_id:
                getCurrentTopWebView().debugDump();
                break;

            case R.id.dump_counters_menu_id:
                getCurrentTopWebView().dumpV8Counters();
                break;

            case R.id.zoom_in_menu_id:
                getCurrentTopWebView().zoomIn();
                break;

            case R.id.zoom_out_menu_id:
                getCurrentTopWebView().zoomOut();
                break;

            case R.id.view_downloads_menu_id:
                viewDownloads();
                break;

            case R.id.window_one_menu_id:
            case R.id.window_two_menu_id:
            case R.id.window_three_menu_id:
            case R.id.window_four_menu_id:
            case R.id.window_five_menu_id:
            case R.id.window_six_menu_id:
            case R.id.window_seven_menu_id:
            case R.id.window_eight_menu_id:
                {
                    int menuid = item.getItemId();
                    for (int id = 0; id < WINDOW_SHORTCUT_ID_ARRAY.length; id++) {
                        if (WINDOW_SHORTCUT_ID_ARRAY[id] == menuid) {
                            Tab desiredTab = mTabControl.getTab(id);
                            if (desiredTab != null &&
                                    desiredTab != mTabControl.getCurrentTab()) {
                                switchToTab(id);
                            }
                            break;
                        }
                    }
                }
                break;

            default:
                return false;
        }
        return true;
    }

    public boolean onContextItemSelected(MenuItem item) {
        // Let the History and Bookmark fragments handle menus they created.
        if (item.getGroupId() == R.id.CONTEXT_MENU) {
            return false;
        }

        int id = item.getItemId();
        boolean result = true;
        switch (id) {
            // For the context menu from the title bar
            case R.id.title_bar_copy_page_url:
                Tab currentTab = mTabControl.getCurrentTab();
                if (null == currentTab) {
                    result = false;
                    break;
                }
                WebView mainView = currentTab.getWebView();
                if (null == mainView) {
                    result = false;
                    break;
                }
                copy(mainView.getUrl());
                break;
            // -- Browser context menu
            case R.id.open_context_menu_id:
            case R.id.save_link_context_menu_id:
            case R.id.copy_link_context_menu_id:
                final WebView webView = getCurrentTopWebView();
                if (null == webView) {
                    result = false;
                    break;
                }
                final HashMap<String, WebView> hrefMap =
                        new HashMap<String, WebView>();
                hrefMap.put("webview", webView);
                final Message msg = mHandler.obtainMessage(
                        FOCUS_NODE_HREF, id, 0, hrefMap);
                webView.requestFocusNodeHref(msg);
                break;

            default:
                // For other context menus
                result = onOptionsItemSelected(item);
        }
        return result;
    }

    /**
     * support programmatically opening the context menu
     */
    public void openContextMenu(View view) {
        mActivity.openContextMenu(view);
    }

    /**
     * programmatically open the options menu
     */
    public void openOptionsMenu() {
        mActivity.openOptionsMenu();
    }

    public boolean onMenuOpened(int featureId, Menu menu) {
        if (mOptionsMenuOpen) {
            if (mConfigChanged) {
                // We do not need to make any changes to the state of the
                // title bar, since the only thing that happened was a
                // change in orientation
                mConfigChanged = false;
            } else {
                if (!mExtendedMenuOpen) {
                    mExtendedMenuOpen = true;
                    mUi.onExtendedMenuOpened();
                } else {
                    // Switching the menu back to icon view, so show the
                    // title bar once again.
                    mExtendedMenuOpen = false;
                    mUi.onExtendedMenuClosed(mInLoad);
                }
            }
        } else {
            // The options menu is closed, so open it, and show the title
            mOptionsMenuOpen = true;
            mConfigChanged = false;
            mExtendedMenuOpen = false;
            mUi.onOptionsMenuOpened();
        }
        return true;
    }

    public void onOptionsMenuClosed(Menu menu) {
        mOptionsMenuOpen = false;
        mUi.onOptionsMenuClosed(mInLoad);
    }

    public void onContextMenuClosed(Menu menu) {
        mUi.onContextMenuClosed(menu, mInLoad);
    }

    // Helper method for getting the top window.
    @Override
    public WebView getCurrentTopWebView() {
        return mTabControl.getCurrentTopWebView();
    }

    @Override
    public WebView getCurrentWebView() {
        return mTabControl.getCurrentWebView();
    }

    /*
     * This method is called as a result of the user selecting the options
     * menu to see the download window. It shows the download window on top of
     * the current window.
     */
    void viewDownloads() {
        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        mActivity.startActivity(intent);
    }

    // action mode

    void onActionModeStarted(ActionMode mode) {
        mUi.onActionModeStarted(mode);
        mActionMode = mode;
    }

    /*
     * True if a custom ActionMode (i.e. find or select) is in use.
     */
    @Override
    public boolean isInCustomActionMode() {
        return mActionMode != null;
    }

    /*
     * End the current ActionMode.
     */
    @Override
    public void endActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    /*
     * Called by find and select when they are finished.  Replace title bars
     * as necessary.
     */
    public void onActionModeFinished(ActionMode mode) {
        if (!isInCustomActionMode()) return;
        mUi.onActionModeFinished(mInLoad);
        mActionMode = null;
    }

    boolean isInLoad() {
        return mInLoad;
    }

    // bookmark handling

    /**
     * add the current page as a bookmark to the given folder id
     * @param folderId use -1 for the default folder
     * @param canBeAnEdit If true, check to see whether the site is already
     *          bookmarked, and if it is, edit that bookmark.  If false, and
     *          the site is already bookmarked, do not attempt to edit the
     *          existing bookmark.
     */
    @Override
    public void bookmarkCurrentPage(long folderId, boolean canBeAnEdit) {
        Intent i = new Intent(mActivity,
                AddBookmarkPage.class);
        WebView w = getCurrentTopWebView();
        i.putExtra(BrowserContract.Bookmarks.URL, w.getUrl());
        i.putExtra(BrowserContract.Bookmarks.TITLE, w.getTitle());
        String touchIconUrl = w.getTouchIconUrl();
        if (touchIconUrl != null) {
            i.putExtra(AddBookmarkPage.TOUCH_ICON_URL, touchIconUrl);
            WebSettings settings = w.getSettings();
            if (settings != null) {
                i.putExtra(AddBookmarkPage.USER_AGENT,
                        settings.getUserAgentString());
            }
        }
        i.putExtra(BrowserContract.Bookmarks.THUMBNAIL,
                createScreenshot(w, getDesiredThumbnailWidth(mActivity),
                getDesiredThumbnailHeight(mActivity)));
        i.putExtra(BrowserContract.Bookmarks.FAVICON, w.getFavicon());
        i.putExtra(BrowserContract.Bookmarks.PARENT,
                folderId);
        if (canBeAnEdit) {
            i.putExtra(AddBookmarkPage.CHECK_FOR_DUPE, true);
        }
        // Put the dialog at the upper right of the screen, covering the
        // star on the title bar.
        i.putExtra("gravity", Gravity.RIGHT | Gravity.TOP);
        mActivity.startActivity(i);
    }

    // file chooser
    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        mUploadHandler = new UploadHandler(this);
        mUploadHandler.openFileChooser(uploadMsg, acceptType);
    }

    // thumbnails

    /**
     * Return the desired width for thumbnail screenshots, which are stored in
     * the database, and used on the bookmarks screen.
     * @param context Context for finding out the density of the screen.
     * @return desired width for thumbnail screenshot.
     */
    static int getDesiredThumbnailWidth(Context context) {
        return context.getResources().getDimensionPixelOffset(
                R.dimen.bookmarkThumbnailWidth);
    }

    /**
     * Return the desired height for thumbnail screenshots, which are stored in
     * the database, and used on the bookmarks screen.
     * @param context Context for finding out the density of the screen.
     * @return desired height for thumbnail screenshot.
     */
    static int getDesiredThumbnailHeight(Context context) {
        return context.getResources().getDimensionPixelOffset(
                R.dimen.bookmarkThumbnailHeight);
    }

    static Bitmap createScreenshot(Tab tab, int width, int height) {
        if ((tab != null) && (tab.getWebView() != null)) {
            return createScreenshot(tab.getWebView(), width, height);
        }
        return null;
    }

    private static Bitmap createScreenshot(WebView view, int width, int height) {
        // We render to a bitmap 2x the desired size so that we can then
        // re-scale it with filtering since canvas.scale doesn't filter
        // This helps reduce aliasing at the cost of being slightly blurry
        final int filter_scale = 2;
        Picture thumbnail = view.capturePicture();
        if (thumbnail == null) {
            return null;
        }
        width *= filter_scale;
        height *= filter_scale;
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bm);
        // May need to tweak these values to determine what is the
        // best scale factor
        int thumbnailWidth = thumbnail.getWidth();
        int thumbnailHeight = thumbnail.getHeight();
        float scaleFactor = 1.0f;
        if (thumbnailWidth > 0 && thumbnailHeight > 0) {
            scaleFactor = (float) width / (float)thumbnailWidth;
        } else {
            return null;
        }

        float scaleFactorY = (float) height / (float)thumbnailHeight;
        if (scaleFactorY > scaleFactor) {
            // The picture is narrower than the requested AR
            // Center the thumnail and crop the sides
            scaleFactor = scaleFactorY;
            float wx = (thumbnailWidth * scaleFactor) - width;
            canvas.translate((int) -(wx / 2), 0);
        }

        canvas.scale(scaleFactor, scaleFactor);

        thumbnail.draw(canvas);
        Bitmap ret = Bitmap.createScaledBitmap(bm, width / filter_scale,
                height / filter_scale, true);
        bm.recycle();
        return ret;
    }

    private void updateScreenshot(Tab tab) {
        // If this is a bookmarked site, add a screenshot to the database.
        // FIXME: Would like to make sure there is actually something to
        // draw, but the API for that (WebViewCore.pictureReady()) is not
        // currently accessible here.

        WebView view = tab.getWebView();
        if (view == null) {
            // Tab was destroyed
            return;
        }
        final String url = tab.getUrl();
        final String originalUrl = view.getOriginalUrl();

        if (TextUtils.isEmpty(url)) {
            return;
        }

        // Only update thumbnails for web urls (http(s)://), not for
        // about:, javascript:, data:, etc...
        // Unless it is a bookmarked site, then always update
        if (!Patterns.WEB_URL.matcher(url).matches() && !tab.isBookmarkedSite()) {
            return;
        }

        final Bitmap bm = createScreenshot(view, getDesiredThumbnailWidth(mActivity),
                getDesiredThumbnailHeight(mActivity));
        if (bm == null) {
            return;
        }

        final ContentResolver cr = mActivity.getContentResolver();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                Cursor cursor = null;
                try {
                    // TODO: Clean this up
                    cursor = Bookmarks.queryCombinedForUrl(cr, originalUrl, url);
                    if (cursor != null && cursor.moveToFirst()) {
                        final ByteArrayOutputStream os =
                                new ByteArrayOutputStream();
                        bm.compress(Bitmap.CompressFormat.PNG, 100, os);

                        ContentValues values = new ContentValues();
                        values.put(Images.THUMBNAIL, os.toByteArray());

                        do {
                            values.put(Images.URL, cursor.getString(0));
                            cr.update(Images.CONTENT_URI, values, null, null);
                        } while (cursor.moveToNext());
                    }
                } catch (IllegalStateException e) {
                    // Ignore
                } finally {
                    if (cursor != null) cursor.close();
                }
                return null;
            }
        }.execute();
    }

    private class Copy implements OnMenuItemClickListener {
        private CharSequence mText;

        public boolean onMenuItemClick(MenuItem item) {
            copy(mText);
            return true;
        }

        public Copy(CharSequence toCopy) {
            mText = toCopy;
        }
    }

    private static class Download implements OnMenuItemClickListener {
        private Activity mActivity;
        private String mText;
        private boolean mPrivateBrowsing;

        public boolean onMenuItemClick(MenuItem item) {
            DownloadHandler.onDownloadStartNoStream(mActivity, mText, null,
                    null, null, mPrivateBrowsing);
            return true;
        }

        public Download(Activity activity, String toDownload, boolean privateBrowsing) {
            mActivity = activity;
            mText = toDownload;
            mPrivateBrowsing = privateBrowsing;
        }
    }

    private static class SelectText implements OnMenuItemClickListener {
        private WebView mWebView;

        public boolean onMenuItemClick(MenuItem item) {
            if (mWebView != null) {
                return mWebView.selectText();
            }
            return false;
        }

        public SelectText(WebView webView) {
            mWebView = webView;
        }

    }

    /********************** TODO: UI stuff *****************************/

    // these methods have been copied, they still need to be cleaned up

    /****************** tabs ***************************************************/

    // basic tab interactions:

    // it is assumed that tabcontrol already knows about the tab
    protected void addTab(Tab tab) {
        mUi.addTab(tab);
    }

    protected void removeTab(Tab tab) {
        mUi.removeTab(tab);
        mTabControl.removeTab(tab);
    }

    @Override
    public void setActiveTab(Tab tab) {
        // monkey protection against delayed start
        if (tab != null) {
            mTabControl.setCurrentTab(tab);
            // the tab is guaranteed to have a webview after setCurrentTab
            mUi.setActiveTab(tab);
        }
    }

    protected void closeEmptyChildTab() {
        Tab current = mTabControl.getCurrentTab();
        if (current != null
                && current.getWebView().copyBackForwardList().getSize() == 0) {
            Tab parent = current.getParentTab();
            if (parent != null) {
                switchToTab(mTabControl.getTabIndex(parent));
                closeTab(current);
            }
        }
    }

    protected void reuseTab(Tab appTab, String appId, UrlData urlData) {
        // Dismiss the subwindow if applicable.
        dismissSubWindow(appTab);
        // Since we might kill the WebView, remove it from the
        // content view first.
        mUi.detachTab(appTab);
        // Recreate the main WebView after destroying the old one.
        mTabControl.recreateWebView(appTab);
        // TODO: analyze why the remove and add are necessary
        mUi.attachTab(appTab);
        if (mTabControl.getCurrentTab() != appTab) {
            switchToTab(mTabControl.getTabIndex(appTab));
            loadUrlDataIn(appTab, urlData);
        } else {
            // If the tab was the current tab, we have to attach
            // it to the view system again.
            setActiveTab(appTab);
            loadUrlDataIn(appTab, urlData);
        }
    }

    // Remove the sub window if it exists. Also called by TabControl when the
    // user clicks the 'X' to dismiss a sub window.
    public void dismissSubWindow(Tab tab) {
        removeSubWindow(tab);
        // dismiss the subwindow. This will destroy the WebView.
        tab.dismissSubWindow();
        getCurrentTopWebView().requestFocus();
    }

    @Override
    public void removeSubWindow(Tab t) {
        if (t.getSubWebView() != null) {
            mUi.removeSubWindow(t.getSubViewContainer());
        }
    }

    @Override
    public void attachSubWindow(Tab tab) {
        if (tab.getSubWebView() != null) {
            mUi.attachSubWindow(tab.getSubViewContainer());
            getCurrentTopWebView().requestFocus();
        }
    }

    // open a non inconito tab with the given url data
    // and set as active tab
    public Tab openTab(UrlData urlData) {
        Tab tab = createNewTab(false, true, true);
        if ((tab != null) && !urlData.isEmpty()) {
            loadUrlDataIn(tab, urlData);
        }
        return tab;
    }

    @Override
    public Tab openTabToHomePage() {
        return openTab(mSettings.getHomePage(), false, true, false);
    }

    @Override
    public Tab openIncognitoTab() {
        return openTab(INCOGNITO_URI, true, true, false);
    }

    @Override
    public Tab openTab(String url, boolean incognito, boolean setActive,
            boolean useCurrent) {
        Tab tab = createNewTab(incognito, setActive, useCurrent);
        if (tab != null) {
            WebView w = tab.getWebView();
            if (url != null) {
                loadUrl(w, url);
            }
        }
        return tab;
    }

    // this method will attempt to create a new tab
    // incognito: private browsing tab
    // setActive: ste tab as current tab
    // useCurrent: if no new tab can be created, return current tab
    private Tab createNewTab(boolean incognito, boolean setActive,
            boolean useCurrent) {
        Tab tab = null;
        if (mTabControl.canCreateNewTab()) {
            tab = mTabControl.createNewTab(incognito);
            addTab(tab);
            if (setActive) {
                setActiveTab(tab);
            }
        } else {
            if (useCurrent) {
                tab = mTabControl.getCurrentTab();
                // Get rid of the subwindow if it exists
                dismissSubWindow(tab);
            } else {
                mUi.showMaxTabsWarning();
            }
        }
        return tab;
    }

    /**
     * @param index Index of the tab to change to, as defined by
     *              mTabControl.getTabIndex(Tab t).
     * @return boolean True if we successfully switched to a different tab.  If
     *                 the indexth tab is null, or if that tab is the same as
     *                 the current one, return false.
     */
    @Override
    public boolean switchToTab(int index) {
        // hide combo view if open
        removeComboView();
        Tab tab = mTabControl.getTab(index);
        Tab currentTab = mTabControl.getCurrentTab();
        if (tab == null || tab == currentTab) {
            return false;
        }
        setActiveTab(tab);
        return true;
    }

    @Override
    public void closeCurrentTab() {
        // hide combo view if open
        removeComboView();
        final Tab current = mTabControl.getCurrentTab();
        if (mTabControl.getTabCount() == 1) {
            mActivity.finish();
            return;
        }
        final Tab parent = current.getParentTab();
        int indexToShow = -1;
        if (parent != null) {
            indexToShow = mTabControl.getTabIndex(parent);
        } else {
            final int currentIndex = mTabControl.getCurrentIndex();
            // Try to move to the tab to the right
            indexToShow = currentIndex + 1;
            if (indexToShow > mTabControl.getTabCount() - 1) {
                // Try to move to the tab to the left
                indexToShow = currentIndex - 1;
            }
        }
        if (switchToTab(indexToShow)) {
            // Close window
            closeTab(current);
        }
    }

    /**
     * Close the tab, remove its associated title bar, and adjust mTabControl's
     * current tab to a valid value.
     */
    @Override
    public void closeTab(Tab tab) {
        // hide combo view if open
        removeComboView();
        int currentIndex = mTabControl.getCurrentIndex();
        int removeIndex = mTabControl.getTabIndex(tab);
        Tab newtab = mTabControl.getTab(currentIndex);
        setActiveTab(newtab);
        removeTab(tab);
    }

    /**************** TODO: Url loading clean up *******************************/

    // Called when loading from context menu or LOAD_URL message
    protected void loadUrlFromContext(WebView view, String url) {
        // In case the user enters nothing.
        if (url != null && url.length() != 0 && view != null) {
            url = UrlUtils.smartUrlFilter(url);
            if (!view.getWebViewClient().shouldOverrideUrlLoading(view, url)) {
                loadUrl(view, url);
            }
        }
    }

    /**
     * Load the URL into the given WebView and update the title bar
     * to reflect the new load.  Call this instead of WebView.loadUrl
     * directly.
     * @param view The WebView used to load url.
     * @param url The URL to load.
     */
    protected void loadUrl(WebView view, String url) {
        view.loadUrl(url);
    }

    /**
     * Load UrlData into a Tab and update the title bar to reflect the new
     * load.  Call this instead of UrlData.loadIn directly.
     * @param t The Tab used to load.
     * @param data The UrlData being loaded.
     */
    protected void loadUrlDataIn(Tab t, UrlData data) {
        data.loadIn(t);
    }

    @Override
    public void onUserCanceledSsl(Tab tab) {
        WebView web = tab.getWebView();
        // TODO: Figure out the "right" behavior
        if (web.canGoBack()) {
            web.goBack();
        } else {
            web.loadUrl(mSettings.getHomePage());
        }
    }

    void goBackOnePageOrQuit() {
        Tab current = mTabControl.getCurrentTab();
        if (current == null) {
            /*
             * Instead of finishing the activity, simply push this to the back
             * of the stack and let ActivityManager to choose the foreground
             * activity. As BrowserActivity is singleTask, it will be always the
             * root of the task. So we can use either true or false for
             * moveTaskToBack().
             */
            mActivity.moveTaskToBack(true);
            return;
        }
        WebView w = current.getWebView();
        if (w.canGoBack()) {
            w.goBack();
        } else {
            // Check to see if we are closing a window that was created by
            // another window. If so, we switch back to that window.
            Tab parent = current.getParentTab();
            if (parent != null) {
                switchToTab(mTabControl.getTabIndex(parent));
                // Now we close the other tab
                closeTab(current);
            } else {
                /*
                 * Instead of finishing the activity, simply push this to the back
                 * of the stack and let ActivityManager to choose the foreground
                 * activity. As BrowserActivity is singleTask, it will be always the
                 * root of the task. So we can use either true or false for
                 * moveTaskToBack().
                 */
                mActivity.moveTaskToBack(true);
            }
        }
    }

    /**
     * Feed the previously stored results strings to the BrowserProvider so that
     * the SearchDialog will show them instead of the standard searches.
     * @param result String to show on the editable line of the SearchDialog.
     */
    @Override
    public void showVoiceSearchResults(String result) {
        ContentProviderClient client = mActivity.getContentResolver()
                .acquireContentProviderClient(Browser.BOOKMARKS_URI);
        ContentProvider prov = client.getLocalContentProvider();
        BrowserProvider bp = (BrowserProvider) prov;
        bp.setQueryResults(mTabControl.getCurrentTab().getVoiceSearchResults());
        client.release();

        Bundle bundle = createGoogleSearchSourceBundle(
                GOOGLE_SEARCH_SOURCE_SEARCHKEY);
        bundle.putBoolean(SearchManager.CONTEXT_IS_VOICE, true);
        startSearch(result, false, bundle, false);
    }

    private void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (appSearchData == null) {
            appSearchData = createGoogleSearchSourceBundle(
                    GOOGLE_SEARCH_SOURCE_TYPE);
        }

        SearchEngine searchEngine = mSettings.getSearchEngine();
        if (searchEngine != null && !searchEngine.supportsVoiceSearch()) {
            appSearchData.putBoolean(SearchManager.DISABLE_VOICE_SEARCH, true);
        }
        mActivity.startSearch(initialQuery, selectInitialQuery, appSearchData,
                globalSearch);
    }

    private Bundle createGoogleSearchSourceBundle(String source) {
        Bundle bundle = new Bundle();
        bundle.putString(Search.SOURCE, source);
        return bundle;
    }

    /**
     * helper method for key handler
     * returns the current tab if it can't advance
     */
    private int getNextTabIndex() {
        return Math.min(mTabControl.getTabCount() - 1,
                mTabControl.getCurrentIndex() + 1);
    }

    /**
     * helper method for key handler
     * returns the current tab if it can't advance
     */
    private int getPrevTabIndex() {
        return  Math.max(0, mTabControl.getCurrentIndex() - 1);
    }

    /**
     * handle key events in browser
     *
     * @param keyCode
     * @param event
     * @return true if handled, false to pass to super
     */
    boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean noModifiers = event.hasNoModifiers();

        // Even if MENU is already held down, we need to call to super to open
        // the IME on long press.
        if (!noModifiers
                && ((KeyEvent.KEYCODE_MENU == keyCode)
                        || (KeyEvent.KEYCODE_CTRL_LEFT == keyCode)
                        || (KeyEvent.KEYCODE_CTRL_RIGHT == keyCode))) {
            mMenuIsDown = true;
            return false;
        }

        WebView webView = getCurrentTopWebView();
        if (webView == null) return false;

        boolean ctrl = event.hasModifiers(KeyEvent.META_CTRL_ON);
        boolean shift = event.hasModifiers(KeyEvent.META_SHIFT_ON);

        switch(keyCode) {
            case KeyEvent.KEYCODE_TAB:
                if (event.isCtrlPressed()) {
                    if (event.isShiftPressed()) {
                        // prev tab
                        switchToTab(getPrevTabIndex());
                    } else {
                        // next tab
                        switchToTab(getNextTabIndex());
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_SPACE:
                // WebView/WebTextView handle the keys in the KeyDown. As
                // the Activity's shortcut keys are only handled when WebView
                // doesn't, have to do it in onKeyDown instead of onKeyUp.
                if (shift) {
                    pageUp();
                } else if (noModifiers) {
                    pageDown();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!noModifiers) break;
                event.startTracking();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (ctrl) {
                    webView.goBack();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (ctrl) {
                    webView.goForward();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_A:
                if (ctrl) {
                    webView.selectAll();
                    return true;
                }
                break;
//          case KeyEvent.KEYCODE_B:    // menu
            case KeyEvent.KEYCODE_C:
                if (ctrl) {
                    webView.copySelection();
                    return true;
                }
                break;
//          case KeyEvent.KEYCODE_D:    // menu
//          case KeyEvent.KEYCODE_E:    // in Chrome: puts '?' in URL bar
//          case KeyEvent.KEYCODE_F:    // menu
//          case KeyEvent.KEYCODE_G:    // in Chrome: finds next match
//          case KeyEvent.KEYCODE_H:    // menu
//          case KeyEvent.KEYCODE_I:    // unused
//          case KeyEvent.KEYCODE_J:    // menu
//          case KeyEvent.KEYCODE_K:    // in Chrome: puts '?' in URL bar
//          case KeyEvent.KEYCODE_L:    // menu
//          case KeyEvent.KEYCODE_M:    // unused
//          case KeyEvent.KEYCODE_N:    // in Chrome: new window
//          case KeyEvent.KEYCODE_O:    // in Chrome: open file
//          case KeyEvent.KEYCODE_P:    // in Chrome: print page
//          case KeyEvent.KEYCODE_Q:    // unused
//          case KeyEvent.KEYCODE_R:
//          case KeyEvent.KEYCODE_S:    // in Chrome: saves page
            case KeyEvent.KEYCODE_T:
                // we can't use the ctrl/shift flags, they check for
                // exclusive use of a modifier
                if (event.isCtrlPressed()) {
                    if (event.isShiftPressed()) {
                        openIncognitoTab();
                    } else {
                        openTabToHomePage();
                    }
                    return true;
                }
                break;
//          case KeyEvent.KEYCODE_U:    // in Chrome: opens source of page
//          case KeyEvent.KEYCODE_V:    // text view intercepts to paste
            case KeyEvent.KEYCODE_W:    // in Chrome: close tab
                if (ctrl) {
                    closeCurrentTab();
                    return true;
                }
                break;
//          case KeyEvent.KEYCODE_X:    // text view intercepts to cut
//          case KeyEvent.KEYCODE_Y:    // unused
//          case KeyEvent.KEYCODE_Z:    // unused
        }
        // it is a regular key and webview is not null
         return mUi.dispatchKey(keyCode, event);
    }

    boolean onKeyLongPress(int keyCode, KeyEvent event) {
        switch(keyCode) {
        case KeyEvent.KEYCODE_BACK:
            if (mUi.showsWeb()) {
                bookmarksOrHistoryPicker(true);
                return true;
            }
            break;
        }
        return false;
    }

    boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!event.hasNoModifiers()) return false;
        switch(keyCode) {
            case KeyEvent.KEYCODE_MENU:
                mMenuIsDown = false;
                break;
            case KeyEvent.KEYCODE_BACK:
                if (event.isTracking() && !event.isCanceled()) {
                    onBackKey();
                    return true;
                }
                break;
        }
        return false;
    }

    public boolean isMenuDown() {
        return mMenuIsDown;
    }

    public void setupAutoFill(Message message) {
        // Open the settings activity at the AutoFill profile fragment so that
        // the user can create a new profile. When they return, we will dispatch
        // the message so that we can autofill the form using their new profile.
        Intent intent = new Intent(mActivity, BrowserPreferencesPage.class);
        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                AutoFillSettingsFragment.class.getName());
        mAutoFillSetupMessage = message;
        mActivity.startActivityForResult(intent, AUTOFILL_SETUP);
    }

    @Override
    public void registerOptionsMenuHandler(OptionsMenuHandler handler) {
        mOptionsMenuHandler = handler;
    }

    @Override
    public void unregisterOptionsMenuHandler(OptionsMenuHandler handler) {
        if (mOptionsMenuHandler == handler) {
            mOptionsMenuHandler = null;
        }
    }

    @Override
    public void registerDropdownChangeListener(DropdownChangeListener d) {
        mUi.registerDropdownChangeListener(d);
    }
}
