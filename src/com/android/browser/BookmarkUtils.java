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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.BrowserContract;
import android.text.TextUtils;

class BookmarkUtils {
    private final static String LOGTAG = "BookmarkUtils";

    // XXX: There is no public string defining this intent so if Home changes the value, we
    // have to update this string.
    private static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

    enum BookmarkIconType {
        ICON_INSTALLABLE_WEB_APP, // Icon for an installable web app (launches WebAppRuntime).
        ICON_HOME_SHORTCUT        // Icon for a shortcut on the home screen (launches Browser).
    };

    /**
     * Creates an icon to be associated with this bookmark. If available, the apple touch icon
     * will be used, else we draw our own depending on the type of "bookmark" being created.
     */
    static Bitmap createIcon(Context context, Bitmap touchIcon, Bitmap favicon,
            BookmarkIconType type) {
        int iconDimension = context.getResources().getDimensionPixelSize(
                android.R.dimen.app_icon_size);

        Bitmap bm = Bitmap.createBitmap(iconDimension, iconDimension, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        Rect iconBounds = new Rect(0, 0, bm.getWidth(), bm.getHeight());

        // Use the apple-touch-icon if available
        if (touchIcon != null) {
            drawTouchIconToCanvas(touchIcon, canvas, iconBounds);
        } else {
            // No touch icon so create our own.
            // Set the background based on the type of shortcut (either webapp or home shortcut).
            Bitmap icon = getIconBackground(context, type);

            if (icon != null) {
                // Now draw the correct icon background into our new bitmap.
                canvas.drawBitmap(icon, null, iconBounds, null);
            }

            // If we have a favicon, overlay it in a nice rounded white box on top of the
            // background.
            if (favicon != null) {
                drawFaviconToCanvas(favicon, canvas, iconBounds,
                        context.getResources().getDisplayMetrics().density);
            }
        }
        return bm;
    }

    /**
     * Convenience method for creating an intent that will add a shortcut to the home screen.
     */
    static Intent createAddToHomeIntent(Context context, String url, String title,
            Bitmap touchIcon, Bitmap favicon) {
        Intent i = new Intent(INSTALL_SHORTCUT);
        Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        long urlHash = url.hashCode();
        long uniqueId = (urlHash << 32) | shortcutIntent.hashCode();
        shortcutIntent.putExtra(Browser.EXTRA_APPLICATION_ID, Long.toString(uniqueId));
        i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        i.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        i.putExtra(Intent.EXTRA_SHORTCUT_ICON, createIcon(context, touchIcon, favicon,
                BookmarkIconType.ICON_HOME_SHORTCUT));

        // Do not allow duplicate items
        i.putExtra("duplicate", false);
        return i;
    }

    private static Bitmap getIconBackground(Context context, BookmarkIconType type) {
        if (type == BookmarkIconType.ICON_HOME_SHORTCUT) {
            // Want to create a shortcut icon on the homescreen, so the icon
            // background is the red bookmark.
            return BitmapFactory.decodeResource(context.getResources(),
                    R.mipmap.ic_launcher_shortcut_browser_bookmark);
        } else if (type == BookmarkIconType.ICON_INSTALLABLE_WEB_APP) {
            // Use the web browser icon as the background for the icon for an installable
            // web app.
            return BitmapFactory.decodeResource(context.getResources(),
                    R.mipmap.ic_launcher_browser);
        }
        return null;
    }

    private static void drawTouchIconToCanvas(Bitmap touchIcon, Canvas canvas, Rect iconBounds) {
        Rect src = new Rect(0, 0, touchIcon.getWidth(), touchIcon.getHeight());

        // Paint used for scaling the bitmap and drawing the rounded rect.
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(touchIcon, src, iconBounds, paint);

        // Construct a path from a round rect. This will allow drawing with
        // an inverse fill so we can punch a hole using the round rect.
        Path path = new Path();
        path.setFillType(Path.FillType.INVERSE_WINDING);
        RectF rect = new RectF(iconBounds);
        rect.inset(1, 1);
        path.addRoundRect(rect, 8f, 8f, Path.Direction.CW);

        // Reuse the paint and clear the outside of the rectangle.
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPath(path, paint);
    }

    private static void drawFaviconToCanvas(Bitmap favicon, Canvas canvas, Rect iconBounds,
            float density) {
        // Make a Paint for the white background rectangle and for
        // filtering the favicon.
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setColor(Color.WHITE);

        // Create a rectangle that is slightly wider than the favicon
        final float iconSize = 16 * density; // 16x16 favicon
        final float padding = 2 * density; // white padding around icon
        final float rectSize = iconSize + 2 * padding;
        final float x = iconBounds.exactCenterX() - (rectSize / 2);
        // Note: Subtract 2 dip from the y position since the box is
        // slightly higher than center. Use padding since it is already
        // 2 * density.
        final float y = iconBounds.exactCenterY() - (rectSize / 2) - padding;
        RectF r = new RectF(x, y, x + rectSize, y + rectSize);

        // Draw a white rounded rectangle behind the favicon
        canvas.drawRoundRect(r, 2, 2, p);

        // Draw the favicon in the same rectangle as the rounded
        // rectangle but inset by the padding
        // (results in a 16x16 favicon).
        r.inset(padding, padding);
        canvas.drawBitmap(favicon, null, r, p);
    }

    /* package */ static Uri getBookmarksUri(Context context) {
        Uri uri = BrowserContract.Bookmarks.CONTENT_URI;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String accountType = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_TYPE, null);
        String accountName = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
            uri = uri.buildUpon()
                    .appendQueryParameter(BrowserContract.Bookmarks.PARAM_ACCOUNT_NAME, accountName)
                    .appendQueryParameter(BrowserContract.Bookmarks.PARAM_ACCOUNT_TYPE, accountType)
                    .build();
        }
        return uri;
    }
};
