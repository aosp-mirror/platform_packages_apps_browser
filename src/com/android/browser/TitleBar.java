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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TitleBar extends LinearLayout {
    private TextView        mTitle;
    private TextView        mUrl;
    private ImageView       mLftButton;
    private Drawable        mBookmarkDrawable;
    private View            mRtButton;
    private View            mDivider;
    private ProgressBar     mCircularProgress;
    private ProgressBar     mHorizontalProgress;
    private ImageView       mFavicon;
    private ImageView       mLockIcon;
    private boolean         mInLoad;

    public TitleBar(Context context) {
        this(context, null);
    }

    public TitleBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar, this);

        mTitle = (TextView) findViewById(R.id.title);
        mUrl = (TextView) findViewById(R.id.url);

        mLftButton = (ImageView) findViewById(R.id.lft_button);
        mRtButton = findViewById(R.id.rt_button);

        mCircularProgress = (ProgressBar) findViewById(R.id.progress_circular);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mLockIcon = (ImageView) findViewById(R.id.lock_icon);
        mDivider = findViewById(R.id.divider);
    }

    /* package */ void setBrowserActivity(final BrowserActivity activity) {
        mLftButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (mInLoad) {
                            WebView webView = activity.getTopWindow();
                            if (webView != null) {
                                webView.stopLoading();
                            }
                        } else {
                            activity.bookmarksOrHistoryPicker(false);
                        }
                    }
                });
        mRtButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        WebView webView = activity.getTopWindow();
                        if (webView != null) {
                            webView.zoomScrollOut();
                        }
                    }
                });
        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                activity.onSearchRequested();
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
            mDivider.setVisibility(View.VISIBLE);
            mRtButton.setVisibility(View.VISIBLE);
            mLftButton.setImageDrawable(mBookmarkDrawable);
            mInLoad = false;
        } else {
            mCircularProgress.setProgress(newProgress);
            mHorizontalProgress.setProgress(newProgress);
            mCircularProgress.setVisibility(View.VISIBLE);
            mHorizontalProgress.setVisibility(View.VISIBLE);
            mDivider.setVisibility(View.GONE);
            mRtButton.setVisibility(View.GONE);
            if (mBookmarkDrawable == null) {
                // The drawable was assigned in the xml file, so it already
                // exists.  Keep a pointer to it when we switch to the resource
                // so we can easily switch back.
                mBookmarkDrawable = mLftButton.getDrawable();
            }
            mLftButton.setImageResource(
                    com.android.internal.R.drawable.ic_menu_stop);
            mInLoad = true;
        }
    }

    /* package */ void setTitleAndUrl(CharSequence title, CharSequence url) {
        if (null == title) {
            mTitle.setText(R.string.title_bar_loading);
        } else {
            mTitle.setText(title);
        }
        if (url != null) {
            url = BrowserActivity.buildTitleUrl(url.toString());
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
