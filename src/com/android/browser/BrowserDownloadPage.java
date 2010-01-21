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

import android.app.AlertDialog;
import android.app.ExpandableListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Downloads;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ExpandableListView;

import java.io.File;
import java.util.List;

/**
 *  View showing the user's current browser downloads
 */
public class BrowserDownloadPage extends ExpandableListActivity {
    
    private ExpandableListView      mListView;
    private Cursor                  mDownloadCursor;
    private BrowserDownloadAdapter  mDownloadAdapter;
    private int                     mStatusColumnId;
    private int                     mIdColumnId;
    private int                     mTitleColumnId;
    private long                    mContextMenuPosition;
    
    @Override 
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.browser_downloads_page);
        
        setTitle(getText(R.string.download_title));

        mListView = (ExpandableListView) findViewById(android.R.id.list);
        mListView.setEmptyView(findViewById(R.id.empty));
        mDownloadCursor = managedQuery(Downloads.Impl.CONTENT_URI,
                new String [] {"_id", Downloads.Impl.COLUMN_TITLE, Downloads.Impl.COLUMN_STATUS,
                Downloads.Impl.COLUMN_TOTAL_BYTES, Downloads.Impl.COLUMN_CURRENT_BYTES,
                Downloads.Impl._DATA, Downloads.Impl.COLUMN_DESCRIPTION,
                Downloads.Impl.COLUMN_MIME_TYPE, Downloads.Impl.COLUMN_LAST_MODIFICATION,
                Downloads.Impl.COLUMN_VISIBILITY},
                null, Downloads.Impl.COLUMN_LAST_MODIFICATION + " DESC");
        
        // only attach everything to the listbox if we can access
        // the download database. Otherwise, just show it empty
        if (mDownloadCursor != null) {
            mStatusColumnId = 
                    mDownloadCursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_STATUS);
            mIdColumnId =
                    mDownloadCursor.getColumnIndexOrThrow(Downloads.Impl._ID);
            mTitleColumnId = 
                    mDownloadCursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_TITLE);
            
            // Create a list "controller" for the data
            mDownloadAdapter = new BrowserDownloadAdapter(this, 
                    mDownloadCursor, mDownloadCursor.getColumnIndexOrThrow(
                    Downloads.Impl.COLUMN_LAST_MODIFICATION));

            setListAdapter(mDownloadAdapter);
            mListView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            mListView.setOnCreateContextMenuListener(this);

            Intent intent = getIntent();
            final int groupToShow = intent == null || intent.getData() == null
                    ? 0 : checkStatus(ContentUris.parseId(intent.getData()));
            if (mDownloadAdapter.getGroupCount() > groupToShow) {
                mListView.post(new Runnable() {
                    public void run() {
                        if (mDownloadAdapter.getGroupCount() > groupToShow) {
                            mListView.expandGroup(groupToShow);
                        }
                    }
                });
            }
        }
    }
        
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mDownloadCursor != null) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.downloadhistory, menu);
        }
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showCancel = getCancelableCount() > 0;
        menu.findItem(R.id.download_menu_cancel_all).setEnabled(showCancel);
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.download_menu_cancel_all:
                promptCancelAll();
                return true;
        }
        return false;
    }

    /**
     * Remove the file from the list of downloads.
     * @param id Unique ID of the download to remove.
     */
    private void clearFromDownloads(long id) {
        getContentResolver().delete(ContentUris.withAppendedId(
                Downloads.Impl.CONTENT_URI, id), null, null);
    }

    /**
     * Remove the file from the SD card
     * @param filename Name of the file to delete.
     * @param mimetype Mimetype of the file to delete.
     * @return boolean True on success, false on failure.
     */
    private boolean deleteFile(String filename, String mimetype) {
        Uri uri;
        if (mimetype.startsWith("image")) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if (mimetype.startsWith("audio")) {
            uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        } else if (mimetype.startsWith("video")) {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else {
            File file = new File(filename);
            return file.delete();
        }
        return getContentResolver().delete(uri, MediaStore.MediaColumns.DATA
                + " = " + DatabaseUtils.sqlEscapeString(filename), null) > 0;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!mDownloadAdapter.moveCursorToPackedChildPosition(
                mContextMenuPosition)) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.download_menu_open:
                hideCompletedDownload();
                openCurrentDownload();
                return true;

            case R.id.download_menu_delete:
                int filenameColumnId =
                        mDownloadCursor.getColumnIndexOrThrow(Downloads._DATA);
                final String filename = mDownloadCursor.getString(
                        filenameColumnId);
                int mimetypeColumnId = mDownloadCursor.getColumnIndexOrThrow(
                        Downloads.Impl.COLUMN_MIME_TYPE);
                final String mimetype = mDownloadCursor.getString(
                        mimetypeColumnId);
                final long id = mDownloadCursor.getLong(mIdColumnId);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.download_delete_file)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(filename)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton) {
                                        if (deleteFile(filename, mimetype)) {
                                            clearFromDownloads(id);
                                        }
                                    }
                                })
                        .show();
                break;

            case R.id.download_menu_clear:
            case R.id.download_menu_cancel:
                clearFromDownloads(mDownloadCursor.getLong(mIdColumnId));
                return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        if (mDownloadCursor != null) {
            ExpandableListView.ExpandableListContextMenuInfo info
                    = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
            long packedPosition = info.packedPosition;
            // Only show a context menu for the child views
            if (!mDownloadAdapter.moveCursorToPackedChildPosition(
                    packedPosition)) {
                return;
            }
            mContextMenuPosition = packedPosition;
            menu.setHeaderTitle(mDownloadCursor.getString(mTitleColumnId));
            
            MenuInflater inflater = getMenuInflater();
            int status = mDownloadCursor.getInt(mStatusColumnId);
            if (Downloads.Impl.isStatusSuccess(status)) {
                inflater.inflate(R.menu.downloadhistorycontextfinished, menu);
            } else if (Downloads.Impl.isStatusError(status)) {
                inflater.inflate(R.menu.downloadhistorycontextfailed, menu);
            } else {
                inflater.inflate(R.menu.downloadhistorycontextrunning, menu);
            }
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    /**
     * This function is called to check the status of the download and if it
     * has an error show an error dialog.
     * @param id Row id of the download to check
     * @return Group which contains the download
     */
    private int checkStatus(final long id) {
        int groupToShow = mDownloadAdapter.groupFromChildId(id);
        if (-1 == groupToShow) return 0;
        int status = mDownloadCursor.getInt(mStatusColumnId);
        if (!Downloads.Impl.isStatusError(status)) {
            return groupToShow;
        }
        if (status == Downloads.Impl.STATUS_FILE_ERROR) {
            String title = mDownloadCursor.getString(mTitleColumnId);
            if (title == null || title.length() == 0) {
                title = getString(R.string.download_unknown_filename);
            }
            String msg = getString(R.string.download_file_error_dlg_msg, title);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.download_file_error_dlg_title)
                    .setIcon(android.R.drawable.ic_popup_disk_full)
                    .setMessage(msg)
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.retry,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    resumeDownload(id);
                                }
                            })
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.download_failed_generic_dlg_title)
                    .setIcon(R.drawable.ssl_icon)
                    .setMessage(BrowserDownloadAdapter.getErrorText(status))
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
        return groupToShow;
    }
    
    /**
     * Resume a given download
     * @param id Row id of the download to resume
     */
    private void resumeDownload(final long id) {
        // the relevant functionality doesn't exist in the download manager
    }
    
    /**
     * Return the number of items in the list that can be canceled.
     * @return count
     */
    private int getCancelableCount() {
        // Count the number of items that will be canceled.
        int count = 0;
        if (mDownloadCursor != null) {
            for (mDownloadCursor.moveToFirst(); !mDownloadCursor.isAfterLast(); 
                    mDownloadCursor.moveToNext()) {
                int status = mDownloadCursor.getInt(mStatusColumnId);
                if (!Downloads.Impl.isStatusCompleted(status)) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    /**
     * Prompt the user if they would like to clear the download history
     */
    private void promptCancelAll() {
        int count = getCancelableCount();
        
        // If there is nothing to do, just return
        if (count == 0) {
            return;
        }
        
        // Don't show the dialog if there is only one download
        if (count == 1) {
            cancelAllDownloads();
            return;
        }
        String msg = 
            getString(R.string.download_cancel_dlg_msg, count);
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_cancel_dlg_title)
                .setIcon(R.drawable.ssl_icon)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, 
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, 
                                    int whichButton) {
                                cancelAllDownloads();
                            }
                        })
                 .setNegativeButton(R.string.cancel, null)
                 .show();
    }
    
    /**
     * Cancel all downloads. As canceled downloads are not
     * listed, we removed them from the db. Removing a download
     * record, cancels the download.
     */
    private void cancelAllDownloads() {
        if (mDownloadCursor.moveToFirst()) {
            StringBuilder where = new StringBuilder();
            boolean firstTime = true;
            while (!mDownloadCursor.isAfterLast()) {
                int status = mDownloadCursor.getInt(mStatusColumnId);
                if (!Downloads.Impl.isStatusCompleted(status)) {
                    if (firstTime) {
                        firstTime = false;
                    } else {
                        where.append(" OR ");
                    }
                    where.append("( ");
                    where.append(Downloads.Impl._ID);
                    where.append(" = '");
                    where.append(mDownloadCursor.getLong(mIdColumnId));
                    where.append("' )");
                }
                mDownloadCursor.moveToNext();
            }
            if (!firstTime) {
                getContentResolver().delete(Downloads.Impl.CONTENT_URI,
                        where.toString(), null);
            }
        }
    }
    
    private int getClearableCount() {
        int count = 0;
        if (mDownloadCursor.moveToFirst()) {
            while (!mDownloadCursor.isAfterLast()) {
                int status = mDownloadCursor.getInt(mStatusColumnId);
                if (Downloads.Impl.isStatusCompleted(status)) {
                    count++;
                }
                mDownloadCursor.moveToNext();
            }
        }
        return count;
    }

    /**
     * Open the content where the download db cursor currently is
     */
    private void openCurrentDownload() {
        int filenameColumnId = 
                mDownloadCursor.getColumnIndexOrThrow(Downloads.Impl._DATA);
        String filename = mDownloadCursor.getString(filenameColumnId);
        int mimetypeColumnId =
                mDownloadCursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_MIME_TYPE);
        String mimetype = mDownloadCursor.getString(mimetypeColumnId);
        Uri path = Uri.parse(filename);
        // If there is no scheme, then it must be a file
        if (path.getScheme() == null) {
            path = Uri.fromFile(new File(filename));
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(path, mimetype);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.download_no_application_title)
                    .setIcon(R.drawable.ssl_icon)
                    .setMessage(R.string.download_no_application)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        // Open the selected item
        mDownloadAdapter.moveCursorToChildPosition(groupPosition,
                childPosition);
        
        hideCompletedDownload();

        int status = mDownloadCursor.getInt(mStatusColumnId);
        if (Downloads.Impl.isStatusSuccess(status)) {
            // Open it if it downloaded successfully
            openCurrentDownload();
        } else {
            // Check to see if there is an error.
            checkStatus(id);
        }
        return true;
    }
    
    /**
     * hides the notification for the download pointed by mDownloadCursor
     * if the download has completed.
     */
    private void hideCompletedDownload() {
        int status = mDownloadCursor.getInt(mStatusColumnId);

        int visibilityColumn = mDownloadCursor.getColumnIndexOrThrow(
                Downloads.Impl.COLUMN_VISIBILITY);
        int visibility = mDownloadCursor.getInt(visibilityColumn);

        if (Downloads.Impl.isStatusCompleted(status) &&
                visibility == Downloads.Impl.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) {
            ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_VISIBILITY, Downloads.Impl.VISIBILITY_VISIBLE);
            getContentResolver().update(
                    ContentUris.withAppendedId(Downloads.Impl.CONTENT_URI,
                    mDownloadCursor.getLong(mIdColumnId)), values, null, null);
        }
    }
}
