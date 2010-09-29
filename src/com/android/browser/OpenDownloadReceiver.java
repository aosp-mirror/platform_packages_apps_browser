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

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Downloads;
import android.widget.Toast;

import java.io.File;

/**
 * This {@link BroadcastReceiver} handles clicks to notifications that
 * downloads from the browser are in progress/complete.  Clicking on an
 * in-progress or failed download will open the download manager.  Clicking on
 * a complete, successful download will open the file.
 */
public class OpenDownloadReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        ContentResolver cr = context.getContentResolver();
        Uri data = intent.getData();
        Cursor cursor = null;
        try {
            cursor = cr.query(data,
                    new String[] { Downloads.Impl._ID, Downloads.Impl._DATA,
                    Downloads.Impl.COLUMN_MIME_TYPE, Downloads.COLUMN_STATUS },
                    null, null, null);
            if (cursor.moveToFirst()) {
                String filename = cursor.getString(1);
                String mimetype = cursor.getString(2);
                String action = intent.getAction();
                if (Downloads.ACTION_NOTIFICATION_CLICKED.equals(action)) {
                    int status = cursor.getInt(3);
                    if (Downloads.isStatusCompleted(status)
                            && Downloads.isStatusSuccess(status)) {
                        Intent launchIntent = new Intent(Intent.ACTION_VIEW);
                        Uri path = Uri.parse(filename);
                        // If there is no scheme, then it must be a file
                        if (path.getScheme() == null) {
                            path = Uri.fromFile(new File(filename));
                        }
                        launchIntent.setDataAndType(path, mimetype);
                        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(launchIntent);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(context,
                                    R.string.download_no_application_title,
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Open the downloads page
                        Intent pageView = new Intent(
                                DownloadManager.ACTION_VIEW_DOWNLOADS);
                        pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(pageView);
                    }
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
