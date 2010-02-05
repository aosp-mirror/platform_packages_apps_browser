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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.drm.mobile1.DrmRawContent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Downloads;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * This class is used to represent the data for the download list box. The only 
 * real work done by this class is to construct a custom view for the line
 * items.
 */
public class BrowserDownloadAdapter extends DateSortedExpandableListAdapter {
    
    private int mTitleColumnId;
    private int mDescColumnId;
    private int mStatusColumnId;
    private int mTotalBytesColumnId;
    private int mCurrentBytesColumnId;
    private int mMimetypeColumnId;
    private int mDateColumnId;

    public BrowserDownloadAdapter(Context context, Cursor c, int index) {
        super(context, c, index);
        mTitleColumnId = c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_TITLE);
        mDescColumnId = c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_DESCRIPTION);
        mStatusColumnId = c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_STATUS);
        mTotalBytesColumnId = c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_TOTAL_BYTES);
        mCurrentBytesColumnId = 
            c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_CURRENT_BYTES);
        mMimetypeColumnId = c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_MIME_TYPE);
        mDateColumnId = c.getColumnIndexOrThrow(Downloads.Impl.COLUMN_LAST_MODIFICATION);
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                boolean isLastChild, View convertView, ViewGroup parent) {
        Context context = getContext();
        // The layout file uses a RelativeLayout, whereas the GroupViews use
        // TextView.
        if (null == convertView || !(convertView instanceof RelativeLayout)) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.browser_download_item, null);
        }

        // Bail early if the Cursor is closed.
        if (!moveCursorToChildPosition(groupPosition, childPosition)) {
            return convertView;
        }

        Resources r = context.getResources();
        
        // Retrieve the icon for this download
        String mimeType = getString(mMimetypeColumnId);
        ImageView iv = (ImageView) convertView.findViewById(R.id.download_icon);
        if (DrmRawContent.DRM_MIMETYPE_MESSAGE_STRING.equalsIgnoreCase(mimeType)) {
            iv.setImageResource(R.drawable.ic_launcher_drm_file);
        } else if (mimeType == null) {
            iv.setVisibility(View.INVISIBLE);
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromParts("file", "", null), mimeType);
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (list.size() > 0) {
                Drawable icon = list.get(0).activityInfo.loadIcon(pm);
                iv.setImageDrawable(icon);
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.INVISIBLE);
            }
        }
        
        TextView tv = (TextView) convertView.findViewById(R.id.download_title);
        String title = getString(mTitleColumnId);
        if (title == null) {
            title = r.getString(R.string.download_unknown_filename);
        }
        tv.setText(title);
        
        tv = (TextView) convertView.findViewById(R.id.domain);
        tv.setText(getString(mDescColumnId));
        
        long totalBytes = getLong(mTotalBytesColumnId);
        
        int status = getInt(mStatusColumnId);
        if (Downloads.Impl.isStatusCompleted(status)) { // Download stopped
            View v = convertView.findViewById(R.id.progress_text);
            v.setVisibility(View.GONE);

            v = convertView.findViewById(R.id.download_progress);
            v.setVisibility(View.GONE);

            tv = (TextView) convertView.findViewById(R.id.complete_text);
            tv.setVisibility(View.VISIBLE);
            if (Downloads.Impl.isStatusError(status)) {
                tv.setText(getErrorText(status));
            } else {
                tv.setText(r.getString(R.string.download_success, 
                        Formatter.formatFileSize(context, totalBytes)));
            }
            
            long time = getLong(mDateColumnId);
            Date d = new Date(time);
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
            tv = (TextView) convertView.findViewById(R.id.complete_date);
            tv.setVisibility(View.VISIBLE);
            tv.setText(df.format(d));
            
        } else { // Download is still running
            tv = (TextView) convertView.findViewById(R.id.progress_text);
            tv.setVisibility(View.VISIBLE);

            View progress = convertView.findViewById(R.id.download_progress);
            progress.setVisibility(View.VISIBLE);
            
            View v = convertView.findViewById(R.id.complete_date);
            v.setVisibility(View.GONE);

            v = convertView.findViewById(R.id.complete_text);
            v.setVisibility(View.GONE);
            
            if (status == Downloads.Impl.STATUS_PENDING) {
                tv.setText(r.getText(R.string.download_pending));
            } else if (status == Downloads.Impl.STATUS_PENDING_PAUSED) {
                tv.setText(r.getText(R.string.download_pending_network));
            } else {
                ProgressBar pb = (ProgressBar) progress;

                StringBuilder sb = new StringBuilder();
                if (status == Downloads.Impl.STATUS_RUNNING) {
                    sb.append(r.getText(R.string.download_running));
                } else {
                    sb.append(r.getText(R.string.download_running_paused));
                }
                if (totalBytes > 0) {
                    long currentBytes = getLong(mCurrentBytesColumnId);
                    int progressAmount = (int)(currentBytes * 100 / totalBytes);
                    sb.append(' ');
                    sb.append(progressAmount);
                    sb.append("% (");
                    sb.append(Formatter.formatFileSize(context, currentBytes));
                    sb.append("/");
                    sb.append(Formatter.formatFileSize(context, totalBytes));
                    sb.append(")");
                    pb.setIndeterminate(false);
                    pb.setProgress(progressAmount);
                } else {
                    pb.setIndeterminate(true);
                }
                tv.setText(sb.toString()); 
            }
        }
        return convertView;
    }
    
    /**
     * Provide the resource id for the error string.
     * @param status status of the download item
     * @return resource id for the error string.
     */
    public static int getErrorText(int status) {
        switch (status) {
            case Downloads.Impl.STATUS_NOT_ACCEPTABLE:
                return R.string.download_not_acceptable;
                
            case Downloads.Impl.STATUS_LENGTH_REQUIRED:
                return R.string.download_length_required;
                
            case Downloads.Impl.STATUS_PRECONDITION_FAILED:
                return R.string.download_precondition_failed;
                
            case Downloads.Impl.STATUS_CANCELED:
                return R.string.download_canceled;

            case Downloads.Impl.STATUS_FILE_ERROR:
                return R.string.download_file_error;
                
            case Downloads.Impl.STATUS_BAD_REQUEST:
            case Downloads.Impl.STATUS_UNKNOWN_ERROR:
            default:
                return R.string.download_error;
        }
    }
}
