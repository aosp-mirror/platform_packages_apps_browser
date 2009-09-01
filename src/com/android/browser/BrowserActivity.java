/*
 * Copyright (C) 2006 The Android Open Source Project
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

import com.google.android.googleapps.IGoogleLoginService;
import com.google.android.googlelogin.GoogleLoginServiceConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DrawFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.WebAddress;
import android.net.http.EventHandler;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Browser;
import android.provider.Contacts;
import android.provider.Downloads;
import android.provider.MediaStore;
import android.provider.Contacts.Intents.Insert;
import android.text.IClipboard;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.util.Regex;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PluginManager;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebHistoryItem;
import android.webkit.WebIconDatabase;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BrowserActivity extends Activity
    implements KeyTracker.OnKeyTracker,
        View.OnCreateContextMenuListener,
        DownloadListener {

    /* Define some aliases to make these debugging flags easier to refer to.
     * This file imports android.provider.Browser, so we can't just refer to "Browser.DEBUG".
     */
    private final static boolean DEBUG = com.android.browser.Browser.DEBUG;
    private final static boolean LOGV_ENABLED = com.android.browser.Browser.LOGV_ENABLED;
    private final static boolean LOGD_ENABLED = com.android.browser.Browser.LOGD_ENABLED;

    private IGoogleLoginService mGls = null;
    private ServiceConnection mGlsConnection = null;

    private SensorManager mSensorManager = null;

    // These are single-character shortcuts for searching popular sources.
    private static final int SHORTCUT_INVALID = 0;
    private static final int SHORTCUT_GOOGLE_SEARCH = 1;
    private static final int SHORTCUT_WIKIPEDIA_SEARCH = 2;
    private static final int SHORTCUT_DICTIONARY_SEARCH = 3;
    private static final int SHORTCUT_GOOGLE_MOBILE_LOCAL_SEARCH = 4;

    /* Whitelisted webpages
    private static HashSet<String> sWhiteList;

    static {
        sWhiteList = new HashSet<String>();
        sWhiteList.add("cnn.com/");
        sWhiteList.add("espn.go.com/");
        sWhiteList.add("nytimes.com/");
        sWhiteList.add("engadget.com/");
        sWhiteList.add("yahoo.com/");
        sWhiteList.add("msn.com/");
        sWhiteList.add("amazon.com/");
        sWhiteList.add("consumerist.com/");
        sWhiteList.add("google.com/m/news");
    }
    */

    private void setupHomePage() {
        final Runnable getAccount = new Runnable() {
            public void run() {
                // Lower priority
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                // get the default home page
                String homepage = mSettings.getHomePage();

                try {
                    if (mGls == null) return;

                    if (!homepage.startsWith("http://www.google.")) return;
                    if (homepage.indexOf('?') == -1) return;

                    String hostedUser = mGls.getAccount(GoogleLoginServiceConstants.PREFER_HOSTED);
                    String googleUser = mGls.getAccount(GoogleLoginServiceConstants.REQUIRE_GOOGLE);

                    // three cases:
                    //
                    //   hostedUser == googleUser
                    //      The device has only a google account
                    //
                    //   hostedUser != googleUser
                    //      The device has a hosted account and a google account
                    //
                    //   hostedUser != null, googleUser == null
                    //      The device has only a hosted account (so far)

                    // developers might have no accounts at all
                    if (hostedUser == null) return;

                    if (googleUser == null || !hostedUser.equals(googleUser)) {
                        String domain = hostedUser.substring(hostedUser.lastIndexOf('@')+1);
                        homepage = homepage.replace("?", "/a/" + domain + "?");
                    }
                } catch (RemoteException ignore) {
                    // Login service died; carry on
                } catch (RuntimeException ignore) {
                    // Login service died; carry on
                } finally {
                    finish(homepage);
                }
            }

            private void finish(final String homepage) {
                mHandler.post(new Runnable() {
                    public void run() {
                        mSettings.setHomePage(BrowserActivity.this, homepage);
                        resumeAfterCredentials();

                        // as this is running in a separate thread,
                        // BrowserActivity's onDestroy() may have been called,
                        // which also calls unbindService().
                        if (mGlsConnection != null) {
                            // we no longer need to keep GLS open
                            unbindService(mGlsConnection);
                            mGlsConnection = null;
                        }
                    } });
            } };

        final boolean[] done = { false };

        // Open a connection to the Google Login Service.  The first
        // time the connection is established, set up the homepage depending on
        // the account in a background thread.
        mGlsConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                mGls = IGoogleLoginService.Stub.asInterface(service);
                if (done[0] == false) {
                    done[0] = true;
                    Thread account = new Thread(getAccount);
                    account.setName("GLSAccount");
                    account.start();
                }
            }
            public void onServiceDisconnected(ComponentName className) {
                mGls = null;
            }
        };

        bindService(GoogleLoginServiceConstants.SERVICE_INTENT,
                    mGlsConnection, Context.BIND_AUTO_CREATE);
    }

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

    // Flag to enable the touchable browser bar with buttons
    private final boolean CUSTOM_BROWSER_BAR = true;

    @Override public void onCreate(Bundle icicle) {
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, this + " onStart");
        }
        super.onCreate(icicle);
        if (CUSTOM_BROWSER_BAR) {
            this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            this.requestWindowFeature(Window.FEATURE_LEFT_ICON);
            this.requestWindowFeature(Window.FEATURE_RIGHT_ICON);
            this.requestWindowFeature(Window.FEATURE_PROGRESS);
            this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        }
        // test the browser in OpenGL
        // requestWindowFeature(Window.FEATURE_OPENGL);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mResolver = getContentResolver();

        //
        // start MASF proxy service
        //
        //Intent proxyServiceIntent = new Intent();
        //proxyServiceIntent.setComponent
        //    (new ComponentName(
        //        "com.android.masfproxyservice",
        //        "com.android.masfproxyservice.MasfProxyService"));
        //startService(proxyServiceIntent, null);

        mSecLockIcon = Resources.getSystem().getDrawable(
                android.R.drawable.ic_secure);
        mMixLockIcon = Resources.getSystem().getDrawable(
                android.R.drawable.ic_partial_secure);
        mGenericFavicon = getResources().getDrawable(
                R.drawable.app_web_browser_sm);

        FrameLayout frameLayout = (FrameLayout) getWindow().getDecorView()
                .findViewById(com.android.internal.R.id.content);
        if (CUSTOM_BROWSER_BAR) {
            // This FrameLayout will hold the custom FrameLayout and a LinearLayout
            // that contains the title bar and a FrameLayout, which
            // holds everything else.
            FrameLayout browserFrameLayout = (FrameLayout) LayoutInflater.from(this)
                    .inflate(R.layout.custom_screen, null);
            mTitleBar = (TitleBarSet) browserFrameLayout.findViewById(R.id.title_bar);
            mContentView = (FrameLayout) browserFrameLayout.findViewById(
                    R.id.main_content);
            mErrorConsoleContainer = (LinearLayout) browserFrameLayout.findViewById(
                    R.id.error_console);
            mCustomViewContainer = (FrameLayout) browserFrameLayout
                    .findViewById(R.id.fullscreen_custom_content);
            frameLayout.addView(browserFrameLayout, COVER_SCREEN_PARAMS);
        } else {
            mCustomViewContainer = new FrameLayout(this);
            mCustomViewContainer.setBackgroundColor(Color.BLACK);
            mContentView = new FrameLayout(this);

            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            mErrorConsoleContainer = new LinearLayout(this);
            linearLayout.addView(mErrorConsoleContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            linearLayout.addView(mContentView, COVER_SCREEN_PARAMS);
            frameLayout.addView(mCustomViewContainer, COVER_SCREEN_PARAMS);
            frameLayout.addView(linearLayout, COVER_SCREEN_PARAMS);
        }

        // Create the tab control and our initial tab
        mTabControl = new TabControl(this);

        // Open the icon database and retain all the bookmark urls for favicons
        retainIconsOnStartup();

        // Keep a settings instance handy.
        mSettings = BrowserSettings.getInstance();
        mSettings.setTabControl(mTabControl);
        mSettings.loadFromDb(this);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Browser");

        /* enables registration for changes in network status from
           http stack */
        mNetworkStateChangedFilter = new IntentFilter();
        mNetworkStateChangedFilter.addAction(
                ConnectivityManager.CONNECTIVITY_ACTION);
        mNetworkStateIntentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(
                            ConnectivityManager.CONNECTIVITY_ACTION)) {
                        boolean down = intent.getBooleanExtra(
                                ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                        onNetworkToggle(!down);
                    }
                }
            };

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mPackageInstallationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final String packageName = intent.getData()
                        .getSchemeSpecificPart();
                final boolean replacing = intent.getBooleanExtra(
                        Intent.EXTRA_REPLACING, false);
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action) && replacing) {
                    // if it is replacing, refreshPlugins() when adding
                    return;
                }
                PackageManager pm = BrowserActivity.this.getPackageManager();
                PackageInfo pkgInfo = null;
                try {
                    pkgInfo = pm.getPackageInfo(packageName,
                            PackageManager.GET_PERMISSIONS);
                } catch (PackageManager.NameNotFoundException e) {
                    return;
                }
                if (pkgInfo != null) {
                    String permissions[] = pkgInfo.requestedPermissions;
                    if (permissions == null) {
                        return;
                    }
                    boolean permissionOk = false;
                    for (String permit : permissions) {
                        if (PluginManager.PLUGIN_PERMISSION.equals(permit)) {
                            permissionOk = true;
                            break;
                        }
                    }
                    if (permissionOk) {
                        PluginManager.getInstance(BrowserActivity.this)
                                .refreshPlugins(
                                        Intent.ACTION_PACKAGE_ADDED
                                                .equals(action));
                    }
                }
            }
        };
        registerReceiver(mPackageInstallationReceiver, filter);

        // If this was a web search request, pass it on to the default web search provider.
        if (handleWebSearchIntent(getIntent())) {
            moveTaskToBack(true);
            return;
        }

        if (!mTabControl.restoreState(icicle)) {
            // clear up the thumbnail directory if we can't restore the state as
            // none of the files in the directory are referenced any more.
            new ClearThumbnails().execute(
                    mTabControl.getThumbnailDir().listFiles());
            // there is no quit on Android. But if we can't restore the state,
            // we can treat it as a new Browser, remove the old session cookies.
            CookieManager.getInstance().removeSessionCookie();
            final Intent intent = getIntent();
            final Bundle extra = intent.getExtras();
            // Create an initial tab.
            // If the intent is ACTION_VIEW and data is not null, the Browser is
            // invoked to view the content by another application. In this case,
            // the tab will be close when exit.
            UrlData urlData = getUrlDataFromIntent(intent);

            final TabControl.Tab t = mTabControl.createNewTab(
                    Intent.ACTION_VIEW.equals(intent.getAction()) &&
                    intent.getData() != null,
                    intent.getStringExtra(Browser.EXTRA_APPLICATION_ID), urlData.mUrl);
            mTabControl.setCurrentTab(t);
            attachTabToContentView(t);
            WebView webView = t.getWebView();
            if (extra != null) {
                int scale = extra.getInt(Browser.INITIAL_ZOOM_LEVEL, 0);
                if (scale > 0 && scale <= 1000) {
                    webView.setInitialScale(scale);
                }
            }
            // If we are not restoring from an icicle, then there is a high
            // likely hood this is the first run. So, check to see if the
            // homepage needs to be configured and copy any plugins from our
            // asset directory to the data partition.
            if ((extra == null || !extra.getBoolean("testing"))
                    && !mSettings.isLoginInitialized()) {
                setupHomePage();
            }

            if (urlData.isEmpty()) {
                bookmarksOrHistoryPicker(false, true);
            } else {
                if (extra != null) {
                    urlData.setPostData(extra
                            .getByteArray(Browser.EXTRA_POST_DATA));
                }
                urlData.loadIn(webView);
            }
        } else {
            // TabControl.restoreState() will create a new tab even if
            // restoring the state fails.
            attachTabToContentView(mTabControl.getCurrentTab());
        }

        if (CUSTOM_BROWSER_BAR) {
            mTitleBar.init(this);
            // Create title bars for all of the tabs that have been created
            for (int i = 0; i < mTabControl.getTabCount(); i ++) {
                WebView view = mTabControl.getTab(i).getWebView();
                mTitleBar.addTab(view, false);
            }

            mTitleBar.setCurrentTab(mTabControl.getCurrentIndex());
        }

        // Read JavaScript flags if it exists.
        String jsFlags = mSettings.getJsFlags();
        if (jsFlags.trim().length() != 0) {
            mTabControl.getCurrentWebView().setJsFlags(jsFlags);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        TabControl.Tab current = mTabControl.getCurrentTab();
        // When a tab is closed on exit, the current tab index is set to -1.
        // Reset before proceed as Browser requires the current tab to be set.
        if (current == null) {
            // Try to reset the tab in case the index was incorrect.
            current = mTabControl.getTab(0);
            if (current == null) {
                // No tabs at all so just ignore this intent.
                return;
            }
            mTabControl.setCurrentTab(current);
            if (CUSTOM_BROWSER_BAR) {
                mTitleBar.setCurrentTab(mTabControl.getTabIndex(current));
            }
            attachTabToContentView(current);
            resetTitleAndIcon(current.getWebView());
        }
        final String action = intent.getAction();
        final int flags = intent.getFlags();
        if (Intent.ACTION_MAIN.equals(action) ||
                (flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            // just resume the browser
            return;
        }
        if (Intent.ACTION_VIEW.equals(action)
                || Intent.ACTION_SEARCH.equals(action)
                || MediaStore.INTENT_ACTION_MEDIA_SEARCH.equals(action)
                || Intent.ACTION_WEB_SEARCH.equals(action)) {
            // If this was a search request (e.g. search query directly typed into the address bar),
            // pass it on to the default web search provider.
            if (handleWebSearchIntent(intent)) {
                return;
            }

            UrlData urlData = getUrlDataFromIntent(intent);
            if (urlData.isEmpty()) {
                urlData = new UrlData(mSettings.getHomePage());
            }
            urlData.setPostData(intent
                    .getByteArrayExtra(Browser.EXTRA_POST_DATA));

            final String appId = intent
                    .getStringExtra(Browser.EXTRA_APPLICATION_ID);
            if (Intent.ACTION_VIEW.equals(action)
                    && !getPackageName().equals(appId)
                    && (flags & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
                TabControl.Tab appTab = mTabControl.getTabFromId(appId);
                if (appTab != null) {
                    Log.i(LOGTAG, "Reusing tab for " + appId);
                    // Dismiss the subwindow if applicable.
                    dismissSubWindow(appTab);
                    // Since we might kill the WebView, remove it from the
                    // content view first.
                    removeTabFromContentView(appTab);
                    // Recreate the main WebView after destroying the old one.
                    // If the WebView has the same original url and is on that
                    // page, it can be reused.
                    boolean needsLoad =
                            mTabControl.recreateWebView(appTab, urlData.mUrl);

                    if (current != appTab) {
                        switchToTab(mTabControl.getTabIndex(appTab));
                        if (needsLoad) {
                            urlData.loadIn(appTab.getWebView());
                        }
                    } else {
                        // If the tab was the current tab, we have to attach
                        // it to the view system again.
                        attachTabToContentView(appTab);
                        if (needsLoad) {
                            urlData.loadIn(appTab.getWebView());
                        }
                    }
                    return;
                } else {
                    // No matching application tab, try to find a regular tab
                    // with a matching url.
                    appTab = mTabControl.findUnusedTabWithUrl(urlData.mUrl);
                    if (appTab != null) {
                        if (current != appTab) {
                            switchToTab(mTabControl.getTabIndex(appTab));
                        }
                        // Otherwise, we are already viewing the correct tab.
                    } else {
                        // if FLAG_ACTIVITY_BROUGHT_TO_FRONT flag is on, the url
                        // will be opened in a new tab unless we have reached
                        // MAX_TABS. Then the url will be opened in the current
                        // tab. If a new tab is created, it will have "true" for
                        // exit on close.
                        openTabAndShow(urlData, true, appId);
                    }
                }
            } else {
                if ("about:debug".equals(urlData.mUrl)) {
                    mSettings.toggleDebugSettings();
                    return;
                }
                // Get rid of the subwindow if it exists
                dismissSubWindow(current);
                urlData.loadIn(current.getWebView());
            }
        }
    }

    private int parseUrlShortcut(String url) {
        if (url == null) return SHORTCUT_INVALID;

        // FIXME: quick search, need to be customized by setting
        if (url.length() > 2 && url.charAt(1) == ' ') {
            switch (url.charAt(0)) {
            case 'g': return SHORTCUT_GOOGLE_SEARCH;
            case 'w': return SHORTCUT_WIKIPEDIA_SEARCH;
            case 'd': return SHORTCUT_DICTIONARY_SEARCH;
            case 'l': return SHORTCUT_GOOGLE_MOBILE_LOCAL_SEARCH;
            }
        }
        return SHORTCUT_INVALID;
    }

    /**
     * Launches the default web search activity with the query parameters if the given intent's data
     * are identified as plain search terms and not URLs/shortcuts.
     * @return true if the intent was handled and web search activity was launched, false if not.
     */
    private boolean handleWebSearchIntent(Intent intent) {
        if (intent == null) return false;

        String url = null;
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            url = intent.getData().toString();
        } else if (Intent.ACTION_SEARCH.equals(action)
                || MediaStore.INTENT_ACTION_MEDIA_SEARCH.equals(action)
                || Intent.ACTION_WEB_SEARCH.equals(action)) {
            url = intent.getStringExtra(SearchManager.QUERY);
        }
        return handleWebSearchRequest(url, intent.getBundleExtra(SearchManager.APP_DATA));
    }

    /**
     * Launches the default web search activity with the query parameters if the given url string
     * was identified as plain search terms and not URL/shortcut.
     * @return true if the request was handled and web search activity was launched, false if not.
     */
    private boolean handleWebSearchRequest(String inUrl, Bundle appData) {
        if (inUrl == null) return false;

        // In general, we shouldn't modify URL from Intent.
        // But currently, we get the user-typed URL from search box as well.
        String url = fixUrl(inUrl).trim();

        // URLs and site specific search shortcuts are handled by the regular flow of control, so
        // return early.
        if (Regex.WEB_URL_PATTERN.matcher(url).matches()
                || ACCEPTED_URI_SCHEMA.matcher(url).matches()
                || parseUrlShortcut(url) != SHORTCUT_INVALID) {
            return false;
        }

        Browser.updateVisitedHistory(mResolver, url, false);
        Browser.addSearchUrl(mResolver, url);

        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(SearchManager.QUERY, url);
        if (appData != null) {
            intent.putExtra(SearchManager.APP_DATA, appData);
        }
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
        startActivity(intent);

        return true;
    }

    private UrlData getUrlDataFromIntent(Intent intent) {
        String url = null;
        if (intent != null) {
            final String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action)) {
                url = smartUrlFilter(intent.getData());
                if (url != null && url.startsWith("content:")) {
                    /* Append mimetype so webview knows how to display */
                    String mimeType = intent.resolveType(getContentResolver());
                    if (mimeType != null) {
                        url += "?" + mimeType;
                    }
                }
                if ("inline:".equals(url)) {
                    return new InlinedUrlData(
                            intent.getStringExtra(Browser.EXTRA_INLINE_CONTENT),
                            intent.getType(),
                            intent.getStringExtra(Browser.EXTRA_INLINE_ENCODING),
                            intent.getStringExtra(Browser.EXTRA_INLINE_FAILURL));
                }
            } else if (Intent.ACTION_SEARCH.equals(action)
                    || MediaStore.INTENT_ACTION_MEDIA_SEARCH.equals(action)
                    || Intent.ACTION_WEB_SEARCH.equals(action)) {
                url = intent.getStringExtra(SearchManager.QUERY);
                if (url != null) {
                    mLastEnteredUrl = url;
                    // Don't add Urls, just search terms.
                    // Urls will get added when the page is loaded.
                    if (!Regex.WEB_URL_PATTERN.matcher(url).matches()) {
                        Browser.updateVisitedHistory(mResolver, url, false);
                    }
                    // In general, we shouldn't modify URL from Intent.
                    // But currently, we get the user-typed URL from search box as well.
                    url = fixUrl(url);
                    url = smartUrlFilter(url);
                    String searchSource = "&source=android-" + GOOGLE_SEARCH_SOURCE_SUGGEST + "&";
                    if (url.contains(searchSource)) {
                        String source = null;
                        final Bundle appData = intent.getBundleExtra(SearchManager.APP_DATA);
                        if (appData != null) {
                            source = appData.getString(SearchManager.SOURCE);
                        }
                        if (TextUtils.isEmpty(source)) {
                            source = GOOGLE_SEARCH_SOURCE_UNKNOWN;
                        }
                        url = url.replace(searchSource, "&source=android-"+source+"&");
                    }
                }
            }
        }
        return new UrlData(url);
    }

    /* package */ static String fixUrl(String inUrl) {
        if (inUrl.startsWith("http://") || inUrl.startsWith("https://"))
            return inUrl;
        if (inUrl.startsWith("http:") ||
                inUrl.startsWith("https:")) {
            if (inUrl.startsWith("http:/") || inUrl.startsWith("https:/")) {
                inUrl = inUrl.replaceFirst("/", "//");
            } else inUrl = inUrl.replaceFirst(":", "://");
        }
        return inUrl;
    }

    /**
     * Looking for the pattern like this
     *
     *          *
     *         * *
     *      ***   *     *******
     *             *   *
     *              * *
     *               *
     */
    private final SensorListener mSensorListener = new SensorListener() {
        private long mLastGestureTime;
        private float[] mPrev = new float[3];
        private float[] mPrevDiff = new float[3];
        private float[] mDiff = new float[3];
        private float[] mRevertDiff = new float[3];

        public void onSensorChanged(int sensor, float[] values) {
            boolean show = false;
            float[] diff = new float[3];

            for (int i = 0; i < 3; i++) {
                diff[i] = values[i] - mPrev[i];
                if (Math.abs(diff[i]) > 1) {
                    show = true;
                }
                if ((diff[i] > 1.0 && mDiff[i] < 0.2)
                        || (diff[i] < -1.0 && mDiff[i] > -0.2)) {
                    // start track when there is a big move, or revert
                    mRevertDiff[i] = mDiff[i];
                    mDiff[i] = 0;
                } else if (diff[i] > -0.2 && diff[i] < 0.2) {
                    // reset when it is flat
                    mDiff[i] = mRevertDiff[i]  = 0;
                }
                mDiff[i] += diff[i];
                mPrevDiff[i] = diff[i];
                mPrev[i] = values[i];
            }

            if (false) {
                // only shows if we think the delta is big enough, in an attempt
                // to detect "serious" moves left/right or up/down
                Log.d("BrowserSensorHack", "sensorChanged " + sensor + " ("
                        + values[0] + ", " + values[1] + ", " + values[2] + ")"
                        + " diff(" + diff[0] + " " + diff[1] + " " + diff[2]
                        + ")");
                Log.d("BrowserSensorHack", "      mDiff(" + mDiff[0] + " "
                        + mDiff[1] + " " + mDiff[2] + ")" + " mRevertDiff("
                        + mRevertDiff[0] + " " + mRevertDiff[1] + " "
                        + mRevertDiff[2] + ")");
            }

            long now = android.os.SystemClock.uptimeMillis();
            if (now - mLastGestureTime > 1000) {
                mLastGestureTime = 0;

                float y = mDiff[1];
                float z = mDiff[2];
                float ay = Math.abs(y);
                float az = Math.abs(z);
                float ry = mRevertDiff[1];
                float rz = mRevertDiff[2];
                float ary = Math.abs(ry);
                float arz = Math.abs(rz);
                boolean gestY = ay > 2.5f && ary > 1.0f && ay > ary;
                boolean gestZ = az > 3.5f && arz > 1.0f && az > arz;

                if ((gestY || gestZ) && !(gestY && gestZ)) {
                    WebView view = mTabControl.getCurrentWebView();

                    if (view != null) {
                        if (gestZ) {
                            if (z < 0) {
                                view.zoomOut();
                            } else {
                                view.zoomIn();
                            }
                        } else {
                            view.flingScroll(0, Math.round(y * 100));
                        }
                    }
                    mLastGestureTime = now;
                }
            }
        }

        public void onAccuracyChanged(int sensor, int accuracy) {
            // TODO Auto-generated method stub

        }
    };

    @Override protected void onResume() {
        super.onResume();
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "BrowserActivity.onResume: this=" + this);
        }

        if (!mActivityInPause) {
            Log.e(LOGTAG, "BrowserActivity is already resumed.");
            return;
        }

        mTabControl.resumeCurrentTab();
        mActivityInPause = false;
        resumeWebViewTimers();

        if (mWakeLock.isHeld()) {
            mHandler.removeMessages(RELEASE_WAKELOCK);
            mWakeLock.release();
        }

        if (mCredsDlg != null) {
            if (!mHandler.hasMessages(CANCEL_CREDS_REQUEST)) {
             // In case credential request never comes back
                mHandler.sendEmptyMessageDelayed(CANCEL_CREDS_REQUEST, 6000);
            }
        }

        registerReceiver(mNetworkStateIntentReceiver,
                         mNetworkStateChangedFilter);
        WebView.enablePlatformNotifications();

        if (mSettings.doFlick()) {
            if (mSensorManager == null) {
                mSensorManager = (SensorManager) getSystemService(
                        Context.SENSOR_SERVICE);
            }
            mSensorManager.registerListener(mSensorListener,
                    SensorManager.SENSOR_ACCELEROMETER,
                    SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            mSensorManager = null;
        }
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        mTitleBar.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     *  onSaveInstanceState(Bundle map)
     *  onSaveInstanceState is called right before onStop(). The map contains
     *  the saved state.
     */
    @Override protected void onSaveInstanceState(Bundle outState) {
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "BrowserActivity.onSaveInstanceState: this=" + this);
        }
        // the default implementation requires each view to have an id. As the
        // browser handles the state itself and it doesn't use id for the views,
        // don't call the default implementation. Otherwise it will trigger the
        // warning like this, "couldn't save which view has focus because the
        // focused view XXX has no id".

        // Save all the tabs
        mTabControl.saveState(outState);
    }

    @Override protected void onPause() {
        super.onPause();

        if (mActivityInPause) {
            Log.e(LOGTAG, "BrowserActivity is already paused.");
            return;
        }

        mTabControl.pauseCurrentTab();
        mActivityInPause = true;
        if (mTabControl.getCurrentIndex() >= 0 && !pauseWebViewTimers()) {
            mWakeLock.acquire();
            mHandler.sendMessageDelayed(mHandler
                    .obtainMessage(RELEASE_WAKELOCK), WAKELOCK_TIMEOUT);
        }

        // Clear the credentials toast if it is up
        if (mCredsDlg != null && mCredsDlg.isShowing()) {
            mCredsDlg.dismiss();
        }
        mCredsDlg = null;

        cancelStopToast();

        // unregister network state listener
        unregisterReceiver(mNetworkStateIntentReceiver);
        WebView.disablePlatformNotifications();

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mSensorListener);
        }
    }

    @Override protected void onDestroy() {
        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "BrowserActivity.onDestroy: this=" + this);
        }
        super.onDestroy();
        // Remove the current tab and sub window
        TabControl.Tab t = mTabControl.getCurrentTab();
        if (t != null) {
            dismissSubWindow(t);
            removeTabFromContentView(t);
        }
        // Destroy all the tabs
        mTabControl.destroy();
        WebIconDatabase.getInstance().close();
        if (mGlsConnection != null) {
            unbindService(mGlsConnection);
            mGlsConnection = null;
        }

        //
        // stop MASF proxy service
        //
        //Intent proxyServiceIntent = new Intent();
        //proxyServiceIntent.setComponent
        //   (new ComponentName(
        //        "com.android.masfproxyservice",
        //        "com.android.masfproxyservice.MasfProxyService"));
        //stopService(proxyServiceIntent);

        unregisterReceiver(mPackageInstallationReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mPageInfoDialog != null) {
            mPageInfoDialog.dismiss();
            showPageInfo(
                mPageInfoView,
                mPageInfoFromShowSSLCertificateOnError.booleanValue());
        }
        if (mSSLCertificateDialog != null) {
            mSSLCertificateDialog.dismiss();
            showSSLCertificate(
                mSSLCertificateView);
        }
        if (mSSLCertificateOnErrorDialog != null) {
            mSSLCertificateOnErrorDialog.dismiss();
            showSSLCertificateOnError(
                mSSLCertificateOnErrorView,
                mSSLCertificateOnErrorHandler,
                mSSLCertificateOnErrorError);
        }
        if (mHttpAuthenticationDialog != null) {
            String title = ((TextView) mHttpAuthenticationDialog
                    .findViewById(com.android.internal.R.id.alertTitle)).getText()
                    .toString();
            String name = ((TextView) mHttpAuthenticationDialog
                    .findViewById(R.id.username_edit)).getText().toString();
            String password = ((TextView) mHttpAuthenticationDialog
                    .findViewById(R.id.password_edit)).getText().toString();
            int focusId = mHttpAuthenticationDialog.getCurrentFocus()
                    .getId();
            mHttpAuthenticationDialog.dismiss();
            showHttpAuthentication(mHttpAuthHandler, null, null, title,
                    name, password, focusId);
        }
        if (mFindDialog != null && mFindDialog.isShowing()) {
            mFindDialog.onConfigurationChanged(newConfig);
        }
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        mTabControl.freeMemory();
    }

    private boolean resumeWebViewTimers() {
        if ((!mActivityInPause && !mPageStarted) ||
                (mActivityInPause && mPageStarted)) {
            CookieSyncManager.getInstance().startSync();
            WebView w = mTabControl.getCurrentWebView();
            if (w != null) {
                w.resumeTimers();
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean pauseWebViewTimers() {
        if (mActivityInPause && !mPageStarted) {
            CookieSyncManager.getInstance().stopSync();
            WebView w = mTabControl.getCurrentWebView();
            if (w != null) {
                w.pauseTimers();
            }
            return true;
        } else {
            return false;
        }
    }

    // FIXME: Do we want to call this when loading google for the first time?
    /*
     * This function is called when we are launching for the first time. We
     * are waiting for the login credentials before loading Google home
     * pages. This way the user will be logged in straight away.
     */
    private void waitForCredentials() {
        // Show a toast
        mCredsDlg = new ProgressDialog(this);
        mCredsDlg.setIndeterminate(true);
        mCredsDlg.setMessage(getText(R.string.retrieving_creds_dlg_msg));
        // If the user cancels the operation, then cancel the Google
        // Credentials request.
        mCredsDlg.setCancelMessage(mHandler.obtainMessage(CANCEL_CREDS_REQUEST));
        mCredsDlg.show();

        // We set a timeout for the retrieval of credentials in onResume()
        // as that is when we have freed up some CPU time to get
        // the login credentials.
    }

    /*
     * If we have received the credentials or we have timed out and we are
     * showing the credentials dialog, then it is time to move on.
     */
    private void resumeAfterCredentials() {
        if (mCredsDlg == null) {
            return;
        }

        // Clear the toast
        if (mCredsDlg.isShowing()) {
            mCredsDlg.dismiss();
        }
        mCredsDlg = null;

        // Clear any pending timeout
        mHandler.removeMessages(CANCEL_CREDS_REQUEST);

        // Load the page
        WebView w = mTabControl.getCurrentWebView();
        if (w != null) {
            w.loadUrl(mSettings.getHomePage());
        }

        // Update the settings, need to do this last as it can take a moment
        // to persist the settings. In the mean time we could be loading
        // content.
        mSettings.setLoginInitialized(this);
    }

    // Open the icon database and retain all the icons for visited sites.
    private void retainIconsOnStartup() {
        final WebIconDatabase db = WebIconDatabase.getInstance();
        db.open(getDir("icons", 0).getPath());
        try {
            Cursor c = Browser.getAllBookmarks(mResolver);
            if (!c.moveToFirst()) {
                c.deactivate();
                return;
            }
            int urlIndex = c.getColumnIndex(Browser.BookmarkColumns.URL);
            do {
                String url = c.getString(urlIndex);
                db.retainIconForPageUrl(url);
            } while (c.moveToNext());
            c.deactivate();
        } catch (IllegalStateException e) {
            Log.e(LOGTAG, "retainIconsOnStartup", e);
        }
    }

    // Helper method for getting the top window.
    WebView getTopWindow() {
        return mTabControl.getCurrentTopWebView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.browser, menu);
        mMenu = menu;
        updateInLoadMenuItems();
        return true;
    }

    /**
     * As the menu can be open when loading state changes
     * we must manually update the state of the stop/reload menu
     * item
     */
    private void updateInLoadMenuItems() {
        if (mMenu == null) {
            return;
        }
        MenuItem src = mInLoad ?
                mMenu.findItem(R.id.stop_menu_id):
                    mMenu.findItem(R.id.reload_menu_id);
        MenuItem dest = mMenu.findItem(R.id.stop_reload_menu_id);
        dest.setIcon(src.getIcon());
        dest.setTitle(src.getTitle());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // chording is not an issue with context menus, but we use the same
        // options selector, so set mCanChord to true so we can access them.
        mCanChord = true;
        int id = item.getItemId();
        final WebView webView = getTopWindow();
        if (null == webView) {
            return false;
        }
        final HashMap hrefMap = new HashMap();
        hrefMap.put("webview", webView);
        final Message msg = mHandler.obtainMessage(
                FOCUS_NODE_HREF, id, 0, hrefMap);
        switch (id) {
            // -- Browser context menu
            case R.id.open_context_menu_id:
            case R.id.open_newtab_context_menu_id:
            case R.id.bookmark_context_menu_id:
            case R.id.save_link_context_menu_id:
            case R.id.share_link_context_menu_id:
            case R.id.copy_link_context_menu_id:
                webView.requestFocusNodeHref(msg);
                break;

            default:
                // For other context menus
                return onOptionsItemSelected(item);
        }
        mCanChord = false;
        return true;
    }

    private Bundle createGoogleSearchSourceBundle(String source) {
        Bundle bundle = new Bundle();
        bundle.putString(SearchManager.SOURCE, source);
        return bundle;
    }

    /**
     * Overriding this to insert a local information bundle
     */
    @Override
    public boolean onSearchRequested() {
        String url = (getTopWindow() == null) ? null : getTopWindow().getUrl();
        startSearch(mSettings.getHomePage().equals(url) ? null : url, true,
                createGoogleSearchSourceBundle(GOOGLE_SEARCH_SOURCE_SEARCHKEY), false);
        return true;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (appSearchData == null) {
            appSearchData = createGoogleSearchSourceBundle(GOOGLE_SEARCH_SOURCE_TYPE);
        }
        super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
    }

    /**
     * Switch tabs.  Called by the TitleBarSet when sliding the title bar
     * results in changing tabs.
     * @param index Index of the tab to change to, as defined by
     *              mTabControl.getTabIndex(Tab t).
     * @return boolean True if we successfully switched to a different tab.  If
     *                 the indexth tab is null, or if that tab is the same as
     *                 the current one, return false.
     */
    /* package */ boolean switchToTab(int index) {
        TabControl.Tab tab = mTabControl.getTab(index);
        TabControl.Tab currentTab = mTabControl.getCurrentTab();
        if (tab == null || tab == currentTab) {
            return false;
        }
        if (currentTab != null) {
            // currentTab may be null if it was just removed.  In that case,
            // we do not need to remove it
            removeTabFromContentView(currentTab);
        }
        removeTabFromContentView(tab);
        mTabControl.setCurrentTab(tab);
        attachTabToContentView(tab);
        if (CUSTOM_BROWSER_BAR) {
            mTitleBar.setCurrentTab(index);
            WebView view = tab.getWebView();
            view.slideIntoFocus();
        }
        return true;
    }

    /* package */ void closeCurrentWindow() {
        final TabControl.Tab current = mTabControl.getCurrentTab();
        if (mTabControl.getTabCount() == 1) {
            // This is the last tab.  Open a new one, as well as the history
            // picker, and close the current one.
            TabControl.Tab newTab = openTabAndShow(
                    BrowserActivity.EMPTY_URL_DATA, false, null);
            bookmarksOrHistoryPicker(false, true);
            closeTab(current);
            mTabControl.setCurrentTab(newTab);
            return;
        }
        final TabControl.Tab parent = current.getParentTab();
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mCanChord) {
            // The user has already fired a shortcut with this hold down of the
            // menu key.
            return false;
        }
        if (null == getTopWindow()) {
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
                openTabAndShow(EMPTY_URL_DATA, false, null);
                bookmarksOrHistoryPicker(false, true);
                break;

            case R.id.goto_menu_id:
                bookmarksOrHistoryPicker(false, false);
                break;

            case R.id.add_bookmark_menu_id:
                Intent i = new Intent(BrowserActivity.this,
                        AddBookmarkPage.class);
                WebView w = getTopWindow();
                i.putExtra("url", w.getUrl());
                i.putExtra("title", w.getTitle());
                startActivity(i);
                break;

            case R.id.stop_reload_menu_id:
                if (mInLoad) {
                    stopLoading();
                } else {
                    getTopWindow().reload();
                }
                break;

            case R.id.back_menu_id:
                getTopWindow().goBack();
                break;

            case R.id.forward_menu_id:
                getTopWindow().goForward();
                break;

            case R.id.close_menu_id:
                // Close the subwindow if it exists.
                if (mTabControl.getCurrentSubWindow() != null) {
                    dismissSubWindow(mTabControl.getCurrentTab());
                    break;
                }
                closeCurrentWindow();
                break;

            case R.id.homepage_menu_id:
                TabControl.Tab current = mTabControl.getCurrentTab();
                if (current != null) {
                    dismissSubWindow(current);
                    current.getWebView().loadUrl(mSettings.getHomePage());
                }
                break;

            case R.id.preferences_menu_id:
                Intent intent = new Intent(this,
                        BrowserPreferencesPage.class);
                startActivityForResult(intent, PREFERENCES_PAGE);
                break;

            case R.id.find_menu_id:
                if (null == mFindDialog) {
                    mFindDialog = new FindDialog(this);
                }
                mFindDialog.setWebView(getTopWindow());
                mFindDialog.show();
                mMenuState = EMPTY_MENU;
                break;

            case R.id.select_text_id:
                getTopWindow().emulateShiftHeld();
                break;
            case R.id.page_info_menu_id:
                showPageInfo(mTabControl.getCurrentTab(), false);
                break;

            case R.id.classic_history_menu_id:
                bookmarksOrHistoryPicker(true, false);
                break;

            case R.id.share_page_menu_id:
                Browser.sendString(this, getTopWindow().getUrl());
                break;

            case R.id.dump_nav_menu_id:
                getTopWindow().debugDump();
                break;

            case R.id.zoom_in_menu_id:
                getTopWindow().zoomIn();
                break;

            case R.id.zoom_out_menu_id:
                getTopWindow().zoomOut();
                break;

            case R.id.view_downloads_menu_id:
                viewDownloads(null);
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
                            TabControl.Tab desiredTab = mTabControl.getTab(id);
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
                if (!super.onOptionsItemSelected(item)) {
                    return false;
                }
                // Otherwise fall through.
        }
        mCanChord = false;
        return true;
    }

    public void closeFind() {
        mMenuState = R.id.MAIN_MENU;
    }

    @Override public boolean onPrepareOptionsMenu(Menu menu)
    {
        // This happens when the user begins to hold down the menu key, so
        // allow them to chord to get a shortcut.
        mCanChord = true;
        // Note: setVisible will decide whether an item is visible; while
        // setEnabled() will decide whether an item is enabled, which also means
        // whether the matching shortcut key will function.
        super.onPrepareOptionsMenu(menu);
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
                final WebView w = getTopWindow();
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

                menu.findItem(R.id.forward_menu_id)
                        .setEnabled(canGoForward);

                menu.findItem(R.id.new_tab_menu_id).setEnabled(
                        mTabControl.getTabCount() < TabControl.MAX_TABS);

                // decide whether to show the share link option
                PackageManager pm = getPackageManager();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                ResolveInfo ri = pm.resolveActivity(send, PackageManager.MATCH_DEFAULT_ONLY);
                menu.findItem(R.id.share_page_menu_id).setVisible(ri != null);

                boolean isNavDump = mSettings.isNavDump();
                final MenuItem nav = menu.findItem(R.id.dump_nav_menu_id);
                nav.setVisible(isNavDump);
                nav.setEnabled(isNavDump);
                break;
        }
        mCurrentMenuState = mMenuState;
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
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
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.browsercontext, menu);

        // Show the correct menu group
        String extra = result.getExtra();
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
                addIntent.setType(Contacts.People.CONTENT_ITEM_TYPE);
                menu.findItem(R.id.add_contact_context_menu_id).setIntent(
                        addIntent);
                menu.findItem(R.id.copy_phone_context_menu_id).setOnMenuItemClickListener(
                        new Copy(extra));
                break;

            case WebView.HitTestResult.EMAIL_TYPE:
                menu.setHeaderTitle(extra);
                menu.findItem(R.id.email_context_menu_id).setIntent(
                        new Intent(Intent.ACTION_VIEW, Uri
                                .parse(WebView.SCHEME_MAILTO + extra)));
                menu.findItem(R.id.copy_mail_context_menu_id).setOnMenuItemClickListener(
                        new Copy(extra));
                break;

            case WebView.HitTestResult.GEO_TYPE:
                menu.setHeaderTitle(extra);
                menu.findItem(R.id.map_context_menu_id).setIntent(
                        new Intent(Intent.ACTION_VIEW, Uri
                                .parse(WebView.SCHEME_GEO
                                        + URLEncoder.encode(extra))));
                menu.findItem(R.id.copy_geo_context_menu_id).setOnMenuItemClickListener(
                        new Copy(extra));
                break;

            case WebView.HitTestResult.SRC_ANCHOR_TYPE:
            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                TextView titleView = (TextView) LayoutInflater.from(this)
                        .inflate(android.R.layout.browser_link_context_header,
                        null);
                titleView.setText(extra);
                menu.setHeaderView(titleView);
                // decide whether to show the open link in new tab option
                menu.findItem(R.id.open_newtab_context_menu_id).setVisible(
                        mTabControl.getTabCount() < TabControl.MAX_TABS);
                PackageManager pm = getPackageManager();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                ResolveInfo ri = pm.resolveActivity(send, PackageManager.MATCH_DEFAULT_ONLY);
                menu.findItem(R.id.share_link_context_menu_id).setVisible(ri != null);
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
                        setOnMenuItemClickListener(new Download(extra));
                break;

            default:
                Log.w(LOGTAG, "We should not get here.");
                break;
        }
    }

    // Attach the given tab to the content view.
    private void attachTabToContentView(TabControl.Tab t) {
        // Attach the container that contains the main WebView and any other UI
        // associated with the tab.
        mContentView.addView(t.getContainer(), COVER_SCREEN_PARAMS);

        if (mShouldShowErrorConsole) {
            ErrorConsoleView errorConsole = mTabControl.getCurrentErrorConsole(true);
            if (errorConsole.numberOfErrors() == 0) {
                errorConsole.showConsole(ErrorConsoleView.SHOW_NONE);
            } else {
                errorConsole.showConsole(ErrorConsoleView.SHOW_MINIMIZED);
            }

            mErrorConsoleContainer.addView(errorConsole,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                                  ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // Attach the sub window if necessary
        attachSubWindow(t);
        // Request focus on the top window.
        t.getTopWindow().requestFocus();
    }

    // Attach a sub window to the main WebView of the given tab.
    private void attachSubWindow(TabControl.Tab t) {
        // If a sub window exists, attach it to the content view.
        final WebView subView = t.getSubWebView();
        if (subView != null) {
            final View container = t.getSubWebViewContainer();
            mContentView.addView(container, COVER_SCREEN_PARAMS);
            subView.requestFocus();
        }
    }

    // Remove the given tab from the content view.
    private void removeTabFromContentView(TabControl.Tab t) {
        // Remove the container that contains the main WebView.
        mContentView.removeView(t.getContainer());

        if (mTabControl.getCurrentErrorConsole(false) != null) {
            mErrorConsoleContainer.removeView(mTabControl.getCurrentErrorConsole(false));
        }

        // Remove the sub window if it exists.
        if (t.getSubWebView() != null) {
            mContentView.removeView(t.getSubWebViewContainer());
        }
    }

    // Remove the sub window if it exists. Also called by TabControl when the
    // user clicks the 'X' to dismiss a sub window.
    /* package */ void dismissSubWindow(TabControl.Tab t) {
        final WebView mainView = t.getWebView();
        if (t.getSubWebView() != null) {
            // Remove the container view and request focus on the main WebView.
            mContentView.removeView(t.getSubWebViewContainer());
            mainView.requestFocus();
            // Tell the TabControl to dismiss the subwindow. This will destroy
            // the WebView.
            mTabControl.dismissSubWindow(t);
        }
    }

    // A wrapper function of {@link #openTabAndShow(UrlData, boolean, String)}
    // that accepts url as string.
    private TabControl.Tab openTabAndShow(String url, boolean closeOnExit,
            String appId) {
        return openTabAndShow(new UrlData(url), closeOnExit, appId);
    }

    // This method does a ton of stuff. It will attempt to create a new tab
    // if we haven't reached MAX_TABS. Otherwise it uses the current tab. If
    // url isn't null, it will load the given url.
    /* package */ TabControl.Tab openTabAndShow(UrlData urlData,
            boolean closeOnExit, String appId) {
        final boolean newTab = mTabControl.getTabCount() != TabControl.MAX_TABS;
        final TabControl.Tab currentTab = mTabControl.getCurrentTab();
        if (newTab) {
            final TabControl.Tab tab = mTabControl.createNewTab(
                    closeOnExit, appId, urlData.mUrl);
            WebView webview = tab.getWebView();
            if (CUSTOM_BROWSER_BAR) {
                mTitleBar.addTab(webview, true);
            }
            removeTabFromContentView(currentTab);
            attachTabToContentView(tab);
            // We must set the new tab as the current tab to reflect the old
            // animation behavior.
            mTabControl.setCurrentTab(tab);
            if (!urlData.isEmpty()) {
                urlData.loadIn(webview);
            }
            return tab;
        } else {
            // Get rid of the subwindow if it exists
            dismissSubWindow(currentTab);
            if (!urlData.isEmpty()) {
                // Load the given url.
                urlData.loadIn(currentTab.getWebView());
            }
        }
        return currentTab;
    }

    private TabControl.Tab openTab(String url) {
        if (mSettings.openInBackground()) {
            TabControl.Tab t = mTabControl.createNewTab();
            if (t != null) {
                WebView view = t.getWebView();
                if (CUSTOM_BROWSER_BAR) {
                    mTitleBar.addTab(view, false);
                }
                view.loadUrl(url);
            }
            return t;
        } else {
            return openTabAndShow(url, false, null);
        }
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

    private class Download implements OnMenuItemClickListener {
        private String mText;

        public boolean onMenuItemClick(MenuItem item) {
            onDownloadStartNoStream(mText, null, null, null, -1);
            return true;
        }

        public Download(String toDownload) {
            mText = toDownload;
        }
    }

    private void copy(CharSequence text) {
        try {
            IClipboard clip = IClipboard.Stub.asInterface(ServiceManager.getService("clipboard"));
            if (clip != null) {
                clip.setClipboardText(text);
            }
        } catch (android.os.RemoteException e) {
            Log.e(LOGTAG, "Copy failed", e);
        }
    }

    /**
     * Resets the browser title-view to whatever it must be (for example, if we
     * load a page from history).
     */
    private void resetTitle() {
        resetLockIcon();
        resetTitleIconAndProgress();
    }

    /**
     * Resets the browser title-view to whatever it must be
     * (for example, if we had a loading error)
     * When we have a new page, we call resetTitle, when we
     * have to reset the titlebar to whatever it used to be
     * (for example, if the user chose to stop loading), we
     * call resetTitleAndRevertLockIcon.
     */
    /* package */ void resetTitleAndRevertLockIcon() {
        revertLockIcon();
        resetTitleIconAndProgress();
    }

    /**
     * Reset the title, favicon, and progress.
     */
    private void resetTitleIconAndProgress() {
        WebView current = mTabControl.getCurrentWebView();
        if (current == null) {
            return;
        }
        resetTitleAndIcon(current);
        int progress = current.getProgress();
        mWebChromeClient.onProgressChanged(current, progress);
    }

    // Reset the title and the icon based on the given item.
    private void resetTitleAndIcon(WebView view) {
        WebHistoryItem item = view.copyBackForwardList().getCurrentItem();
        if (item != null) {
            setUrlTitle(item.getUrl(), item.getTitle(), view);
            setFavicon(item.getFavicon());
        } else {
            setUrlTitle(null, null, view);
            setFavicon(null);
        }
    }

    /**
     * Sets a title composed of the URL and the title string.
     * @param url The URL of the site being loaded.
     * @param title The title of the site being loaded.
     */
    private void setUrlTitle(String url, String title, WebView view) {
        mUrl = url;
        mTitle = title;

        if (CUSTOM_BROWSER_BAR) {
            mTitleBar.setTitleAndUrl(title, url, view);
        } else {
            setTitle(buildUrlTitle(url, title));
        }
    }

    /**
     * Builds and returns the page title, which is some
     * combination of the page URL and title.
     * @param url The URL of the site being loaded.
     * @param title The title of the site being loaded.
     * @return The page title.
     */
    private String buildUrlTitle(String url, String title) {
        String urlTitle = "";

        if (url != null) {
            String titleUrl = buildTitleUrl(url);

            if (title != null && 0 < title.length()) {
                if (titleUrl != null && 0 < titleUrl.length()) {
                    urlTitle = titleUrl + ": " + title;
                } else {
                    urlTitle = title;
                }
            } else {
                if (titleUrl != null) {
                    urlTitle = titleUrl;
                }
            }
        }

        return urlTitle;
    }

    /**
     * @param url The URL to build a title version of the URL from.
     * @return The title version of the URL or null if fails.
     * The title version of the URL can be either the URL hostname,
     * or the hostname with an "https://" prefix (for secure URLs),
     * or an empty string if, for example, the URL in question is a
     * file:// URL with no hostname.
     */
    /* package */ static String buildTitleUrl(String url) {
        String titleUrl = null;

        if (url != null) {
            try {
                // parse the url string
                URL urlObj = new URL(url);
                if (urlObj != null) {
                    titleUrl = "";

                    String protocol = urlObj.getProtocol();
                    String host = urlObj.getHost();

                    if (host != null && 0 < host.length()) {
                        titleUrl = host;
                        if (protocol != null) {
                            // if a secure site, add an "https://" prefix!
                            if (protocol.equalsIgnoreCase("https")) {
                                titleUrl = protocol + "://" + host;
                            }
                        }
                    }
                }
            } catch (MalformedURLException e) {}
        }

        return titleUrl;
    }

    // Set the favicon in the title bar.
    private void setFavicon(Bitmap icon) {
        if (CUSTOM_BROWSER_BAR) {
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
            mTitleBar.setFavicon(d, getTopWindow());
        } else {
            Drawable[] array = new Drawable[2];
            PaintDrawable p = new PaintDrawable(Color.WHITE);
            p.setCornerRadius(3f);
            array[0] = p;
            if (icon == null) {
                array[1] = mGenericFavicon;
            } else {
                array[1] = new BitmapDrawable(icon);
            }
            LayerDrawable d = new LayerDrawable(array);
            d.setLayerInset(1, 2, 2, 2, 2);
            getWindow().setFeatureDrawable(Window.FEATURE_LEFT_ICON, d);
        }
    }

    /**
     * Saves the current lock-icon state before resetting
     * the lock icon. If we have an error, we may need to
     * roll back to the previous state.
     */
    private void saveLockIcon() {
        mPrevLockType = mLockIconType;
    }

    /**
     * Reverts the lock-icon state to the last saved state,
     * for example, if we had an error, and need to cancel
     * the load.
     */
    private void revertLockIcon() {
        mLockIconType = mPrevLockType;

        if (LOGV_ENABLED) {
            Log.v(LOGTAG, "BrowserActivity.revertLockIcon:" +
                  " revert lock icon to " + mLockIconType);
        }

        updateLockIconImage(mLockIconType);
    }

    /**
     * Close the tab after removing its associated title bar.
     */
    private void closeTab(TabControl.Tab t) {
        if (CUSTOM_BROWSER_BAR) {
            mTitleBar.removeTab(mTabControl.getTabIndex(t));
        }
        mTabControl.removeTab(t);
    }

    private void goBackOnePageOrQuit() {
        TabControl.Tab current = mTabControl.getCurrentTab();
        if (current == null) {
            /*
             * Instead of finishing the activity, simply push this to the back
             * of the stack and let ActivityManager to choose the foreground
             * activity. As BrowserActivity is singleTask, it will be always the
             * root of the task. So we can use either true or false for
             * moveTaskToBack().
             */
            moveTaskToBack(true);
        }
        WebView w = current.getWebView();
        if (w.canGoBack()) {
            w.goBack();
        } else {
            // Check to see if we are closing a window that was created by
            // another window. If so, we switch back to that window.
            TabControl.Tab parent = current.getParentTab();
            if (parent != null) {
                switchToTab(mTabControl.getTabIndex(parent));
                // Now we close the other tab
                closeTab(current);
            } else {
                if (current.closeOnExit()) {
                    if (mTabControl.getTabCount() == 1) {
                        finish();
                        return;
                    }
                    // call pauseWebViewTimers() now, we won't be able to call
                    // it in onPause() as the WebView won't be valid.
                    // Temporarily change mActivityInPause to be true as
                    // pauseWebViewTimers() will do nothing if mActivityInPause
                    // is false.
                    boolean savedState = mActivityInPause;
                    if (savedState) {
                        Log.e(LOGTAG, "BrowserActivity is already paused "
                                + "while handing goBackOnePageOrQuit.");
                    }
                    mActivityInPause = true;
                    pauseWebViewTimers();
                    mActivityInPause = savedState;
                    removeTabFromContentView(current);
                    mTabControl.removeTab(current);
                }
                /*
                 * Instead of finishing the activity, simply push this to the back
                 * of the stack and let ActivityManager to choose the foreground
                 * activity. As BrowserActivity is singleTask, it will be always the
                 * root of the task. So we can use either true or false for
                 * moveTaskToBack().
                 */
                moveTaskToBack(true);
            }
        }
    }

    public KeyTracker.State onKeyTracker(int keyCode,
                                         KeyEvent event,
                                         KeyTracker.Stage stage,
                                         int duration) {
        // if onKeyTracker() is called after activity onStop()
        // because of accumulated key events,
        // we should ignore it as browser is not active any more.
        WebView topWindow = getTopWindow();
        if (topWindow == null && mCustomView == null)
            return KeyTracker.State.NOT_TRACKING;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Check if a custom view is currently showing and, if it is, hide it.
            if (mCustomView != null) {
                mWebChromeClient.onHideCustomView();
                return KeyTracker.State.DONE_TRACKING;
            }
            if (stage == KeyTracker.Stage.LONG_REPEAT) {
                bookmarksOrHistoryPicker(true, false);
                return KeyTracker.State.DONE_TRACKING;
            } else if (stage == KeyTracker.Stage.UP) {
                // FIXME: Currently, we do not have a notion of the
                // history picker for the subwindow, but maybe we
                // should?
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
                return KeyTracker.State.DONE_TRACKING;
            }
            return KeyTracker.State.KEEP_TRACKING;
        }
        return KeyTracker.State.NOT_TRACKING;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            mMenuIsDown = true;
        } else if (mMenuIsDown) {
            // The default key mode is DEFAULT_KEYS_SEARCH_LOCAL. As the MENU is
            // still down, we don't want to trigger the search. Pretend to
            // consume the key and do nothing.
            return true;
        }
        boolean handled =  mKeyTracker.doKeyDown(keyCode, event);
        if (!handled) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_SPACE:
                    if (event.isShiftPressed()) {
                        getTopWindow().pageUp(false);
                    } else {
                        getTopWindow().pageDown(false);
                    }
                    handled = true;
                    break;

                default:
                    break;
            }
        }
        return handled || super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            mMenuIsDown = false;
        }
        return mKeyTracker.doKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    private void stopLoading() {
        resetTitleAndRevertLockIcon();
        WebView w = getTopWindow();
        w.stopLoading();
        mWebViewClient.onPageFinished(w, w.getUrl());

        cancelStopToast();
        mStopToast = Toast
                .makeText(this, R.string.stopping, Toast.LENGTH_SHORT);
        mStopToast.show();
    }

    private void cancelStopToast() {
        if (mStopToast != null) {
            mStopToast.cancel();
            mStopToast = null;
        }
    }

    // called by a non-UI thread to post the message
    public void postMessage(int what, int arg1, int arg2, Object obj) {
        mHandler.sendMessage(mHandler.obtainMessage(what, arg1, arg2, obj));
    }

    // public message ids
    public final static int LOAD_URL                = 1001;
    public final static int STOP_LOAD               = 1002;

    // Message Ids
    private static final int FOCUS_NODE_HREF         = 102;
    private static final int CANCEL_CREDS_REQUEST    = 103;
    private static final int RELEASE_WAKELOCK        = 107;

    // Private handler for handling javascript and saving passwords
    private Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FOCUS_NODE_HREF:
                    String url = (String) msg.getData().get("url");
                    if (url == null || url.length() == 0) {
                        break;
                    }
                    HashMap focusNodeMap = (HashMap) msg.obj;
                    WebView view = (WebView) focusNodeMap.get("webview");
                    // Only apply the action if the top window did not change.
                    if (getTopWindow() != view) {
                        break;
                    }
                    switch (msg.arg1) {
                        case R.id.open_context_menu_id:
                        case R.id.view_image_context_menu_id:
                            loadURL(getTopWindow(), url);
                            break;
                        case R.id.open_newtab_context_menu_id:
                            final TabControl.Tab parent = mTabControl
                                    .getCurrentTab();
                            final TabControl.Tab newTab = openTab(url);
                            if (newTab != parent) {
                                parent.addChildTab(newTab);
                            }
                            break;
                        case R.id.bookmark_context_menu_id:
                            Intent intent = new Intent(BrowserActivity.this,
                                    AddBookmarkPage.class);
                            intent.putExtra("url", url);
                            startActivity(intent);
                            break;
                        case R.id.share_link_context_menu_id:
                            Browser.sendString(BrowserActivity.this, url);
                            break;
                        case R.id.copy_link_context_menu_id:
                            copy(url);
                            break;
                        case R.id.save_link_context_menu_id:
                        case R.id.download_context_menu_id:
                            onDownloadStartNoStream(url, null, null, null, -1);
                            break;
                    }
                    break;

                case LOAD_URL:
                    loadURL(getTopWindow(), (String) msg.obj);
                    break;

                case STOP_LOAD:
                    stopLoading();
                    break;

                case CANCEL_CREDS_REQUEST:
                    resumeAfterCredentials();
                    break;

                case RELEASE_WAKELOCK:
                    if (mWakeLock.isHeld()) {
                        mWakeLock.release();
                    }
                    break;
            }
        }
    };

    private void updateScreenshot(WebView view) {
        // If this is a bookmarked site, add a screenshot to the database.
        // FIXME: When should we update?  Every time?
        // FIXME: Would like to make sure there is actually something to
        // draw, but the API for that (WebViewCore.pictureReady()) is not
        // currently accessible here.
        ContentResolver cr = getContentResolver();
        final Cursor c = BrowserBookmarksAdapter.queryBookmarksForUrl(
                cr, view.getOriginalUrl(), view.getUrl(), false);
        if (c != null) {
            boolean succeed = c.moveToFirst();
            ContentValues values = null;
            while (succeed) {
                if (values == null) {
                    final ByteArrayOutputStream os
                            = new ByteArrayOutputStream();
                    Picture thumbnail = view.capturePicture();
                    // Keep width and height in sync with BrowserBookmarksPage
                    // and bookmark_thumb
                    Bitmap bm = Bitmap.createBitmap(100, 80,
                            Bitmap.Config.ARGB_4444);
                    Canvas canvas = new Canvas(bm);
                    // May need to tweak these values to determine what is the
                    // best scale factor
                    canvas.scale(.5f, .5f);
                    thumbnail.draw(canvas);
                    bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                    values = new ContentValues();
                    values.put(Browser.BookmarkColumns.THUMBNAIL,
                            os.toByteArray());
                }
                cr.update(ContentUris.withAppendedId(Browser.BOOKMARKS_URI,
                        c.getInt(0)), values, null, null);
                succeed = c.moveToNext();
            }
            c.close();
        }
    }

    // -------------------------------------------------------------------------
    // WebViewClient implementation.
    //-------------------------------------------------------------------------

    // Use in overrideUrlLoading
    /* package */ final static String SCHEME_WTAI = "wtai://wp/";
    /* package */ final static String SCHEME_WTAI_MC = "wtai://wp/mc;";
    /* package */ final static String SCHEME_WTAI_SD = "wtai://wp/sd;";
    /* package */ final static String SCHEME_WTAI_AP = "wtai://wp/ap;";

    /* package */ WebViewClient getWebViewClient() {
        return mWebViewClient;
    }

    private void updateIcon(WebView view, Bitmap icon) {
        if (icon != null) {
            BrowserBookmarksAdapter.updateBookmarkFavicon(mResolver,
                    view, icon);
        }
        setFavicon(icon);
    }

    private final WebViewClient mWebViewClient = new WebViewClient() {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            resetLockIcon(url);
            setUrlTitle(url, null, view);

            ErrorConsoleView errorConsole = mTabControl.getCurrentErrorConsole(false);
            if (errorConsole != null) {
                errorConsole.clearErrorMessages();
                if (mShouldShowErrorConsole) {
                    errorConsole.showConsole(ErrorConsoleView.SHOW_NONE);
                }
            }

            // Call updateIcon instead of setFavicon so the bookmark
            // database can be updated.
            updateIcon(view, favicon);

            if (mSettings.isTracing()) {
                String host;
                try {
                    WebAddress uri = new WebAddress(url);
                    host = uri.mHost;
                } catch (android.net.ParseException ex) {
                    host = "browser";
                }
                host = host.replace('.', '_');
                host += ".trace";
                mInTrace = true;
                Debug.startMethodTracing(host, 20 * 1024 * 1024);
            }

            // Performance probe
            if (false) {
                mStart = SystemClock.uptimeMillis();
                mProcessStart = Process.getElapsedCpuTime();
                long[] sysCpu = new long[7];
                if (Process.readProcFile("/proc/stat", SYSTEM_CPU_FORMAT, null,
                        sysCpu, null)) {
                    mUserStart = sysCpu[0] + sysCpu[1];
                    mSystemStart = sysCpu[2];
                    mIdleStart = sysCpu[3];
                    mIrqStart = sysCpu[4] + sysCpu[5] + sysCpu[6];
                }
                mUiStart = SystemClock.currentThreadTimeMillis();
            }

            if (!mPageStarted) {
                mPageStarted = true;
                // if onResume() has been called, resumeWebViewTimers() does
                // nothing.
                resumeWebViewTimers();
            }

            // reset sync timer to avoid sync starts during loading a page
            CookieSyncManager.getInstance().resetSync();

            mInLoad = true;
            if (CUSTOM_BROWSER_BAR) {
                mTitleBar.setVisibility(View.VISIBLE);
            }
            updateInLoadMenuItems();
            if (!mIsNetworkUp) {
                if ( mAlertDialog == null) {
                    mAlertDialog = new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle(R.string.loadSuspendedTitle)
                        .setMessage(R.string.loadSuspended)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                }
                if (view != null) {
                    view.setNetworkAvailable(false);
                }
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // Reset the title and icon in case we stopped a provisional
            // load.
            resetTitleAndIcon(view);

            // Update the lock icon image only once we are done loading
            updateLockIconImage(mLockIconType);
            updateScreenshot(view);

            // Performance probe
            if (false) {
                long[] sysCpu = new long[7];
                if (Process.readProcFile("/proc/stat", SYSTEM_CPU_FORMAT, null,
                        sysCpu, null)) {
                    String uiInfo = "UI thread used "
                            + (SystemClock.currentThreadTimeMillis() - mUiStart)
                            + " ms";
                    if (LOGD_ENABLED) {
                        Log.d(LOGTAG, uiInfo);
                    }
                    //The string that gets written to the log
                    String performanceString = "It took total "
                            + (SystemClock.uptimeMillis() - mStart)
                            + " ms clock time to load the page."
                            + "\nbrowser process used "
                            + (Process.getElapsedCpuTime() - mProcessStart)
                            + " ms, user processes used "
                            + (sysCpu[0] + sysCpu[1] - mUserStart) * 10
                            + " ms, kernel used "
                            + (sysCpu[2] - mSystemStart) * 10
                            + " ms, idle took " + (sysCpu[3] - mIdleStart) * 10
                            + " ms and irq took "
                            + (sysCpu[4] + sysCpu[5] + sysCpu[6] - mIrqStart)
                            * 10 + " ms, " + uiInfo;
                    if (LOGD_ENABLED) {
                        Log.d(LOGTAG, performanceString + "\nWebpage: " + url);
                    }
                    if (url != null) {
                        // strip the url to maintain consistency
                        String newUrl = new String(url);
                        if (newUrl.startsWith("http://www.")) {
                            newUrl = newUrl.substring(11);
                        } else if (newUrl.startsWith("http://")) {
                            newUrl = newUrl.substring(7);
                        } else if (newUrl.startsWith("https://www.")) {
                            newUrl = newUrl.substring(12);
                        } else if (newUrl.startsWith("https://")) {
                            newUrl = newUrl.substring(8);
                        }
                        if (LOGD_ENABLED) {
                            Log.d(LOGTAG, newUrl + " loaded");
                        }
                        /*
                        if (sWhiteList.contains(newUrl)) {
                            // The string that gets pushed to the statistcs
                            // service
                            performanceString = performanceString
                                    + "\nWebpage: "
                                    + newUrl
                                    + "\nCarrier: "
                                    + android.os.SystemProperties
                                            .get("gsm.sim.operator.alpha");
                            if (mWebView != null
                                    && mWebView.getContext() != null
                                    && mWebView.getContext().getSystemService(
                                    Context.CONNECTIVITY_SERVICE) != null) {
                                ConnectivityManager cManager =
                                        (ConnectivityManager) mWebView
                                        .getContext().getSystemService(
                                        Context.CONNECTIVITY_SERVICE);
                                NetworkInfo nInfo = cManager
                                        .getActiveNetworkInfo();
                                if (nInfo != null) {
                                    performanceString = performanceString
                                            + "\nNetwork Type: "
                                            + nInfo.getType().toString();
                                }
                            }
                            Checkin.logEvent(mResolver,
                                    Checkin.Events.Tag.WEBPAGE_LOAD,
                                    performanceString);
                            Log.w(LOGTAG, "pushed to the statistics service");
                        }
                        */
                    }
                }
             }

            if (mInTrace) {
                mInTrace = false;
                Debug.stopMethodTracing();
            }

            if (mPageStarted) {
                mPageStarted = false;
                // pauseWebViewTimers() will do nothing and return false if
                // onPause() is not called yet.
                if (pauseWebViewTimers()) {
                    if (mWakeLock.isHeld()) {
                        mHandler.removeMessages(RELEASE_WAKELOCK);
                        mWakeLock.release();
                    }
                }
            }
        }

        // return true if want to hijack the url to let another app to handle it
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith(SCHEME_WTAI)) {
                // wtai://wp/mc;number
                // number=string(phone-number)
                if (url.startsWith(SCHEME_WTAI_MC)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(WebView.SCHEME_TEL +
                            url.substring(SCHEME_WTAI_MC.length())));
                    startActivity(intent);
                    return true;
                }
                // wtai://wp/sd;dtmf
                // dtmf=string(dialstring)
                if (url.startsWith(SCHEME_WTAI_SD)) {
                    // TODO
                    // only send when there is active voice connection
                    return false;
                }
                // wtai://wp/ap;number;name
                // number=string(phone-number)
                // name=string
                if (url.startsWith(SCHEME_WTAI_AP)) {
                    // TODO
                    return false;
                }
            }

            // The "about:" schemes are internal to the browser; don't
            // want these to be dispatched to other apps.
            if (url.startsWith("about:")) {
                return false;
            }

            Intent intent;

            // perform generic parsing of the URI to turn it into an Intent.
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                Log.w("Browser", "Bad URI " + url + ": " + ex.getMessage());
                return false;
            }

            // check whether the intent can be resolved. If not, we will see
            // whether we can download it from the Market.
            if (getPackageManager().resolveActivity(intent, 0) == null) {
                String packagename = intent.getPackage();
                if (packagename != null) {
                    intent = new Intent(Intent.ACTION_VIEW, Uri
                            .parse("market://search?q=pname:" + packagename));
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    startActivity(intent);
                    return true;
                } else {
                    return false;
                }
            }

            // sanitize the Intent, ensuring web pages can not bypass browser
            // security (only access to BROWSABLE activities).
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            try {
                if (startActivityIfNeeded(intent, -1)) {
                    return true;
                }
            } catch (ActivityNotFoundException ex) {
                // ignore the error. If no application can handle the URL,
                // eg about:blank, assume the browser can handle it.
            }

            if (mMenuIsDown) {
                openTab(url);
                closeOptionsMenu();
                return true;
            }

            return false;
        }

        /**
         * Updates the lock icon. This method is called when we discover another
         * resource to be loaded for this page (for example, javascript). While
         * we update the icon type, we do not update the lock icon itself until
         * we are done loading, it is slightly more secure this way.
         */
        @Override
        public void onLoadResource(WebView view, String url) {
            if (url != null && url.length() > 0) {
                // It is only if the page claims to be secure
                // that we may have to update the lock:
                if (mLockIconType == LOCK_ICON_SECURE) {
                    // If NOT a 'safe' url, change the lock to mixed content!
                    if (!(URLUtil.isHttpsUrl(url) || URLUtil.isDataUrl(url) || URLUtil.isAboutUrl(url))) {
                        mLockIconType = LOCK_ICON_MIXED;
                        if (LOGV_ENABLED) {
                            Log.v(LOGTAG, "BrowserActivity.updateLockIcon:" +
                                  " updated lock icon to " + mLockIconType + " due to " + url);
                        }
                    }
                }
            }
        }

        /**
         * Show the dialog, asking the user if they would like to continue after
         * an excessive number of HTTP redirects.
         */
        @Override
        public void onTooManyRedirects(WebView view, final Message cancelMsg,
                final Message continueMsg) {
            new AlertDialog.Builder(BrowserActivity.this)
                .setTitle(R.string.browserFrameRedirect)
                .setMessage(R.string.browserFrame307Post)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        continueMsg.sendToTarget();
                    }})
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        cancelMsg.sendToTarget();
                    }})
                .setOnCancelListener(new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        cancelMsg.sendToTarget();
                    }})
                .show();
        }

        // Container class for the next error dialog that needs to be
        // displayed.
        class ErrorDialog {
            public final int mTitle;
            public final String mDescription;
            public final int mError;
            ErrorDialog(int title, String desc, int error) {
                mTitle = title;
                mDescription = desc;
                mError = error;
            }
        };

        private void processNextError() {
            if (mQueuedErrors == null) {
                return;
            }
            // The first one is currently displayed so just remove it.
            mQueuedErrors.removeFirst();
            if (mQueuedErrors.size() == 0) {
                mQueuedErrors = null;
                return;
            }
            showError(mQueuedErrors.getFirst());
        }

        private DialogInterface.OnDismissListener mDialogListener =
                new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface d) {
                        processNextError();
                    }
                };
        private LinkedList<ErrorDialog> mQueuedErrors;

        private void queueError(int err, String desc) {
            if (mQueuedErrors == null) {
                mQueuedErrors = new LinkedList<ErrorDialog>();
            }
            for (ErrorDialog d : mQueuedErrors) {
                if (d.mError == err) {
                    // Already saw a similar error, ignore the new one.
                    return;
                }
            }
            ErrorDialog errDialog = new ErrorDialog(
                    err == WebViewClient.ERROR_FILE_NOT_FOUND ?
                    R.string.browserFrameFileErrorLabel :
                    R.string.browserFrameNetworkErrorLabel,
                    desc, err);
            mQueuedErrors.addLast(errDialog);

            // Show the dialog now if the queue was empty.
            if (mQueuedErrors.size() == 1) {
                showError(errDialog);
            }
        }

        private void showError(ErrorDialog errDialog) {
            AlertDialog d = new AlertDialog.Builder(BrowserActivity.this)
                    .setTitle(errDialog.mTitle)
                    .setMessage(errDialog.mDescription)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            d.setOnDismissListener(mDialogListener);
            d.show();
        }

        /**
         * Show a dialog informing the user of the network error reported by
         * WebCore.
         */
        @Override
        public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            if (errorCode != WebViewClient.ERROR_HOST_LOOKUP &&
                    errorCode != WebViewClient.ERROR_CONNECT &&
                    errorCode != WebViewClient.ERROR_BAD_URL &&
                    errorCode != WebViewClient.ERROR_UNSUPPORTED_SCHEME &&
                    errorCode != WebViewClient.ERROR_FILE) {
                queueError(errorCode, description);
            }
            Log.e(LOGTAG, "onReceivedError " + errorCode + " " + failingUrl
                    + " " + description);

            // We need to reset the title after an error.
            resetTitleAndRevertLockIcon();
        }

        /**
         * Check with the user if it is ok to resend POST data as the page they
         * are trying to navigate to is the result of a POST.
         */
        @Override
        public void onFormResubmission(WebView view, final Message dontResend,
                                       final Message resend) {
            new AlertDialog.Builder(BrowserActivity.this)
                .setTitle(R.string.browserFrameFormResubmitLabel)
                .setMessage(R.string.browserFrameFormResubmitMessage)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        resend.sendToTarget();
                    }})
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dontResend.sendToTarget();
                    }})
                .setOnCancelListener(new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dontResend.sendToTarget();
                    }})
                .show();
        }

        /**
         * Insert the url into the visited history database.
         * @param url The url to be inserted.
         * @param isReload True if this url is being reloaded.
         * FIXME: Not sure what to do when reloading the page.
         */
        @Override
        public void doUpdateVisitedHistory(WebView view, String url,
                boolean isReload) {
            if (url.regionMatches(true, 0, "about:", 0, 6)) {
                return;
            }
            Browser.updateVisitedHistory(mResolver, url, true);
            WebIconDatabase.getInstance().retainIconForPageUrl(url);
        }

        /**
         * Displays SSL error(s) dialog to the user.
         */
        @Override
        public void onReceivedSslError(
            final WebView view, final SslErrorHandler handler, final SslError error) {

            if (mSettings.showSecurityWarnings()) {
                final LayoutInflater factory =
                    LayoutInflater.from(BrowserActivity.this);
                final View warningsView =
                    factory.inflate(R.layout.ssl_warnings, null);
                final LinearLayout placeholder =
                    (LinearLayout)warningsView.findViewById(R.id.placeholder);

                if (error.hasError(SslError.SSL_UNTRUSTED)) {
                    LinearLayout ll = (LinearLayout)factory
                        .inflate(R.layout.ssl_warning, null);
                    ((TextView)ll.findViewById(R.id.warning))
                        .setText(R.string.ssl_untrusted);
                    placeholder.addView(ll);
                }

                if (error.hasError(SslError.SSL_IDMISMATCH)) {
                    LinearLayout ll = (LinearLayout)factory
                        .inflate(R.layout.ssl_warning, null);
                    ((TextView)ll.findViewById(R.id.warning))
                        .setText(R.string.ssl_mismatch);
                    placeholder.addView(ll);
                }

                if (error.hasError(SslError.SSL_EXPIRED)) {
                    LinearLayout ll = (LinearLayout)factory
                        .inflate(R.layout.ssl_warning, null);
                    ((TextView)ll.findViewById(R.id.warning))
                        .setText(R.string.ssl_expired);
                    placeholder.addView(ll);
                }

                if (error.hasError(SslError.SSL_NOTYETVALID)) {
                    LinearLayout ll = (LinearLayout)factory
                        .inflate(R.layout.ssl_warning, null);
                    ((TextView)ll.findViewById(R.id.warning))
                        .setText(R.string.ssl_not_yet_valid);
                    placeholder.addView(ll);
                }

                new AlertDialog.Builder(BrowserActivity.this)
                    .setTitle(R.string.security_warning)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setView(warningsView)
                    .setPositiveButton(R.string.ssl_continue,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    handler.proceed();
                                }
                            })
                    .setNeutralButton(R.string.view_certificate,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    showSSLCertificateOnError(view, handler, error);
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    handler.cancel();
                                    BrowserActivity.this.resetTitleAndRevertLockIcon();
                                }
                            })
                    .setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                public void onCancel(DialogInterface dialog) {
                                    handler.cancel();
                                    BrowserActivity.this.resetTitleAndRevertLockIcon();
                                }
                            })
                    .show();
            } else {
                handler.proceed();
            }
        }

        /**
         * Handles an HTTP authentication request.
         *
         * @param handler The authentication handler
         * @param host The host
         * @param realm The realm
         */
        @Override
        public void onReceivedHttpAuthRequest(WebView view,
                final HttpAuthHandler handler, final String host, final String realm) {
            String username = null;
            String password = null;

            boolean reuseHttpAuthUsernamePassword =
                handler.useHttpAuthUsernamePassword();

            if (reuseHttpAuthUsernamePassword &&
                    (mTabControl.getCurrentWebView() != null)) {
                String[] credentials =
                        mTabControl.getCurrentWebView()
                                .getHttpAuthUsernamePassword(host, realm);
                if (credentials != null && credentials.length == 2) {
                    username = credentials[0];
                    password = credentials[1];
                }
            }

            if (username != null && password != null) {
                handler.proceed(username, password);
            } else {
                showHttpAuthentication(handler, host, realm, null, null, null, 0);
            }
        }

        @Override
        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            if (mMenuIsDown) {
                // only check shortcut key when MENU is held
                return getWindow().isShortcutKey(event.getKeyCode(), event);
            } else {
                return false;
            }
        }

        @Override
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            if (view != mTabControl.getCurrentTopWebView()) {
                return;
            }
            if (event.isDown()) {
                BrowserActivity.this.onKeyDown(event.getKeyCode(), event);
            } else {
                BrowserActivity.this.onKeyUp(event.getKeyCode(), event);
            }
        }
    };

    //--------------------------------------------------------------------------
    // WebChromeClient implementation
    //--------------------------------------------------------------------------

    /* package */ WebChromeClient getWebChromeClient() {
        return mWebChromeClient;
    }

    private final WebChromeClient mWebChromeClient = new WebChromeClient() {
        // Helper method to create a new tab or sub window.
        private void createWindow(final boolean dialog, final Message msg) {
            if (dialog) {
                mTabControl.createSubWindow();
                final TabControl.Tab t = mTabControl.getCurrentTab();
                attachSubWindow(t);
                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) msg.obj;
                transport.setWebView(t.getSubWebView());
                msg.sendToTarget();
            } else {
                final TabControl.Tab parent = mTabControl.getCurrentTab();
                final TabControl.Tab newTab
                        = openTabAndShow(EMPTY_URL_DATA, false, null);
                if (newTab != parent) {
                    parent.addChildTab(newTab);
                }
                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) msg.obj;
                transport.setWebView(mTabControl.getCurrentWebView());
                msg.sendToTarget();
            }
        }

        @Override
        public void onChangeViewingMode(WebView view, int newViewingMode) {
            if (!CUSTOM_BROWSER_BAR || view != getTopWindow()) {
                return;
            }
            switch (newViewingMode) {
            case WebView.NO_VIEWING_MODE:
                break;
            case WebView.OVERVIEW_MODE:
            case WebView.READING_MODE_WITH_TITLE_BAR:
            case WebView.TITLE_BAR_DISMISS_MODE:
                mTitleBar.setVisibility(View.VISIBLE);
                break;
            case WebView.READING_MODE:
                mTitleBar.setVisibility(View.GONE);
                break;
            default:
                break;
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, final boolean dialog,
                final boolean userGesture, final Message resultMsg) {
            // Short-circuit if we can't create any more tabs or sub windows.
            if (dialog && mTabControl.getCurrentSubWindow() != null) {
                new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle(R.string.too_many_subwindows_dialog_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.too_many_subwindows_dialog_message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                return false;
            } else if (mTabControl.getTabCount() >= TabControl.MAX_TABS) {
                new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle(R.string.too_many_windows_dialog_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.too_many_windows_dialog_message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                return false;
            }

            // Short-circuit if this was a user gesture.
            if (userGesture) {
                createWindow(dialog, resultMsg);
                return true;
            }

            // Allow the popup and create the appropriate window.
            final AlertDialog.OnClickListener allowListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d,
                                int which) {
                            createWindow(dialog, resultMsg);
                        }
                    };

            // Block the popup by returning a null WebView.
            final AlertDialog.OnClickListener blockListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            resultMsg.sendToTarget();
                        }
                    };

            // Build a confirmation dialog to display to the user.
            final AlertDialog d =
                    new AlertDialog.Builder(BrowserActivity.this)
                    .setTitle(R.string.attention)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.popup_window_attempt)
                    .setPositiveButton(R.string.allow, allowListener)
                    .setNegativeButton(R.string.block, blockListener)
                    .setCancelable(false)
                    .create();

            // Show the confirmation dialog.
            d.show();
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            final TabControl.Tab current = mTabControl.getCurrentTab();
            final TabControl.Tab parent = current.getParentTab();
            if (parent != null) {
                // JavaScript can only close popup window.
                switchToTab(mTabControl.getTabIndex(parent));
                // Now we need to close the window
                closeTab(current);
            }
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (CUSTOM_BROWSER_BAR) {
                mTitleBar.setProgress(newProgress, view);
            } else {
                getWindow().setFeatureInt(Window.FEATURE_PROGRESS,
                        newProgress * 100);
            }

            if (newProgress == 100) {
                // onProgressChanged() is called for sub-frame too while
                // onPageFinished() is only called for the main frame. sync
                // cookie and cache promptly here.
                CookieSyncManager.getInstance().sync();
                if (mInLoad) {
                    mInLoad = false;
                    updateInLoadMenuItems();
                }
            } else {
                // onPageFinished may have already been called but a subframe
                // is still loading and updating the progress. Reset mInLoad
                // and update the menu items.
                if (!mInLoad) {
                    mInLoad = true;
                    updateInLoadMenuItems();
                }
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            String url = view.getUrl();

            // here, if url is null, we want to reset the title
            setUrlTitle(url, title, view);

            if (url == null ||
                url.length() >= SQLiteDatabase.SQLITE_MAX_LIKE_PATTERN_LENGTH) {
                return;
            }
            // See if we can find the current url in our history database and
            // add the new title to it.
            if (url.startsWith("http://www.")) {
                url = url.substring(11);
            } else if (url.startsWith("http://")) {
                url = url.substring(4);
            }
            try {
                url = "%" + url;
                String [] selArgs = new String[] { url };

                String where = Browser.BookmarkColumns.URL + " LIKE ? AND "
                        + Browser.BookmarkColumns.BOOKMARK + " = 0";
                Cursor c = mResolver.query(Browser.BOOKMARKS_URI,
                    Browser.HISTORY_PROJECTION, where, selArgs, null);
                if (c.moveToFirst()) {
                    // Current implementation of database only has one entry per
                    // url.
                    ContentValues map = new ContentValues();
                    map.put(Browser.BookmarkColumns.TITLE, title);
                    mResolver.update(Browser.BOOKMARKS_URI, map,
                            "_id = " + c.getInt(0), null);
                }
                c.close();
            } catch (IllegalStateException e) {
                Log.e(LOGTAG, "BrowserActivity onReceived title", e);
            } catch (SQLiteException ex) {
                Log.e(LOGTAG, "onReceivedTitle() caught SQLiteException: ", ex);
            }
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            updateIcon(view, icon);
        }

        @Override
        public void onReceivedTouchIconUrl(WebView view, String url) {
            final ContentResolver cr = getContentResolver();
            final Cursor c =
                    BrowserBookmarksAdapter.queryBookmarksForUrl(cr,
                            view.getOriginalUrl(), view.getUrl(), true);
            if (c != null) {
                if (c.getCount() > 0) {
                    new DownloadTouchIcon(cr, c, view).execute(url);
                } else {
                    c.close();
                }
            }
        }

        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            if (mCustomView != null)
                return;

            // Add the custom view to its container.
            mCustomViewContainer.addView(view, COVER_SCREEN_GRAVITY_CENTER);
            mCustomView = view;
            mCustomViewCallback = callback;
            // Save the menu state and set it to empty while the custom
            // view is showing.
            mOldMenuState = mMenuState;
            mMenuState = EMPTY_MENU;
            // Hide the content view.
            mContentView.setVisibility(View.GONE);
            // Finally show the custom view container.
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
            // Reset the old menu state.
            mMenuState = mOldMenuState;
            mOldMenuState = EMPTY_MENU;
            mCustomViewContainer.setVisibility(View.GONE);
            mCustomViewCallback.onCustomViewHidden();
            // Show the content view.
            mContentView.setVisibility(View.VISIBLE);
        }

        /**
         * The origin has exceeded its database quota.
         * @param url the URL that exceeded the quota
         * @param databaseIdentifier the identifier of the database on
         *     which the transaction that caused the quota overflow was run
         * @param currentQuota the current quota for the origin.
         * @param estimatedSize the estimated size of the database.
         * @param totalUsedQuota is the sum of all origins' quota.
         * @param quotaUpdater The callback to run when a decision to allow or
         *     deny quota has been made. Don't forget to call this!
         */
        @Override
        public void onExceededDatabaseQuota(String url,
            String databaseIdentifier, long currentQuota, long estimatedSize,
            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
            mSettings.getWebStorageSizeManager().onExceededDatabaseQuota(
                    url, databaseIdentifier, currentQuota, estimatedSize,
                    totalUsedQuota, quotaUpdater);
        }

        /**
         * The Application Cache has exceeded its max size.
         * @param spaceNeeded is the amount of disk space that would be needed
         * in order for the last appcache operation to succeed.
         * @param totalUsedQuota is the sum of all origins' quota.
         * @param quotaUpdater A callback to inform the WebCore thread that a new
         * app cache size is available. This callback must always be executed at
         * some point to ensure that the sleeping WebCore thread is woken up.
         */
        @Override
        public void onReachedMaxAppCacheSize(long spaceNeeded,
                long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
            mSettings.getWebStorageSizeManager().onReachedMaxAppCacheSize(
                    spaceNeeded, totalUsedQuota, quotaUpdater);
        }

        /**
         * Instructs the browser to show a prompt to ask the user to set the
         * Geolocation permission state for the specified origin.
         * @param origin The origin for which Geolocation permissions are
         *     requested.
         * @param callback The callback to call once the user has set the
         *     Geolocation permission state.
         */
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
            mTabControl.getCurrentTab().getGeolocationPermissionsPrompt().show(
                    origin, callback);
        }

        /**
         * Instructs the browser to hide the Geolocation permissions prompt.
         */
        @Override
        public void onGeolocationPermissionsHidePrompt() {
            mTabControl.getCurrentTab().getGeolocationPermissionsPrompt().hide();
        }

        /* Adds a JavaScript error message to the system log.
         * @param message The error message to report.
         * @param lineNumber The line number of the error.
         * @param sourceID The name of the source file that caused the error.
         */
        @Override
        public void addMessageToConsole(String message, int lineNumber, String sourceID) {
            ErrorConsoleView errorConsole = mTabControl.getCurrentErrorConsole(true);
            errorConsole.addErrorMessage(message, sourceID, lineNumber);
                if (mShouldShowErrorConsole &&
                        errorConsole.getShowState() != ErrorConsoleView.SHOW_MAXIMIZED) {
                    errorConsole.showConsole(ErrorConsoleView.SHOW_MINIMIZED);
                }
            Log.w(LOGTAG, "Console: " + message + " " + sourceID + ":" + lineNumber);
        }
    };

    /**
     * Notify the host application a download should be done, or that
     * the data should be streamed if a streaming viewer is available.
     * @param url The full url to the content that should be downloaded
     * @param contentDisposition Content-disposition http header, if
     *                           present.
     * @param mimetype The mimetype of the content reported by the server
     * @param contentLength The file size reported by the server
     */
    public void onDownloadStart(String url, String userAgent,
            String contentDisposition, String mimetype, long contentLength) {
        // if we're dealing wih A/V content that's not explicitly marked
        //     for download, check if it's streamable.
        if (contentDisposition == null
                || !contentDisposition.regionMatches(
                        true, 0, "attachment", 0, 10)) {
            // query the package manager to see if there's a registered handler
            //     that matches.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), mimetype);
            ResolveInfo info = getPackageManager().resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null) {
                ComponentName myName = getComponentName();
                // If we resolved to ourselves, we don't want to attempt to
                // load the url only to try and download it again.
                if (!myName.getPackageName().equals(
                        info.activityInfo.packageName)
                        || !myName.getClassName().equals(
                                info.activityInfo.name)) {
                    // someone (other than us) knows how to handle this mime
                    // type with this scheme, don't download.
                    try {
                        startActivity(intent);
                        return;
                    } catch (ActivityNotFoundException ex) {
                        if (LOGD_ENABLED) {
                            Log.d(LOGTAG, "activity not found for " + mimetype
                                    + " over " + Uri.parse(url).getScheme(),
                                    ex);
                        }
                        // Best behavior is to fall back to a download in this
                        // case
                    }
                }
            }
        }
        onDownloadStartNoStream(url, userAgent, contentDisposition, mimetype, contentLength);
    }

    /**
     * Notify the host application a download should be done, even if there
     * is a streaming viewer available for thise type.
     * @param url The full url to the content that should be downloaded
     * @param contentDisposition Content-disposition http header, if
     *                           present.
     * @param mimetype The mimetype of the content reported by the server
     * @param contentLength The file size reported by the server
     */
    /*package */ void onDownloadStartNoStream(String url, String userAgent,
            String contentDisposition, String mimetype, long contentLength) {

        String filename = URLUtil.guessFileName(url,
                contentDisposition, mimetype);

        // Check to see if we have an SDCard
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            int title;
            String msg;

            // Check to see if the SDCard is busy, same as the music app
            if (status.equals(Environment.MEDIA_SHARED)) {
                msg = getString(R.string.download_sdcard_busy_dlg_msg);
                title = R.string.download_sdcard_busy_dlg_title;
            } else {
                msg = getString(R.string.download_no_sdcard_dlg_msg, filename);
                title = R.string.download_no_sdcard_dlg_title;
            }

            new AlertDialog.Builder(this)
                .setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, null)
                .show();
            return;
        }

        // java.net.URI is a lot stricter than KURL so we have to undo
        // KURL's percent-encoding and redo the encoding using java.net.URI.
        URI uri = null;
        try {
            // Undo the percent-encoding that KURL may have done.
            String newUrl = new String(URLUtil.decode(url.getBytes()));
            // Parse the url into pieces
            WebAddress w = new WebAddress(newUrl);
            String frag = null;
            String query = null;
            String path = w.mPath;
            // Break the path into path, query, and fragment
            if (path.length() > 0) {
                // Strip the fragment
                int idx = path.lastIndexOf('#');
                if (idx != -1) {
                    frag = path.substring(idx + 1);
                    path = path.substring(0, idx);
                }
                idx = path.lastIndexOf('?');
                if (idx != -1) {
                    query = path.substring(idx + 1);
                    path = path.substring(0, idx);
                }
            }
            uri = new URI(w.mScheme, w.mAuthInfo, w.mHost, w.mPort, path,
                    query, frag);
        } catch (Exception e) {
            Log.e(LOGTAG, "Could not parse url for download: " + url, e);
            return;
        }

        // XXX: Have to use the old url since the cookies were stored using the
        // old percent-encoded url.
        String cookies = CookieManager.getInstance().getCookie(url);

        ContentValues values = new ContentValues();
        values.put(Downloads.COLUMN_URI, uri.toString());
        values.put(Downloads.COLUMN_COOKIE_DATA, cookies);
        values.put(Downloads.COLUMN_USER_AGENT, userAgent);
        values.put(Downloads.COLUMN_NOTIFICATION_PACKAGE,
                getPackageName());
        values.put(Downloads.COLUMN_NOTIFICATION_CLASS,
                BrowserDownloadPage.class.getCanonicalName());
        values.put(Downloads.COLUMN_VISIBILITY, Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        values.put(Downloads.COLUMN_MIME_TYPE, mimetype);
        values.put(Downloads.COLUMN_FILE_NAME_HINT, filename);
        values.put(Downloads.COLUMN_DESCRIPTION, uri.getHost());
        if (contentLength > 0) {
            values.put(Downloads.COLUMN_TOTAL_BYTES, contentLength);
        }
        if (mimetype == null) {
            // We must have long pressed on a link or image to download it. We
            // are not sure of the mimetype in this case, so do a head request
            new FetchUrlMimeType(this).execute(values);
        } else {
            final Uri contentUri =
                    getContentResolver().insert(Downloads.CONTENT_URI, values);
            viewDownloads(contentUri);
        }

    }

    /**
     * Resets the lock icon. This method is called when we start a new load and
     * know the url to be loaded.
     */
    private void resetLockIcon(String url) {
        // Save the lock-icon state (we revert to it if the load gets cancelled)
        saveLockIcon();

        mLockIconType = LOCK_ICON_UNSECURE;
        if (URLUtil.isHttpsUrl(url)) {
            mLockIconType = LOCK_ICON_SECURE;
            if (LOGV_ENABLED) {
                Log.v(LOGTAG, "BrowserActivity.resetLockIcon:" +
                      " reset lock icon to " + mLockIconType);
            }
        }

        updateLockIconImage(LOCK_ICON_UNSECURE);
    }

    /**
     * Resets the lock icon.  This method is called when the icon needs to be
     * reset but we do not know whether we are loading a secure or not secure
     * page.
     */
    private void resetLockIcon() {
        // Save the lock-icon state (we revert to it if the load gets cancelled)
        saveLockIcon();

        mLockIconType = LOCK_ICON_UNSECURE;

        if (LOGV_ENABLED) {
          Log.v(LOGTAG, "BrowserActivity.resetLockIcon:" +
                " reset lock icon to " + mLockIconType);
        }

        updateLockIconImage(LOCK_ICON_UNSECURE);
    }

    /**
     * Updates the lock-icon image in the title-bar.
     */
    private void updateLockIconImage(int lockIconType) {
        Drawable d = null;
        if (lockIconType == LOCK_ICON_SECURE) {
            d = mSecLockIcon;
        } else if (lockIconType == LOCK_ICON_MIXED) {
            d = mMixLockIcon;
        }
        if (CUSTOM_BROWSER_BAR) {
            mTitleBar.setLock(d, getTopWindow());
        } else {
            getWindow().setFeatureDrawable(Window.FEATURE_RIGHT_ICON, d);
        }
    }

    /**
     * Displays a page-info dialog.
     * @param tab The tab to show info about
     * @param fromShowSSLCertificateOnError The flag that indicates whether
     * this dialog was opened from the SSL-certificate-on-error dialog or
     * not. This is important, since we need to know whether to return to
     * the parent dialog or simply dismiss.
     */
    private void showPageInfo(final TabControl.Tab tab,
                              final boolean fromShowSSLCertificateOnError) {
        final LayoutInflater factory = LayoutInflater
                .from(this);

        final View pageInfoView = factory.inflate(R.layout.page_info, null);

        final WebView view = tab.getWebView();

        String url = null;
        String title = null;

        if (view == null) {
            url = tab.getUrl();
            title = tab.getTitle();
        } else if (view == mTabControl.getCurrentWebView()) {
             // Use the cached title and url if this is the current WebView
            url = mUrl;
            title = mTitle;
        } else {
            url = view.getUrl();
            title = view.getTitle();
        }

        if (url == null) {
            url = "";
        }
        if (title == null) {
            title = "";
        }

        ((TextView) pageInfoView.findViewById(R.id.address)).setText(url);
        ((TextView) pageInfoView.findViewById(R.id.title)).setText(title);

        mPageInfoView = tab;
        mPageInfoFromShowSSLCertificateOnError = new Boolean(fromShowSSLCertificateOnError);

        AlertDialog.Builder alertDialogBuilder =
            new AlertDialog.Builder(this)
            .setTitle(R.string.page_info).setIcon(android.R.drawable.ic_dialog_info)
            .setView(pageInfoView)
            .setPositiveButton(
                R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        mPageInfoDialog = null;
                        mPageInfoView = null;
                        mPageInfoFromShowSSLCertificateOnError = null;

                        // if we came here from the SSL error dialog
                        if (fromShowSSLCertificateOnError) {
                            // go back to the SSL error dialog
                            showSSLCertificateOnError(
                                mSSLCertificateOnErrorView,
                                mSSLCertificateOnErrorHandler,
                                mSSLCertificateOnErrorError);
                        }
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        mPageInfoDialog = null;
                        mPageInfoView = null;
                        mPageInfoFromShowSSLCertificateOnError = null;

                        // if we came here from the SSL error dialog
                        if (fromShowSSLCertificateOnError) {
                            // go back to the SSL error dialog
                            showSSLCertificateOnError(
                                mSSLCertificateOnErrorView,
                                mSSLCertificateOnErrorHandler,
                                mSSLCertificateOnErrorError);
                        }
                    }
                });

        // if we have a main top-level page SSL certificate set or a certificate
        // error
        if (fromShowSSLCertificateOnError ||
                (view != null && view.getCertificate() != null)) {
            // add a 'View Certificate' button
            alertDialogBuilder.setNeutralButton(
                R.string.view_certificate,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        mPageInfoDialog = null;
                        mPageInfoView = null;
                        mPageInfoFromShowSSLCertificateOnError = null;

                        // if we came here from the SSL error dialog
                        if (fromShowSSLCertificateOnError) {
                            // go back to the SSL error dialog
                            showSSLCertificateOnError(
                                mSSLCertificateOnErrorView,
                                mSSLCertificateOnErrorHandler,
                                mSSLCertificateOnErrorError);
                        } else {
                            // otherwise, display the top-most certificate from
                            // the chain
                            if (view.getCertificate() != null) {
                                showSSLCertificate(tab);
                            }
                        }
                    }
                });
        }

        mPageInfoDialog = alertDialogBuilder.show();
    }

       /**
     * Displays the main top-level page SSL certificate dialog
     * (accessible from the Page-Info dialog).
     * @param tab The tab to show certificate for.
     */
    private void showSSLCertificate(final TabControl.Tab tab) {
        final View certificateView =
                inflateCertificateView(tab.getWebView().getCertificate());
        if (certificateView == null) {
            return;
        }

        LayoutInflater factory = LayoutInflater.from(this);

        final LinearLayout placeholder =
                (LinearLayout)certificateView.findViewById(R.id.placeholder);

        LinearLayout ll = (LinearLayout) factory.inflate(
            R.layout.ssl_success, placeholder);
        ((TextView)ll.findViewById(R.id.success))
            .setText(R.string.ssl_certificate_is_valid);

        mSSLCertificateView = tab;
        mSSLCertificateDialog =
            new AlertDialog.Builder(this)
                .setTitle(R.string.ssl_certificate).setIcon(
                    R.drawable.ic_dialog_browser_certificate_secure)
                .setView(certificateView)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                mSSLCertificateDialog = null;
                                mSSLCertificateView = null;

                                showPageInfo(tab, false);
                            }
                        })
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                mSSLCertificateDialog = null;
                                mSSLCertificateView = null;

                                showPageInfo(tab, false);
                            }
                        })
                .show();
    }

    /**
     * Displays the SSL error certificate dialog.
     * @param view The target web-view.
     * @param handler The SSL error handler responsible for cancelling the
     * connection that resulted in an SSL error or proceeding per user request.
     * @param error The SSL error object.
     */
    private void showSSLCertificateOnError(
        final WebView view, final SslErrorHandler handler, final SslError error) {

        final View certificateView =
            inflateCertificateView(error.getCertificate());
        if (certificateView == null) {
            return;
        }

        LayoutInflater factory = LayoutInflater.from(this);

        final LinearLayout placeholder =
                (LinearLayout)certificateView.findViewById(R.id.placeholder);

        if (error.hasError(SslError.SSL_UNTRUSTED)) {
            LinearLayout ll = (LinearLayout)factory
                .inflate(R.layout.ssl_warning, placeholder);
            ((TextView)ll.findViewById(R.id.warning))
                .setText(R.string.ssl_untrusted);
        }

        if (error.hasError(SslError.SSL_IDMISMATCH)) {
            LinearLayout ll = (LinearLayout)factory
                .inflate(R.layout.ssl_warning, placeholder);
            ((TextView)ll.findViewById(R.id.warning))
                .setText(R.string.ssl_mismatch);
        }

        if (error.hasError(SslError.SSL_EXPIRED)) {
            LinearLayout ll = (LinearLayout)factory
                .inflate(R.layout.ssl_warning, placeholder);
            ((TextView)ll.findViewById(R.id.warning))
                .setText(R.string.ssl_expired);
        }

        if (error.hasError(SslError.SSL_NOTYETVALID)) {
            LinearLayout ll = (LinearLayout)factory
                .inflate(R.layout.ssl_warning, placeholder);
            ((TextView)ll.findViewById(R.id.warning))
                .setText(R.string.ssl_not_yet_valid);
        }

        mSSLCertificateOnErrorHandler = handler;
        mSSLCertificateOnErrorView = view;
        mSSLCertificateOnErrorError = error;
        mSSLCertificateOnErrorDialog =
            new AlertDialog.Builder(this)
                .setTitle(R.string.ssl_certificate).setIcon(
                    R.drawable.ic_dialog_browser_certificate_partially_secure)
                .setView(certificateView)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                mSSLCertificateOnErrorDialog = null;
                                mSSLCertificateOnErrorView = null;
                                mSSLCertificateOnErrorHandler = null;
                                mSSLCertificateOnErrorError = null;

                                mWebViewClient.onReceivedSslError(
                                    view, handler, error);
                            }
                        })
                 .setNeutralButton(R.string.page_info_view,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                mSSLCertificateOnErrorDialog = null;

                                // do not clear the dialog state: we will
                                // need to show the dialog again once the
                                // user is done exploring the page-info details

                                showPageInfo(mTabControl.getTabFromView(view),
                                        true);
                            }
                        })
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                mSSLCertificateOnErrorDialog = null;
                                mSSLCertificateOnErrorView = null;
                                mSSLCertificateOnErrorHandler = null;
                                mSSLCertificateOnErrorError = null;

                                mWebViewClient.onReceivedSslError(
                                    view, handler, error);
                            }
                        })
                .show();
    }

    /**
     * Inflates the SSL certificate view (helper method).
     * @param certificate The SSL certificate.
     * @return The resultant certificate view with issued-to, issued-by,
     * issued-on, expires-on, and possibly other fields set.
     * If the input certificate is null, returns null.
     */
    private View inflateCertificateView(SslCertificate certificate) {
        if (certificate == null) {
            return null;
        }

        LayoutInflater factory = LayoutInflater.from(this);

        View certificateView = factory.inflate(
            R.layout.ssl_certificate, null);

        // issued to:
        SslCertificate.DName issuedTo = certificate.getIssuedTo();
        if (issuedTo != null) {
            ((TextView) certificateView.findViewById(R.id.to_common))
                .setText(issuedTo.getCName());
            ((TextView) certificateView.findViewById(R.id.to_org))
                .setText(issuedTo.getOName());
            ((TextView) certificateView.findViewById(R.id.to_org_unit))
                .setText(issuedTo.getUName());
        }

        // issued by:
        SslCertificate.DName issuedBy = certificate.getIssuedBy();
        if (issuedBy != null) {
            ((TextView) certificateView.findViewById(R.id.by_common))
                .setText(issuedBy.getCName());
            ((TextView) certificateView.findViewById(R.id.by_org))
                .setText(issuedBy.getOName());
            ((TextView) certificateView.findViewById(R.id.by_org_unit))
                .setText(issuedBy.getUName());
        }

        // issued on:
        String issuedOn = reformatCertificateDate(
            certificate.getValidNotBefore());
        ((TextView) certificateView.findViewById(R.id.issued_on))
            .setText(issuedOn);

        // expires on:
        String expiresOn = reformatCertificateDate(
            certificate.getValidNotAfter());
        ((TextView) certificateView.findViewById(R.id.expires_on))
            .setText(expiresOn);

        return certificateView;
    }

    /**
     * Re-formats the certificate date (Date.toString()) string to
     * a properly localized date string.
     * @return Properly localized version of the certificate date string and
     * the original certificate date string if fails to localize.
     * If the original string is null, returns an empty string "".
     */
    private String reformatCertificateDate(String certificateDate) {
      String reformattedDate = null;

      if (certificateDate != null) {
          Date date = null;
          try {
              date = java.text.DateFormat.getInstance().parse(certificateDate);
          } catch (ParseException e) {
              date = null;
          }

          if (date != null) {
              reformattedDate =
                  DateFormat.getDateFormat(this).format(date);
          }
      }

      return reformattedDate != null ? reformattedDate :
          (certificateDate != null ? certificateDate : "");
    }

    /**
     * Displays an http-authentication dialog.
     */
    private void showHttpAuthentication(final HttpAuthHandler handler,
            final String host, final String realm, final String title,
            final String name, final String password, int focusId) {
        LayoutInflater factory = LayoutInflater.from(this);
        final View v = factory
                .inflate(R.layout.http_authentication, null);
        if (name != null) {
            ((EditText) v.findViewById(R.id.username_edit)).setText(name);
        }
        if (password != null) {
            ((EditText) v.findViewById(R.id.password_edit)).setText(password);
        }

        String titleText = title;
        if (titleText == null) {
            titleText = getText(R.string.sign_in_to).toString().replace(
                    "%s1", host).replace("%s2", realm);
        }

        mHttpAuthHandler = handler;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleText)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setView(v)
                .setPositiveButton(R.string.action,
                        new DialogInterface.OnClickListener() {
                             public void onClick(DialogInterface dialog,
                                     int whichButton) {
                                String nm = ((EditText) v
                                        .findViewById(R.id.username_edit))
                                        .getText().toString();
                                String pw = ((EditText) v
                                        .findViewById(R.id.password_edit))
                                        .getText().toString();
                                BrowserActivity.this.setHttpAuthUsernamePassword
                                        (host, realm, nm, pw);
                                handler.proceed(nm, pw);
                                mHttpAuthenticationDialog = null;
                                mHttpAuthHandler = null;
                            }})
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                handler.cancel();
                                BrowserActivity.this.resetTitleAndRevertLockIcon();
                                mHttpAuthenticationDialog = null;
                                mHttpAuthHandler = null;
                            }})
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            handler.cancel();
                            BrowserActivity.this.resetTitleAndRevertLockIcon();
                            mHttpAuthenticationDialog = null;
                            mHttpAuthHandler = null;
                        }})
                .create();
        // Make the IME appear when the dialog is displayed if applicable.
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        if (focusId != 0) {
            dialog.findViewById(focusId).requestFocus();
        } else {
            v.findViewById(R.id.username_edit).requestFocus();
        }
        mHttpAuthenticationDialog = dialog;
    }

    public int getProgress() {
        WebView w = mTabControl.getCurrentWebView();
        if (w != null) {
            return w.getProgress();
        } else {
            return 100;
        }
    }

    /**
     * Set HTTP authentication password.
     *
     * @param host The host for the password
     * @param realm The realm for the password
     * @param username The username for the password. If it is null, it means
     *            password can't be saved.
     * @param password The password
     */
    public void setHttpAuthUsernamePassword(String host, String realm,
                                            String username,
                                            String password) {
        WebView w = mTabControl.getCurrentWebView();
        if (w != null) {
            w.setHttpAuthUsernamePassword(host, realm, username, password);
        }
    }

    /**
     * connectivity manager says net has come or gone... inform the user
     * @param up true if net has come up, false if net has gone down
     */
    public void onNetworkToggle(boolean up) {
        if (up == mIsNetworkUp) {
            return;
        } else if (up) {
            mIsNetworkUp = true;
            if (mAlertDialog != null) {
                mAlertDialog.cancel();
                mAlertDialog = null;
            }
        } else {
            mIsNetworkUp = false;
            if (mInLoad && mAlertDialog == null) {
                mAlertDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.loadSuspendedTitle)
                        .setMessage(R.string.loadSuspended)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }
        WebView w = mTabControl.getCurrentWebView();
        if (w != null) {
            w.setNetworkAvailable(up);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        switch (requestCode) {
            case COMBO_PAGE:
                if (resultCode == RESULT_OK && intent != null) {
                    String data = intent.getAction();
                    Bundle extras = intent.getExtras();
                    if (extras != null && extras.getBoolean("new_window", false)) {
                        final TabControl.Tab newTab = openTab(data);
                        if (mSettings.openInBackground() &&
                                newTab != null) {
                            mTabControl.populatePickerData(newTab);
                            mTabControl.setCurrentTab(newTab);
                            int newIndex = mTabControl.getCurrentIndex();
                            if (CUSTOM_BROWSER_BAR) {
                                mTitleBar.setCurrentTab(newIndex);
                            }
                        }
                    } else {
                        final TabControl.Tab currentTab =
                                mTabControl.getCurrentTab();
                        dismissSubWindow(currentTab);
                        if (data != null && data.length() != 0) {
                            getTopWindow().loadUrl(data);
                        }
                    }
/*
                FIXME: Removing this breaks the behavior of pressing BACK from
                the Go page resulting in the window being closed.  However, it
                needs to be removed so that the user can use the Search bar to
                enter a URL.  Further, the Go behavior is going to change
                drastically, so this behavior may not last anyway.
                } else if (resultCode == RESULT_CANCELED
                        && mCancelGoPageMeansClose) {
                    if (mTabControl.getTabCount() == 1) {
                        // finish the Browser.  When the Browser opens up again,
                        // we will go through onCreate and once again open up
                        // the Go page.
                        finish();
                        return;
                    }
                    closeCurrentWindow();
*/
                }
                break;
            default:
                break;
        }
        mCancelGoPageMeansClose = false;
        if (getTopWindow() != null) {
            getTopWindow().requestFocus();
        }
    }

    /*
     * This method is called as a result of the user selecting the options
     * menu to see the download window, or when a download changes state. It
     * shows the download window ontop of the current window.
     */
    /* package */ void viewDownloads(Uri downloadRecord) {
        Intent intent = new Intent(this,
                BrowserDownloadPage.class);
        intent.setData(downloadRecord);
        startActivityForResult(intent, this.DOWNLOAD_PAGE);

    }

    // True if canceling the "Go" screen should result in closing the current
    // window/browser.
    private boolean mCancelGoPageMeansClose;

    /**
     * Open the Go page.
     * @param startWithHistory If true, open starting on the history tab.
     *                         Otherwise, start with the bookmarks tab.
     * @param cancelGoPageMeansClose Set to true if this came from a new tab, or
     *                               from the only tab, and canceling means to
     *                               close the tab (and possibly the browser)
     */
    /* package */ void bookmarksOrHistoryPicker(boolean startWithHistory,
            boolean cancelGoPageMeansClose) {
        WebView current = mTabControl.getCurrentWebView();
        if (current == null) {
            return;
        }
        Intent intent = new Intent(this,
                CombinedBookmarkHistoryActivity.class);
        String title = current.getTitle();
        String url = current.getUrl();
        // Just in case the user opens bookmarks before a page finishes loading
        // so the current history item, and therefore the page, is null.
        if (null == url) {
            url = mLastEnteredUrl;
            // This can happen.
            if (null == url) {
                url = mSettings.getHomePage();
            }
        }
        // In case the web page has not yet received its associated title.
        if (title == null) {
            title = url;
        }
        intent.putExtra("title", title);
        intent.putExtra("url", url);
        // If this is opening in a new window, then disable opening in a
        // (different) new window.  Also disable it if we have maxed out the
        // windows.
        intent.putExtra("disable_new_window", cancelGoPageMeansClose
                || mTabControl.getTabCount() >= TabControl.MAX_TABS);
        intent.putExtra("touch_icon_url", current.getTouchIconUrl());
        if (startWithHistory) {
            intent.putExtra(CombinedBookmarkHistoryActivity.STARTING_TAB,
                    CombinedBookmarkHistoryActivity.HISTORY_TAB);
        }
        mCancelGoPageMeansClose = cancelGoPageMeansClose;
        startActivityForResult(intent, COMBO_PAGE);
    }

    // Called when loading from context menu or LOAD_URL message
    private void loadURL(WebView view, String url) {
        // In case the user enters nothing.
        if (url != null && url.length() != 0 && view != null) {
            url = smartUrlFilter(url);
            if (!mWebViewClient.shouldOverrideUrlLoading(view, url)) {
                view.loadUrl(url);
            }
        }
    }

    private String smartUrlFilter(Uri inUri) {
        if (inUri != null) {
            return smartUrlFilter(inUri.toString());
        }
        return null;
    }


    // get window count

    int getWindowCount(){
      if(mTabControl != null){
        return mTabControl.getTabCount();
      }
      return 0;
    }

    protected static final Pattern ACCEPTED_URI_SCHEMA = Pattern.compile(
            "(?i)" + // switch on case insensitive matching
            "(" +    // begin group for schema
            "(?:http|https|file):\\/\\/" +
            "|(?:inline|data|about|content|javascript):" +
            ")" +
            "(.*)" );

    /**
     * Attempts to determine whether user input is a URL or search
     * terms.  Anything with a space is passed to search.
     *
     * Converts to lowercase any mistakenly uppercased schema (i.e.,
     * "Http://" converts to "http://"
     *
     * @return Original or modified URL
     *
     */
    String smartUrlFilter(String url) {

        String inUrl = url.trim();
        boolean hasSpace = inUrl.indexOf(' ') != -1;

        Matcher matcher = ACCEPTED_URI_SCHEMA.matcher(inUrl);
        if (matcher.matches()) {
            // force scheme to lowercase
            String scheme = matcher.group(1);
            String lcScheme = scheme.toLowerCase();
            if (!lcScheme.equals(scheme)) {
                inUrl = lcScheme + matcher.group(2);
            }
            if (hasSpace) {
                inUrl = inUrl.replace(" ", "%20");
            }
            return inUrl;
        }
        if (hasSpace) {
            // FIXME: Is this the correct place to add to searches?
            // what if someone else calls this function?
            int shortcut = parseUrlShortcut(inUrl);
            if (shortcut != SHORTCUT_INVALID) {
                Browser.addSearchUrl(mResolver, inUrl);
                String query = inUrl.substring(2);
                switch (shortcut) {
                case SHORTCUT_GOOGLE_SEARCH:
                    return URLUtil.composeSearchUrl(query, QuickSearch_G, QUERY_PLACE_HOLDER);
                case SHORTCUT_WIKIPEDIA_SEARCH:
                    return URLUtil.composeSearchUrl(query, QuickSearch_W, QUERY_PLACE_HOLDER);
                case SHORTCUT_DICTIONARY_SEARCH:
                    return URLUtil.composeSearchUrl(query, QuickSearch_D, QUERY_PLACE_HOLDER);
                case SHORTCUT_GOOGLE_MOBILE_LOCAL_SEARCH:
                    // FIXME: we need location in this case
                    return URLUtil.composeSearchUrl(query, QuickSearch_L, QUERY_PLACE_HOLDER);
                }
            }
        } else {
            if (Regex.WEB_URL_PATTERN.matcher(inUrl).matches()) {
                return URLUtil.guessUrl(inUrl);
            }
        }

        Browser.addSearchUrl(mResolver, inUrl);
        return URLUtil.composeSearchUrl(inUrl, QuickSearch_G, QUERY_PLACE_HOLDER);
    }

    /* package */ void setShouldShowErrorConsole(boolean flag) {
        if (flag == mShouldShowErrorConsole) {
            // Nothing to do.
            return;
        }

        mShouldShowErrorConsole = flag;

        ErrorConsoleView errorConsole = mTabControl.getCurrentErrorConsole(true);

        if (flag) {
            // Setting the show state of the console will cause it's the layout to be inflated.
            if (errorConsole.numberOfErrors() > 0) {
                errorConsole.showConsole(ErrorConsoleView.SHOW_MINIMIZED);
            } else {
                errorConsole.showConsole(ErrorConsoleView.SHOW_NONE);
            }

            // Now we can add it to the main view.
            mErrorConsoleContainer.addView(errorConsole,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                                                  ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            mErrorConsoleContainer.removeView(errorConsole);
        }

    }

    private final static int LOCK_ICON_UNSECURE = 0;
    private final static int LOCK_ICON_SECURE   = 1;
    private final static int LOCK_ICON_MIXED    = 2;

    private int mLockIconType = LOCK_ICON_UNSECURE;
    private int mPrevLockType = LOCK_ICON_UNSECURE;

    private BrowserSettings mSettings;
    private TabControl      mTabControl;
    private ContentResolver mResolver;
    private FrameLayout     mContentView;
    private View            mCustomView;
    private FrameLayout     mCustomViewContainer;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;

    // FIXME, temp address onPrepareMenu performance problem. When we move everything out of
    // view, we should rewrite this.
    private int mCurrentMenuState = 0;
    private int mMenuState = R.id.MAIN_MENU;
    private int mOldMenuState = EMPTY_MENU;
    private static final int EMPTY_MENU = -1;
    private Menu mMenu;

    private FindDialog mFindDialog;
    // Used to prevent chording to result in firing two shortcuts immediately
    // one after another.  Fixes bug 1211714.
    boolean mCanChord;

    private boolean mInLoad;
    private boolean mIsNetworkUp;

    private boolean mPageStarted;
    private boolean mActivityInPause = true;

    private boolean mMenuIsDown;

    private final KeyTracker mKeyTracker = new KeyTracker(this);

    // As trackball doesn't send repeat down, we have to track it ourselves
    private boolean mTrackTrackball;

    private static boolean mInTrace;

    // Performance probe
    private static final int[] SYSTEM_CPU_FORMAT = new int[] {
            Process.PROC_SPACE_TERM | Process.PROC_COMBINE,
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG, // 1: user time
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG, // 2: nice time
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG, // 3: sys time
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG, // 4: idle time
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG, // 5: iowait time
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG, // 6: irq time
            Process.PROC_SPACE_TERM | Process.PROC_OUT_LONG  // 7: softirq time
    };

    private long mStart;
    private long mProcessStart;
    private long mUserStart;
    private long mSystemStart;
    private long mIdleStart;
    private long mIrqStart;

    private long mUiStart;

    private Drawable    mMixLockIcon;
    private Drawable    mSecLockIcon;
    private Drawable    mGenericFavicon;

    /* hold a ref so we can auto-cancel if necessary */
    private AlertDialog mAlertDialog;

    // Wait for credentials before loading google.com
    private ProgressDialog mCredsDlg;

    // The up-to-date URL and title (these can be different from those stored
    // in WebView, since it takes some time for the information in WebView to
    // get updated)
    private String mUrl;
    private String mTitle;

    // As PageInfo has different style for landscape / portrait, we have
    // to re-open it when configuration changed
    private AlertDialog mPageInfoDialog;
    private TabControl.Tab mPageInfoView;
    // If the Page-Info dialog is launched from the SSL-certificate-on-error
    // dialog, we should not just dismiss it, but should get back to the
    // SSL-certificate-on-error dialog. This flag is used to store this state
    private Boolean mPageInfoFromShowSSLCertificateOnError;

    // as SSLCertificateOnError has different style for landscape / portrait,
    // we have to re-open it when configuration changed
    private AlertDialog mSSLCertificateOnErrorDialog;
    private WebView mSSLCertificateOnErrorView;
    private SslErrorHandler mSSLCertificateOnErrorHandler;
    private SslError mSSLCertificateOnErrorError;

    // as SSLCertificate has different style for landscape / portrait, we
    // have to re-open it when configuration changed
    private AlertDialog mSSLCertificateDialog;
    private TabControl.Tab mSSLCertificateView;

    // as HttpAuthentication has different style for landscape / portrait, we
    // have to re-open it when configuration changed
    private AlertDialog mHttpAuthenticationDialog;
    private HttpAuthHandler mHttpAuthHandler;

    /*package*/ static final FrameLayout.LayoutParams COVER_SCREEN_PARAMS =
                                            new FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.FILL_PARENT,
                                            ViewGroup.LayoutParams.FILL_PARENT);
    /*package*/ static final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER =
                                            new FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.FILL_PARENT,
                                            ViewGroup.LayoutParams.FILL_PARENT,
                                            Gravity.CENTER);
    // Google search
    final static String QuickSearch_G = "http://www.google.com/m?q=%s";
    // Wikipedia search
    final static String QuickSearch_W = "http://en.wikipedia.org/w/index.php?search=%s&go=Go";
    // Dictionary search
    final static String QuickSearch_D = "http://dictionary.reference.com/search?q=%s";
    // Google Mobile Local search
    final static String QuickSearch_L = "http://www.google.com/m/search?site=local&q=%s&near=mountain+view";

    final static String QUERY_PLACE_HOLDER = "%s";

    // "source" parameter for Google search through search key
    final static String GOOGLE_SEARCH_SOURCE_SEARCHKEY = "browser-key";
    // "source" parameter for Google search through goto menu
    final static String GOOGLE_SEARCH_SOURCE_GOTO = "browser-goto";
    // "source" parameter for Google search through simplily type
    final static String GOOGLE_SEARCH_SOURCE_TYPE = "browser-type";
    // "source" parameter for Google search suggested by the browser
    final static String GOOGLE_SEARCH_SOURCE_SUGGEST = "browser-suggest";
    // "source" parameter for Google search from unknown source
    final static String GOOGLE_SEARCH_SOURCE_UNKNOWN = "unknown";

    private final static String LOGTAG = "browser";

    private String mLastEnteredUrl;

    private PowerManager.WakeLock mWakeLock;
    private final static int WAKELOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes

    private Toast mStopToast;

    private TitleBarSet mTitleBar;

    private LinearLayout mErrorConsoleContainer = null;
    private boolean mShouldShowErrorConsole = false;

    // As the ids are dynamically created, we can't guarantee that they will
    // be in sequence, so this static array maps ids to a window number.
    final static private int[] WINDOW_SHORTCUT_ID_ARRAY =
    { R.id.window_one_menu_id, R.id.window_two_menu_id, R.id.window_three_menu_id,
      R.id.window_four_menu_id, R.id.window_five_menu_id, R.id.window_six_menu_id,
      R.id.window_seven_menu_id, R.id.window_eight_menu_id };

    // monitor platform changes
    private IntentFilter mNetworkStateChangedFilter;
    private BroadcastReceiver mNetworkStateIntentReceiver;

    private BroadcastReceiver mPackageInstallationReceiver;

    // activity requestCode
    final static int COMBO_PAGE                 = 1;
    final static int DOWNLOAD_PAGE              = 2;
    final static int PREFERENCES_PAGE           = 3;

    /**
     * A UrlData class to abstract how the content will be set to WebView.
     * This base class uses loadUrl to show the content.
     */
    private static class UrlData {
        String mUrl;
        byte[] mPostData;

        UrlData(String url) {
            this.mUrl = url;
        }

        void setPostData(byte[] postData) {
            mPostData = postData;
        }

        boolean isEmpty() {
            return mUrl == null || mUrl.length() == 0;
        }

        public void loadIn(WebView webView) {
            if (mPostData != null) {
                webView.postUrl(mUrl, mPostData);
            } else {
                webView.loadUrl(mUrl);
            }
        }
    };

    /**
     * A subclass of UrlData class that can display inlined content using
     * {@link WebView#loadDataWithBaseURL(String, String, String, String, String)}.
     */
    private static class InlinedUrlData extends UrlData {
        InlinedUrlData(String inlined, String mimeType, String encoding, String failUrl) {
            super(failUrl);
            mInlined = inlined;
            mMimeType = mimeType;
            mEncoding = encoding;
        }
        String mMimeType;
        String mInlined;
        String mEncoding;
        @Override
        boolean isEmpty() {
            return mInlined == null || mInlined.length() == 0 || super.isEmpty();
        }

        @Override
        public void loadIn(WebView webView) {
            webView.loadDataWithBaseURL(null, mInlined, mMimeType, mEncoding, mUrl);
        }
    }

    /* package */ static final UrlData EMPTY_URL_DATA = new UrlData(null);
}
