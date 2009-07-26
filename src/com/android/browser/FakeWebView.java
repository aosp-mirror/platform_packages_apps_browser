/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;

import android.util.Log;

/**
 *  This class is used by ImageAdapter to draw a representation of each tab. It 
 *  overrides ImageView so it can be used for the new tab image as well.
 */
public class FakeWebView extends ImageView {
    private TabControl.PickerData mPickerData;
    private boolean        mUsesResource;

    public FakeWebView(Context context) {
        this(context, null);
    }
    
    public FakeWebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public FakeWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mUsesResource) {
            super.onDraw(canvas);
        } else {
            // Always draw white behind the picture just in case the picture
            // draws nothing.
            // FIXME: We used to draw white only when the WebView was null but
            // sometimes the picture was empty. So now we always draw white. It
            // would be nice to know if the picture is empty so we can avoid
            // drawing white.
            canvas.drawColor(Color.WHITE);
            if (mPickerData != null) {
                final Picture p = mPickerData.mPicture;
                if (p != null) {
                    canvas.save();
                    float scale = getWidth() * mPickerData.mScale
                            / mPickerData.mWidth;
                    // Check for NaN and infinity.
                    if (Float.isNaN(scale) || Float.isInfinite(scale)) {
                        scale = 1.0f;
                    }
                    canvas.scale(scale, scale);
                    canvas.translate(-mPickerData.mScrollX,
                            -mPickerData.mScrollY);
                    canvas.drawPicture(p);
                    canvas.restore();
                }
            }
        }
    }
    
    @Override
    public void setImageResource(int resId) {
        mUsesResource = true;
        mPickerData = null;
        super.setImageResource(resId);
    }

    /**
     *  Set a WebView for this FakeWebView to represent.
     *  @param  t The tab whose picture and other data will be used in onDraw.
     */
    public void setTab(TabControl.Tab t) {
        mUsesResource = false;
        if (mPickerData != null) {
            // Clear the old tab's view first
            mPickerData.mFakeWebView = null;
        }
        mPickerData = null;
        if (t != null && t.getPickerData() != null) {
            mPickerData = t.getPickerData();
            mPickerData.mFakeWebView = this;
        }
    }
}
