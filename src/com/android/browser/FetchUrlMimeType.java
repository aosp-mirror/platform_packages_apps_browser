/*
 * Copyright (C) 2008 The Android Open Source Project
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

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.params.ConnRouteParams;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Proxy;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.IOException;

/**
 * This class is used to pull down the http headers of a given URL so that
 * we can analyse the mimetype and make any correction needed before we give
 * the URL to the download manager. The ContentValues class holds the
 * content that would be provided to the download manager, so that on
 * completion of checking the mimetype, we can issue the download to
 * the download manager.
 * This operation is needed when the user long-clicks on a link or image and
 * we don't know the mimetype. If the user just clicks on the link, we will
 * do the same steps of correcting the mimetype down in
 * android.os.webkit.LoadListener rather than handling it here.
 *
 */
class FetchUrlMimeType extends AsyncTask<ContentValues, String, ContentValues> {

    public static final String URI = "uri";
    public static final String USER_AGENT = "user_agent";
    public static final String COOKIE_DATA = "cookie_data";

    Activity mActivity;
    ContentValues mValues;
    DownloadManager.Request mRequest;

    public FetchUrlMimeType(Activity activity,
            DownloadManager.Request request) {
        mActivity = activity;
        mRequest = request;
    }

    @Override
    public ContentValues doInBackground(ContentValues... values) {
        mValues = values[0];

        // Check to make sure we have a URI to download
        String uri = mValues.getAsString(URI);
        if (uri == null || uri.length() == 0) {
            return null;
        }

        // User agent is likely to be null, though the AndroidHttpClient
        // seems ok with that.
        AndroidHttpClient client = AndroidHttpClient.newInstance(
                mValues.getAsString(USER_AGENT));
        HttpHost httpHost = Proxy.getPreferredHttpHost(mActivity, uri);
        if (httpHost != null) {
            ConnRouteParams.setDefaultProxy(client.getParams(), httpHost);
        }
        HttpHead request = new HttpHead(uri);

        String cookie = mValues.getAsString(COOKIE_DATA);
        if (cookie != null && cookie.length() > 0) {
            request.addHeader("Cookie", cookie);
        }

        HttpResponse response;
        ContentValues result = new ContentValues();
        try {
            response = client.execute(request);
            // We could get a redirect here, but if we do lets let
            // the download manager take care of it, and thus trust that
            // the server sends the right mimetype
            if (response.getStatusLine().getStatusCode() == 200) {
                Header header = response.getFirstHeader("Content-Type");
                if (header != null) {
                    String mimeType = header.getValue();
                    final int semicolonIndex = mimeType.indexOf(';');
                    if (semicolonIndex != -1) {
                        mimeType = mimeType.substring(0, semicolonIndex);
                    }
                    result.put("Content-Type", mimeType);
                }
                Header contentDispositionHeader = response.getFirstHeader("Content-Disposition");
                if (contentDispositionHeader != null) {
                    result.put("Content-Disposition", contentDispositionHeader.getValue());
                }
            }
        } catch (IllegalArgumentException ex) {
            request.abort();
        } catch (IOException ex) {
            request.abort();
        } finally {
            client.close();
        }

        return result;
    }

   @Override
    public void onPostExecute(ContentValues values) {
       final String mimeType = values.getAsString("Content-Type");
       final String contentDisposition = values.getAsString("Content-Disposition");
       if (mimeType != null) {
           String url = mValues.getAsString(URI);
           if (mimeType.equalsIgnoreCase("text/plain") ||
                   mimeType.equalsIgnoreCase("application/octet-stream")) {
               String newMimeType =
                       MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                           MimeTypeMap.getFileExtensionFromUrl(url));
               if (newMimeType != null) {
                   mRequest.setMimeType(newMimeType);
               }
           }
           String filename = URLUtil.guessFileName(url,
                   contentDisposition, mimeType);
           mRequest.setDestinationInExternalFilesDir(mActivity, null, filename);
       }

       // Start the download
       DownloadManager manager = (DownloadManager) mActivity.getSystemService(
               Context.DOWNLOAD_SERVICE);
       manager.enqueue(mRequest);
    }

}
