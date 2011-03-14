
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

import com.android.browser.homepages.HomeProvider;
import com.android.browser.search.SearchEngine;
import com.android.browser.search.SearchEngines;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebSettings.AutoFillProfile;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import java.util.HashMap;
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
public class BrowserSettings extends Observable implements OnSharedPreferenceChangeListener {
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
    private boolean autoFillEnabled;
    private boolean openInBackground;
    private String defaultTextEncodingName;
    private String homeUrl = "";
    private SearchEngine searchEngine;
    private boolean autoFitPage;
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
    private boolean hardwareAccelerated = true;
    private boolean showVisualIndicator = false;
    // Lab settings
    private boolean quickControls = false;
    private boolean useMostVisitedHomepage = false;
    private boolean useInstant = false;

    // By default the error console is shown once the user navigates to about:debug.
    // The setting can be then toggled from the settings menu.
    private boolean showConsole = true;

    // Private preconfigured values
    private static int minimumFontSize = 1;
    private static int minimumLogicalFontSize = 1;
    private static int defaultFontSize = 16;
    private static int defaultFixedFontSize = 13;
    private static WebSettings.TextSize textSize =
        WebSettings.TextSize.NORMAL;
    private static WebSettings.ZoomDensity zoomDensity =
        WebSettings.ZoomDensity.MEDIUM;
    private static int pageCacheCapacity;


    private AutoFillProfile autoFillProfile;
    // Default to zero. In the case no profile is set up, the initial
    // value will come from the AutoFillSettingsFragment when the user
    // creates a profile. Otherwise, we'll read the ID of the last used
    // profile from the prefs db.
    private int autoFillActiveProfileId;
    private static final int NO_AUTOFILL_PROFILE_SET = 0;

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
    public final static String PREF_AUTOFILL_ENABLED = "autofill_enabled";
    public final static String PREF_AUTOFILL_PROFILE = "autofill_profile";
    public final static String PREF_AUTOFILL_ACTIVE_PROFILE_ID = "autofill_active_profile_id";
    public final static String PREF_HARDWARE_ACCEL = "enable_hardware_accel";
    public final static String PREF_VISUAL_INDICATOR = "enable_visual_indicator";
    public final static String PREF_USER_AGENT = "user_agent";

    public final static String PREF_QUICK_CONTROLS = "enable_quick_controls";
    public final static String PREF_MOST_VISITED_HOMEPAGE = "use_most_visited_homepage";
    public final static String PREF_PLUGIN_STATE = "plugin_state";
    public final static String PREF_USE_INSTANT = "use_instant_search";

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

    // Set to true to enable some of the about:debug options
    public static final boolean DEV_BUILD = false;

    private Controller mController;

    // Single instance of the BrowserSettings for use in the Browser app.
    private static BrowserSettings sSingleton;

    // Private map of WebSettings to Observer objects used when deleting an
    // observer.
    private HashMap<WebSettings,Observer> mWebSettingsToObservers =
        new HashMap<WebSettings,Observer>();

    private boolean mLoadFromDbComplete;

    public void waitForLoadFromDbToComplete() {
        synchronized (sSingleton) {
            while (!mLoadFromDbComplete) {
                try {
                    sSingleton.wait();
                } catch (InterruptedException e) { }
            }
        }
    }

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
            s.setShowVisualIndicator(b.showVisualIndicator);
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
            s.setAutoFillEnabled(b.autoFillEnabled);
            s.setSavePassword(b.rememberPasswords);
            s.setLoadWithOverviewMode(b.loadsPageInOverviewMode);
            s.setPageCacheCapacity(pageCacheCapacity);

            // WebView inside Browser doesn't want initial focus to be set.
            s.setNeedInitialFocus(false);
            // Browser supports multiple windows
            s.setSupportMultipleWindows(true);
            // enable smooth transition for better performance during panning or
            // zooming
            s.setEnableSmoothTransition(true);
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

            // Active AutoFill profile data.
            s.setAutoFillProfile(b.autoFillProfile);

            b.updateTabControlSettings();
        }
    }

    /**
     * Load settings from the browser app's database. It is performed in
     * an AsyncTask as it involves plenty of slow disk IO.
     * NOTE: Strings used for the preferences must match those specified
     * in the various preference XML files.
     * @param ctx A Context object used to query the browser's settings
     *            database. If the database exists, the saved settings will be
     *            stored in this BrowserSettings object. This will update all
     *            observers of this object.
     */
    public void asyncLoadFromDb(final Context ctx) {
        mLoadFromDbComplete = false;
        // Run the initial settings load in an AsyncTask as it hits the
        // disk multiple times through SharedPreferences and SQLite. We
        // need to be certain though that this has completed before we start
        // to load pages though, so in the worst case we will block waiting
        // for it to finish in BrowserActivity.onCreate().
         new LoadFromDbTask(ctx).execute();
    }

    private class LoadFromDbTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public LoadFromDbTask(Context context) {
            mContext = context;
        }

        protected Void doInBackground(Void... unused) {
            SharedPreferences p =
                    PreferenceManager.getDefaultSharedPreferences(mContext);
            // Set the default value for the Application Caches path.
            appCachePath = mContext.getDir("appcache", 0).getPath();
            // Determine the maximum size of the application cache.
            webStorageSizeManager = new WebStorageSizeManager(
                    mContext,
                    new WebStorageSizeManager.StatFsDiskInfo(appCachePath),
                    new WebStorageSizeManager.WebKitAppCacheInfo(appCachePath));
            appCacheMaxSize = webStorageSizeManager.getAppCacheMaxSize();
            // Set the default value for the Database path.
            databasePath = mContext.getDir("databases", 0).getPath();
            // Set the default value for the Geolocation database path.
            geolocationDatabasePath = mContext.getDir("geolocation", 0).getPath();

            if (p.getString(PREF_HOMEPAGE, null) == null) {
                // No home page preferences is set, set it to default.
                setHomePage(mContext, getFactoryResetHomeUrl(mContext));
            }

            // the cost of one cached page is ~3M (measured using nytimes.com). For
            // low end devices, we only cache one page. For high end devices, we try
            // to cache more pages, currently choose 5.
            ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am.getMemoryClass() > 16) {
                pageCacheCapacity = 5;
            } else {
                pageCacheCapacity = 1;
            }

            // Read the last active AutoFill profile id.
            autoFillActiveProfileId = p.getInt(
                    PREF_AUTOFILL_ACTIVE_PROFILE_ID, autoFillActiveProfileId);

            // Load the autofill profile data from the database. We use a database separate
            // to the browser preference DB to make it easier to support multiple profiles
            // and switching between them.
            AutoFillProfileDatabase autoFillDb = AutoFillProfileDatabase.getInstance(mContext);
            Cursor c = autoFillDb.getProfile(autoFillActiveProfileId);

            if (c.getCount() > 0) {
                c.moveToFirst();

                String fullName = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.FULL_NAME));
                String email = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.EMAIL_ADDRESS));
                String company = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.COMPANY_NAME));
                String addressLine1 = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.ADDRESS_LINE_1));
                String addressLine2 = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.ADDRESS_LINE_2));
                String city = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.CITY));
                String state = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.STATE));
                String zip = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.ZIP_CODE));
                String country = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.COUNTRY));
                String phone = c.getString(c.getColumnIndex(
                        AutoFillProfileDatabase.Profiles.PHONE_NUMBER));
                autoFillProfile = new AutoFillProfile(autoFillActiveProfileId,
                        fullName, email, company, addressLine1, addressLine2, city,
                        state, zip, country, phone);
            }
            c.close();
            autoFillDb.close();

            // PreferenceManager.setDefaultValues is TOO SLOW, need to manually keep
            // the defaults in sync
            p.registerOnSharedPreferenceChangeListener(BrowserSettings.this);
            syncSharedPreferences(mContext, p);

            synchronized (sSingleton) {
                mLoadFromDbComplete = true;
                sSingleton.notify();
            }
            return null;
        }
    }

    private void updateSearchEngine(Context ctx, String searchEngineName, boolean force) {
        if (force || searchEngine == null ||
                !searchEngine.getName().equals(searchEngineName)) {
            if (searchEngine != null) {
                if (searchEngine.supportsVoiceSearch()) {
                     // One or more tabs could have been in voice search mode.
                     // Clear it, since the new SearchEngine may not support
                     // it, or may handle it differently.
                     for (int i = 0; i < mController.getTabControl().getTabCount(); i++) {
                         mController.getTabControl().getTab(i).revertVoiceSearchMode();
                     }
                 }
                 searchEngine.close();
             }
             searchEngine = SearchEngines.get(ctx, searchEngineName);

             if (mController != null && (searchEngine instanceof InstantSearchEngine)) {
                 ((InstantSearchEngine) searchEngine).setController(mController);
             }
         }
    }

    /* package */ void syncSharedPreferences(Context ctx, SharedPreferences p) {

        homeUrl =
            p.getString(PREF_HOMEPAGE, homeUrl);

        useInstant = p.getBoolean(PREF_USE_INSTANT, useInstant);
        String searchEngineName = p.getString(PREF_SEARCH_ENGINE,
               SearchEngine.GOOGLE);
        updateSearchEngine(ctx, searchEngineName, false);

        loadsImagesAutomatically = p.getBoolean("load_images",
                loadsImagesAutomatically);
        javaScriptEnabled = p.getBoolean("enable_javascript",
                javaScriptEnabled);
        pluginState = WebSettings.PluginState.valueOf(
                p.getString(PREF_PLUGIN_STATE, pluginState.name()));
        javaScriptCanOpenWindowsAutomatically = !p.getBoolean(
            "block_popup_windows",
            !javaScriptCanOpenWindowsAutomatically);
        showSecurityWarnings = p.getBoolean("show_security_warnings",
                showSecurityWarnings);
        rememberPasswords = p.getBoolean("remember_passwords",
                rememberPasswords);
        saveFormData = p.getBoolean("save_formdata",
                saveFormData);
        autoFillEnabled = p.getBoolean("autofill_enabled", autoFillEnabled);
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
            showVisualIndicator = p.getBoolean(PREF_VISUAL_INDICATOR, showVisualIndicator);
        }

        quickControls = p.getBoolean(PREF_QUICK_CONTROLS, quickControls);
        useMostVisitedHomepage = p.getBoolean(PREF_MOST_VISITED_HOMEPAGE, useMostVisitedHomepage);

        // Only set these if this is a dev build or debug is enabled
        if (DEV_BUILD || showDebugSettings()) {
            userAgent = Integer.parseInt(p.getString(PREF_USER_AGENT, "0"));
            hardwareAccelerated = p.getBoolean(PREF_HARDWARE_ACCEL, hardwareAccelerated);
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
        if (useMostVisitedHomepage) {
            return HomeProvider.MOST_VISITED;
        }
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

    public boolean isHardwareAccelerated() {
        return hardwareAccelerated;
    }

    public boolean showVisualIndicator() {
        return showVisualIndicator;
    }

    public boolean useQuickControls() {
        return quickControls;
    }

    public boolean useMostVisitedHomepage() {
        return useMostVisitedHomepage;
    }

    public boolean useInstant() {
        return useInstant;
    }

    public boolean showDebugSettings() {
        return showDebugSettings;
    }

    public void toggleDebugSettings(Context context) {
        showDebugSettings = !showDebugSettings;
        navDump = showDebugSettings;
        syncSharedPreferences(context,
                PreferenceManager.getDefaultSharedPreferences(context));
        update();
    }

    public void setAutoFillProfile(Context ctx, AutoFillProfile profile, Message msg) {
        if (profile != null) {
            setActiveAutoFillProfileId(ctx, profile.getUniqueId());
            // Update the AutoFill DB with the new profile.
            new SaveProfileToDbTask(ctx, msg).execute(profile);
        } else {
            // Delete the current profile.
            if (autoFillProfile != null) {
                new DeleteProfileFromDbTask(ctx, msg).execute(autoFillProfile.getUniqueId());
                setActiveAutoFillProfileId(ctx, NO_AUTOFILL_PROFILE_SET);
            }
        }
        autoFillProfile = profile;
    }

    public AutoFillProfile getAutoFillProfile() {
        return autoFillProfile;
    }

    private void setActiveAutoFillProfileId(Context context, int activeProfileId) {
        autoFillActiveProfileId = activeProfileId;
        Editor ed = PreferenceManager.
            getDefaultSharedPreferences(context).edit();
        ed.putInt(PREF_AUTOFILL_ACTIVE_PROFILE_ID, activeProfileId);
        ed.apply();
    }

    /* package */ void disableAutoFill(Context ctx) {
        autoFillEnabled = false;
        Editor ed = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
        ed.putBoolean(PREF_AUTOFILL_ENABLED, false);
        ed.apply();
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
     * Application level method for obtaining a single app instance of the
     * BrowserSettings.
     */
    public static BrowserSettings getInstance() {
        if (sSingleton == null ) {
            sSingleton = new BrowserSettings();
        }
        return sSingleton;
    }

    /*
     * Package level method for associating the BrowserSettings with TabControl
     */
    /* package */void setController(Controller ctrl) {
        mController = ctrl;
        updateTabControlSettings();

        if (mController != null && (searchEngine instanceof InstantSearchEngine)) {
             ((InstantSearchEngine) searchEngine).setController(mController);
        }
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
        if (mController != null) {
            WebView current = mController.getCurrentWebView();
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
        if (mController!= null) {
            WebView currentTopView = mController.getCurrentTopWebView();
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
        mController.setShouldShowErrorConsole(
            showDebugSettings && showConsole);
    }

    /*package*/ void clearDatabases(Context context) {
        WebStorage.getInstance().deleteAllData();
    }

    /*package*/ void clearLocationAccess(Context context) {
        GeolocationPermissions.getInstance().clearAll();
    }

    /*package*/ void resetDefaultPreferences(Context ctx) {
        reset();
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
        p.edit().clear().apply();
        PreferenceManager.setDefaultValues(ctx, R.xml.general_preferences, true);
        PreferenceManager.setDefaultValues(ctx, R.xml.privacy_security_preferences, true);
        PreferenceManager.setDefaultValues(ctx, R.xml.advanced_preferences, true);
        // reset homeUrl
        setHomePage(ctx, getFactoryResetHomeUrl(ctx));
        // reset appcache max size
        appCacheMaxSize = webStorageSizeManager.getAppCacheMaxSize();
        setActiveAutoFillProfileId(ctx, NO_AUTOFILL_PROFILE_SET);
    }

    /*package*/ static String getFactoryResetHomeUrl(Context context) {
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
        autoFillEnabled = true;
        openInBackground = false;
        autoFitPage = true;
        loadsPageInOverviewMode = true;
        showDebugSettings = false;
        // HTML5 API flags
        appCacheEnabled = true;
        databaseEnabled = true;
        domStorageEnabled = true;
        geolocationEnabled = true;
        workersEnabled = true;  // only affects V8. JSC does not have a similar setting
    }

    private abstract class AutoFillProfileDbTask<T> extends AsyncTask<T, Void, Void> {
        Context mContext;
        AutoFillProfileDatabase mAutoFillProfileDb;
        Message mCompleteMessage;

        public AutoFillProfileDbTask(Context ctx, Message msg) {
            mContext = ctx;
            mCompleteMessage = msg;
        }

        protected void onPostExecute(Void result) {
            if (mCompleteMessage != null) {
                mCompleteMessage.sendToTarget();
            }
            mAutoFillProfileDb.close();
        }

        abstract protected Void doInBackground(T... values);
    }


    private class SaveProfileToDbTask extends AutoFillProfileDbTask<AutoFillProfile> {
        public SaveProfileToDbTask(Context ctx, Message msg) {
            super(ctx, msg);
        }

        protected Void doInBackground(AutoFillProfile... values) {
            mAutoFillProfileDb = AutoFillProfileDatabase.getInstance(mContext);
            assert autoFillActiveProfileId != NO_AUTOFILL_PROFILE_SET;
            AutoFillProfile newProfile = values[0];
            mAutoFillProfileDb.addOrUpdateProfile(autoFillActiveProfileId, newProfile);
            return null;
        }
    }

    private class DeleteProfileFromDbTask extends AutoFillProfileDbTask<Integer> {
        public DeleteProfileFromDbTask(Context ctx, Message msg) {
            super(ctx, msg);
        }

        protected Void doInBackground(Integer... values) {
            mAutoFillProfileDb = AutoFillProfileDatabase.getInstance(mContext);
            int id = values[0];
            assert  id > 0;
            mAutoFillProfileDb.dropProfile(id);
            return null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences p, String key) {
        if (PREF_HARDWARE_ACCEL.equals(key)) {
            hardwareAccelerated = p.getBoolean(PREF_HARDWARE_ACCEL, hardwareAccelerated);
        } else if (PREF_VISUAL_INDICATOR.equals(key)) {
            showVisualIndicator = p.getBoolean(PREF_VISUAL_INDICATOR, showVisualIndicator);
        } else if (PREF_USER_AGENT.equals(key)) {
            userAgent = Integer.parseInt(p.getString(PREF_USER_AGENT, "0"));
            update();
        } else if (PREF_QUICK_CONTROLS.equals(key)) {
            quickControls = p.getBoolean(PREF_QUICK_CONTROLS, quickControls);
        } else if (PREF_MOST_VISITED_HOMEPAGE.equals(key)) {
            useMostVisitedHomepage = p.getBoolean(PREF_MOST_VISITED_HOMEPAGE, useMostVisitedHomepage);
        } else if (PREF_USE_INSTANT.equals(key)) {
            useInstant = p.getBoolean(PREF_USE_INSTANT, useInstant);
            updateSearchEngine(mController.getActivity(), SearchEngine.GOOGLE, true);
        } else if (PREF_SEARCH_ENGINE.equals(key)) {
            final String searchEngineName = p.getString(PREF_SEARCH_ENGINE,
                    SearchEngine.GOOGLE);
            updateSearchEngine(mController.getActivity(), searchEngineName, false);
        }
    }
}
