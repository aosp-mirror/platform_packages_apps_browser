
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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 *
 */
public class CircularProgressView extends ImageButton {

    private static final int[] ALPHAS = {
       64, 96, 128, 160, 192, 192, 160, 128, 96, 64
    };

    // 100 ms delay between frames, 10fps
    private static int ALPHA_REFRESH_DELAY = 100;

    private int     mEndAngle;
    private int     mProgress;
    private Paint   mPaint;
    private int     mAlpha;
    private boolean mAnimated;
    private RectF   mRect;
    private int     mMaxProgress;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public CircularProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public CircularProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public CircularProgressView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mEndAngle = 0;
        mProgress = 0;
        mMaxProgress = 100;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.BLACK);
        mRect = new RectF();
    }

    void setMaxProgress(int max) {
        mMaxProgress = max;
    }

    private synchronized boolean isAnimated() {
        return mAnimated;
    }

    private synchronized void setAnimated(boolean animated) {
        mAnimated = animated;
    }

    void setProgress(int progress) {
        mProgress = progress;
        mEndAngle = 360 * progress / mMaxProgress;
        invalidate();
        if (!isAnimated() && (progress > 0) && (progress < mMaxProgress)) {
            setAnimated(true);
            mAlpha = 0;
            post(new Runnable() {
                @Override
                public void run() {
                    if (isAnimated()) {
                        mAlpha = (mAlpha + 1) % ALPHAS.length;
                        mPaint.setAlpha(ALPHAS[mAlpha]);
                        invalidate();
                        postDelayed(this, ALPHA_REFRESH_DELAY);
                    }
                }
            });
        } else if ((progress <= 0) || (progress >= mMaxProgress))  {
            setAnimated(false);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        mRect.set(0, 0, w, h);
        if ((mProgress > 0) && (mProgress < mMaxProgress)) {
            Path p = new Path();
            p.moveTo(cx, cy);
            p.lineTo(cx, 0);
            p.arcTo(mRect, 270, mEndAngle);
            p.lineTo(cx, cy);
            int state = canvas.save();
            canvas.drawPath(p, mPaint);
            canvas.restoreToCount(state);
        }
        super.onDraw(canvas);
    }

}
