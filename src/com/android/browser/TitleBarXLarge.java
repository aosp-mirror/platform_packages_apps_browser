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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.common.speech.LoggingEvents;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBarXLarge extends TitleBarBase {
    private Drawable        mCircularProgress;
    private ProgressBar     mHorizontalProgress;
    private Drawable        mStopDrawable;
    private Drawable        mReloadDrawable;
    private boolean         mInLoad;
    private BrowserActivity mBrowserActivity;

    private final View            mBackButton;
    private final View            mForwardButton;
    private final View            mStar;
    private final View            mMenu;
    private final ImageView       mStopButton;
    private final TextView        mTitle;
    private final View            mAllButton;

    public TitleBarXLarge(BrowserActivity context) {
        super(context);
        Resources resources = context.getResources();
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar_xlarge, this);
        mBrowserActivity = context;

        mTitle = (TextView) findViewById(R.id.title);
        mTitle.setCompoundDrawablePadding(5);
        mTitle.setLongClickable(true);

        mLockIcon = (ImageView) findViewById(R.id.lock);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mStopDrawable = mStopButton.getDrawable();
        mReloadDrawable = resources.getDrawable(R.drawable.ic_reload);

        mAllButton = (ImageView) findViewById(R.id.all_btn);
        mCircularProgress = (Drawable) resources.getDrawable(
                com.android.internal.R.drawable.search_spinner);
        DisplayMetrics metrics = resources.getDisplayMetrics();
        int iconDimension = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f, metrics);
        mCircularProgress.setBounds(0, 0, iconDimension, iconDimension);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mHorizontalProgress.setProgressDrawable(
                resources.getDrawable(R.drawable.progress));

        // FIXME: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.
        mBackButton = findViewById(R.id.back);
        mForwardButton = findViewById(R.id.forward);
        mStar = findViewById(R.id.star);
        mMenu = findViewById(R.id.menu);
        View.OnClickListener listener = new View.OnClickListener() {
                public void onClick(View v) {
                    if (mBackButton == v) {
                        mBrowserActivity.getTopWindow().goBack();
                    } else if (mForwardButton == v) {
                        mBrowserActivity.getTopWindow().goForward();
                    } else if (mStar == v) {
                        // FIXME: Show a menu with option to bookmark or
                        // save to home page
                        mBrowserActivity.bookmarkCurrentPage();
                    } else if (mMenu == v) {
                        mBrowserActivity.openOptionsMenu();
                    } else if (mStopButton == v) {
                        if (mInLoad) {
                            mBrowserActivity.stopLoading();
                        } else {
                            mBrowserActivity.getTopWindow().reload();
                        }
                    } else if (mTitle == v) {
                        mBrowserActivity.editUrl();
                    } else if (mAllButton == v) {
                        // FIXME: Show the new bookmarks/windows view.
                        mBrowserActivity.bookmarksOrHistoryPicker(false);
                    }
                }
        };
        mBackButton.setOnClickListener(listener);
        mForwardButton.setOnClickListener(listener);
        mStar.setOnClickListener(listener);
        mStopButton.setOnClickListener(listener);
        mTitle.setOnClickListener(listener);
        mAllButton.setOnClickListener(listener);
        mMenu.setOnClickListener(listener);
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mBrowserActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mBrowserActivity.onCreateContextMenu(menu, this, null);
    }

    /**
     * Update the progress, from 0 to 100.
     */
    /* package */ void setProgress(int newProgress) {
        if (newProgress >= mHorizontalProgress.getMax()) {
            mTitle.setCompoundDrawables(null, null, null, null);
            ((Animatable) mCircularProgress).stop();
            mHorizontalProgress.setVisibility(View.GONE);
            mInLoad = false;
            mStopButton.setImageDrawable(mReloadDrawable);
        } else {
            mHorizontalProgress.setProgress(newProgress);
            if (!mInLoad && getWindowToken() != null) {
                // checking the window token lets us be sure that we
                // are attached to a window before starting the animation,
                // preventing a potential race condition
                // (fix for bug http://b/2115736)
                mTitle.setCompoundDrawables(null, null, mCircularProgress,
                        null);
                ((Animatable) mCircularProgress).start();
                mHorizontalProgress.setVisibility(View.VISIBLE);
                mInLoad = true;
                mStopButton.setImageDrawable(mStopDrawable);
            }
        }
    }

    /**
     * Update the text displayed in the title bar.
     * @param title String to display.  If null, the loading string will be
     *      shown.
     */
    /* package */ void setDisplayTitle(String title) {
        if (title == null) {
            mTitle.setText(R.string.title_bar_loading);
        } else {
            mTitle.setText(title);
        }
    }

}
