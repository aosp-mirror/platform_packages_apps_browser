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

import com.android.browser.UrlInputView.UrlInputListener;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Message;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.ImageView;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TitleBarXLarge extends TitleBarBase
    implements UrlInputListener, OnClickListener, OnFocusChangeListener {

    private static final int PROGRESS_MAX = 100;

    private BrowserActivity mBrowserActivity;
    private Drawable mStopDrawable;
    private Drawable mReloadDrawable;
    private Drawable mFocusDrawable;
    private Drawable mUnFocusDrawable;


    private View mContainer;
    private View mBackButton;
    private View mForwardButton;
    private View mStar;
    private View mSearchButton;
    private View mInputContainer;
    private ImageView mStopButton;
    private View mAllButton;
    private PageProgressView mProgressView;
    private UrlInputView mUrlView;
    private boolean mInLoad;

    public TitleBarXLarge(BrowserActivity context) {
        super(context);
        mBrowserActivity = context;
        Resources resources = context.getResources();
        mStopDrawable = resources.getDrawable(R.drawable.ic_stop_normal);
        mReloadDrawable = resources.getDrawable(R.drawable.ic_refresh_normal);
        mFocusDrawable = resources.getDrawable(R.drawable.text_field_results);
        mUnFocusDrawable = resources.getDrawable(R.drawable.text_field);
        rebuildLayout(context, true);
    }

    private void rebuildLayout(Context context, boolean rebuildData) {
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.url_bar, this);

        mContainer = findViewById(R.id.taburlbar);
        mUrlView = (UrlInputView) findViewById(R.id.editurl);
        mAllButton = findViewById(R.id.all_btn);
        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.
        mBackButton = findViewById(R.id.back);
        mForwardButton = findViewById(R.id.forward);
        mStar = findViewById(R.id.star);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mSearchButton = findViewById(R.id.search);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mProgressView = (PageProgressView) findViewById(R.id.progress);
        mInputContainer = findViewById(R.id.urlbar);

        mBackButton.setOnClickListener(this);
        mForwardButton.setOnClickListener(this);
        mStar.setOnClickListener(this);
        mAllButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mSearchButton.setOnClickListener(this);
        mUrlView.setUrlInputListener(this);
        mUrlView.setOnFocusChangeListener(this);
        mInputContainer.setBackgroundDrawable(mUnFocusDrawable);
        mUrlView.setTextColor(Color.GRAY);

    }
    
    public void onFocusChange(View v, boolean hasFocus) {
        mInputContainer.setBackgroundDrawable(hasFocus ? mFocusDrawable : mUnFocusDrawable);
        mUrlView.setTextColor(hasFocus ? Color.BLACK : Color.GRAY);
    }

    @Override
    public void onClick(View v) {
        if (mBackButton == v) {
            mBrowserActivity.getTopWindow().goBack();
        } else if (mForwardButton == v) {
            mBrowserActivity.getTopWindow().goForward();
        } else if (mStar == v) {
            mBrowserActivity.promptAddOrInstallBookmark(mStar);
        } else if (mAllButton == v) {
            mBrowserActivity.bookmarksOrHistoryPicker(false, false);
        } else if (mSearchButton == v) {
            search();
        } else if (mStopButton == v) {
            stopOrRefresh();
        }
    }

    int getHeightWithoutProgress() {
        return mContainer.getHeight();
    }

    void requestUrlInputFocus() {
        mUrlView.requestFocus();
    }

    @Override
    void setFavicon(Bitmap icon) { }

    // UrlInputListener implementation

    @Override
    public void onAction(String text) {
        mBrowserActivity.getTabControl().getCurrentTopWebView().requestFocus();
        mBrowserActivity.hideFakeTitleBar();
        Intent i = new Intent();
        i.setAction(Intent.ACTION_SEARCH);
        i.putExtra(SearchManager.QUERY, text);
        mBrowserActivity.onNewIntent(i);
    }

    @Override
    public void onDismiss() {
        mBrowserActivity.getTabControl().getCurrentTopWebView().requestFocus();
        mBrowserActivity.hideFakeTitleBar();
        mUrlView.setText(mBrowserActivity.getTabControl().getCurrentWebView().getUrl());
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mBrowserActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mBrowserActivity.onCreateContextMenu(menu, this, null);
    }

    private void search() {
        mUrlView.setText("");
        mUrlView.requestFocus();
    }

    private void stopOrRefresh() {
        if (mInLoad) {
            mBrowserActivity.stopLoading();
        } else {
            mBrowserActivity.getTopWindow().reload();
        }
    }

    /**
     * Update the progress, from 0 to 100.
     */
    @Override
    void setProgress(int newProgress) {
        if (newProgress >= PROGRESS_MAX) {
            mProgressView.setVisibility(View.GONE);
            mInLoad = false;
            mStopButton.setImageDrawable(mReloadDrawable);
        } else {
            if (!mInLoad) {
                mProgressView.setVisibility(View.VISIBLE);
                mInLoad = true;
                mStopButton.setImageDrawable(mStopDrawable);
            }
            mProgressView.setProgress(newProgress*10000/PROGRESS_MAX);
        }
    }

    @Override
    /* package */ void setDisplayTitle(String title) {
        mUrlView.setText(title);
    }

}
