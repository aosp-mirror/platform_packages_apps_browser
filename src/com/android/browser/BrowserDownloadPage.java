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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Downloads;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

import java.io.File;
import java.util.List;

/**
 *  View showing the user's current browser downloads
 */
public class BrowserDownloadPage extends Activity 
        implements View.OnCreateContextMenuListener, OnItemClickListener {
    
    private ListView                mListView;
    private Cursor                  mDownloadCursor;
    private BrowserDownloadAdapter  mDownloadAdapter;
    private int                     mStatusColumnId;
    private int                     mIdColumnId;
    private int                     mTitleColumnId;
    private int                     mContextMenuPosition;
    
    @Override 
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.browser_downloads_page);
        
        setTitle(getText(R.string.download_title));

        mListView = (ListView) findViewById(R.id.list);
        LayoutInflater factory = LayoutInflater.from(this);
        View v = factory.inflate(R.layout.no_downloads, null);
        addContentView(v, new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
        mListView.setEmptyView(v);
        
        mDownloadCursor = managedQuery(Downloads.CONTENT_URI, 
                new String [] {"_id", Downloads.TITLE, Downloads.STATUS,
                Downloads.TOTAL_BYTES, Downloads.CURRENT_BYTES, 
                Downloads._DATA, Downloads.DESCRIPTION, 
                Downloads.MIMETYPE, Downloads.LAST_MODIFICATION,
                Downloads.VISIBILITY}, 
                null, null);
        
        // only attach everything to the listbox if we can access
        // the download database. Otherwise, just show it empty
        if (mDownloadCursor != null) {
            mStatusColumnId = 
                    mDownloadCursor.getColumnIndexOrThrow(Downloads.STATUS);
            mIdColumnId =
                    mDownloadCursor.getColumnIndexOrThrow(Downloads._ID);
            mTitleColumnId = 
                    mDownloadCursor.getColumnIndexOrThrow(Downloads.TITLE);
            
            // Create a list "controller" for the data
            mDownloadAdapter = new BrowserDownloadAdapter(this, 
                    R.layout.browser_download_item, mDownloadCursor);
            
            mListView.setAdapter(mDownloadAdapter);
            mListView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            mListView.setOnCreateContextMenuListener(this);
            mListView.setOnItemClickListener(this);
            
            Intent intent = getIntent();
            if (intent != null && intent.getData() != null) {
                int position = checkStatus(
                        ContentUris.parseId(intent.getData()));
                if (position >= 0) {
                    mListView.setSelection(position);
                }
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
        
        boolean showClear = getClearableCount() > 0;
        menu.findItem(R.id.download_menu_clear_all).setEnabled(showClear);
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.download_menu_cancel_all:
                promptCancelAll();
                return true;
                
            case R.id.download_menu_clear_all:
                promptClearList();
                return true;
        }
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        mDownloadCursor.moveToPosition(mContextMenuPosition);
        switch (item.getItemId()) {
            case R.id.download_menu_open:
                hideCompletedDownload();
                openCurrentDownload();
                return true;
                
            case R.id.download_menu_clear:
            case R.id.download_menu_cancel:
                getContentResolver().delete(
                        ContentUris.withAppendedId(Downloads.CONTENT_URI,
                        mDownloadCursor.getLong(mIdColumnId)), null, null);
                return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        if (mDownloadCursor != null) {
            AdapterView.AdapterContextMenuInfo info = 
                    (AdapterView.AdapterContextMenuInfo) menuInfo;
            mDownloadCursor.moveToPosition(info.position);
            mContextMenuPosition = info.position;
            menu.setHeaderTitle(mDownloadCursor.getString(mTitleColumnId));
            
            MenuInflater inflater = getMenuInflater();
            int status = mDownloadCursor.getInt(mStatusColumnId);
            if (Downloads.isStatusSuccess(status)) {
                inflater.inflate(R.menu.downloadhistorycontextfinished, menu);
            } else if (Downloads.isStatusError(status)) {
                inflater.inflate(R.menu.downloadhistorycontextfailed, menu);
            } else {
                inflater.inflate(R.menu.downloadhistorycontextrunning, menu);
            }
        }
    }

    /**
     * This function is called to check the status of the download and if it
     * has an error show an error dialog.
     * @param id Row id of the download to check
     * @return position of item
     */
    int checkStatus(final long id) {
        int position = -1;
        for (mDownloadCursor.moveToFirst(); !mDownloadCursor.isAfterLast(); 
                mDownloadCursor.moveToNext()) {
            if (id == mDownloadCursor.getLong(mIdColumnId)) {
                position = mDownloadCursor.getPosition();
                break;
            }
            
        }
        if (!mDownloadCursor.isAfterLast()) {
            int status = mDownloadCursor.getInt(mStatusColumnId);
            if (!Downloads.isStatusError(status)) {
                return position;
            }
            
            if (status == Downloads.STATUS_FILE_ERROR) {
                String title = mDownloadCursor.getString(mTitleColumnId);
                if (title == null || title.length() == 0) {
                    title = getString(R.string.download_unknown_filename);
                }
                String msg = getString(R.string.download_file_error_dlg_msg, 
                        title);
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
        }
        return position;
    }
    
    /**
     * Resume a given download
     * @param id Row id of the download to resume
     */
    private void resumeDownload(final long id) {
        // the relevant functionality doesn't exist in the download manager
    }
    
    /**
     * Prompt the user if they would like to clear the download history
     */
    private void promptClearList() {
        new AlertDialog.Builder(this)
               .setTitle(R.string.download_clear_dlg_title)
               .setIcon(R.drawable.ssl_icon)
               .setMessage(R.string.download_clear_dlg_msg)
               .setPositiveButton(R.string.ok, 
                       new DialogInterface.OnClickListener() {
                           public void onClick(DialogInterface dialog, 
                                   int whichButton) {
                               clearAllDownloads();
                           }
                       })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
                if (!Downloads.isStatusCompleted(status)) {
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
                if (!Downloads.isStatusCompleted(status)) {
                    if (firstTime) {
                        firstTime = false;
                    } else {
                        where.append(" OR ");
                    }
                    where.append("( ");
                    where.append(Downloads._ID);
                    where.append(" = '");
                    where.append(mDownloadCursor.getLong(mIdColumnId));
                    where.append("' )");
                }
                mDownloadCursor.moveToNext();
            }
            if (!firstTime) {
                getContentResolver().delete(Downloads.CONTENT_URI,
                        where.toString(), null);
            }
        }
    }
    
    private int getClearableCount() {
        int count = 0;
        if (mDownloadCursor.moveToFirst()) {
            while (!mDownloadCursor.isAfterLast()) {
                int status = mDownloadCursor.getInt(mStatusColumnId);
                if (Downloads.isStatusCompleted(status)) {
                    count++;
                }
                mDownloadCursor.moveToNext();
            }
        }
        return count;
    }
    
    /**
     * Clear all stopped downloads, ie canceled (though should not be
     * there), error and success download items.
     */
    private void clearAllDownloads() {
        if (mDownloadCursor.moveToFirst()) {
            StringBuilder where = new StringBuilder();
            boolean firstTime = true;
            while (!mDownloadCursor.isAfterLast()) {
                int status = mDownloadCursor.getInt(mStatusColumnId);
                if (Downloads.isStatusCompleted(status)) {
                    if (firstTime) {
                        firstTime = false;
                    } else {
                        where.append(" OR ");
                    }
                    where.append("( ");
                    where.append(Downloads._ID);
                    where.append(" = '");
                    where.append(mDownloadCursor.getLong(mIdColumnId));
                    where.append("' )");
                }
                mDownloadCursor.moveToNext();
            }
            if (!firstTime) {
                getContentResolver().delete(Downloads.CONTENT_URI,
                        where.toString(), null);
            }
        }
    }
    
    /**
     * Open the content where the download db cursor currently is
     */
    private void openCurrentDownload() {
        int filenameColumnId = 
                mDownloadCursor.getColumnIndexOrThrow(Downloads._DATA);
        String filename = mDownloadCursor.getString(filenameColumnId);
        int mimetypeColumnId =
                mDownloadCursor.getColumnIndexOrThrow(Downloads.MIMETYPE);
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
                    .setTitle(R.string.download_failed_generic_dlg_title)
                    .setIcon(R.drawable.ssl_icon)
                    .setMessage(R.string.download_no_application)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    /*
     * (non-Javadoc)
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     */
    public void onItemClick(AdapterView parent, View view, int position, 
            long id) {
        // Open the selected item
        mDownloadCursor.moveToPosition(position);
        
        hideCompletedDownload();

        int status = mDownloadCursor.getInt(mStatusColumnId);
        if (Downloads.isStatusSuccess(status)) {
            // Open it if it downloaded successfully
            openCurrentDownload();
        } else {
            // Check to see if there is an error.
            checkStatus(id);
        }
    }
    
    /**
     * hides the notification for the download pointed by mDownloadCursor
     * if the download has completed.
     */
    private void hideCompletedDownload() {
        int status = mDownloadCursor.getInt(mStatusColumnId);

        int visibilityColumn = mDownloadCursor.getColumnIndexOrThrow(Downloads.VISIBILITY);
        int visibility = mDownloadCursor.getInt(visibilityColumn);

        if (Downloads.isStatusCompleted(status) &&
                visibility == Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) {
            ContentValues values = new ContentValues();
            values.put(Downloads.VISIBILITY, Downloads.VISIBILITY_VISIBLE);
            getContentResolver().update(
                    ContentUris.withAppendedId(Downloads.CONTENT_URI,
                    mDownloadCursor.getLong(mIdColumnId)), values, null, null);
        }
    }
}
