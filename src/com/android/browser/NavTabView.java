/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.browser;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NavTabView extends LinearLayout {

    Tab mTab;
    BrowserWebView mWebView;
    ImageButton mForward;
    ImageButton mRefresh;
    ImageView mFavicon;
    ImageButton mClose;
    FrameLayout mContainer;
    TextView mTitle;
    View mTitleBar;
    OnClickListener mClickListener;
    boolean mHighlighted;
    Drawable mTitleBg;
    Drawable mUrlBg;
    float mMediumTextSize;
    float mSmallTextSize;

    public NavTabView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public NavTabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NavTabView(Context context) {
        super(context);
        init();
    }

    private void init() {
        final Resources res = mContext.getResources();
        mMediumTextSize = res.getDimension(R.dimen.nav_tab_text_normal);
        mSmallTextSize = res.getDimension(R.dimen.nav_tab_text_small);
        LayoutInflater.from(mContext).inflate(R.layout.nav_tab_view,
                    this);
        mContainer = (FrameLayout) findViewById(R.id.tab_view);
        mForward = (ImageButton) findViewById(R.id.forward);
        mClose = (ImageButton) findViewById(R.id.closetab);
        mRefresh = (ImageButton) findViewById(R.id.refresh);
        mTitle = (TextView) findViewById(R.id.title);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mTitleBar = findViewById(R.id.titlebar);
        mTitleBg = res.getDrawable(R.drawable.bg_urlbar);
        mUrlBg = res.getDrawable(
                com.android.internal.R.drawable.edit_text_holo_dark);
        setState(false);
    }

    protected boolean isRefresh(View v) {
        return v == mRefresh;
    }

    protected boolean isClose(View v) {
        return v == mClose;
    }

    protected boolean isTitle(View v) {
        return v == mTitleBar;
    }

    protected boolean isForward(View v) {
        return v == mForward;
    }

    protected boolean isWebView(View v) {
        return v == mWebView;
    }

    protected void setHighlighted(boolean highlighted) {
        if (highlighted == mHighlighted) return;
        mHighlighted = highlighted;
        setState(highlighted);
    }

    private void setState(boolean highlighted) {
        if (highlighted) {
            setAlpha(1.0f);
            mRefresh.setVisibility(View.VISIBLE);
            mFavicon.setVisibility(View.VISIBLE);
            mForward.setVisibility(mWebView.canGoForward()
                    ? View.VISIBLE : View.GONE);
            mTitleBar.setBackgroundDrawable(mTitleBg);
            mClose.setVisibility(View.VISIBLE);
            mTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, mMediumTextSize);
            mTitle.setBackgroundDrawable(mUrlBg);
        } else {
            setAlpha(0.8f);
            mForward.setVisibility(View.GONE);
            mRefresh.setVisibility(View.INVISIBLE);
            mFavicon.setVisibility(View.INVISIBLE);
            mClose.setVisibility(View.GONE);
            mTitleBar.setBackgroundDrawable(null);
            mTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, mSmallTextSize);
            mTitle.setBackgroundDrawable(null);
        }
        setTitle();
    }

    private void setTitle() {
        if (mTab == null) return;
        if (mHighlighted) {
            mTitle.setText(mTab.getUrl());
        } else {
            String txt = mTab.getTitle();
            if (txt == null) txt = mTab.getUrl();
            mTitle.setText(txt);
        }
    }

    protected boolean isHighlighted() {
        return mHighlighted;
    }

    protected void setWebView(PhoneUi ui, Tab tab) {
        mTab = tab;
        BrowserWebView web = (BrowserWebView) tab.getWebView();
        if (web == null) return;
        mWebView = web;
        removeFromParent(mWebView);
        mWebView.setNavMode(true);
        mContainer.addView(mWebView, 0);
        if (mWebView != null) {
            mForward.setVisibility(mWebView.canGoForward()
                    ? View.VISIBLE : View.GONE);
        }
        mFavicon.setImageDrawable(ui.getFaviconDrawable(tab.getFavicon()));
        setTitle();
    }

    protected void hideTitle() {
        mTitleBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        mClickListener = listener;
        mTitleBar.setOnClickListener(mClickListener);
        mRefresh.setOnClickListener(mClickListener);
        mForward.setOnClickListener(mClickListener);
        mClose.setOnClickListener(mClickListener);
        if (mWebView != null) {
            mWebView.setOnClickListener(mClickListener);
        }
    }

    private static void removeFromParent(View v) {
        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

}
