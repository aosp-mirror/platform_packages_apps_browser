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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;

/* package */ class TitleBar extends LinearLayout {
    private TextView        mTitle;
    private TextView        mUrl;
    private ImageView       mZoomIn;
    private View            mZoomOut;
    private View            mDivider;
    private ProgressBar     mCircularProgress;
    private ProgressBar     mHorizontalProgress;
    private ImageView       mFavicon;
    private ImageView       mLockIcon;
    private boolean         mInLoad;
    private CharSequence    mTitleOnceLoaded;
    private BrowserActivity mActivity;


    /* package */ TitleBar(BrowserActivity context) {
        super(context);
        mActivity = context;
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar, this);

        mTitle = (TextView) findViewById(R.id.title);
        mUrl = (TextView) findViewById(R.id.url);

        mZoomIn = (ImageView) findViewById(R.id.zoom_in);
        mZoomIn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (mInLoad) {
                            mActivity.getTopWindow().stopLoading();
                        } else {
                            mActivity.getTopWindow().zoomIn();
                        }
                    }
                });
        mZoomOut = findViewById(R.id.zoom_out);
        // Make zoom out disappear while loading
        mZoomOut.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mActivity.getTopWindow().zoomOut();
                    }
                });

        mCircularProgress = (ProgressBar) findViewById(R.id.progress_circular);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mLockIcon = (ImageView) findViewById(R.id.lock_icon);
        mDivider = findViewById(R.id.divider);
        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mActivity.onSearchRequested();
            }
        });
    }

    /* package */ void setFavicon(Drawable d) {
        mFavicon.setImageDrawable(d);
    }

    /* package */ void setLock(Drawable d) {
        if (d == null) {
            mLockIcon.setVisibility(View.GONE);
        } else {
            mLockIcon.setImageDrawable(d);
            mLockIcon.setVisibility(View.VISIBLE);
        }
    }

    /* package */ void setProgress(int newProgress) {
        if (newProgress == mCircularProgress.getMax()) {
            mCircularProgress.setVisibility(View.GONE);
            mHorizontalProgress.setVisibility(View.GONE);
            mTitle.setText(mTitleOnceLoaded);
            mDivider.setVisibility(View.VISIBLE);
            mZoomOut.setVisibility(View.VISIBLE);
            mZoomIn.setImageResource(R.drawable.ic_titlebar_zoom);
            mInLoad = false;
        } else {
            mCircularProgress.setProgress(newProgress);
            mHorizontalProgress.setProgress(newProgress);
            mCircularProgress.setVisibility(View.VISIBLE);
            mHorizontalProgress.setVisibility(View.VISIBLE);
            mDivider.setVisibility(View.GONE);
            mZoomOut.setVisibility(View.GONE);
            mZoomIn.setImageResource(com.android.internal.R.drawable.ic_menu_stop);
            mInLoad = true;
        }
    }

    /* package */ void setTitleAndUrl(CharSequence title, CharSequence url) {
        mTitleOnceLoaded = title;
        if (null == title || mHorizontalProgress.getProgress() <
                mHorizontalProgress.getMax()) {
            mTitle.setText(R.string.title_bar_loading);
        } else {
            mTitle.setText(title);
        }
        mUrl.setText(url);
    }

    /* package */ void setToTabPicker() {
        mTitle.setText(R.string.tab_picker_title);
        setFavicon(null);
        setLock(null);
        mCircularProgress.setVisibility(View.GONE);
        mHorizontalProgress.setVisibility(View.GONE);
    }
}
