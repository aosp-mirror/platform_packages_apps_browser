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
import android.graphics.Canvas;
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

    private Tab mTab;
    private BrowserWebView mWebView;
    private WebProxyView mProxy;
    private ImageView mClose;
    private FrameLayout mContainer;
    private TextView mTitle;
    private View mTitleBar;
    private OnClickListener mClickListener;
    private boolean mHighlighted;

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
        LayoutInflater.from(mContext).inflate(R.layout.nav_tab_view, this);
        mContainer = (FrameLayout) findViewById(R.id.tab_view);
        mClose = (ImageView) findViewById(R.id.closetab);
        mTitle = (TextView) findViewById(R.id.title);
        mTitleBar = findViewById(R.id.titlebar);
    }

    protected boolean isClose(View v) {
        return v == mClose;
    }

    protected boolean isTitle(View v) {
        return v == mTitleBar;
    }

    protected boolean isWebView(View v) {
        return v == mProxy;
    }

    protected void setHighlighted(boolean highlighted) {
        if (highlighted == mHighlighted) return;
        mHighlighted = highlighted;
    }

    private void setTitle() {
        if (mTab == null) return;
        if (mHighlighted) {
            mTitle.setText(mTab.getUrl());
        } else {
            String txt = mTab.getTitle();
            if (txt == null) {
                txt = mTab.getUrl();
            }
            mTitle.setText(txt);
        }
    }

    protected boolean isHighlighted() {
        return mHighlighted;
    }

    protected void setWebView(PhoneUi ui, Tab tab) {
        mTab = tab;
        setTitle();
        BrowserWebView web = (BrowserWebView) tab.getWebView();
        if (web != null) {
            mWebView = web;
            removeFromParent(mWebView);
            mProxy = new WebProxyView(mContext, mWebView);
            mContainer.addView(mProxy, 0);
        }
    }

    protected void hideTitle() {
        mTitleBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        mClickListener = listener;
        mTitleBar.setOnClickListener(mClickListener);
        mClose.setOnClickListener(mClickListener);
        if (mProxy != null) {
            mProxy.setOnClickListener(mClickListener);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mWebView != null) {
            mWebView.setProxyView(null);
        }
    }

    @Override
    public void onAttachedToWindow() {
        if (mWebView != null) {
            mWebView.invalidate();
        }
    }

    private static void removeFromParent(View v) {
        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

    static class WebProxyView extends View {

        private BrowserWebView mWeb;

        public WebProxyView(Context context, BrowserWebView web) {
            super(context);
            setWillNotDraw(false);
            mWeb = web;
            mWeb.setProxyView(this);

        }

        public void onDraw(Canvas c) {
            float scale = 0.7f;
            int sx = mWeb.getScrollX();
            int sy = mWeb.getScrollY();
            c.scale(scale, scale);
            c.translate(-sx, -sy);
            mWeb.onDraw(c);
        }

    }

}
