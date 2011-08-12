/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.webkit.WebView;

import com.android.browser.BrowserWebView.OnScrollChangedListener;

/**
 * Helper class to manage when to show the URL bar based off of touch
 * input, and when to begin the hide timer.
 */
public class UrlBarAutoShowManager implements OnTouchListener,
        OnScrollChangedListener {

    private static float V_TRIGGER_ANGLE = .9f;

    private BrowserWebView mTarget;
    private BaseUi mUi;

    private int mSlop;

    private float mStartTouchX;
    private float mStartTouchY;
    private float mLastTouchX;
    private float mLastTouchY;
    private boolean mIsTracking;
    private boolean mHasTriggered;

    public UrlBarAutoShowManager(BaseUi ui) {
        mUi = ui;
        ViewConfiguration config = ViewConfiguration.get(mUi.getActivity());
        mSlop = config.getScaledTouchSlop() * 2;
    }

    public void setTarget(BrowserWebView v) {
        if (mTarget == v) return;

        if (mTarget != null) {
            mTarget.setOnTouchListener(null);
            mTarget.setOnScrollChangedListener(null);
        }
        mTarget = v;
        if (mTarget != null) {
            mTarget.setOnTouchListener(this);
            mTarget.setOnScrollChangedListener(this);
        }
    }

    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        if (t != oldt) {
            if (t != 0) {
                // If it is showing, extend it
                if (mUi.isTitleBarShowing()) {
                    mUi.showTitleBarForDuration();
                }
            } else {
                mUi.suggestHideTitleBar();
            }
        }
    }

    void stopTracking() {
        if (mIsTracking) {
            mIsTracking = false;
            if (mUi.isTitleBarShowing()) {
                mUi.showTitleBarForDuration();
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getPointerCount() > 1) {
            stopTracking();
        }
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            if (!mIsTracking && event.getPointerCount() == 1) {
                mStartTouchY = event.getY();
                mStartTouchX = event.getX();
                mIsTracking = true;
                mHasTriggered = false;
            }
            break;
        case MotionEvent.ACTION_MOVE:
            if (mIsTracking && !mHasTriggered) {
                WebView web = (WebView) v;
                float dy = event.getY() - mStartTouchY;
                float ady = Math.abs(dy);
                float adx = Math.abs(event.getX() - mStartTouchX);
                if (ady > mSlop) {
                    mHasTriggered = true;
                    float angle = (float) Math.atan2(ady, adx);
                    if (dy > mSlop && angle > V_TRIGGER_ANGLE
                            && !mUi.isTitleBarShowing()
                            && web.getVisibleTitleHeight() == 0) {
                        mUi.showTitleBar();
                    }
                }
            }
            break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            stopTracking();
            break;
        }
        return false;
    }

}
