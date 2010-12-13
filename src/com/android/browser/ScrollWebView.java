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
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

import java.util.Map;

/**
 *  Manage WebView scroll events
 */
public class ScrollWebView extends WebView implements Runnable {

    private ScrollListener mScrollListener;
    private boolean mIsCancelled;

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
    public ScrollWebView(Context context, AttributeSet attrs, int defStyle,
            boolean privateBrowsing) {
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

    // scroll runnable implementation
    public void run() {
        if (!mIsCancelled && (mScrollListener != null)) {
            mScrollListener.onScroll(getVisibleTitleHeight());
        }
    }

    void hideEmbeddedTitleBar() {
        scrollBy(0, getVisibleTitleHeight());
    }

    @Override
    public void setEmbeddedTitleBar(final View title) {
        super.setEmbeddedTitleBar(title);
        if (title != null && mScrollListener != null) {
            // allow the scroll listener to initialize its state
            post(this);
        }
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

    // callback for scroll events

    interface ScrollListener {
        public void onScroll(int visibleTitleHeight);
    }

}
