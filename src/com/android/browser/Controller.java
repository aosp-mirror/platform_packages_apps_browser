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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Browser;
import android.provider.BrowserContract;
import android.provider.BrowserContract.History;
import android.provider.BrowserContract.Images;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.speech.RecognizerResultsIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Controller for browser
 */
public class Controller
        implements WebViewController, UiController {

    private static final String LOGTAG = "Controller";

    // public message ids
    public final static int LOAD_URL = 1001;
    public final static int STOP_LOAD = 1002;

    // Message Ids
    private static final int FOCUS_NODE_HREF = 102;
    private static final int RELEASE_WAKELOCK = 107;

    static final int UPDATE_BOOKMARK_THUMBNAIL = 108;

    private static final int OPEN_BOOKMARKS = 201;

    private static final int EMPTY_MENU = -1;

    // Keep this initial progress in sync with initialProgressValue (* 100)
    // in ProgressTracker.cpp
    private final static int INITIAL_PROGRESS = 10;

    // activity requestCode
    final static int PREFERENCES_PAGE = 3;
    final static int FILE_SELECTED = 4;
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

    private Activity mActivity;
    private UI mUi;
    private TabControl mTabControl;
    private BrowserSettings mSettings;
    private WebViewFactory mFactory;

    private WakeLock mWakeLock;

    private UrlHandler mUrlHandler;
    private UploadHandler mUploadHandler;
    private IntentHandler mIntentHandler;
    private PageDialogsHandler mPageDialogsHandler;
    private NetworkStateHandler mNetworkHandler;

    private boolean mShouldShowErrorConsole;

    private SystemAllowGeolocationOrigins mSystemAllowGeolocationOrigins;

    // FIXME, temp address onPrepareMenu performance problem.
    // When we move everything out of view, we should rewrite this.
    private int mCurrentMenuState = 0;
    private int mMenuState = R.id.MAIN_MENU;
    private int mOldMenuState = EMPTY_MENU;
    private Menu mCachedMenu;

    // Used to prevent chording to result in firing two shortcuts immediately
    // one after another.  Fixes bug 1211714.
    boolean mCanChord;
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
        mTabControl = new TabControl(this);
        mSettings.setController(this);

        mUrlHandler = new UrlHandler(this);
        mIntentHandler = new IntentHandler(mActivity, this);
        mPageDialogsHandler = new PageDialogsHandler(mActivity, this);

        PowerManager pm = (PowerManager) mActivity
                .getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Browser");

        startHandler();

        mNetworkHandler = new NetworkStateHandler(mActivity, this);
        // Start watching the default geolocation permissions
        mSystemAllowGeolocationOrigins =
                new SystemAllowGeolocationOrigins(mActivity.getApplicationContext());
        mSystemAllowGeolocationOrigins.start();

        retainIconsOnStartup();
    }

    void start(Bundle icicle, Intent intent) {
        // Unless the last browser usage was within 24 hours, destroy any
        // remaining incognito tabs.

        Calendar lastActiveDate = icicle != null ?
                (Calendar) icicle.getSerializable("lastActiveDate") : null;
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);

        boolean dontRestoreIncognitoTabs = lastActiveDate == null
            || lastActiveDate.before(yesterday)
            || lastActiveDate.after(today);

        if (!mTabControl.restoreState(icicle, dontRestoreIncognitoTabs)) {
            // there is no quit on Android. But if we can't restore the state,
            // we can treat it as a new Browser, remove the old session cookies.
            CookieManager.getInstance().removeSessionCookie();
            // remove any incognito files
            WebView.cleanupPrivateBrowsingFiles(mActivity);
            final Bundle extra = intent.getExtras();
            // Create an initial tab.
            // If the intent is ACTION_VIEW and data is not null, the Browser is
            // invoked to view the content by another application. In this case,
            // the tab will be close when exit.
            UrlData urlData = mIntentHandler.getUrlDataFromIntent(intent);

            String action = intent.getAction();
            final Tab t = mTabControl.createNewTab(
                    (Intent.ACTION_VIEW.equals(action) &&
                    intent.getData() != null)
                    || RecognizerResultsIntent.ACTION_VOICE_SEARCH_RESULTS
                    .equals(action),
                    intent.getStringExtra(Browser.EXTRA_APPLICATION_ID),
                    urlData.mUrl, false);
            addTab(t);
            setActiveTab(t);
            WebView webView = t.getWebView();
            if (extra != null) {
                int scale = extra.getInt(Browser.INITIAL_ZOOM_LEVEL, 0);
                if (scale > 0 && scale <= 1000) {
                    webView.setInitialScale(scale);
                }
            }

            if (urlData.isEmpty()) {
                loadUrl(webView, mSettings.getHomePage());
            } else {
                loadUrlDataIn(t, urlData);
            }
        } else {
            if (dontRestoreIncognitoTabs) {
                WebView.cleanupPrivateBrowsingFiles(mActivity);
            }
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
        String jsFlags = getSettings().getJsFlags();
        if (jsFlags.trim().length() != 0) {
            getCurrentWebView().setJsFlags(jsFlags);
        }
    }

    void setWebViewFactory(WebViewFactory factory) {
        mFactory = factory;
    }

    WebViewFactory getWebViewFactory() {
        return mFactory;
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

    // Open the icon database and retain all the icons for visited sites.
    private void retainIconsOnStartup() {
        final WebIconDatabase db = WebIconDatabase.getInstance();
        db.open(mActivity.getDir("icons", 0).getPath());
        Cursor c = null;
        try {
            c = Browser.getAllBookmarks(mActivity.getContentResolver());
            if (c.moveToFirst()) {
                int urlIndex = c.getColumnIndex(Browser.BookmarkColumns.URL);
                do {
                    String url = c.getString(urlIndex);
                    db.retainIconForPageUrl(url);
                } while (c.moveToNext());
            }
        } catch (IllegalStateException e) {
            Log.e(LOGTAG, "retainIconsOnStartup", e);
        } finally {
            if (c!= null) c.close();
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
                            case R.id.view_image_context_menu_id:
                                loadUrlFromContext(getCurrentTopWebView(), url);
                                break;
                            case R.id.bookmark_context_menu_id:
                                Intent intent = new Intent(mActivity,
                                        AddBookmarkPage.class);
                                intent.putExtra(BrowserContract.Bookmarks.URL, url);
                                intent.putExtra(BrowserContract.Bookmarks.TITLE,
                                        title);
                                mActivity.startActivity(intent);
                                break;
                            case R.id.share_link_context_menu_id:
                                sharePage(mActivity, title, url, null,
                                        null);
                                break;
                            case R.id.copy_link_context_menu_id:
                                copy(url);
                                break;
                            case R.id.save_link_context_menu_id:
                            case R.id.download_context_menu_id:
                                DownloadHandler.onDownloadStartNoStream(
                                        mActivity, url, null, null, null);
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
                        WebView view = (WebView) msg.obj;
                        if (view != null) {
                            updateScreenshot(view);
                        }
                        break;
                }
            }
        };

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
        if (mActivityPaused) {
            Log.e(LOGTAG, "BrowserActivity is already paused.");
            return;
        }
        mTabControl.pauseCurrentTab();
        mActivityPaused = true;
        if (mTabControl.getCurrentIndex() >= 0 &&
                !pauseWebViewTimers(mActivityPaused)) {
            mWakeLock.acquire();
            mHandler.sendMessageDelayed(mHandler
                    .obtainMessage(RELEASE_WAKELOCK), WAKELOCK_TIMEOUT);
        }
        mUi.onPause();
        mNetworkHandler.onPause();

        WebView.disablePlatformNotifications();
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
        mTabControl.resumeCurrentTab();
        mActivityPaused = false;
        resumeWebViewTimers();

        if (mWakeLock.isHeld()) {
            mHandler.removeMessages(RELEASE_WAKELOCK);
            mWakeLock.release();
        }
        mUi.onResume();
        mNetworkHandler.onResume();
        WebView.enablePlatformNotifications();
    }

    private void resumeWebViewTimers() {
        Tab tab = mTabControl.getCurrentTab();
        if (tab == null) return; // monkey can trigger this
        boolean inLoad = tab.inPageLoad();
        if ((!mActivityPaused && !inLoad) || (mActivityPaused && inLoad)) {
            CookieSyncManager.getInstance().startSync();
            WebView w = tab.getWebView();
            if (w != null) {
                w.resumeTimers();
            }
        }
    }

    private boolean pauseWebViewTimers(boolean activityPaused) {
        Tab tab = mTabControl.getCurrentTab();
        boolean inLoad = tab.inPageLoad();
        if (activityPaused && !inLoad) {
            CookieSyncManager.getInstance().stopSync();
            WebView w = getCurrentWebView();
            if (w != null) {
                w.pauseTimers();
            }
            return true;
        } else {
            return false;
        }
    }

    void onDestroy() {
        if (mUploadHandler != null) {
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
        resetTitleAndRevertLockIcon(tab);
        WebView w = getCurrentTopWebView();
        w.stopLoading();
        // FIXME: before refactor, it is using mWebViewClient. So I keep the
        // same logic here. But for subwindow case, should we call into the main
        // WebView's onPageFinished as we never call its onPageStarted and if
        // the page finishes itself, we don't call onPageFinished.
        mTabControl.getCurrentWebView().getWebViewClient().onPageFinished(w,
                w.getUrl());
        mUi.onPageStopped(tab);
    }

    boolean didUserStopLoading() {
        return mLoadStopped;
    }

    // WebViewController

    @Override
    public void onPageStarted(Tab tab, WebView view, String url, Bitmap favicon) {

        // We've started to load a new page. If there was a pending message
        // to save a screenshot then we will now take the new page and save
        // an incorrect screenshot. Therefore, remove any pending thumbnail
        // messages from the queue.
        mHandler.removeMessages(Controller.UPDATE_BOOKMARK_THUMBNAIL,
                view);

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
            resumeWebViewTimers();
        }
        mLoadStopped = false;
        if (!mNetworkHandler.isNetworkUp()) {
            mNetworkHandler.createAndShowNetworkDialog();
        }
        endActionMode();

        mUi.onPageStarted(tab, url, favicon);

        // Show some progress so that the user knows the page is beginning to
        // load
        onProgressChanged(tab, INITIAL_PROGRESS);

        // update the bookmark database for favicon
        maybeUpdateFavicon(tab, null, url, favicon);

        Performance.tracePageStart(url);

        // Performance probe
        if (false) {
            Performance.onPageStarted();
        }

    }

    @Override
    public void onPageFinished(Tab tab, String url) {
        mUi.onPageFinished(tab, url);
        if (!tab.isPrivateBrowsingEnabled()) {
            if (tab.inForeground() && !didUserStopLoading()
                    || !tab.inForeground()) {
                // Only update the bookmark screenshot if the user did not
                // cancel the load early.
                mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        UPDATE_BOOKMARK_THUMBNAIL, 0, 0, tab.getWebView()),
                        500);
            }
        }
        // pause the WebView timer and release the wake lock if it is finished
        // while BrowserActivity is in pause state.
        if (mActivityPaused && pauseWebViewTimers(mActivityPaused)) {
            if (mWakeLock.isHeld()) {
                mHandler.removeMessages(RELEASE_WAKELOCK);
                mWakeLock.release();
            }
        }
        // Performance probe
        if (false) {
            Performance.onPageFinished(url);
         }

        Performance.tracePageFinished();
    }

    @Override
    public void onProgressChanged(Tab tab, int newProgress) {

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
        mUi.onProgressChanged(tab, newProgress);
    }

    @Override
    public void onReceivedTitle(Tab tab, final String title) {
        final String pageUrl = tab.getWebView().getUrl();
        setUrlTitle(tab, pageUrl, title);
        if (pageUrl == null || pageUrl.length()
                >= SQLiteDatabase.SQLITE_MAX_LIKE_PATTERN_LENGTH) {
            return;
        }
        // Update the title in the history database if not in private browsing mode
        if (!tab.isPrivateBrowsingEnabled()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... unused) {
                    // See if we can find the current url in our history
                    // database and add the new title to it.
                    String url = pageUrl;
                    if (url.startsWith("http://www.")) {
                        url = url.substring(11);
                    } else if (url.startsWith("http://")) {
                        url = url.substring(4);
                    }
                    // Escape wildcards for LIKE operator.
                    url = url.replace("\\", "\\\\").replace("%", "\\%")
                            .replace("_", "\\_");
                    Cursor c = null;
                    try {
                        final ContentResolver cr =
                                getActivity().getContentResolver();
                        String selection = History.URL + " LIKE ? ESCAPE '\\'";
                        String [] selectionArgs = new String[] { "%" + url };
                        ContentValues values = new ContentValues();
                        values.put(History.TITLE, title);
                        cr.update(History.CONTENT_URI, values, selection,
                                selectionArgs);
                    } catch (IllegalStateException e) {
                        Log.e(LOGTAG, "Tab onReceived title", e);
                    } catch (SQLiteException ex) {
                        Log.e(LOGTAG,
                                "onReceivedTitle() caught SQLiteException: ",
                                ex);
                    } finally {
                        if (c != null) c.close();
                    }
                    return null;
                }
            }.execute();
        }
    }

    @Override
    public void onFavicon(Tab tab, WebView view, Bitmap icon) {
        mUi.setFavicon(tab, icon);
        maybeUpdateFavicon(tab, view.getOriginalUrl(), view.getUrl(), icon);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return mUrlHandler.shouldOverrideUrlLoading(view, url);
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
    public void doUpdateVisitedHistory(Tab tab, String url,
            boolean isReload) {
        // Don't save anything in private browsing mode
        if (tab.isPrivateBrowsingEnabled()) return;

        if (url.regionMatches(true, 0, "about:", 0, 6)) {
            return;
        }
        // remove "client" before updating it to the history so that it wont
        // show up in the auto-complete list.
        int index = url.indexOf("client=ms-");
        if (index > 0 && url.contains(".google.")) {
            int end = url.indexOf('&', index);
            if (end > 0) {
                url = url.substring(0, index)
                        .concat(url.substring(end + 1));
            } else {
                // the url.charAt(index-1) should be either '?' or '&'
                url = url.substring(0, index-1);
            }
        }
        final ContentResolver cr = getActivity().getContentResolver();
        final String newUrl = url;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                Browser.updateVisitedHistory(cr, newUrl, true);
                return null;
            }
        }.execute();
        WebIconDatabase.getInstance().retainIconForPageUrl(url);
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
        DownloadHandler.onDownloadStart(mActivity, url, userAgent,
                contentDisposition, mimetype);
        if (tab.getWebView().copyBackForwardList().getSize() == 0) {
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
        String url = (getCurrentTopWebView() == null) ? null : getCurrentTopWebView().getUrl();
        startSearch(mSettings.getHomePage().equals(url) ? null : url, true,
                null, false);
    }

    public void activateVoiceSearchMode(String title) {
        mUi.showVoiceTitleBar(title);
    }

    public void revertVoiceSearchMode(Tab tab) {
        mUi.revertVoiceTitleBar(tab);
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
        }
    }

    @Override
    public void hideCustomView() {
        if (mUi.isCustomViewShowing()) {
            mUi.onHideCustomView();
            // Reset the old menu state.
            mMenuState = mOldMenuState;
            mOldMenuState = EMPTY_MENU;
        }
    }

    protected void onActivityResult(int requestCode, int resultCode,
            Intent intent) {
        if (getCurrentTopWebView() == null) return;
        switch (requestCode) {
            case PREFERENCES_PAGE:
                if (resultCode == Activity.RESULT_OK && intent != null) {
                    String action = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (BrowserSettings.PREF_CLEAR_HISTORY.equals(action)) {
                        mTabControl.removeParentChildRelationShips();
                    }
                }
                break;
            case FILE_SELECTED:
                // Choose a file from the file picker.
                if (null == mUploadHandler) break;
                mUploadHandler.onResult(resultCode, intent);
                mUploadHandler = null;
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
                openTab(url, false);
            } else {
                final Tab currentTab = mTabControl.getCurrentTab();
                dismissSubWindow(currentTab);
                loadUrl(getCurrentTopWebView(), url);
            }
        }
    }

    /**
     * callback from ComboPage when dismissed
     */
    @Override
    public void onComboCanceled() {
        removeComboView();
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
        WebView webview = (WebView) v;
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
                TextView titleView = (TextView) LayoutInflater.from(mActivity)
                        .inflate(android.R.layout.browser_link_context_header,
                        null);
                titleView.setText(extra);
                menu.setHeaderView(titleView);
                // decide whether to show the open link in new tab option
                boolean showNewTab = mTabControl.canCreateNewTab();
                MenuItem newTabItem
                        = menu.findItem(R.id.open_newtab_context_menu_id);
                newTabItem.setVisible(showNewTab);
                if (showNewTab) {
                    newTabItem.setOnMenuItemClickListener(
                            new MenuItem.OnMenuItemClickListener() {
                                public boolean onMenuItemClick(MenuItem item) {
                                    final Tab parent = mTabControl.getCurrentTab();
                                    final Tab newTab = openTab(extra, false);
                                    if (newTab != parent) {
                                        parent.addChildTab(newTab);
                                    }
                                    return true;
                                }
                            });
                }
                menu.findItem(R.id.bookmark_context_menu_id).setVisible(
                        Bookmarks.urlHasAcceptableScheme(extra));
                PackageManager pm = mActivity.getPackageManager();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                ResolveInfo ri = pm.resolveActivity(send,
                        PackageManager.MATCH_DEFAULT_ONLY);
                menu.findItem(R.id.share_link_context_menu_id)
                        .setVisible(ri != null);
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
                        setOnMenuItemClickListener(new Download(mActivity, extra));
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

    boolean prepareOptionsMenu(Menu menu) {
        // This happens when the user begins to hold down the menu key, so
        // allow them to chord to get a shortcut.
        mCanChord = true;
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

                boolean isNavDump = mSettings.isNavDump();
                final MenuItem nav = menu.findItem(R.id.dump_nav_menu_id);
                nav.setVisible(isNavDump);
                nav.setEnabled(isNavDump);

                boolean showDebugSettings = mSettings.showDebugSettings();
                final MenuItem counter = menu.findItem(R.id.dump_counters_menu_id);
                counter.setVisible(showDebugSettings);
                counter.setEnabled(showDebugSettings);

                // allow the ui to adjust state based settings
                mUi.onPrepareOptionsMenu(menu);

                break;
        }
        mCurrentMenuState = mMenuState;
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getGroupId() != R.id.CONTEXT_MENU) {
            // menu remains active, so ensure comboview is dismissed
            // if main menu option is selected
            removeComboView();
        }
        // check the action bar button before mCanChord check, as the prepare call
        // doesn't come for action bar buttons
        if (item.getItemId() == R.id.newtab) {
            openTabToHomePage();
            return true;
        }
        if (!mCanChord) {
            // The user has already fired a shortcut with this hold down of the
            // menu key.
            return false;
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
                bookmarkCurrentPage(AddBookmarkPage.DEFAULT_FOLDER_ID);
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
                getCurrentTopWebView().showFindDialog(null);
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
                    mCanChord = false;
                    return false;
                }
                currentTab.populatePickerData();
                sharePage(mActivity, currentTab.getTitle(),
                        currentTab.getUrl(), currentTab.getFavicon(),
                        createScreenshot(currentTab.getWebView(),
                                getDesiredThumbnailWidth(mActivity),
                                getDesiredThumbnailHeight(mActivity)));
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
        mCanChord = false;
        return true;
    }

    public boolean onContextItemSelected(MenuItem item) {
        // Let the History and Bookmark fragments handle menus they created.
        if (item.getGroupId() == R.id.CONTEXT_MENU) {
            return false;
        }

        // chording is not an issue with context menus, but we use the same
        // options selector, so set mCanChord to true so we can access them.
        mCanChord = true;
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
            case R.id.bookmark_context_menu_id:
            case R.id.save_link_context_menu_id:
            case R.id.share_link_context_menu_id:
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
        mCanChord = false;
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
                    mUi.onOptionsMenuOpened();
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
     */
    @Override
    public void bookmarkCurrentPage(long folderId) {
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

    private static Bitmap createScreenshot(WebView view, int width, int height) {
        Picture thumbnail = view.capturePicture();
        if (thumbnail == null) {
            return null;
        }
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bm);
        // May need to tweak these values to determine what is the
        // best scale factor
        int thumbnailWidth = thumbnail.getWidth();
        int thumbnailHeight = thumbnail.getHeight();
        float scaleFactor = 1.0f;
        if (thumbnailWidth > 0) {
            scaleFactor = (float) width / (float)thumbnailWidth;
        } else {
            return null;
        }

        if (view.getWidth() > view.getHeight() &&
                thumbnailHeight < view.getHeight() && thumbnailHeight > 0) {
            // If the device is in landscape and the page is shorter
            // than the height of the view, center the thumnail and crop the sides
            scaleFactor = (float) height / (float)thumbnailHeight;
            float wx = (thumbnailWidth * scaleFactor) - width;
            canvas.translate((int) -(wx / 2), 0);
        }

        canvas.scale(scaleFactor, scaleFactor);

        thumbnail.draw(canvas);
        return bm;
    }

    private void updateScreenshot(WebView view) {
        // If this is a bookmarked site, add a screenshot to the database.
        // FIXME: When should we update?  Every time?
        // FIXME: Would like to make sure there is actually something to
        // draw, but the API for that (WebViewCore.pictureReady()) is not
        // currently accessible here.

        final Bitmap bm = createScreenshot(view, getDesiredThumbnailWidth(mActivity),
                getDesiredThumbnailHeight(mActivity));
        if (bm == null) {
            return;
        }

        final ContentResolver cr = mActivity.getContentResolver();
        final String url = view.getUrl();
        final String originalUrl = view.getOriginalUrl();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... unused) {
                Cursor cursor = null;
                try {
                    cursor = Bookmarks.queryCombinedForUrl(cr, originalUrl, url);
                    if (cursor != null && cursor.moveToFirst()) {
                        final ByteArrayOutputStream os =
                                new ByteArrayOutputStream();
                        bm.compress(Bitmap.CompressFormat.PNG, 100, os);

                        ContentValues values = new ContentValues();
                        values.put(Images.THUMBNAIL, os.toByteArray());
                        values.put(Images.URL, cursor.getString(0));

                        do {
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

        public boolean onMenuItemClick(MenuItem item) {
            DownloadHandler.onDownloadStartNoStream(mActivity, mText, null,
                    null, null);
            return true;
        }

        public Download(Activity activity, String toDownload) {
            mActivity = activity;
            mText = toDownload;
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

    protected void setActiveTab(Tab tab) {
        // Update the UI before setting the current tab in TabControl
        // so the UI can access the old tab to switch over from
        mUi.setActiveTab(tab);
        mTabControl.setCurrentTab(tab);
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
        Log.i(LOGTAG, "Reusing tab for " + appId);
        // Dismiss the subwindow if applicable.
        dismissSubWindow(appTab);
        // Since we might kill the WebView, remove it from the
        // content view first.
        mUi.detachTab(appTab);
        // Recreate the main WebView after destroying the old one.
        // If the WebView has the same original url and is on that
        // page, it can be reused.
        boolean needsLoad =
                mTabControl.recreateWebView(appTab, urlData);
        // TODO: analyze why the remove and add are necessary
        mUi.attachTab(appTab);
        if (mTabControl.getCurrentTab() != appTab) {
            switchToTab(mTabControl.getTabIndex(appTab));
            if (needsLoad) {
                loadUrlDataIn(appTab, urlData);
            }
        } else {
            // If the tab was the current tab, we have to attach
            // it to the view system again.
            setActiveTab(appTab);
            if (needsLoad) {
                loadUrlDataIn(appTab, urlData);
            }
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

    // A wrapper function of {@link #openTabAndShow(UrlData, boolean, String)}
    // that accepts url as string.

    protected Tab openTabAndShow(String url, boolean closeOnExit, String appId) {
        return openTabAndShow(new UrlData(url), closeOnExit, appId);
    }

    // This method does a ton of stuff. It will attempt to create a new tab
    // if we haven't reached MAX_TABS. Otherwise it uses the current tab. If
    // url isn't null, it will load the given url.

    public Tab openTabAndShow(UrlData urlData, boolean closeOnExit,
            String appId) {
        final Tab currentTab = mTabControl.getCurrentTab();
        if (mTabControl.canCreateNewTab()) {
            final Tab tab = mTabControl.createNewTab(closeOnExit, appId,
                    urlData.mUrl, false);
            WebView webview = tab.getWebView();
            // We must set the new tab as the current tab to reflect the old
            // animation behavior.
            addTab(tab);
            setActiveTab(tab);
            if (!urlData.isEmpty()) {
                loadUrlDataIn(tab, urlData);
            }
            return tab;
        } else {
            // Get rid of the subwindow if it exists
            dismissSubWindow(currentTab);
            if (!urlData.isEmpty()) {
                // Load the given url.
                loadUrlDataIn(currentTab, urlData);
            }
            return currentTab;
        }
    }

    protected Tab openTab(String url, boolean forceForeground) {
        if (mSettings.openInBackground() && !forceForeground) {
            Tab tab = mTabControl.createNewTab();
            if (tab != null) {
                addTab(tab);
                WebView view = tab.getWebView();
                loadUrl(view, url);
            }
            return tab;
        } else {
            return openTabAndShow(url, false, null);
        }
    }

    @Override
    public Tab openIncognitoTab() {
        if (mTabControl.canCreateNewTab()) {
            Tab currentTab = mTabControl.getCurrentTab();
            Tab tab = mTabControl.createNewTab(false, null, null, true);
            addTab(tab);
            setActiveTab(tab);
            return tab;
        }
        return null;
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
        Tab tab = mTabControl.getTab(index);
        Tab currentTab = mTabControl.getCurrentTab();
        if (tab == null || tab == currentTab) {
            return false;
        }
        setActiveTab(tab);
        return true;
    }

    @Override
    public Tab openTabToHomePage() {
        return openTabAndShow(mSettings.getHomePage(), false, null);
    }

    @Override
    public void closeCurrentTab() {
        final Tab current = mTabControl.getCurrentTab();
        if (mTabControl.getTabCount() == 1) {
            // This is the last tab.  Open a new one, with the home
            // page and close the current one.
            openTabToHomePage();
            closeTab(current);
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
        int currentIndex = mTabControl.getCurrentIndex();
        int removeIndex = mTabControl.getTabIndex(tab);
        removeTab(tab);
        if (currentIndex >= removeIndex && currentIndex != 0) {
            currentIndex--;
        }
        Tab newtab = mTabControl.getTab(currentIndex);
        setActiveTab(newtab);
        if (!mTabControl.hasAnyOpenIncognitoTabs()) {
            WebView.cleanupPrivateBrowsingFiles(mActivity);
        }
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
        updateTitleBarForNewLoad(view, url);
        view.loadUrl(url);
    }

    /**
     * Load UrlData into a Tab and update the title bar to reflect the new
     * load.  Call this instead of UrlData.loadIn directly.
     * @param t The Tab used to load.
     * @param data The UrlData being loaded.
     */
    protected void loadUrlDataIn(Tab t, UrlData data) {
        updateTitleBarForNewLoad(t.getWebView(), data.mUrl);
        data.loadIn(t);
    }

    /**
     * Resets the browser title-view to whatever it must be
     * (for example, if we had a loading error)
     * When we have a new page, we call resetTitle, when we
     * have to reset the titlebar to whatever it used to be
     * (for example, if the user chose to stop loading), we
     * call resetTitleAndRevertLockIcon.
     */
    public void resetTitleAndRevertLockIcon(Tab tab) {
        mUi.resetTitleAndRevertLockIcon(tab);
    }

    void resetTitleAndIcon(Tab tab) {
        mUi.resetTitleAndIcon(tab);
    }

    /**
     * If the WebView is the top window, update the title bar to reflect
     * loading the new URL.  i.e. set its text, clear the favicon (which
     * will be set once the page begins loading), and set the progress to
     * INITIAL_PROGRESS to show that the page has begun to load. Called
     * by loadUrl and loadUrlDataIn.
     * @param view The WebView that is starting a load.
     * @param url The URL that is being loaded.
     */
    private void updateTitleBarForNewLoad(WebView view, String url) {
        if (view == getCurrentTopWebView()) {
            // TODO we should come with a tab and not with a view
            Tab tab = mTabControl.getTabFromView(view);
            setUrlTitle(tab, url, null);
            mUi.setFavicon(tab, null);
            onProgressChanged(tab, INITIAL_PROGRESS);
        }
    }

    /**
     * Sets a title composed of the URL and the title string.
     * @param url The URL of the site being loaded.
     * @param title The title of the site being loaded.
     */
    void setUrlTitle(Tab tab, String url, String title) {
        tab.setCurrentUrl(url);
        tab.setCurrentTitle(title);
        // If we are in voice search mode, the title has already been set.
        if (tab.isInVoiceSearchMode()) return;
        mUi.setUrlTitle(tab, url, title);
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
                if (current.closeOnExit()) {
                    // force the tab's inLoad() to be false as we are going to
                    // either finish the activity or remove the tab. This will
                    // ensure pauseWebViewTimers() taking action.
                    mTabControl.getCurrentTab().clearInPageLoad();
                    if (mTabControl.getTabCount() == 1) {
                        mActivity.finish();
                        return;
                    }
                    if (mActivityPaused) {
                        Log.e(LOGTAG, "BrowserActivity is already paused "
                                + "while handing goBackOnePageOrQuit.");
                    }
                    pauseWebViewTimers(true);
                    removeTab(current);
                }
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
     * handle key events in browser
     *
     * @param keyCode
     * @param event
     * @return true if handled, false to pass to super
     */
    boolean onKeyDown(int keyCode, KeyEvent event) {
        // Even if MENU is already held down, we need to call to super to open
        // the IME on long press.
        if (KeyEvent.KEYCODE_MENU == keyCode) {
            mMenuIsDown = true;
            return false;
        }
        // The default key mode is DEFAULT_KEYS_SEARCH_LOCAL. As the MENU is
        // still down, we don't want to trigger the search. Pretend to consume
        // the key and do nothing.
        if (mMenuIsDown) return true;

        switch(keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                // WebView/WebTextView handle the keys in the KeyDown. As
                // the Activity's shortcut keys are only handled when WebView
                // doesn't, have to do it in onKeyDown instead of onKeyUp.
                if (event.isShiftPressed()) {
                    pageUp();
                } else {
                    pageDown();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (event.getRepeatCount() == 0) {
                    event.startTracking();
                    return true;
                } else if (mUi.showsWeb()
                        && event.isLongPress()) {
                    bookmarksOrHistoryPicker(true);
                    return true;
                }
                break;
        }
        return false;
    }

    boolean onKeyUp(int keyCode, KeyEvent event) {
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

}
