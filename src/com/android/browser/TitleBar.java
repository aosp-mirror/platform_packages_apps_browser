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
    private TextView        mUrl;
    private Drawable        mCloseDrawable;
    private ImageView       mRtButton;
    private ProgressBar     mCircularProgress;
    private ProgressBar     mHorizontalProgress;
    private ImageView       mFavicon;
    private ImageView       mLockIcon;  // FIXME: Needs to be below the favicon
    private boolean         mInLoad;
    private boolean         mTitleSet;
    private WebView         mWebView;

    public TitleBar(Context context, WebView webview) {
        super(context, null);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar, this);

        mTitle = (TextView) findViewById(R.id.title);
        mUrl = (TextView) findViewById(R.id.url);

        mRtButton = (ImageView) findViewById(R.id.rt_button);

        mCircularProgress = (ProgressBar) findViewById(R.id.progress_circular);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mLockIcon = (ImageView) findViewById(R.id.lock_icon);
        mWebView = webview;
    }

    /**
     * Return the WebView associated with this TitleBar.
     */
    /* package */ WebView getWebView() {
        return mWebView;
    }

    /**
     * Determine whether a point (from a touch) hits the right button.
     */
    /* package */ boolean hitRightButton(int x, int y) {
        Rect hitRect = new Rect();
        mRtButton.getHitRect(hitRect);
        return hitRect.contains(x - getLeft(), y - getTop());
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
        mFavicon.setImageDrawable(d);
    }

    /**
     * Set the Drawable for the lock icon, or null to hide it.
     */
    /* package */ void setLock(Drawable d) {
        if (d == null) {
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
        if (newProgress == mCircularProgress.getMax()) {
            mCircularProgress.setVisibility(View.GONE);
            mHorizontalProgress.setVisibility(View.GONE);
            mRtButton.setVisibility(View.VISIBLE);
            mUrl.setVisibility(View.VISIBLE);
            if (mCloseDrawable != null) {
                mRtButton.setImageDrawable(mCloseDrawable);
            }
            mInLoad = false;
            if (!mTitleSet) {
                mTitle.setText(mUrl.getText());
                mUrl.setText(null);
                mTitleSet = true;
            }
        } else {
            mCircularProgress.setProgress(newProgress);
            mHorizontalProgress.setProgress(newProgress);
            mCircularProgress.setVisibility(View.VISIBLE);
            mHorizontalProgress.setVisibility(View.VISIBLE);
            mUrl.setVisibility(View.VISIBLE);
            if (mCloseDrawable == null) {
                // The drawable was assigned in the xml file, so it already
                // exists.  Keep a pointer to it when we switch to the resource
                // so we can easily switch back.
                mCloseDrawable = mRtButton.getDrawable();
            }
            mRtButton.setImageResource(
                    com.android.internal.R.drawable.ic_menu_stop);
            mInLoad = true;
        }
    }

    /**
     * Update the title and url.
     */
    /* package */ void setTitleAndUrl(CharSequence title, CharSequence url) {
        if (url != null) {
            url = BrowserActivity.buildTitleUrl(url.toString());
        }
        if (null == title) {
            if (mInLoad) {
                mTitleSet = false;
                mTitle.setText(R.string.title_bar_loading);
            } else {
                // If the page has no title, put the url in the title space
                // and leave the url blank.
                mTitle.setText(url);
                mUrl.setText(null);
                mTitleSet = true;
                return;
            }
        } else {
            mTitle.setText(title);
            mTitleSet = true;
        }
        mUrl.setText(url);
    }

    /* package */ void setToTabPicker() {
        mTitle.setText(R.string.tab_picker_title);
        setFavicon(null);
        setLock(null);
        mCircularProgress.setVisibility(View.GONE);
        mHorizontalProgress.setVisibility(View.GONE);
        mUrl.setVisibility(View.INVISIBLE);
    }
}
