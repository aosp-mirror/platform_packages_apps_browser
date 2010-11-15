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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * This {@link BroadcastReceiver} handles clicks to notifications that
 * downloads from the browser are in progress/complete.  Clicking on an
 * in-progress or failed download will open the download manager.  Clicking on
 * a complete, successful download will open the file.
 */
public class OpenDownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
            openDownloadsPage(context);
            return;
        }
        long ids[] = intent.getLongArrayExtra(
                DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
        if (ids == null || ids.length == 0) {
            openDownloadsPage(context);
            return;
        }
        long id = ids[0];
        DownloadManager manager = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        Uri uri = manager.getUriForDownloadedFile(id);
        if (uri == null) {
            // Open the downloads page
            openDownloadsPage(context);
        } else {
            Intent launchIntent = new Intent(Intent.ACTION_VIEW);
            launchIntent.setDataAndType(uri, context.getContentResolver().getType(uri));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launchIntent);
            } catch (ActivityNotFoundException e) {
                openDownloadsPage(context);
            }
        }
    }

    /**
     * Open the Activity which shows a list of all downloads.
     * @param context
     */
    private void openDownloadsPage(Context context) {
        Intent pageView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(pageView);
    }
}
