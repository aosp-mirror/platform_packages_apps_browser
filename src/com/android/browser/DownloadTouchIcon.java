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

class DownloadTouchIcon extends AsyncTask<String, Void, Bitmap> {
    private final ContentResolver mContentResolver;
    private final Cursor mCursor;
    private final String mOriginalUrl;
    private final String mUrl;
    private final String mUserAgent;
    /* package */ BrowserActivity mActivity;

    public DownloadTouchIcon(BrowserActivity activity, ContentResolver cr,
            Cursor c, WebView view) {
        mActivity = activity;
        mContentResolver = cr;
        mCursor = c;
        // Store these in case they change.
        mOriginalUrl = view.getOriginalUrl();
        mUrl = view.getUrl();
        mUserAgent = view.getSettings().getUserAgentString();
    }

    public DownloadTouchIcon(ContentResolver cr, Cursor c, String url) {
        mActivity = null;
        mContentResolver = cr;
        mCursor = c;
        mOriginalUrl = null;
        mUrl = url;
        mUserAgent = null;
    }

    @Override
    public Bitmap doInBackground(String... values) {
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
                        return icon;
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
        return null;
    }

    @Override
    protected void onCancelled() {
        if (mCursor != null) {
            mCursor.close();
        }
    }

    @Override
    public void onPostExecute(Bitmap icon) {
        // Do this first in case the download failed.
        if (mActivity != null) {
            // Remove the touch icon loader from the BrowserActivity.
            mActivity.mTouchIconLoader = null;
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
        mCursor.close();
    }
}
