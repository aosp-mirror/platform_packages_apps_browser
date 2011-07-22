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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.browser.view.Gallery;

/**
 * custom view for displaying tabs in the nav screen
 */
public class NavTabGallery extends Gallery {

    interface OnRemoveListener {
        public void onRemovePosition(int position);
    }

    // after drag animation velocity in pixels/sec
    private static final float MIN_VELOCITY = 1500;

    private OnRemoveListener mRemoveListener;
    private boolean mBlockUpCallback;

    public NavTabGallery(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public NavTabGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NavTabGallery(Context context) {
        super(context);
    }

    public void setOnRemoveListener(OnRemoveListener l) {
        mRemoveListener = l;
    }

    protected void setSelection(int ix) {
        super.setSelectedPositionInt(ix);
    }

    protected int getSelectionIndex() {
        return getSelectedItemPosition();
    }

    protected Tab getSelectedItem() {
        return (Tab) mAdapter.getItem(getSelectedItemPosition());
    }

    View getSelectedTab() {
        return getSelectedView();
    }

    @Override
    protected void onOrthoDrag(View v, MotionEvent down, MotionEvent move,
            float distance) {
        offsetView(v, - distance);
    }

    @Override
    protected void onOrthoFling(View v, MotionEvent down, MotionEvent move,
            float velocity) {
        if (Math.abs(velocity) > MIN_VELOCITY) {
            mBlockUpCallback = true;
            animateOut(v, velocity);
        }
    }

    @Override
    protected void onUp(View downView) {
        if (mBlockUpCallback) {
            mBlockUpCallback = false;
            return;
        }
        if (mIsOrthoDragged && downView != null) {
            // offset
            int diff = calculateTop(downView, false) - (mHorizontal ? downView.getTop()
                    : downView.getLeft());
            if (Math.abs(diff) > (mHorizontal ? downView.getHeight() : downView.getWidth()) / 2) {
                // remove it
                animateOut(downView, - Math.signum(diff) * MIN_VELOCITY);
            } else {
                // snap back
                offsetView(downView, diff);
            }
        } else {
            super.onUp(downView);
        }
    }

    private void offsetView(View v, float distance) {
        if (mHorizontal) {
            v.offsetTopAndBottom((int) distance);
        } else {
            v.offsetLeftAndRight((int) distance);
        }
    }

    private void animateOut(View v, float velocity) {
        final int position = mDownTouchPosition;
        int target = 0;
        if (velocity < 0) {
            target = mHorizontal ? -v.getHeight() :  - v.getWidth();
        } else {
            target = mHorizontal ? getHeight() : getWidth();
        }
        int distance = target - (mHorizontal ? v.getTop() : v.getLeft());
        long duration = (long) (Math.abs(distance) * 1000 / Math.abs(velocity));
        ObjectAnimator animator = null;
        if (mHorizontal) {
            animator = ObjectAnimator.ofFloat(v, TRANSLATION_Y, 0, target);
        } else {
            animator = ObjectAnimator.ofFloat(v, TRANSLATION_X, 0, target);
        }
        animator.setDuration(duration);
        animator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                if (mRemoveListener !=  null) {
                    mRemoveListener.onRemovePosition(position);
                }
            }
        });
        animator.start();
    }

}
