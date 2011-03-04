/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import java.util.Map;

/**
 * Manage WebView scroll events
 */
public class ScrollWebView extends WebView implements Runnable {

    private ScrollListener mScrollListener;
    private boolean mIsCancelled;
    private boolean mBackgroundRemoved = false;
    private boolean mUserInitiated = false;
    private TitleBarBase mTitleBar;
    private boolean mDrawCached = false;
    private Bitmap mBitmap;
    private Paint mCachePaint = new Paint();

    /**
     * @param context
     * @param attrs
     * @param defStyle
     * @param javascriptInterfaces
     */
    public ScrollWebView(Context context, AttributeSet attrs, int defStyle,
            Map<String, Object> javascriptInterfaces, boolean privateBrowsing) {
        super(context, attrs, defStyle, javascriptInterfaces, privateBrowsing);
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public ScrollWebView(
            Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
    }

    /**
     * @param context
     * @param attrs
     */
    public ScrollWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param context
     */
    public ScrollWebView(Context context) {
        super(context);
    }

    @Override
    protected int getTitleHeight() {
        return (mTitleBar != null) ? mTitleBar.getEmbeddedHeight() : 0;
    }

    // scroll runnable implementation
    public void run() {
        if (!mIsCancelled && (mScrollListener != null)) {
            mScrollListener.onScroll(getVisibleTitleHeight(), mUserInitiated);
        }
    }

    void hideEmbeddedTitleBar() {
        scrollBy(0, getVisibleTitleHeight());
    }

    @Override
    public void setEmbeddedTitleBar(final View title) {
        super.setEmbeddedTitleBar(title);
        mTitleBar = (TitleBarBase) title;
        if (title != null && mScrollListener != null) {
            // allow the scroll listener to initialize its state
            post(this);
        }
    }

    public boolean hasTitleBar() {
        return (mTitleBar != null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        if (MotionEvent.ACTION_DOWN == evt.getActionMasked()) {
            mUserInitiated = true;
        } else if (MotionEvent.ACTION_UP == evt.getActionMasked()
                || (MotionEvent.ACTION_CANCEL == evt.getActionMasked())) {
            mUserInitiated = false;
        }
        return super.onTouchEvent(evt);
    }

    @Override
    public void stopScroll() {
        mIsCancelled = true;
        super.stopScroll();
    }

    @Override
    protected void onScrollChanged(int l, final int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (!mIsCancelled) {
            post(this);
        } else {
            mIsCancelled = false;
        }
    }

    void setScrollListener(ScrollListener l) {
        mScrollListener = l;
    }

    @Override
    public void invalidate() {
        if (!mDrawCached) {
            super.invalidate();
        }
    }

    // callback for scroll events

    interface ScrollListener {
        public void onScroll(int visibleTitleHeight, boolean userInitiated);
    }

    void setDrawCached(boolean cached) {
        if (cached == mDrawCached) return;
        if (cached) {
            buildDrawingCache();
            mBitmap = getDrawingCache(false);
            mDrawCached = (mBitmap != null);
        } else {
            mDrawCached = false;
            mBitmap = null;
            destroyDrawingCache();
        }
    }

    @Override
    protected void onDraw(android.graphics.Canvas c) {
        if (mDrawCached) {
            c.drawBitmap(mBitmap, getScrollX(), getScrollY(), mCachePaint);
        } else {
            super.onDraw(c);
            if (!mBackgroundRemoved && getRootView().getBackground() != null) {
                mBackgroundRemoved = true;
                post(new Runnable() {
                    public void run() {
                        getRootView().setBackgroundDrawable(null);
                    }
                });
            }
        }
    }

}
