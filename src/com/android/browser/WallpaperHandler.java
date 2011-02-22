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

import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Handle setWallpaper requests
 *
 */
public class WallpaperHandler extends Thread
        implements OnMenuItemClickListener, DialogInterface.OnCancelListener {


    private static final String LOGTAG = "WallpaperHandler";

    private Context mContext;
    private URL mUrl;
    private ProgressDialog mWallpaperProgress;
    private boolean mCanceled = false;

    public WallpaperHandler(Context context, String url) {
        mContext = context;
        try {
            mUrl = new URL(url);
        } catch (MalformedURLException e) {
            mUrl = null;
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        mCanceled = true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mUrl != null) {
            // The user may have tried to set a image with a large file size as
            // their background so it may take a few moments to perform the
            // operation.
            // Display a progress spinner while it is working.
            mWallpaperProgress = new ProgressDialog(mContext);
            mWallpaperProgress.setIndeterminate(true);
            mWallpaperProgress.setMessage(mContext.getResources()
                    .getText(R.string.progress_dialog_setting_wallpaper));
            mWallpaperProgress.setCancelable(true);
            mWallpaperProgress.setOnCancelListener(this);
            mWallpaperProgress.show();
            start();
        }
        return true;
    }

    @Override
    public void run() {
        Drawable oldWallpaper =
                WallpaperManager.getInstance(mContext).getDrawable();
        try {
            // TODO: This will cause the resource to be downloaded again, when
            // we should in most cases be able to grab it from the cache. To fix
            // this we should query WebCore to see if we can access a cached
            // version and instead open an input stream on that. This pattern
            // could also be used in the download manager where the same problem
            // exists.
            InputStream inputstream = mUrl.openStream();
            if (inputstream != null) {
                WallpaperManager.getInstance(mContext).setStream(inputstream);
            }
        } catch (IOException e) {
            Log.e(LOGTAG, "Unable to set new wallpaper");
            // Act as though the user canceled the operation so we try to
            // restore the old wallpaper.
            mCanceled = true;
        }

        if (mCanceled) {
            // Restore the old wallpaper if the user cancelled whilst we were
            // setting
            // the new wallpaper.
            int width = oldWallpaper.getIntrinsicWidth();
            int height = oldWallpaper.getIntrinsicHeight();
            Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bm);
            oldWallpaper.setBounds(0, 0, width, height);
            oldWallpaper.draw(canvas);
            try {
                WallpaperManager.getInstance(mContext).setBitmap(bm);
            } catch (IOException e) {
                Log.e(LOGTAG, "Unable to restore old wallpaper.");
            }
            mCanceled = false;
        }

        if (mWallpaperProgress.isShowing()) {
            mWallpaperProgress.dismiss();
        }
    }
}
