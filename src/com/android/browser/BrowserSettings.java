
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

import com.android.browser.search.SearchEngine;
import com.android.browser.search.SearchEngines;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Browser.BookmarkColumns;
import android.provider.Settings;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.preference.PreferenceManager;
import android.provider.Browser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Observable;

/*
 * Package level class for storing various WebView and Browser settings. To use
 * this class:
 * BrowserSettings s = BrowserSettings.getInstance();
 * s.addObserver(webView.getSettings());
 * s.loadFromDb(context); // Only needed on app startup
 * s.javaScriptEnabled = true;
 * ... // set any other settings
 * s.update(); // this will update all the observers
 *
 * To remove an observer:
 * s.deleteObserver(webView.getSettings());
 */
class BrowserSettings extends Observable {

    // Private variables for settings
    // NOTE: these defaults need to be kept in sync with the XML
    // until the performance of PreferenceManager.setDefaultValues()
    // is improved.
    // Note: boolean variables are set inside reset function.
    private boolean loadsImagesAutomatically;
    private boolean javaScriptEnabled;
    private WebSettings.PluginState pluginState;
    private boolean javaScriptCanOpenWindowsAutomatically;
    private boolean showSecurityWarnings;
    private boolean rememberPasswords;
    private boolean saveFormData;
    private boolean openInBackground;
    private String defaultTextEncodingName;
    private String homeUrl = "";
    private SearchEngine searchEngine;
    private boolean autoFitPage;
    private boolean landscapeOnly;
    private boolean loadsPageInOverviewMode;
    private boolean showDebugSettings;
    // HTML5 API flags
    private boolean appCacheEnabled;
    private boolean databaseEnabled;
    private boolean domStorageEnabled;
    private boolean geolocationEnabled;
    private boolean workersEnabled;  // only affects V8. JSC does not have a similar setting
    // HTML5 API configuration params
    private long appCacheMaxSize = Long.MAX_VALUE;
    private String appCachePath;  // default value set in loadFromDb().
    private String databasePath; // default value set in loadFromDb()
    private String geolocationDatabasePath; // default value set in loadFromDb()
    private WebStorageSizeManager webStorageSizeManager;

    private String jsFlags = "";

    private final static String TAG = "BrowserSettings";

    // Development settings
    public WebSettings.LayoutAlgorithm layoutAlgorithm =
        WebSettings.LayoutAlgorithm.NARROW_COLUMNS;
    private boolean useWideViewPort = true;
    private int userAgent = 0;
    private boolean tracing = false;
    private boolean lightTouch = false;
    private boolean navDump = false;

    // By default the error console is shown once the user navigates to about:debug.
    // The setting can be then toggled from the settings menu.
    private boolean showConsole = true;

    // Private preconfigured values
    private static int minimumFontSize = 8;
    private static int minimumLogicalFontSize = 8;
    private static int defaultFontSize = 16;
    private static int defaultFixedFontSize = 13;
    private static WebSettings.TextSize textSize =
        WebSettings.TextSize.NORMAL;
    private static WebSettings.ZoomDensity zoomDensity =
        WebSettings.ZoomDensity.MEDIUM;
    private static int pageCacheCapacity;

    // Preference keys that are used outside this class
    public final static String PREF_CLEAR_CACHE = "privacy_clear_cache";
    public final static String PREF_CLEAR_COOKIES = "privacy_clear_cookies";
    public final static String PREF_CLEAR_HISTORY = "privacy_clear_history";
    public final static String PREF_HOMEPAGE = "homepage";
    public final static String PREF_SEARCH_ENGINE = "search_engine";
    public final static String PREF_CLEAR_FORM_DATA =
            "privacy_clear_form_data";
    public final static String PREF_CLEAR_PASSWORDS =
            "privacy_clear_passwords";
    public final static String PREF_EXTRAS_RESET_DEFAULTS =
            "reset_default_preferences";
    public final static String PREF_DEBUG_SETTINGS = "debug_menu";
    public final static String PREF_WEBSITE_SETTINGS = "website_settings";
    public final static String PREF_TEXT_SIZE = "text_size";
    public final static String PREF_DEFAULT_ZOOM = "default_zoom";
    public final static String PREF_DEFAULT_TEXT_ENCODING =
            "default_text_encoding";
    public final static String PREF_CLEAR_GEOLOCATION_ACCESS =
            "privacy_clear_geolocation_access";

    private static final String DESKTOP_USERAGENT = "Mozilla/5.0 (Macintosh; " +
            "U; Intel Mac OS X 10_6_3; en-us) AppleWebKit/533.16 (KHTML, " +
            "like Gecko) Version/5.0 Safari/533.16";

    private static final String IPHONE_USERAGENT = "Mozilla/5.0 (iPhone; U; " +
            "CPU iPhone OS 4_0 like Mac OS X; en-us) AppleWebKit/532.9 " +
            "(KHTML, like Gecko) Version/4.0.5 Mobile/8A293 Safari/6531.22.7";

    private static final String IPAD_USERAGENT = "Mozilla/5.0 (iPad; U; " +
            "CPU OS 3_2 like Mac OS X; en-us) AppleWebKit/531.21.10 " +
            "(KHTML, like Gecko) Version/4.0.4 Mobile/7B367 Safari/531.21.10";

    private static final String FROYO_USERAGENT = "Mozilla/5.0 (Linux; U; " +
            "Android 2.2; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 " +
            "(KHTML, like Gecko) Version/4.0 Mobile Safari/533.1";

    // Value to truncate strings when adding them to a TextView within
    // a ListView
    public final static int MAX_TEXTVIEW_LEN = 80;

    public static final String RLZ_PROVIDER = "com.google.android.partnersetup.rlzappprovider";
    public static final Uri RLZ_PROVIDER_URI = Uri.parse("content://" + RLZ_PROVIDER + "/");

    private String mRlzValue = "";

    private TabControl mTabControl;

    // Single instance of the BrowserSettings for use in the Browser app.
    private static BrowserSettings sSingleton;

    // Private map of WebSettings to Observer objects used when deleting an
    // observer.
    private HashMap<WebSettings,Observer> mWebSettingsToObservers =
        new HashMap<WebSettings,Observer>();

    /*
     * An observer wrapper for updating a WebSettings object with the new
     * settings after a call to BrowserSettings.update().
     */
    static class Observer implements java.util.Observer {
        // Private WebSettings object that will be updated.
        private WebSettings mSettings;

        Observer(WebSettings w) {
            mSettings = w;
        }

        public void update(Observable o, Object arg) {
            BrowserSettings b = (BrowserSettings)o;
            WebSettings s = mSettings;

            s.setLayoutAlgorithm(b.layoutAlgorithm);
            if (b.userAgent == 0) {
                // use the default ua string
                s.setUserAgentString(null);
            } else if (b.userAgent == 1) {
                s.setUserAgentString(DESKTOP_USERAGENT);
            } else if (b.userAgent == 2) {
                s.setUserAgentString(IPHONE_USERAGENT);
            } else if (b.userAgent == 3) {
                s.setUserAgentString(IPAD_USERAGENT);
            } else if (b.userAgent == 4) {
                s.setUserAgentString(FROYO_USERAGENT);
            }
            s.setUseWideViewPort(b.useWideViewPort);
            s.setLoadsImagesAutomatically(b.loadsImagesAutomatically);
            s.setJavaScriptEnabled(b.javaScriptEnabled);
            s.setPluginState(b.pluginState);
            s.setJavaScriptCanOpenWindowsAutomatically(
                    b.javaScriptCanOpenWindowsAutomatically);
            s.setDefaultTextEncodingName(b.defaultTextEncodingName);
            s.setMinimumFontSize(b.minimumFontSize);
            s.setMinimumLogicalFontSize(b.minimumLogicalFontSize);
            s.setDefaultFontSize(b.defaultFontSize);
            s.setDefaultFixedFontSize(b.defaultFixedFontSize);
            s.setNavDump(b.navDump);
            s.setTextSize(b.textSize);
            s.setDefaultZoom(b.zoomDensity);
            s.setLightTouchEnabled(b.lightTouch);
            s.setSaveFormData(b.saveFormData);
            s.setSavePassword(b.rememberPasswords);
            s.setLoadWithOverviewMode(b.loadsPageInOverviewMode);
            s.setPageCacheCapacity(pageCacheCapacity);

            // WebView inside Browser doesn't want initial focus to be set.
            s.setNeedInitialFocus(false);
            // Browser supports multiple windows
            s.setSupportMultipleWindows(true);
            // disable content url access
            s.setAllowContentAccess(false);

            // HTML5 API flags
            s.setAppCacheEnabled(b.appCacheEnabled);
            s.setDatabaseEnabled(b.databaseEnabled);
            s.setDomStorageEnabled(b.domStorageEnabled);
            s.setWorkersEnabled(b.workersEnabled);  // This only affects V8.
            s.setGeolocationEnabled(b.geolocationEnabled);

            // HTML5 configuration parameters.
            s.setAppCacheMaxSize(b.appCacheMaxSize);
            s.setAppCachePath(b.appCachePath);
            s.setDatabasePath(b.databasePath);
            s.setGeolocationDatabasePath(b.geolocationDatabasePath);

            b.updateTabControlSettings();
        }
    }

    /**
     * Load settings from the browser app's database.
     * NOTE: Strings used for the preferences must match those specified
     * in the browser_preferences.xml
     * @param ctx A Context object used to query the browser's settings
     *            database. If the database exists, the saved settings will be
     *            stored in this BrowserSettings object. This will update all
     *            observers of this object.
     */
    public void loadFromDb(final Context ctx) {
        SharedPreferences p =
                PreferenceManager.getDefaultSharedPreferences(ctx);
        // Set the default value for the Application Caches path.
        appCachePath = ctx.getDir("appcache", 0).getPath();
        // Determine the maximum size of the application cache.
        webStorageSizeManager = new WebStorageSizeManager(
                ctx,
                new WebStorageSizeManager.StatFsDiskInfo(appCachePath),
                new WebStorageSizeManager.WebKitAppCacheInfo(appCachePath));
        appCacheMaxSize = webStorageSizeManager.getAppCacheMaxSize();
        // Set the default value for the Database path.
        databasePath = ctx.getDir("databases", 0).getPath();
        // Set the default value for the Geolocation database path.
        geolocationDatabasePath = ctx.getDir("geolocation", 0).getPath();

        if (p.getString(PREF_HOMEPAGE, "") == "") {
            // No home page preferences is set, set it to default.
            setHomePage(ctx, getFactoryResetHomeUrl(ctx));
        }

        // the cost of one cached page is ~3M (measured using nytimes.com). For
        // low end devices, we only cache one page. For high end devices, we try
        // to cache more pages, currently choose 5.
        ActivityManager am = (ActivityManager) ctx
                .getSystemService(Context.ACTIVITY_SERVICE);
        if (am.getMemoryClass() > 16) {
            pageCacheCapacity = 5;
        } else {
            pageCacheCapacity = 1;
        }

    // Load the defaults from the xml
        // This call is TOO SLOW, need to manually keep the defaults
        // in sync
        //PreferenceManager.setDefaultValues(ctx, R.xml.browser_preferences);
        syncSharedPreferences(ctx, p);
    }

    /* package */ void syncSharedPreferences(Context ctx, SharedPreferences p) {

        homeUrl =
            p.getString(PREF_HOMEPAGE, homeUrl);
        String searchEngineName = p.getString(PREF_SEARCH_ENGINE,
                SearchEngine.GOOGLE);
        if (searchEngine == null || !searchEngine.getName().equals(searchEngineName)) {
            if (searchEngine != null) {
                if (searchEngine.supportsVoiceSearch()) {
                    // One or more tabs could have been in voice search mode.
                    // Clear it, since the new SearchEngine may not support
                    // it, or may handle it differently.
                    for (int i = 0; i < mTabControl.getTabCount(); i++) {
                        mTabControl.getTab(i).revertVoiceSearchMode();
                    }
                }
                searchEngine.close();
            }
            searchEngine = SearchEngines.get(ctx, searchEngineName);
        }
        Log.i(TAG, "Selected search engine: " + searchEngine);

        loadsImagesAutomatically = p.getBoolean("load_images",
                loadsImagesAutomatically);
        javaScriptEnabled = p.getBoolean("enable_javascript",
                javaScriptEnabled);
        pluginState = WebSettings.PluginState.valueOf(
                p.getString("plugin_state", pluginState.name()));
        javaScriptCanOpenWindowsAutomatically = !p.getBoolean(
            "block_popup_windows",
            !javaScriptCanOpenWindowsAutomatically);
        showSecurityWarnings = p.getBoolean("show_security_warnings",
                showSecurityWarnings);
        rememberPasswords = p.getBoolean("remember_passwords",
                rememberPasswords);
        saveFormData = p.getBoolean("save_formdata",
                saveFormData);
        boolean accept_cookies = p.getBoolean("accept_cookies",
                CookieManager.getInstance().acceptCookie());
        CookieManager.getInstance().setAcceptCookie(accept_cookies);
        openInBackground = p.getBoolean("open_in_background", openInBackground);
        textSize = WebSettings.TextSize.valueOf(
                p.getString(PREF_TEXT_SIZE, textSize.name()));
        zoomDensity = WebSettings.ZoomDensity.valueOf(
                p.getString(PREF_DEFAULT_ZOOM, zoomDensity.name()));
        autoFitPage = p.getBoolean("autofit_pages", autoFitPage);
        loadsPageInOverviewMode = p.getBoolean("load_page",
                loadsPageInOverviewMode);
        boolean landscapeOnlyTemp =
                p.getBoolean("landscape_only", landscapeOnly);
        if (landscapeOnlyTemp != landscapeOnly) {
            landscapeOnly = landscapeOnlyTemp;
        }
        useWideViewPort = true; // use wide view port for either setting
        if (autoFitPage) {
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS;
        } else {
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL;
        }
        defaultTextEncodingName =
                p.getString(PREF_DEFAULT_TEXT_ENCODING,
                        defaultTextEncodingName);

        showDebugSettings =
                p.getBoolean(PREF_DEBUG_SETTINGS, showDebugSettings);
        // Debug menu items have precidence if the menu is visible
        if (showDebugSettings) {
            boolean small_screen = p.getBoolean("small_screen",
                    layoutAlgorithm ==
                    WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
            if (small_screen) {
                layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN;
            } else {
                boolean normal_layout = p.getBoolean("normal_layout",
                        layoutAlgorithm == WebSettings.LayoutAlgorithm.NORMAL);
                if (normal_layout) {
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL;
                } else {
                    layoutAlgorithm =
                            WebSettings.LayoutAlgorithm.NARROW_COLUMNS;
                }
            }
            useWideViewPort = p.getBoolean("wide_viewport", useWideViewPort);
            tracing = p.getBoolean("enable_tracing", tracing);
            lightTouch = p.getBoolean("enable_light_touch", lightTouch);
            navDump = p.getBoolean("enable_nav_dump", navDump);
            userAgent = Integer.parseInt(p.getString("user_agent", "0"));
        }
        // JS flags is loaded from DB even if showDebugSettings is false,
        // so that it can be set once and be effective all the time.
        jsFlags = p.getString("js_engine_flags", "");

        // Read the setting for showing/hiding the JS Console always so that should the
        // user enable debug settings, we already know if we should show the console.
        // The user will never see the console unless they navigate to about:debug,
        // regardless of the setting we read here. This setting is only used after debug
        // is enabled.
        showConsole = p.getBoolean("javascript_console", showConsole);

        // HTML5 API flags
        appCacheEnabled = p.getBoolean("enable_appcache", appCacheEnabled);
        databaseEnabled = p.getBoolean("enable_database", databaseEnabled);
        domStorageEnabled = p.getBoolean("enable_domstorage", domStorageEnabled);
        geolocationEnabled = p.getBoolean("enable_geolocation", geolocationEnabled);
        workersEnabled = p.getBoolean("enable_workers", workersEnabled);

        update();
    }

    public String getHomePage() {
        return homeUrl;
    }

    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public String getJsFlags() {
        return jsFlags;
    }

    public WebStorageSizeManager getWebStorageSizeManager() {
        return webStorageSizeManager;
    }

    public void setHomePage(Context context, String url) {
        Editor ed = PreferenceManager.
                getDefaultSharedPreferences(context).edit();
        ed.putString(PREF_HOMEPAGE, url);
        ed.apply();
        homeUrl = url;
    }

    public WebSettings.TextSize getTextSize() {
        return textSize;
    }

    public WebSettings.ZoomDensity getDefaultZoom() {
        return zoomDensity;
    }

    public boolean openInBackground() {
        return openInBackground;
    }

    public boolean showSecurityWarnings() {
        return showSecurityWarnings;
    }

    public boolean isTracing() {
        return tracing;
    }

    public boolean isLightTouch() {
        return lightTouch;
    }

    public boolean isNavDump() {
        return navDump;
    }

    public boolean showDebugSettings() {
        return showDebugSettings;
    }

    public void toggleDebugSettings() {
        showDebugSettings = !showDebugSettings;
        navDump = showDebugSettings;
        update();
    }

    /**
     * Add a WebSettings object to the list of observers that will be updated
     * when update() is called.
     *
     * @param s A WebSettings object that is strictly tied to the life of a
     *            WebView.
     */
    public Observer addObserver(WebSettings s) {
        Observer old = mWebSettingsToObservers.get(s);
        if (old != null) {
            super.deleteObserver(old);
        }
        Observer o = new Observer(s);
        mWebSettingsToObservers.put(s, o);
        super.addObserver(o);
        return o;
    }

    /**
     * Delete the given WebSettings observer from the list of observers.
     * @param s The WebSettings object to be deleted.
     */
    public void deleteObserver(WebSettings s) {
        Observer o = mWebSettingsToObservers.get(s);
        if (o != null) {
            mWebSettingsToObservers.remove(s);
            super.deleteObserver(o);
        }
    }

    /*
     * Package level method for obtaining a single app instance of the
     * BrowserSettings.
     */
    /*package*/ static BrowserSettings getInstance() {
        if (sSingleton == null ) {
            sSingleton = new BrowserSettings();
        }
        return sSingleton;
    }

    /*
     * Package level method for associating the BrowserSettings with TabControl
     */
    /* package */void setTabControl(TabControl tabControl) {
        mTabControl = tabControl;
        updateTabControlSettings();
    }

    /*
     * Update all the observers of the object.
     */
    /*package*/ void update() {
        setChanged();
        notifyObservers();
    }

    /*package*/ void clearCache(Context context) {
        WebIconDatabase.getInstance().removeAllIcons();
        if (mTabControl != null) {
            WebView current = mTabControl.getCurrentWebView();
            if (current != null) {
                current.clearCache(true);
            }
        }
    }

    /*package*/ void clearCookies(Context context) {
        CookieManager.getInstance().removeAllCookie();
    }

    /* package */void clearHistory(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Browser.clearHistory(resolver);
        Browser.clearSearches(resolver);
    }

    /* package */ void clearFormData(Context context) {
        WebViewDatabase.getInstance(context).clearFormData();
        if (mTabControl != null) {
            WebView currentTopView = mTabControl.getCurrentTopWebView();
            if (currentTopView != null) {
                currentTopView.clearFormData();
            }
        }
    }

    /*package*/ void clearPasswords(Context context) {
        WebViewDatabase db = WebViewDatabase.getInstance(context);
        db.clearUsernamePassword();
        db.clearHttpAuthUsernamePassword();
    }

    private void updateTabControlSettings() {
        // Enable/disable the error console.
        mTabControl.getBrowserActivity().setShouldShowErrorConsole(
            showDebugSettings && showConsole);
        mTabControl.getBrowserActivity().setRequestedOrientation(
            landscapeOnly ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void maybeDisableWebsiteSettings(Context context) {
        PreferenceActivity activity = (PreferenceActivity) context;
        final PreferenceScreen screen = (PreferenceScreen)
            activity.findPreference(BrowserSettings.PREF_WEBSITE_SETTINGS);
        screen.setEnabled(false);
        WebStorage.getInstance().getOrigins(new ValueCallback<Map>() {
            public void onReceiveValue(Map webStorageOrigins) {
                if ((webStorageOrigins != null) && !webStorageOrigins.isEmpty()) {
                    screen.setEnabled(true);
                }
            }
        });

        GeolocationPermissions.getInstance().getOrigins(new ValueCallback<Set<String> >() {
            public void onReceiveValue(Set<String> geolocationOrigins) {
                if ((geolocationOrigins != null) && !geolocationOrigins.isEmpty()) {
                    screen.setEnabled(true);
                }
            }
        });
    }

    /*package*/ void clearDatabases(Context context) {
        WebStorage.getInstance().deleteAllData();
        maybeDisableWebsiteSettings(context);
    }

    /*package*/ void clearLocationAccess(Context context) {
        GeolocationPermissions.getInstance().clearAll();
        maybeDisableWebsiteSettings(context);
    }

    /*package*/ void resetDefaultPreferences(Context ctx) {
        reset();
        SharedPreferences p =
            PreferenceManager.getDefaultSharedPreferences(ctx);
        p.edit().clear().apply();
        PreferenceManager.setDefaultValues(ctx, R.xml.browser_preferences,
                true);
        // reset homeUrl
        setHomePage(ctx, getFactoryResetHomeUrl(ctx));
        // reset appcache max size
        appCacheMaxSize = webStorageSizeManager.getAppCacheMaxSize();
    }

    private String getFactoryResetHomeUrl(Context context) {
        String url = context.getResources().getString(R.string.homepage_base);
        if (url.indexOf("{CID}") != -1) {
            url = url.replace("{CID}",
                    BrowserProvider.getClientId(context.getContentResolver()));
        }
        return url;
    }

    // Private constructor that does nothing.
    private BrowserSettings() {
        reset();
    }

    private void reset() {
        // Private variables for settings
        // NOTE: these defaults need to be kept in sync with the XML
        // until the performance of PreferenceManager.setDefaultValues()
        // is improved.
        loadsImagesAutomatically = true;
        javaScriptEnabled = true;
        pluginState = WebSettings.PluginState.ON;
        javaScriptCanOpenWindowsAutomatically = false;
        showSecurityWarnings = true;
        rememberPasswords = true;
        saveFormData = true;
        openInBackground = false;
        autoFitPage = true;
        landscapeOnly = false;
        loadsPageInOverviewMode = true;
        showDebugSettings = false;
        // HTML5 API flags
        appCacheEnabled = true;
        databaseEnabled = true;
        domStorageEnabled = true;
        geolocationEnabled = true;
        workersEnabled = true;  // only affects V8. JSC does not have a similar setting
    }

    /*package*/ String getRlzValue() {
        return mRlzValue;
    }

    /*package*/ void updateRlzValues(Context context) {
        // Use AsyncTask because this queries both RlzProvider and Bookmarks URIs
        new RlzUpdateTask(context).execute();
    }

    private class RlzUpdateTask extends AsyncTask<Void, Void, Void> {
        private final Context context;

        public RlzUpdateTask(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(Void...unused) {
            String rlz = retrieveRlzValue(context);
            if (!rlz.isEmpty()) {
                mRlzValue = rlz;
                updateHomePageRlzParameter(context);
                updateBookmarksRlzParameter(context);
            }
            return null;
        }
    }

    // Update RLZ value if present in Home page
    private void updateHomePageRlzParameter(Context context) {
        Uri uri = Uri.parse(homeUrl);
        if ((uri.getQueryParameter("rlz") != null) && UrlUtils.isGoogleUri(uri)) {
            String newHomeUrl = updateRlzParameter(homeUrl);
            if (!homeUrl.equals(newHomeUrl)) {
                setHomePage(context, newHomeUrl);
            }
        }
    }

    // Update RLZ value if present in bookmarks
    private void updateBookmarksRlzParameter(Context context) {
        Cursor cur = null;
        try {
            cur = context.getContentResolver().query(Browser.BOOKMARKS_URI,
                new String[] { BookmarkColumns._ID, BookmarkColumns.URL },
                "url LIKE '%rlz=%'", null, null);
            if ((cur == null) || (cur.getCount() == 0)) {
                return;
            }
            for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                long id = cur.getLong(0);
                String url = cur.getString(1);
                if ((url == null) || url.isEmpty()) {
                    continue;
                }

                Uri uri = Uri.parse(url);
                if ((uri.getQueryParameter("rlz") != null) && UrlUtils.isGoogleUri(uri)) {
                    String newUrl = updateRlzParameter(url);
                    if (!url.equals(newUrl)) {
                        ContentValues values = new ContentValues();
                        values.put(BookmarkColumns.URL, newUrl);
                        Uri bookmarkUri = ContentUris.withAppendedId(Browser.BOOKMARKS_URI, id);
                        context.getContentResolver().update(bookmarkUri, values, null, null);
                    }
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    }

    private String updateRlzParameter(String url) {
        Uri uri = Uri.parse(url);
        String oldRlz = uri.getQueryParameter("rlz");
        if (oldRlz != null) {
            return url.replace("rlz=" + oldRlz, "rlz=" + mRlzValue);
        }
        return url;
    }

    // Retrieve the RLZ value from the Rlz Provider
    private static String retrieveRlzValue(Context context) {
        String rlz = "";
        PackageManager pm = context.getPackageManager();
        if (pm.resolveContentProvider(RLZ_PROVIDER, 0) == null) {
            return rlz;
        }

        String ap = context.getResources().getString(R.string.rlz_access_point);
        if (ap.isEmpty()) {
            return rlz;
        }

        Uri rlzUri = Uri.withAppendedPath(RLZ_PROVIDER_URI, ap);
        Cursor cur = null;
        try {
            cur = context.getContentResolver().query(rlzUri, null, null, null, null);
            if (cur != null && cur.moveToFirst() && !cur.isNull(0)) {
                rlz = cur.getString(0);
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return rlz;
    }
}
