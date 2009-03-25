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
import com.google.android.providers.GoogleSettings.Partner;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnCancelListener;
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
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.Contacts;
import android.provider.Downloads;
import android.provider.MediaStore;
import android.provider.Contacts.Intents.Insert;
import android.text.IClipboard;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.util.Regex;
import android.util.Config;
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
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebIconDatabase;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BrowserActivity extends Activity
    implements KeyTracker.OnKeyTracker,
        View.OnCreateContextMenuListener,
        DownloadListener {

    private IGoogleLoginService mGls = null;
    private ServiceConnection mGlsConnection = null;

    private SensorManager mSensorManager = null;

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
                        homepage = "http://www.google.com/m/a/" + domain + "?client=ms-" +
                            Partner.getString(BrowserActivity.this.getContentResolver(), Partner.CLIENT_ID);
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

    /**
     * This class is in charge of installing pre-packaged plugins
     * from the Browser assets directory to the user's data partition.
     * Plugins are loaded from the "plugins" directory in the assets;
     * Anything that is in this directory will be copied over to the
     * user data partition in app_plugins.
     */
    private class CopyPlugins implements Runnable {
        final static String TAG = "PluginsInstaller";
        final static String ZIP_FILTER = "assets/plugins/";
        final static String APK_PATH = "/system/app/Browser.apk";
        final static String PLUGIN_EXTENSION = ".so";
        final static String TEMPORARY_EXTENSION = "_temp";
        final static String BUILD_INFOS_FILE = "build.prop";
        final static String SYSTEM_BUILD_INFOS_FILE = "/system/"
                              + BUILD_INFOS_FILE;
        final int BUFSIZE = 4096;
        boolean mDoOverwrite = false;
        String pluginsPath;
        Context mContext;
        File pluginsDir;
        AssetManager manager;

        public CopyPlugins (boolean overwrite, Context context) {
            mDoOverwrite = overwrite;
            mContext = context;
        }

        /**
         * Returned a filtered list of ZipEntry.
         * We list all the files contained in the zip and
         * only returns the ones starting with the ZIP_FILTER
         * path.
         *
         * @param zip the zip file used.
         */
        public Vector<ZipEntry> pluginsFilesFromZip(ZipFile zip) {
            Vector<ZipEntry> list = new Vector<ZipEntry>();
            Enumeration entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (entry.getName().startsWith(ZIP_FILTER)) {
                  list.add(entry);
                }
            }
            return list;
        }

        /**
         * Utility method to copy the content from an inputstream
         * to a file output stream.
         */
        public void copyStreams(InputStream is, FileOutputStream fos) {
            BufferedOutputStream os = null;
            try {
                byte data[] = new byte[BUFSIZE];
                int count;
                os = new BufferedOutputStream(fos, BUFSIZE);
                while ((count = is.read(data, 0, BUFSIZE)) != -1) {
                    os.write(data, 0, count);
                }
                os.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception while copying: " + e);
            } finally {
              try {
                if (os != null) {
                    os.close();
                }
              } catch (IOException e2) {
                Log.e(TAG, "Exception while closing the stream: " + e2);
              }
            }
        }

        /**
         * Returns a string containing the contents of a file
         *
         * @param file the target file
         */
        private String contentsOfFile(File file) {
          String ret = null;
          FileInputStream is = null;
          try {
            byte[] buffer = new byte[BUFSIZE];
            int count;
            is = new FileInputStream(file);
            StringBuffer out = new StringBuffer();

            while ((count = is.read(buffer, 0, BUFSIZE)) != -1) {
              out.append(new String(buffer, 0, count));
            }
            ret = out.toString();
          } catch (IOException e) {
            Log.e(TAG, "Exception getting contents of file " + e);
          } finally {
            if (is != null) {
              try {
                is.close();
              } catch (IOException e2) {
                Log.e(TAG, "Exception while closing the file: " + e2);
              }
            }
          }
          return ret;
        }

        /**
         * Utility method to initialize the user data plugins path.
         */
        public void initPluginsPath() {
            BrowserSettings s = BrowserSettings.getInstance();
            pluginsPath = s.getPluginsPath();
            if (pluginsPath == null) {
                s.loadFromDb(mContext);
                pluginsPath = s.getPluginsPath();
            }
            if (Config.LOGV) {
                Log.v(TAG, "Plugin path: " + pluginsPath);
            }
        }

        /**
         * Utility method to delete a file or a directory
         *
         * @param file the File to delete
         */
        public void deleteFile(File file) {
            File[] files = file.listFiles();
            if ((files != null) && files.length > 0) {
              for (int i=0; i< files.length; i++) {
                deleteFile(files[i]);
              }
            }
            if (!file.delete()) {
              Log.e(TAG, file.getPath() + " could not get deleted");
            }
        }

        /**
         * Clean the content of the plugins directory.
         * We delete the directory, then recreate it.
         */
        public void cleanPluginsDirectory() {
          if (Config.LOGV) {
            Log.v(TAG, "delete plugins directory: " + pluginsPath);
          }
          File pluginsDirectory = new File(pluginsPath);
          deleteFile(pluginsDirectory);
          pluginsDirectory.mkdir();
        }


        /**
         * Copy the SYSTEM_BUILD_INFOS_FILE file containing the
         * informations about the system build to the
         * BUILD_INFOS_FILE in the plugins directory.
         */
        public void copyBuildInfos() {
          try {
            if (Config.LOGV) {
              Log.v(TAG, "Copy build infos to the plugins directory");
            }
            File buildInfoFile = new File(SYSTEM_BUILD_INFOS_FILE);
            File buildInfoPlugins = new File(pluginsPath, BUILD_INFOS_FILE);
            copyStreams(new FileInputStream(buildInfoFile),
                        new FileOutputStream(buildInfoPlugins));
          } catch (IOException e) {
            Log.e(TAG, "Exception while copying the build infos: " + e);
          }
        }

        /**
         * Returns true if the current system is newer than the
         * system that installed the plugins.
         * We determinate this by checking the build number of the system.
         *
         * At the end of the plugins copy operation, we copy the
         * SYSTEM_BUILD_INFOS_FILE to the BUILD_INFOS_FILE.
         * We then just have to load both and compare them -- if they
         * are different the current system is newer.
         *
         * Loading and comparing the strings should be faster than
         * creating a hash, the files being rather small. Extracting the
         * version number would require some parsing which may be more
         * brittle.
         */
        public boolean newSystemImage() {
          try {
            File buildInfoFile = new File(SYSTEM_BUILD_INFOS_FILE);
            File buildInfoPlugins = new File(pluginsPath, BUILD_INFOS_FILE);
            if (!buildInfoPlugins.exists()) {
              if (Config.LOGV) {
                Log.v(TAG, "build.prop in plugins directory " + pluginsPath
                  + " does not exist, therefore it's a new system image");
              }
              return true;
            } else {
              String buildInfo = contentsOfFile(buildInfoFile);
              String buildInfoPlugin = contentsOfFile(buildInfoPlugins);
              if (buildInfo == null || buildInfoPlugin == null
                  || buildInfo.compareTo(buildInfoPlugin) != 0) {
                if (Config.LOGV) {
                  Log.v(TAG, "build.prop are different, "
                    + " therefore it's a new system image");
                }
                return true;
              }
            }
          } catch (Exception e) {
            Log.e(TAG, "Exc in newSystemImage(): " + e);
          }
          return false;
        }

        /**
         * Check if the version of the plugins contained in the
         * Browser assets is the same as the version of the plugins
         * in the plugins directory.
         * We simply iterate on every file in the assets/plugins
         * and return false if a file listed in the assets does
         * not exist in the plugins directory.
         */
        private boolean checkIsDifferentVersions() {
          try {
            ZipFile zip = new ZipFile(APK_PATH);
            Vector<ZipEntry> files = pluginsFilesFromZip(zip);
            int zipFilterLength = ZIP_FILTER.length();

            Enumeration entries = files.elements();
            while (entries.hasMoreElements()) {
              ZipEntry entry = (ZipEntry) entries.nextElement();
              String path = entry.getName().substring(zipFilterLength);
              File outputFile = new File(pluginsPath, path);
              if (!outputFile.exists()) {
                if (Config.LOGV) {
                  Log.v(TAG, "checkIsDifferentVersions(): extracted file "
                    + path + " does not exist, we have a different version");
                }
                return true;
              }
            }
          } catch (IOException e) {
            Log.e(TAG, "Exception in checkDifferentVersions(): " + e);
          }
          return false;
        }

        /**
         * Copy every files from the assets/plugins directory
         * to the app_plugins directory in the data partition.
         * Once copied, we copy over the SYSTEM_BUILD_INFOS file
         * in the plugins directory.
         *
         * NOTE: we directly access the content from the Browser
         * package (it's a zip file) and do not use AssetManager
         * as there is a limit of 1Mb (see Asset.h)
         */
        public void run() {
            // Lower the priority
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                if (pluginsPath == null) {
                    Log.e(TAG, "No plugins path found!");
                    return;
                }

                ZipFile zip = new ZipFile(APK_PATH);
                Vector<ZipEntry> files = pluginsFilesFromZip(zip);
                Vector<File> plugins = new Vector<File>();
                int zipFilterLength = ZIP_FILTER.length();

                Enumeration entries = files.elements();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    String path = entry.getName().substring(zipFilterLength);
                    File outputFile = new File(pluginsPath, path);
                    outputFile.getParentFile().mkdirs();

                    if (outputFile.exists() && !mDoOverwrite) {
                        if (Config.LOGV) {
                            Log.v(TAG, path + " already extracted.");
                        }
                    } else {
                        if (path.endsWith(PLUGIN_EXTENSION)) {
                            // We rename plugins to be sure a half-copied
                            // plugin is not loaded by the browser.
                            plugins.add(outputFile);
                            outputFile = new File(pluginsPath,
                                path + TEMPORARY_EXTENSION);
                        }
                        FileOutputStream fos = new FileOutputStream(outputFile);
                        if (Config.LOGV) {
                            Log.v(TAG, "copy " + entry + " to "
                                + pluginsPath + "/" + path);
                        }
                        copyStreams(zip.getInputStream(entry), fos);
                    }
                }

                // We now rename the .so we copied, once all their resources
                // are safely copied over to the user data partition.
                Enumeration elems = plugins.elements();
                while (elems.hasMoreElements()) {
                    File renamedFile = (File) elems.nextElement();
                    File sourceFile = new File(renamedFile.getPath()
                        + TEMPORARY_EXTENSION);
                    if (Config.LOGV) {
                        Log.v(TAG, "rename " + sourceFile.getPath()
                            + " to " + renamedFile.getPath());
                    }
                    sourceFile.renameTo(renamedFile);
                }

                copyBuildInfos();

                // Refresh the plugin list.
                if (mTabControl.getCurrentWebView() != null) {
                    mTabControl.getCurrentWebView().refreshPlugins(false);
                }
            } catch (IOException e) {
                Log.e(TAG, "IO Exception: " + e);
            }
        }
    };

    /**
     * Copy the content of assets/plugins/ to the app_plugins directory
     * in the data partition.
     *
     * This function is called every time the browser is started.
     * We first check if the system image is newer than the one that
     * copied the plugins (if there's plugins in the data partition).
     * If this is the case, we then check if the versions are different.
     * If they are different, we clean the plugins directory in the
     * data partition, then start a thread to copy the plugins while
     * the browser continue to load.
     *
     * @param overwrite if true overwrite the files even if they are
     * already present (to let the user "reset" the plugins if needed).
     */
    private void copyPlugins(boolean overwrite) {
        CopyPlugins copyPluginsFromAssets = new CopyPlugins(overwrite, this);
        copyPluginsFromAssets.initPluginsPath();
        if (copyPluginsFromAssets.newSystemImage())  {
          if (copyPluginsFromAssets.checkIsDifferentVersions()) {
            copyPluginsFromAssets.cleanPluginsDirectory();
            Thread copyplugins = new Thread(copyPluginsFromAssets);
            copyplugins.setName("CopyPlugins");
            copyplugins.start();
          }
        }
    }

    private class ClearThumbnails extends AsyncTask<File, Void, Void> {
        @Override
        public Void doInBackground(File... files) {
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            return null;
        }
    }

    @Override public void onCreate(Bundle icicle) {
        if (Config.LOGV) {
            Log.v(LOGTAG, this + " onStart");
        }
        super.onCreate(icicle);
        this.requestWindowFeature(Window.FEATURE_LEFT_ICON);
        this.requestWindowFeature(Window.FEATURE_RIGHT_ICON);
        this.requestWindowFeature(Window.FEATURE_PROGRESS);
        this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // test the browser in OpenGL
        // requestWindowFeature(Window.FEATURE_OPENGL);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mResolver = getContentResolver();

        setBaseSearchUrl(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("search_url", ""));

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

        mContentView = (FrameLayout) getWindow().getDecorView().findViewById(
                com.android.internal.R.id.content);

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

        if (!mTabControl.restoreState(icicle)) {
            // clear up the thumbnail directory if we can't restore the state as
            // none of the files in the directory are referenced any more.
            new ClearThumbnails().execute(
                    mTabControl.getThumbnailDir().listFiles());
            final Intent intent = getIntent();
            final Bundle extra = intent.getExtras();
            // Create an initial tab.
            // If the intent is ACTION_VIEW and data is not null, the Browser is
            // invoked to view the content by another application. In this case,
            // the tab will be close when exit.
            String url = getUrlFromIntent(intent);
            final TabControl.Tab t = mTabControl.createNewTab(
                    Intent.ACTION_VIEW.equals(intent.getAction()) &&
                    intent.getData() != null,
                    intent.getStringExtra(Browser.EXTRA_APPLICATION_ID), url);
            mTabControl.setCurrentTab(t);
            // This is one of the only places we call attachTabToContentView
            // without animating from the tab picker.
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
            copyPlugins(true);

            if (url == null || url.length() == 0) {
                if (mSettings.isLoginInitialized()) {
                    webView.loadUrl(mSettings.getHomePage());
                } else {
                    waitForCredentials();
                }
            } else {
                webView.loadUrl(url);
            }
        } else {
            // TabControl.restoreState() will create a new tab even if
            // restoring the state fails. Attach it to the view here since we
            // are not animating from the tab picker.
            attachTabToContentView(mTabControl.getCurrentTab());
        }

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
            String url = getUrlFromIntent(intent);
            if (url == null || url.length() == 0) {
                url = mSettings.getHomePage();
            }
            if (Intent.ACTION_VIEW.equals(action) &&
                    (flags & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
                final String appId =
                        intent.getStringExtra(Browser.EXTRA_APPLICATION_ID);
                final TabControl.Tab appTab = mTabControl.getTabFromId(appId);
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
                            mTabControl.recreateWebView(appTab, url);
                    if (current != appTab) {
                        showTab(appTab, needsLoad ? url : null);
                    } else {
                        if (mTabOverview != null && mAnimationCount == 0) {
                            sendAnimateFromOverview(appTab, false,
                                    needsLoad ? url : null, TAB_OVERVIEW_DELAY,
                                    null);
                        } else {
                            // If the tab was the current tab, we have to attach
                            // it to the view system again.
                            attachTabToContentView(appTab);
                            if (needsLoad) {
                                appTab.getWebView().loadUrl(url);
                            }
                        }
                    }
                    return;
                }
                // if FLAG_ACTIVITY_BROUGHT_TO_FRONT flag is on, the url will be
                // opened in a new tab unless we have reached MAX_TABS. Then the
                // url will be opened in the current tab. If a new tab is
                // created, it will have "true" for exit on close.
                openTabAndShow(url, null, true, appId);
            } else {
                if ("about:debug".equals(url)) {
                    mSettings.toggleDebugSettings();
                    return;
                }
                // If the Window overview is up and we are not in the midst of
                // an animation, animate away from the Window overview.
                if (mTabOverview != null && mAnimationCount == 0) {
                    sendAnimateFromOverview(current, false, url,
                            TAB_OVERVIEW_DELAY, null);
                } else {
                    // Get rid of the subwindow if it exists
                    dismissSubWindow(current);
                    current.getWebView().loadUrl(url);
                }
            }
        }
    }

    private String getUrlFromIntent(Intent intent) {
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
        return url;
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
        if (Config.LOGV) {
            Log.v(LOGTAG, "BrowserActivity.onResume: this=" + this);
        }

        if (!mActivityInPause) {
            Log.e(LOGTAG, "BrowserActivity is already resumed.");
            return;
        }

        mActivityInPause = false;
        resumeWebView();

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

    /**
     *  onSaveInstanceState(Bundle map)
     *  onSaveInstanceState is called right before onStop(). The map contains
     *  the saved state.
     */
    @Override protected void onSaveInstanceState(Bundle outState) {
        if (Config.LOGV) {
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

        mActivityInPause = true;
        if (mTabControl.getCurrentIndex() >= 0 && !pauseWebView()) {
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
        if (Config.LOGV) {
            Log.v(LOGTAG, "BrowserActivity.onDestroy: this=" + this);
        }
        super.onDestroy();
        // Remove the current tab and sub window
        TabControl.Tab t = mTabControl.getCurrentTab();
        dismissSubWindow(t);
        removeTabFromContentView(t);
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

    private boolean resumeWebView() {
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

    private boolean pauseWebView() {
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
        startSearch(null, false,
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mCanChord) {
            // The user has already fired a shortcut with this hold down of the
            // menu key.
            return false;
        }
        switch (item.getItemId()) {
            // -- Main menu
            case R.id.goto_menu_id: {
                String url = getTopWindow().getUrl();
                startSearch(mSettings.getHomePage().equals(url) ? null : url, true,
                        createGoogleSearchSourceBundle(GOOGLE_SEARCH_SOURCE_GOTO), false);
                }
                break;

            case R.id.bookmarks_menu_id:
                bookmarksOrHistoryPicker(false);
                break;

            case R.id.windows_menu_id:
                if (mTabControl.getTabCount() == 1) {
                    openTabAndShow(mSettings.getHomePage(), null, false, null);
                } else {
                    tabPicker(true, mTabControl.getCurrentIndex(), false);
                }
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
                final int currentIndex = mTabControl.getCurrentIndex();
                final TabControl.Tab parent =
                        mTabControl.getCurrentTab().getParentTab();
                int indexToShow = -1;
                if (parent != null) {
                    indexToShow = mTabControl.getTabIndex(parent);
                } else {
                    // Get the last tab in the list. If it is the current tab,
                    // subtract 1 more.
                    indexToShow = mTabControl.getTabCount() - 1;
                    if (currentIndex == indexToShow) {
                        indexToShow--;
                    }
                }
                switchTabs(currentIndex, indexToShow, true);
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
                bookmarksOrHistoryPicker(true);
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

            // -- Tab menu
            case R.id.view_tab_menu_id:
                if (mTabListener != null && mTabOverview != null) {
                    int pos = mTabOverview.getContextMenuPosition(item);
                    mTabOverview.setCurrentIndex(pos);
                    mTabListener.onClick(pos);
                }
                break;

            case R.id.remove_tab_menu_id:
                if (mTabListener != null && mTabOverview != null) {
                    int pos = mTabOverview.getContextMenuPosition(item);
                    mTabListener.remove(pos);
                }
                break;

            case R.id.new_tab_menu_id:
                // No need to check for mTabOverview here since we are not
                // dependent on it for a position.
                if (mTabListener != null) {
                    // If the overview happens to be non-null, make the "New
                    // Tab" cell visible.
                    if (mTabOverview != null) {
                        mTabOverview.setCurrentIndex(ImageGrid.NEW_TAB);
                    }
                    mTabListener.onClick(ImageGrid.NEW_TAB);
                }
                break;

            case R.id.bookmark_tab_menu_id:
                if (mTabListener != null && mTabOverview != null) {
                    int pos = mTabOverview.getContextMenuPosition(item);
                    TabControl.Tab t = mTabControl.getTab(pos);
                    // Since we called populatePickerData for all of the
                    // tabs, getTitle and getUrl will return appropriate
                    // values.
                    Browser.saveBookmark(BrowserActivity.this, t.getTitle(),
                            t.getUrl());
                }
                break;

            case R.id.history_tab_menu_id:
                bookmarksOrHistoryPicker(true);
                break;

            case R.id.bookmarks_tab_menu_id:
                bookmarksOrHistoryPicker(false);
                break;

            case R.id.properties_tab_menu_id:
                if (mTabListener != null && mTabOverview != null) {
                    int pos = mTabOverview.getContextMenuPosition(item);
                    showPageInfo(mTabControl.getTab(pos), false);
                }
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
                                switchTabs(mTabControl.getCurrentIndex(), id, false);
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
            case R.id.TAB_MENU:
                if (mCurrentMenuState != mMenuState) {
                    menu.setGroupVisible(R.id.MAIN_MENU, false);
                    menu.setGroupEnabled(R.id.MAIN_MENU, false);
                    menu.setGroupEnabled(R.id.MAIN_SHORTCUT_MENU, false);
                    menu.setGroupVisible(R.id.TAB_MENU, true);
                    menu.setGroupEnabled(R.id.TAB_MENU, true);
                }
                boolean newT = mTabControl.getTabCount() < TabControl.MAX_TABS;
                final MenuItem tab = menu.findItem(R.id.new_tab_menu_id);
                tab.setVisible(newT);
                tab.setEnabled(newT);
                break;
            case EMPTY_MENU:
                if (mCurrentMenuState != mMenuState) {
                    menu.setGroupVisible(R.id.MAIN_MENU, false);
                    menu.setGroupEnabled(R.id.MAIN_MENU, false);
                    menu.setGroupEnabled(R.id.MAIN_SHORTCUT_MENU, false);
                    menu.setGroupVisible(R.id.TAB_MENU, false);
                    menu.setGroupEnabled(R.id.TAB_MENU, false);
                }
                break;
            default:
                if (mCurrentMenuState != mMenuState) {
                    menu.setGroupVisible(R.id.MAIN_MENU, true);
                    menu.setGroupEnabled(R.id.MAIN_MENU, true);
                    menu.setGroupEnabled(R.id.MAIN_SHORTCUT_MENU, true);
                    menu.setGroupVisible(R.id.TAB_MENU, false);
                    menu.setGroupEnabled(R.id.TAB_MENU, false);
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

                // decide whether to show the share link option
                PackageManager pm = getPackageManager();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                ResolveInfo ri = pm.resolveActivity(send, PackageManager.MATCH_DEFAULT_ONLY);
                menu.findItem(R.id.share_page_menu_id).setVisible(ri != null);

                // If there is only 1 window, the text will be "New window"
                final MenuItem windows = menu.findItem(R.id.windows_menu_id);
                windows.setTitleCondensed(mTabControl.getTabCount() > 1 ?
                        getString(R.string.view_tabs_condensed) :
                        getString(R.string.tab_picker_new_tab));

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
        final WebView main = t.getWebView();
        // Attach the main WebView.
        mContentView.addView(main, COVER_SCREEN_PARAMS);
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
        // Remove the main WebView.
        mContentView.removeView(t.getWebView());
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

    // Send the ANIMTE_FROM_OVERVIEW message after changing the current tab.
    private void sendAnimateFromOverview(final TabControl.Tab tab,
            final boolean newTab, final String url, final int delay,
            final Message msg) {
        // Set the current tab.
        mTabControl.setCurrentTab(tab);
        // Attach the WebView so it will layout.
        attachTabToContentView(tab);
        // Set the view to invisibile for now.
        tab.getWebView().setVisibility(View.INVISIBLE);
        // If there is a sub window, make it invisible too.
        if (tab.getSubWebView() != null) {
            tab.getSubWebViewContainer().setVisibility(View.INVISIBLE);
        }
        // Create our fake animating view.
        final AnimatingView view = new AnimatingView(this, tab);
        // Attach it to the view system and make in invisible so it will
        // layout but not flash white on the screen.
        mContentView.addView(view, COVER_SCREEN_PARAMS);
        view.setVisibility(View.INVISIBLE);
        // Send the animate message.
        final HashMap map = new HashMap();
        map.put("view", view);
        // Load the url after the AnimatingView has captured the picture. This
        // prevents any bad layout or bad scale from being used during
        // animation.
        if (url != null) {
            dismissSubWindow(tab);
            tab.getWebView().loadUrl(url);
        }
        map.put("msg", msg);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(
                ANIMATE_FROM_OVERVIEW, newTab ? 1 : 0, 0, map), delay);
        // Increment the count to indicate that we are in an animation.
        mAnimationCount++;
        // Remove the listener so we don't get any more tab changes.
        mTabOverview.setListener(null);
        mTabListener = null;
        // Make the menu empty until the animation completes.
        mMenuState = EMPTY_MENU;

    }

    // 500ms animation with 800ms delay
    private static final int TAB_ANIMATION_DURATION = 500;
    private static final int TAB_OVERVIEW_DELAY     = 800;

    // Called by TabControl when a tab is requesting focus
    /* package */ void showTab(TabControl.Tab t) {
        showTab(t, null);
    }

    private void showTab(TabControl.Tab t, String url) {
        // Disallow focus change during a tab animation.
        if (mAnimationCount > 0) {
            return;
        }
        int delay = 0;
        if (mTabOverview == null) {
            // Add a delay so the tab overview can be shown before the second
            // animation begins.
            delay = TAB_ANIMATION_DURATION + TAB_OVERVIEW_DELAY;
            tabPicker(false, mTabControl.getTabIndex(t), false);
        }
        sendAnimateFromOverview(t, false, url, delay, null);
    }

    // This method does a ton of stuff. It will attempt to create a new tab
    // if we haven't reached MAX_TABS. Otherwise it uses the current tab. If
    // url isn't null, it will load the given url. If the tab overview is not
    // showing, it will animate to the tab overview, create a new tab and
    // animate away from it. After the animation completes, it will dispatch
    // the given Message. If the tab overview is already showing (i.e. this
    // method is called from TabListener.onClick(), the method will animate
    // away from the tab overview.
    private void openTabAndShow(String url, final Message msg,
            boolean closeOnExit, String appId) {
        final boolean newTab = mTabControl.getTabCount() != TabControl.MAX_TABS;
        final TabControl.Tab currentTab = mTabControl.getCurrentTab();
        if (newTab) {
            int delay = 0;
            // If the tab overview is up and there are animations, just load
            // the url.
            if (mTabOverview != null && mAnimationCount > 0) {
                if (url != null) {
                    // We should not have a msg here since onCreateWindow
                    // checks the animation count and every other caller passes
                    // null.
                    assert msg == null;
                    // just dismiss the subwindow and load the given url.
                    dismissSubWindow(currentTab);
                    currentTab.getWebView().loadUrl(url);
                }
            } else {
                // show mTabOverview if it is not there.
                if (mTabOverview == null) {
                    // We have to delay the animation from the tab picker by the
                    // length of the tab animation. Add a delay so the tab
                    // overview can be shown before the second animation begins.
                    delay = TAB_ANIMATION_DURATION + TAB_OVERVIEW_DELAY;
                    tabPicker(false, ImageGrid.NEW_TAB, false);
                }
                // Animate from the Tab overview after any animations have
                // finished.
                sendAnimateFromOverview(
                        mTabControl.createNewTab(closeOnExit, appId, url), true,
                        url, delay, msg);
            }
        } else if (url != null) {
            // We should not have a msg here.
            assert msg == null;
            if (mTabOverview != null && mAnimationCount == 0) {
                sendAnimateFromOverview(currentTab, false, url,
                        TAB_OVERVIEW_DELAY, null);
            } else {
                // Get rid of the subwindow if it exists
                dismissSubWindow(currentTab);
                // Load the given url.
                currentTab.getWebView().loadUrl(url);
            }
        }
    }

    private Animation createTabAnimation(final AnimatingView view,
            final View cell, boolean scaleDown) {
        final AnimationSet set = new AnimationSet(true);
        final float scaleX = (float) cell.getWidth() / view.getWidth();
        final float scaleY = (float) cell.getHeight() / view.getHeight();
        if (scaleDown) {
            set.addAnimation(new ScaleAnimation(1.0f, scaleX, 1.0f, scaleY));
            set.addAnimation(new TranslateAnimation(0, cell.getLeft(), 0,
                    cell.getTop()));
        } else {
            set.addAnimation(new ScaleAnimation(scaleX, 1.0f, scaleY, 1.0f));
            set.addAnimation(new TranslateAnimation(cell.getLeft(), 0,
                    cell.getTop(), 0));
        }
        set.setDuration(TAB_ANIMATION_DURATION);
        set.setInterpolator(new DecelerateInterpolator());
        return set;
    }

    // Animate to the tab overview. currentIndex tells us which position to
    // animate to and newIndex is the position that should be selected after
    // the animation completes.
    // If remove is true, after the animation stops, a confirmation dialog will
    // be displayed to the user.
    private void animateToTabOverview(final int newIndex, final boolean remove,
            final AnimatingView view) {
        // Find the view in the ImageGrid allowing for the "New Tab" cell.
        int position = mTabControl.getTabIndex(view.mTab);
        if (!((ImageAdapter) mTabOverview.getAdapter()).maxedOut()) {
            position++;
        }

        // Offset the tab position with the first visible position to get a
        // number between 0 and 3.
        position -= mTabOverview.getFirstVisiblePosition();

        // Grab the view that we are going to animate to.
        final View v = mTabOverview.getChildAt(position);

        final Animation.AnimationListener l =
                new Animation.AnimationListener() {
                    public void onAnimationStart(Animation a) {
                        mTabOverview.requestFocus();
                        // Clear the listener so we don't trigger a tab
                        // selection.
                        mTabOverview.setListener(null);
                    }
                    public void onAnimationRepeat(Animation a) {}
                    public void onAnimationEnd(Animation a) {
                        // We are no longer animating so decrement the count.
                        mAnimationCount--;
                        // Make the view GONE so that it will not draw between
                        // now and when the Runnable is handled.
                        view.setVisibility(View.GONE);
                        // Post a runnable since we can't modify the view
                        // hierarchy during this callback.
                        mHandler.post(new Runnable() {
                            public void run() {
                                // Remove the AnimatingView.
                                mContentView.removeView(view);
                                if (mTabOverview != null) {
                                    // Make newIndex visible.
                                    mTabOverview.setCurrentIndex(newIndex);
                                    // Restore the listener.
                                    mTabOverview.setListener(mTabListener);
                                    // Change the menu to TAB_MENU if the
                                    // ImageGrid is interactive.
                                    if (mTabOverview.isLive()) {
                                        mMenuState = R.id.TAB_MENU;
                                        mTabOverview.requestFocus();
                                    }
                                }
                                // If a remove was requested, remove the tab.
                                if (remove) {
                                    // During a remove, the current tab has
                                    // already changed. Remember the current one
                                    // here.
                                    final TabControl.Tab currentTab =
                                            mTabControl.getCurrentTab();
                                    // Remove the tab at newIndex from
                                    // TabControl and the tab overview.
                                    final TabControl.Tab tab =
                                            mTabControl.getTab(newIndex);
                                    mTabControl.removeTab(tab);
                                    // Restore the current tab.
                                    if (currentTab != tab) {
                                        mTabControl.setCurrentTab(currentTab);
                                    }
                                    if (mTabOverview != null) {
                                        mTabOverview.remove(newIndex);
                                        // Make the current tab visible.
                                        mTabOverview.setCurrentIndex(
                                                mTabControl.getCurrentIndex());
                                    }
                                }
                            }
                        });
                    }
                };

        // Do an animation if there is a view to animate to.
        if (v != null) {
            // Create our animation
            final Animation anim = createTabAnimation(view, v, true);
            anim.setAnimationListener(l);
            // Start animating
            view.startAnimation(anim);
        } else {
            // If something goes wrong and we didn't find a view to animate to,
            // just do everything here.
            l.onAnimationStart(null);
            l.onAnimationEnd(null);
        }
    }

    // Animate from the tab picker. The index supplied is the index to animate
    // from.
    private void animateFromTabOverview(final AnimatingView view,
            final boolean newTab, final Message msg) {
        // firstVisible is the first visible tab on the screen.  This helps
        // to know which corner of the screen the selected tab is.
        int firstVisible = mTabOverview.getFirstVisiblePosition();
        // tabPosition is the 0-based index of of the tab being opened
        int tabPosition = mTabControl.getTabIndex(view.mTab);
        if (!((ImageAdapter) mTabOverview.getAdapter()).maxedOut()) {
            // Add one to make room for the "New Tab" cell.
            tabPosition++;
        }
        // If this is a new tab, animate from the "New Tab" cell.
        if (newTab) {
            tabPosition = 0;
        }
        // Location corresponds to the four corners of the screen.
        // A new tab or 0 is upper left, 0 for an old tab is upper
        // right, 1 is lower left, and 2 is lower right
        int location = tabPosition - firstVisible;

        // Find the view at this location.
        final View v = mTabOverview.getChildAt(location);

        // Wait until the animation completes to replace the AnimatingView.
        final Animation.AnimationListener l =
                new Animation.AnimationListener() {
                    public void onAnimationStart(Animation a) {}
                    public void onAnimationRepeat(Animation a) {}
                    public void onAnimationEnd(Animation a) {
                        mHandler.post(new Runnable() {
                            public void run() {
                                mContentView.removeView(view);
                                // Dismiss the tab overview. If the cell at the
                                // given location is null, set the fade
                                // parameter to true.
                                dismissTabOverview(v == null);
                                TabControl.Tab t =
                                        mTabControl.getCurrentTab();
                                mMenuState = R.id.MAIN_MENU;
                                // Resume regular updates.
                                t.getWebView().resumeTimers();
                                // Dispatch the message after the animation
                                // completes.
                                if (msg != null) {
                                    msg.sendToTarget();
                                }
                                // The animation is done and the tab overview is
                                // gone so allow key events and other animations
                                // to begin.
                                mAnimationCount--;
                                // Reset all the title bar info.
                                resetTitle();
                            }
                        });
                    }
                };

        if (v != null) {
            final Animation anim = createTabAnimation(view, v, false);
            // Set the listener and start animating
            anim.setAnimationListener(l);
            view.startAnimation(anim);
            // Make the view VISIBLE during the animation.
            view.setVisibility(View.VISIBLE);
        } else {
            // Go ahead and do all the cleanup.
            l.onAnimationEnd(null);
        }
    }

    // Dismiss the tab overview applying a fade if needed.
    private void dismissTabOverview(final boolean fade) {
        if (fade) {
            AlphaAnimation anim = new AlphaAnimation(1.0f, 0.0f);
            anim.setDuration(500);
            anim.startNow();
            mTabOverview.startAnimation(anim);
        }
        // Just in case there was a problem with animating away from the tab
        // overview
        WebView current = mTabControl.getCurrentWebView();
        if (current != null) {
            current.setVisibility(View.VISIBLE);
        } else {
            Log.e(LOGTAG, "No current WebView in dismissTabOverview");
        }
        // Make the sub window container visible.
        if (mTabControl.getCurrentSubWindow() != null) {
            mTabControl.getCurrentTab().getSubWebViewContainer()
                    .setVisibility(View.VISIBLE);
        }
        mContentView.removeView(mTabOverview);
        mTabOverview.clear();
        mTabOverview = null;
        mTabListener = null;
    }

    private void openTab(String url) {
        if (mSettings.openInBackground()) {
            TabControl.Tab t = mTabControl.createNewTab();
            if (t != null) {
                t.getWebView().loadUrl(url);
            }
        } else {
            openTabAndShow(url, null, false, null);
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
            setUrlTitle(item.getUrl(), item.getTitle());
            setFavicon(item.getFavicon());
        } else {
            setUrlTitle(null, null);
            setFavicon(null);
        }
    }

    /**
     * Sets a title composed of the URL and the title string.
     * @param url The URL of the site being loaded.
     * @param title The title of the site being loaded.
     */
    private void setUrlTitle(String url, String title) {
        mUrl = url;
        mTitle = title;

        // While the tab overview is animating or being shown, block changes
        // to the title.
        if (mAnimationCount == 0 && mTabOverview == null) {
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
    private static String buildTitleUrl(String url) {
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
        // While the tab overview is animating or being shown, block changes to
        // the favicon.
        if (mAnimationCount > 0 || mTabOverview != null) {
            return;
        }
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

        if (Config.LOGV) {
            Log.v(LOGTAG, "BrowserActivity.revertLockIcon:" +
                  " revert lock icon to " + mLockIconType);
        }

        updateLockIconImage(mLockIconType);
    }

    private void switchTabs(int indexFrom, int indexToShow, boolean remove) {
        int delay = TAB_ANIMATION_DURATION + TAB_OVERVIEW_DELAY;
        // Animate to the tab picker, remove the current tab, then
        // animate away from the tab picker to the parent WebView.
        tabPicker(false, indexFrom, remove);
        // Change to the parent tab
        final TabControl.Tab tab = mTabControl.getTab(indexToShow);
        if (tab != null) {
            sendAnimateFromOverview(tab, false, null, delay, null);
        } else {
            // Increment this here so that no other animations can happen in
            // between the end of the tab picker transition and the beginning
            // of openTabAndShow. This has a matching decrement in the handler
            // of OPEN_TAB_AND_SHOW.
            mAnimationCount++;
            // Send a message to open a new tab.
            mHandler.sendMessageDelayed(
                    mHandler.obtainMessage(OPEN_TAB_AND_SHOW,
                        mSettings.getHomePage()), delay);
        }
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
                switchTabs(mTabControl.getCurrentIndex(),
                        mTabControl.getTabIndex(parent), true);
            } else {
                if (current.closeOnExit()) {
                    if (mTabControl.getTabCount() == 1) {
                        finish();
                        return;
                    }
                    // call pauseWebView() now, we won't be able to call it in
                    // onPause() as the WebView won't be valid.
                    pauseWebView();
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
        if (topWindow == null)
            return KeyTracker.State.NOT_TRACKING;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // During animations, block the back key so that other animations
            // are not triggered and so that we don't end up destroying all the
            // WebViews before finishing the animation.
            if (mAnimationCount > 0) {
                return KeyTracker.State.DONE_TRACKING;
            }
            if (stage == KeyTracker.Stage.LONG_REPEAT) {
                bookmarksOrHistoryPicker(true);
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
    private static final int ANIMATE_FROM_OVERVIEW   = 104;
    private static final int ANIMATE_TO_OVERVIEW     = 105;
    private static final int OPEN_TAB_AND_SHOW       = 106;
    private static final int CHECK_MEMORY            = 107;
    private static final int RELEASE_WAKELOCK        = 108;

    // Private handler for handling javascript and saving passwords
    private Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ANIMATE_FROM_OVERVIEW:
                    final HashMap map = (HashMap) msg.obj;
                    animateFromTabOverview((AnimatingView) map.get("view"),
                            msg.arg1 == 1, (Message) map.get("msg"));
                    break;

                case ANIMATE_TO_OVERVIEW:
                    animateToTabOverview(msg.arg1, msg.arg2 == 1,
                            (AnimatingView) msg.obj);
                    break;

                case OPEN_TAB_AND_SHOW:
                    // Decrement mAnimationCount before openTabAndShow because
                    // the method relies on the value being 0 to start the next
                    // animation.
                    mAnimationCount--;
                    openTabAndShow((String) msg.obj, null, false, null);
                    break;

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
                            openTab(url);
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

                case CHECK_MEMORY:
                    // reschedule to check memory condition
                    mHandler.removeMessages(CHECK_MEMORY);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage
                            (CHECK_MEMORY), CHECK_MEMORY_INTERVAL);
                    checkMemory();
                    break;

                case RELEASE_WAKELOCK:
                    if (mWakeLock.isHeld()) {
                        mWakeLock.release();
                    }
                    break;
            }
        }
    };

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

    private void updateIcon(String url, Bitmap icon) {
        if (icon != null) {
            BrowserBookmarksAdapter.updateBookmarkFavicon(mResolver,
                    url, icon);
        }
        setFavicon(icon);
    }

    private final WebViewClient mWebViewClient = new WebViewClient() {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            resetLockIcon(url);
            setUrlTitle(url, null);
            // Call updateIcon instead of setFavicon so the bookmark
            // database can be updated.
            updateIcon(url, favicon);

            if (mSettings.isTracing() == true) {
                // FIXME: we should save the trace file somewhere other than data.
                // I can't use "/tmp" as it competes for system memory.
                File file = getDir("browserTrace", 0);
                String baseDir = file.getPath();
                if (!baseDir.endsWith(File.separator)) baseDir += File.separator;
                String host;
                try {
                    WebAddress uri = new WebAddress(url);
                    host = uri.mHost;
                } catch (android.net.ParseException ex) {
                    host = "unknown_host";
                }
                host = host.replace('.', '_');
                baseDir = baseDir + host;
                file = new File(baseDir+".data");
                if (file.exists() == true) {
                    file.delete();
                }
                file = new File(baseDir+".key");
                if (file.exists() == true) {
                    file.delete();
                }
                mInTrace = true;
                Debug.startMethodTracing(baseDir, 8 * 1024 * 1024);
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
                // if onResume() has been called, resumeWebView() does nothing.
                resumeWebView();
            }

            // reset sync timer to avoid sync starts during loading a page
            CookieSyncManager.getInstance().resetSync();

            mInLoad = true;
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

            // schedule to check memory condition
            mHandler.sendMessageDelayed(mHandler.obtainMessage(CHECK_MEMORY),
                    CHECK_MEMORY_INTERVAL);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // Reset the title and icon in case we stopped a provisional
            // load.
            resetTitleAndIcon(view);

            // Update the lock icon image only once we are done loading
            updateLockIconImage(mLockIconType);

            // Performance probe
            if (false) {
                long[] sysCpu = new long[7];
                if (Process.readProcFile("/proc/stat", SYSTEM_CPU_FORMAT, null,
                        sysCpu, null)) {
                    String uiInfo = "UI thread used "
                            + (SystemClock.currentThreadTimeMillis() - mUiStart)
                            + " ms";
                    if (Config.LOGD) {
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
                    if (Config.LOGD) {
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
                        if (Config.LOGD) {
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
                // pauseWebView() will do nothing and return false if onPause()
                // is not called yet.
                if (pauseWebView()) {
                    if (mWakeLock.isHeld()) {
                        mHandler.removeMessages(RELEASE_WAKELOCK);
                        mWakeLock.release();
                    }
                }
            }

            mHandler.removeMessages(CHECK_MEMORY);
            checkMemory();
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

            Uri uri;
            try {
                uri = Uri.parse(url);
            } catch (IllegalArgumentException ex) {
                return false;
            }

            // check whether other activities want to handle this url
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
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
                        if (Config.LOGV) {
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
                    err == EventHandler.FILE_NOT_FOUND_ERROR ?
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
            if (errorCode != EventHandler.ERROR_LOOKUP &&
                    errorCode != EventHandler.ERROR_CONNECT &&
                    errorCode != EventHandler.ERROR_BAD_URL &&
                    errorCode != EventHandler.ERROR_UNSUPPORTED_SCHEME &&
                    errorCode != EventHandler.FILE_ERROR) {
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
                // openTabAndShow will dispatch the message after creating the
                // new WebView. This will prevent another request from coming
                // in during the animation.
                openTabAndShow(null, msg, false, null);
                parent.addChildTab(mTabControl.getCurrentTab());
                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) msg.obj;
                transport.setWebView(mTabControl.getCurrentWebView());
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, final boolean dialog,
                final boolean userGesture, final Message resultMsg) {
            // Ignore these requests during tab animations or if the tab
            // overview is showing.
            if (mAnimationCount > 0 || mTabOverview != null) {
                return false;
            }
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
                // createWindow will call openTabAndShow for new Windows and
                // that will call tabPicker which will increment
                // mAnimationCount.
                createWindow(dialog, resultMsg);
                return true;
            }

            // Allow the popup and create the appropriate window.
            final AlertDialog.OnClickListener allowListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d,
                                int which) {
                            // Same comment as above for setting
                            // mAnimationCount.
                            createWindow(dialog, resultMsg);
                            // Since we incremented mAnimationCount while the
                            // dialog was up, we have to decrement it here.
                            mAnimationCount--;
                        }
                    };

            // Block the popup by returning a null WebView.
            final AlertDialog.OnClickListener blockListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            resultMsg.sendToTarget();
                            // We are not going to trigger an animation so
                            // unblock keys and animation requests.
                            mAnimationCount--;
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
            // We want to increment mAnimationCount here to prevent a
            // potential race condition. If the user allows a pop-up from a
            // site and that pop-up then triggers another pop-up, it is
            // possible to get the BACK key between here and when the dialog
            // appears.
            mAnimationCount++;
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            final int currentIndex = mTabControl.getCurrentIndex();
            final TabControl.Tab parent =
                    mTabControl.getCurrentTab().getParentTab();
            if (parent != null) {
                // JavaScript can only close popup window.
                switchTabs(currentIndex, mTabControl.getTabIndex(parent), true);
            }
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            // Block progress updates to the title bar while the tab overview
            // is animating or being displayed.
            if (mAnimationCount == 0 && mTabOverview == null) {
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
            String url = view.getOriginalUrl();

            // here, if url is null, we want to reset the title
            setUrlTitle(url, title);

            if (url == null ||
                url.length() >= SQLiteDatabase.SQLITE_MAX_LIKE_PATTERN_LENGTH) {
                return;
            }
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
                    if (Config.LOGV) {
                        Log.v(LOGTAG, "updating cursor");
                    }
                    // Current implementation of database only has one entry per
                    // url.
                    int titleIndex =
                            c.getColumnIndex(Browser.BookmarkColumns.TITLE);
                    c.updateString(titleIndex, title);
                    c.commitUpdates();
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
            updateIcon(view.getUrl(), icon);
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
                        || !contentDisposition.regionMatches(true, 0, "attachment", 0, 10)) {
            // query the package manager to see if there's a registered handler
            //     that matches.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), mimetype);
            if (getPackageManager().resolveActivity(intent,
                        PackageManager.MATCH_DEFAULT_ONLY) != null) {
                // someone knows how to handle this mime type with this scheme, don't download.
                try {
                    startActivity(intent);
                    return;
                } catch (ActivityNotFoundException ex) {
                    if (Config.LOGD) {
                        Log.d(LOGTAG, "activity not found for " + mimetype
                                + " over " + Uri.parse(url).getScheme(), ex);
                    }
                    // Best behavior is to fall back to a download in this case
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
        values.put(Downloads.URI, uri.toString());
        values.put(Downloads.COOKIE_DATA, cookies);
        values.put(Downloads.USER_AGENT, userAgent);
        values.put(Downloads.NOTIFICATION_PACKAGE,
                getPackageName());
        values.put(Downloads.NOTIFICATION_CLASS,
                BrowserDownloadPage.class.getCanonicalName());
        values.put(Downloads.VISIBILITY, Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        values.put(Downloads.MIMETYPE, mimetype);
        values.put(Downloads.FILENAME_HINT, filename);
        values.put(Downloads.DESCRIPTION, uri.getHost());
        if (contentLength > 0) {
            values.put(Downloads.TOTAL_BYTES, contentLength);
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
            if (Config.LOGV) {
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

        if (Config.LOGV) {
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
        // If the tab overview is animating or being shown, do not update the
        // lock icon.
        if (mAnimationCount == 0 && mTabOverview == null) {
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
                        openTab(data);
                    } else {
                        final TabControl.Tab currentTab =
                                mTabControl.getCurrentTab();
                        // If the Window overview is up and we are not in the
                        // middle of an animation, animate away from it to the
                        // current tab.
                        if (mTabOverview != null && mAnimationCount == 0) {
                            sendAnimateFromOverview(currentTab, false, data,
                                    TAB_OVERVIEW_DELAY, null);
                        } else {
                            dismissSubWindow(currentTab);
                            if (data != null && data.length() != 0) {
                                getTopWindow().loadUrl(data);
                            }
                        }
                    }
                }
                break;
            default:
                break;
        }
        getTopWindow().requestFocus();
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

    /**
     * Handle results from Tab Switcher mTabOverview tool
     */
    private class TabListener implements ImageGrid.Listener {
        public void remove(int position) {
            // Note: Remove is not enabled if we have only one tab.
            if (Config.DEBUG && mTabControl.getTabCount() == 1) {
                throw new AssertionError();
            }

            // Remember the current tab.
            TabControl.Tab current = mTabControl.getCurrentTab();
            final TabControl.Tab remove = mTabControl.getTab(position);
            mTabControl.removeTab(remove);
            // If we removed the current tab, use the tab at position - 1 if
            // possible.
            if (current == remove) {
                // If the user removes the last tab, act like the New Tab item
                // was clicked on.
                if (mTabControl.getTabCount() == 0) {
                    current = mTabControl.createNewTab();
                    sendAnimateFromOverview(current, true,
                            mSettings.getHomePage(), TAB_OVERVIEW_DELAY, null);
                } else {
                    final int index = position > 0 ? (position - 1) : 0;
                    current = mTabControl.getTab(index);
                }
            }

            // The tab overview could have been dismissed before this method is
            // called.
            if (mTabOverview != null) {
                // Remove the tab and change the index.
                mTabOverview.remove(position);
                mTabOverview.setCurrentIndex(mTabControl.getTabIndex(current));
            }

            // Only the current tab ensures its WebView is non-null. This
            // implies that we are reloading the freed tab.
            mTabControl.setCurrentTab(current);
        }
        public void onClick(int index) {
            // Change the tab if necessary.
            // Index equals ImageGrid.CANCEL when pressing back from the tab
            // overview.
            if (index == ImageGrid.CANCEL) {
                index = mTabControl.getCurrentIndex();
                // The current index is -1 if the current tab was removed.
                if (index == -1) {
                    // Take the last tab as a fallback.
                    index = mTabControl.getTabCount() - 1;
                }
            }

            // Clear all the data for tab picker so next time it will be
            // recreated.
            mTabControl.wipeAllPickerData();

            // NEW_TAB means that the "New Tab" cell was clicked on.
            if (index == ImageGrid.NEW_TAB) {
                openTabAndShow(mSettings.getHomePage(), null, false, null);
            } else {
                sendAnimateFromOverview(mTabControl.getTab(index),
                        false, null, 0, null);
            }
        }
    }

    // A fake View that draws the WebView's picture with a fast zoom filter.
    // The View is used in case the tab is freed during the animation because
    // of low memory.
    private static class AnimatingView extends View {
        private static final int ZOOM_BITS = Paint.FILTER_BITMAP_FLAG |
                Paint.DITHER_FLAG | Paint.SUBPIXEL_TEXT_FLAG;
        private static final DrawFilter sZoomFilter =
                new PaintFlagsDrawFilter(ZOOM_BITS, Paint.LINEAR_TEXT_FLAG);
        private final Picture mPicture;
        private final float   mScale;
        private final int     mScrollX;
        private final int     mScrollY;
        final TabControl.Tab  mTab;

        AnimatingView(Context ctxt, TabControl.Tab t) {
            super(ctxt);
            mTab = t;
            // Use the top window in the animation since the tab overview will
            // display the top window in each cell.
            final WebView w = t.getTopWindow();
            mPicture = w.capturePicture();
            mScale = w.getScale() / w.getWidth();
            mScrollX = w.getScrollX();
            mScrollY = w.getScrollY();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.drawColor(Color.WHITE);
            if (mPicture != null) {
                canvas.setDrawFilter(sZoomFilter);
                float scale = getWidth() * mScale;
                canvas.scale(scale, scale);
                canvas.translate(-mScrollX, -mScrollY);
                canvas.drawPicture(mPicture);
            }
            canvas.restore();
        }
    }

    /**
     *  Open the tab picker. This function will always use the current tab in
     *  its animation.
     *  @param stay boolean stating whether the tab picker is to remain open
     *          (in which case it needs a listener and its menu) or not.
     *  @param index The index of the tab to show as the selection in the tab
     *               overview.
     *  @param remove If true, the tab at index will be removed after the
     *                animation completes.
     */
    private void tabPicker(final boolean stay, final int index,
            final boolean remove) {
        if (mTabOverview != null) {
            return;
        }

        int size = mTabControl.getTabCount();

        TabListener l = null;
        if (stay) {
            l = mTabListener = new TabListener();
        }
        mTabOverview = new ImageGrid(this, stay, l);

        for (int i = 0; i < size; i++) {
            final TabControl.Tab t = mTabControl.getTab(i);
            mTabControl.populatePickerData(t);
            mTabOverview.add(t);
        }

        // Tell the tab overview to show the current tab, the tab overview will
        // handle the "New Tab" case.
        int currentIndex = mTabControl.getCurrentIndex();
        mTabOverview.setCurrentIndex(currentIndex);

        // Attach the tab overview.
        mContentView.addView(mTabOverview, COVER_SCREEN_PARAMS);

        // Create a fake AnimatingView to animate the WebView's picture.
        final TabControl.Tab current = mTabControl.getCurrentTab();
        final AnimatingView v = new AnimatingView(this, current);
        mContentView.addView(v, COVER_SCREEN_PARAMS);
        removeTabFromContentView(current);
        // Pause timers to get the animation smoother.
        current.getWebView().pauseTimers();

        // Send a message so the tab picker has a chance to layout and get
        // positions for all the cells.
        mHandler.sendMessage(mHandler.obtainMessage(ANIMATE_TO_OVERVIEW,
                index, remove ? 1 : 0, v));
        // Setting this will indicate that we are animating to the overview. We
        // set it here to prevent another request to animate from coming in
        // between now and when ANIMATE_TO_OVERVIEW is handled.
        mAnimationCount++;
        // Always change the title bar to the window overview title while
        // animating.
        getWindow().setFeatureDrawable(Window.FEATURE_LEFT_ICON, null);
        getWindow().setFeatureDrawable(Window.FEATURE_RIGHT_ICON, null);
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS,
                Window.PROGRESS_VISIBILITY_OFF);
        setTitle(R.string.tab_picker_title);
        // Make the menu empty until the animation completes.
        mMenuState = EMPTY_MENU;
    }

    private void bookmarksOrHistoryPicker(boolean startWithHistory) {
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
        intent.putExtra("maxTabsOpen",
                mTabControl.getTabCount() >= TabControl.MAX_TABS);
        if (startWithHistory) {
            intent.putExtra(CombinedBookmarkHistoryActivity.STARTING_TAB,
                    CombinedBookmarkHistoryActivity.HISTORY_TAB);
        }
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

    private void checkMemory() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE))
                .getMemoryInfo(mi);
        // FIXME: mi.lowMemory is too aggressive, use (mi.availMem <
        // mi.threshold) for now
        //        if (mi.lowMemory) {
        if (mi.availMem < mi.threshold) {
            Log.w(LOGTAG, "Browser is freeing memory now because: available="
                            + (mi.availMem / 1024) + "K threshold="
                            + (mi.threshold / 1024) + "K");
            mTabControl.freeMemory();
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

    static final Pattern ACCEPTED_URI_SCHEMA = Pattern.compile(
            "(?i)" + // switch on case insensitive matching
            "(" +    // begin group for schema
            "(?:http|https|file):\\/\\/" +
            "|(?:data|about|content|javascript):" +
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
            if (hasSpace) {
                inUrl = inUrl.replace(" ", "%20");
            }
            // force scheme to lowercase
            String scheme = matcher.group(1);
            String lcScheme = scheme.toLowerCase();
            if (!lcScheme.equals(scheme)) {
                return lcScheme + matcher.group(2);
            }
            return inUrl;
        }
        if (hasSpace) {
            // FIXME: quick search, need to be customized by setting
            if (inUrl.length() > 2 && inUrl.charAt(1) == ' ') {
                // FIXME: Is this the correct place to add to searches?
                // what if someone else calls this function?
                char char0 = inUrl.charAt(0);

                if (char0 == 'g') {
                    Browser.addSearchUrl(mResolver, inUrl);
                    return composeSearchUrl(inUrl.substring(2));

                } else if (char0 == 'w') {
                    Browser.addSearchUrl(mResolver, inUrl);
                    return URLUtil.composeSearchUrl(inUrl.substring(2),
                            QuickSearch_W,
                            QUERY_PLACE_HOLDER);

                } else if (char0 == 'd') {
                    Browser.addSearchUrl(mResolver, inUrl);
                    return URLUtil.composeSearchUrl(inUrl.substring(2),
                            QuickSearch_D,
                            QUERY_PLACE_HOLDER);

                } else if (char0 == 'l') {
                    Browser.addSearchUrl(mResolver, inUrl);
                    // FIXME: we need location in this case
                    return URLUtil.composeSearchUrl(inUrl.substring(2),
                            QuickSearch_L,
                            QUERY_PLACE_HOLDER);
                }
            }
        } else {
            if (Regex.WEB_URL_PATTERN.matcher(inUrl).matches()) {
                return URLUtil.guessUrl(inUrl);
            }
        }

        Browser.addSearchUrl(mResolver, inUrl);
        return composeSearchUrl(inUrl);
    }

    /* package */ String composeSearchUrl(String search) {
        return URLUtil.composeSearchUrl(search, QuickSearch_G,
                QUERY_PLACE_HOLDER);
    }

    /* package */void setBaseSearchUrl(String url) {
        if (url == null || url.length() == 0) {
            /*
             * get the google search url based on the SIM. Default is US. NOTE:
             * This code uses resources to optionally select the search Uri,
             * based on the MCC value from the SIM. The default string will most
             * likely be fine. It is parameterized to accept info from the
             * Locale, the language code is the first parameter (%1$s) and the
             * country code is the second (%2$s). This code must function in the
             * same way as a similar lookup in
             * com.android.googlesearch.SuggestionProvider#onCreate(). If you
             * change either of these functions, change them both. (The same is
             * true for the underlying resource strings, which are stored in
             * mcc-specific xml files.)
             */
            Locale l = Locale.getDefault();
            QuickSearch_G = getResources().getString(
                    R.string.google_search_base, l.getLanguage(),
                    l.getCountry().toLowerCase())
                    + "client=ms-"
                    + Partner.getString(this.getContentResolver(), Partner.CLIENT_ID)
                    + "&source=android-" + GOOGLE_SEARCH_SOURCE_SUGGEST + "&q=%s";
        } else {
            QuickSearch_G = url;
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
    private ImageGrid       mTabOverview;

    // FIXME, temp address onPrepareMenu performance problem. When we move everything out of
    // view, we should rewrite this.
    private int mCurrentMenuState = 0;
    private int mMenuState = R.id.MAIN_MENU;
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
    // We may provide UI to customize these
    // Google search from the browser
    static String QuickSearch_G;
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

    private TabListener mTabListener;

    private String mLastEnteredUrl;

    private PowerManager.WakeLock mWakeLock;
    private final static int WAKELOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes

    private Toast mStopToast;

    // Used during animations to prevent other animations from being triggered.
    // A count is used since the animation to and from the Window overview can
    // overlap. A count of 0 means no animation where a count of > 0 means
    // there are animations in progress.
    private int mAnimationCount;

    // As the ids are dynamically created, we can't guarantee that they will
    // be in sequence, so this static array maps ids to a window number.
    final static private int[] WINDOW_SHORTCUT_ID_ARRAY =
    { R.id.window_one_menu_id, R.id.window_two_menu_id, R.id.window_three_menu_id,
      R.id.window_four_menu_id, R.id.window_five_menu_id, R.id.window_six_menu_id,
      R.id.window_seven_menu_id, R.id.window_eight_menu_id };

    // monitor platform changes
    private IntentFilter mNetworkStateChangedFilter;
    private BroadcastReceiver mNetworkStateIntentReceiver;

    // activity requestCode
    final static int COMBO_PAGE             = 1;
    final static int DOWNLOAD_PAGE          = 2;
    final static int PREFERENCES_PAGE       = 3;

    // the frenquency of checking whether system memory is low
    final static int CHECK_MEMORY_INTERVAL = 30000;     // 30 seconds
}
