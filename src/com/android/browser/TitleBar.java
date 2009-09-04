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
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBar extends LinearLayout {
    private TextView        mTitle;
    private Drawable        mCloseDrawable;
    private ImageView       mRtButton;
    private Drawable        mCircularProgress;
    private ProgressBar     mHorizontalProgress;
    private Drawable        mFavicon;
    private ImageView       mLockIcon;
    private Drawable        mStopDrawable;
    private Drawable        mBookmarkDrawable;
    private boolean         mInLoad;
    private WebView         mWebView;
    private BrowserActivity mBrowserActivity;

    public TitleBar(Context context, WebView webview, BrowserActivity ba) {
        super(context, null);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar, this);
        mBrowserActivity = ba;

        mTitle = (TextView) findViewById(R.id.title);
        mTitle.setCompoundDrawablePadding(5);

        mLockIcon = (ImageView) findViewById(R.id.lock);

        mRtButton = (ImageView) findViewById(R.id.rt_btn);
        mRtButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mInLoad) {
                    if (mWebView != null) {
                        mWebView.stopLoading();
                    }
                } else {
                    mBrowserActivity.bookmarksOrHistoryPicker(false);
                }
            }
        });
        mCircularProgress = (Drawable) context.getResources().getDrawable(
                com.android.internal.R.drawable.search_spinner);
        mCircularProgress.setBounds(0,0,20,20);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mWebView = webview;
    }

    /**
     * Return the WebView associated with this TitleBar.
     */
    /* package */ WebView getWebView() {
        return mWebView;
    }

    /**
     * Return whether the associated WebView is currently loading.  Needed to
     * determine whether a click should stop the load or close the tab.
     */
    /* package */ boolean isInLoad() {
        return mInLoad;
    }

    /**
     * Set a new Drawable for the Favicon.
     */
    /* package */ void setFavicon(Drawable d) {
        if (d != null) {
            d.setBounds(0, 0, 20, 20);
        }
        Drawable progress = mInLoad ? mCircularProgress : null;
        mTitle.setCompoundDrawables(d, null, progress, null);
        mFavicon = d;
    }

    /**
     * Set the Drawable for the lock icon, or null to hide it.
     */
    /* package */ void setLock(Drawable d) {
        if (null == d) {
            mLockIcon.setVisibility(View.GONE);
        } else {
            mLockIcon.setImageDrawable(d);
            mLockIcon.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Update the progress, from 0 to 100.
     */
    /* package */ void setProgress(int newProgress) {
        if (newProgress == mHorizontalProgress.getMax()) {
            mTitle.setCompoundDrawables(mFavicon, null, null, null);
            ((Animatable) mCircularProgress).stop();
            mHorizontalProgress.setVisibility(View.INVISIBLE);
            if (mBookmarkDrawable != null) {
                mRtButton.setImageDrawable(mBookmarkDrawable);
            }
            mInLoad = false;
        } else {
            mHorizontalProgress.setProgress(newProgress);
            if (!mInLoad) {
                mTitle.setCompoundDrawables(mFavicon, null, mCircularProgress,
                        null);
                ((Animatable) mCircularProgress).start();
                mHorizontalProgress.setVisibility(View.VISIBLE);
                if (mBookmarkDrawable == null) {
                    mBookmarkDrawable = mRtButton.getDrawable();
                }
                if (mStopDrawable == null) {
                    mRtButton.setImageResource(
                            com.android.internal.R.drawable.ic_menu_stop);
                    mStopDrawable = mRtButton.getDrawable();
                } else {
                    mRtButton.setImageDrawable(mStopDrawable);
                }
                mInLoad = true;
            }
        }
    }

    /**
     * Update the title and url.
     */
    /* package */ void setTitleAndUrl(CharSequence title, CharSequence url) {
        if (url == null) {
            mTitle.setText(R.string.title_bar_loading);
        } else {
            mTitle.setText(url.toString());
        }
    }

    /* package */ void setToTabPicker() {
        mTitle.setText(R.string.tab_picker_title);
        setFavicon(null);
        setLock(null);
        mHorizontalProgress.setVisibility(View.GONE);
    }
}
