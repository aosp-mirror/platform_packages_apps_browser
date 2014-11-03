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

import java.net.HttpURLConnection;
import java.net.URL;

import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.IOException;

/**
 * This class is used to pull down the http headers of a given URL so that
 * we can analyse the mimetype and make any correction needed before we give
 * the URL to the download manager.
 * This operation is needed when the user long-clicks on a link or image and
 * we don't know the mimetype. If the user just clicks on the link, we will
 * do the same steps of correcting the mimetype down in
 * android.os.webkit.LoadListener rather than handling it here.
 *
 */
class FetchUrlMimeType extends Thread {

    private final static String LOGTAG = "FetchUrlMimeType";

    private Context mContext;
    private DownloadManager.Request mRequest;
    private String mUri;
    private String mCookies;
    private String mUserAgent;

    public FetchUrlMimeType(Context context, DownloadManager.Request request,
            String uri, String cookies, String userAgent) {
        mContext = context.getApplicationContext();
        mRequest = request;
        mUri = uri;
        mCookies = cookies;
        mUserAgent = userAgent;
    }

    @Override
    public void run() {
        String mimeType = null;
        String contentDisposition = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(mUri);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");

            if (mUserAgent != null) {
                connection.addRequestProperty("User-Agent", mUserAgent);
            }

            if (mCookies != null && mCookies.length() > 0) {
                connection.addRequestProperty("Cookie", mCookies);
            }

            if (connection.getResponseCode() == 200) {
                mimeType = connection.getContentType();
                if (mimeType != null) {
                    final int semicolonIndex = mimeType.indexOf(';');
                    if (semicolonIndex != -1) {
                        mimeType = mimeType.substring(0, semicolonIndex);
                    }
                }

                contentDisposition = connection.getHeaderField("Content-Disposition");
            }
        } catch (IOException ioe) {
            Log.e(LOGTAG,"Download failed: " + ioe);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (mimeType != null) {
            if (mimeType.equalsIgnoreCase("text/plain") ||
                    mimeType.equalsIgnoreCase("application/octet-stream")) {
                String newMimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                MimeTypeMap.getFileExtensionFromUrl(mUri));
                if (newMimeType != null) {
                    mimeType = newMimeType;
                    mRequest.setMimeType(newMimeType);
                }
            }
            String filename = URLUtil.guessFileName(mUri, contentDisposition,
                    mimeType);
            mRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
        }

        // Start the download
        DownloadManager manager = (DownloadManager) mContext.getSystemService(
                Context.DOWNLOAD_SERVICE);
        manager.enqueue(mRequest);
    }

}
