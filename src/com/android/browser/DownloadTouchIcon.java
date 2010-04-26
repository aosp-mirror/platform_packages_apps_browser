/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.provider.Browser;
import android.webkit.WebView;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class DownloadTouchIcon extends AsyncTask<String, Void, Void> {
    private final ContentResolver mContentResolver;
    private Cursor mCursor;
    private final String mOriginalUrl;
    private final String mUrl;
    private final String mUserAgent;
    /* package */ Tab mTab;

    public DownloadTouchIcon(Tab tab, ContentResolver cr, WebView view) {
        mTab = tab;
        mContentResolver = cr;
        // Store these in case they change.
        mOriginalUrl = view.getOriginalUrl();
        mUrl = view.getUrl();
        mUserAgent = view.getSettings().getUserAgentString();
    }

    public DownloadTouchIcon(ContentResolver cr, String url) {
        mTab = null;
        mContentResolver = cr;
        mOriginalUrl = null;
        mUrl = url;
        mUserAgent = null;
    }

    @Override
    public Void doInBackground(String... values) {
        mCursor = BrowserBookmarksAdapter.queryBookmarksForUrl(mContentResolver,
                mOriginalUrl, mUrl, true);
        if (mCursor != null && mCursor.getCount() > 0) {
            String url = values[0];

            AndroidHttpClient client = AndroidHttpClient.newInstance(
                    mUserAgent);
            HttpGet request = new HttpGet(url);

            // Follow redirects
            HttpClientParams.setRedirecting(client.getParams(), true);

            try {
                HttpResponse response = client.execute(request);

                if (response.getStatusLine().getStatusCode() == 200) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        InputStream content = entity.getContent();
                        if (content != null) {
                            Bitmap icon = BitmapFactory.decodeStream(
                                    content, null, null);
                            storeIcon(icon);
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                request.abort();
            } catch (IOException ex) {
                request.abort();
            } finally {
                client.close();
            }
        }
        if (mCursor != null) {
            mCursor.close();
        }
        return null;
    }

    @Override
    protected void onCancelled() {
        if (mCursor != null) {
            mCursor.close();
        }
    }

    private void storeIcon(Bitmap icon) {
        // Do this first in case the download failed.
        if (mTab != null) {
            // Remove the touch icon loader from the BrowserActivity.
            mTab.mTouchIconLoader = null;
        }

        if (icon == null || mCursor == null || isCancelled()) {
            return;
        }

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        icon.compress(Bitmap.CompressFormat.PNG, 100, os);
        ContentValues values = new ContentValues();
        values.put(Browser.BookmarkColumns.TOUCH_ICON,
                os.toByteArray());

        if (mCursor.moveToFirst()) {
            do {
                mContentResolver.update(ContentUris.withAppendedId(
                        Browser.BOOKMARKS_URI, mCursor.getInt(0)),
                        values, null, null);
            } while (mCursor.moveToNext());
        }
    }
}
