
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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 *
 */
public class PageProgressView extends ImageView {

    private int mProgress;
    private int mMaxProgress;
    private Rect mBounds;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public PageProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public PageProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public PageProgressView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mMaxProgress = 10000;
        mBounds = new Rect(0,0,0,0);
        mProgress = 0;
    }

    @Override
    public void onLayout(boolean f, int l, int t, int r, int b) {
        mBounds.left = 0;
        mBounds.right = (r - l) * mProgress / mMaxProgress;
        mBounds.top = 0;
        mBounds.bottom = b-t;
    }

    void setMaxProgress(int max) {
        mMaxProgress = max;
    }

    void setProgress(int progress) {
        mProgress = progress;
        mBounds.right = getWidth()*mProgress/mMaxProgress;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
        Drawable d = getDrawable();
        d.setBounds(mBounds);
        d.draw(canvas);
    }

}
