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

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import com.android.browser.UrlInputView.UrlInputListener;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TitleBarXLarge extends TitleBarBase
    implements UrlInputListener, OnClickListener {

    private static final int PROGRESS_MAX = 100;

    private BrowserActivity mBrowserActivity;
    private Drawable mStopDrawable;
    private Drawable mReloadDrawable;
    private Drawable mProgressDrawable;

    private View mBackButton;
    private View mForwardButton;
    private View mStar;
    private View mSearchButton;
    private ImageView mStopButton;
    private View mMenu;
    private View mAllButton;
    private ImageView mProgressView;
    private UrlInputView mUrlView;
    private boolean mInLoad;

    public TitleBarXLarge(BrowserActivity context) {
        super(context);
        mBrowserActivity = context;
        Resources resources = context.getResources();
        mStopDrawable = resources.getDrawable(R.drawable.ic_stop);
        mReloadDrawable = resources.getDrawable(R.drawable.ic_reload);
        rebuildLayout(context, true);
    }

    private void rebuildLayout(Context context, boolean rebuildData) {
        removeAllViews();
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.url_bar, this);

        mUrlView = (UrlInputView) findViewById(R.id.editurl);
        mAllButton = findViewById(R.id.all_btn);
        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.
        mBackButton = findViewById(R.id.back);
        mForwardButton = findViewById(R.id.forward);
        mStar = findViewById(R.id.star);
        mMenu = findViewById(R.id.menu);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mSearchButton = findViewById(R.id.search);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mProgressView = (ImageView) findViewById(R.id.progress);
        mProgressDrawable = mProgressView.getDrawable();

        mBackButton.setOnClickListener(this);
        mForwardButton.setOnClickListener(this);
        mStar.setOnClickListener(this);
        mAllButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mSearchButton.setOnClickListener(this);
        mMenu.setOnClickListener(this);
        mUrlView.setUrlInputListener(this);
    }

    @Override
    public void onClick(View v) {
        if (mBackButton == v) {
            mBrowserActivity.getTopWindow().goBack();
        } else if (mForwardButton == v) {
            mBrowserActivity.getTopWindow().goForward();
        } else if (mStar == v) {
            mBrowserActivity.promptAddOrInstallBookmark();
        } else if (mMenu == v) {
            mBrowserActivity.openOptionsMenu();
        } else if (mAllButton == v) {
            mBrowserActivity.bookmarksOrHistoryPicker(false, false);
        } else if (mSearchButton == v) {
            search();
        } else if (mStopButton == v) {
            stopOrRefresh();
        }
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
            mProgressDrawable.setLevel(newProgress*10000/PROGRESS_MAX);
        }
    }

    @Override
    /* package */ void setDisplayTitle(String title) {
        mUrlView.setText(title);
    }

}
