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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * custom view for displaying tabs in the tabbed title bar
 */
public class TabScrollView extends HorizontalScrollView {

    private BrowserActivity mBrowserActivity;
    private LinearLayout mContentView;
    private int mSelected;
    private Drawable mArrowLeft;
    private Drawable mArrowRight;
    private int mAnimationDuration;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public TabScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public TabScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public TabScrollView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mBrowserActivity = (BrowserActivity) ctx;
        mAnimationDuration = ctx.getResources().getInteger(
                R.integer.tab_animation_duration);
        setHorizontalScrollBarEnabled(false);
        mContentView = new LinearLayout(mBrowserActivity);
        mContentView.setOrientation(LinearLayout.HORIZONTAL);
        mContentView.setLayoutParams(
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(mContentView);
        mSelected = -1;
        mArrowLeft = ctx.getResources().getDrawable(R.drawable.ic_arrow_left);
        mArrowRight = ctx.getResources().getDrawable(R.drawable.ic_arrow_right);
        // prevent ProGuard from removing the property methods
        setScroll(getScroll());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        ensureChildVisible(getSelectedTab());
    }

    void setSelectedTab(int position) {
        View v = getSelectedTab();
        if (v != null) {
            v.setActivated(false);
        }
        mSelected = position;
        v = getSelectedTab();
        if (v != null) {
            v.setActivated(true);
        }
        requestLayout();
    }

    int getChildIndex(View v) {
        return mContentView.indexOfChild(v);
    }

    View getSelectedTab() {
        if ((mSelected >= 0) && (mSelected < mContentView.getChildCount())) {
            return mContentView.getChildAt(mSelected);
        } else {
            return null;
        }
    }

    void clearTabs() {
        mContentView.removeAllViews();
    }

    void addTab(View tab) {
        mContentView.addView(tab);
        animateIn(tab);
        tab.setActivated(false);
    }

    void removeTab(View tab) {
        int ix = mContentView.indexOfChild(tab);
        if (ix == mSelected) {
            mSelected = -1;
        } else if (ix < mSelected) {
            mSelected--;
        }
        animateOut(tab);
    }

    private void ensureChildVisible(View child) {
        if (child != null) {
            int childl = child.getLeft();
            int childr = childl + child.getWidth();
            int viewl = getScrollX();
            int viewr = viewl + getWidth();
            if (childl < viewl) {
                // need scrolling to left
                animateScroll(childl);
            } else if (childr > viewr) {
                // need scrolling to right
                animateScroll(childr - viewr + viewl);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int l = getScrollX();
        int r = l + getWidth();
        int dis = 8;
        if (l > 0) {
            int aw = mArrowLeft.getIntrinsicWidth();
            mArrowLeft.setBounds(l + dis, 0, l + dis + aw, getHeight());
            mArrowLeft.draw(canvas);
        }
        if (r < mContentView.getWidth()) {
            int aw = mArrowRight.getIntrinsicWidth();
            mArrowRight.setBounds(r - dis - aw, 0, r - dis, getHeight());
            mArrowRight.draw(canvas);
        }
    }

    private void animateIn(View tab) {
        ObjectAnimator animator = ObjectAnimator.ofInt(tab, "TranslationX", 500, 0);
        animator.setDuration(mAnimationDuration);
        animator.start();
    }

    private void animateOut(final View tab) {
        ObjectAnimator animator = ObjectAnimator.ofInt(
                tab, "TranslationX", 0, getScrollX() - tab.getRight());
        animator.setDuration(mAnimationDuration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContentView.removeView(tab);
            }
        });
        animator.setInterpolator(new AccelerateInterpolator());
        animator.start();
    }

    private void animateScroll(int newscroll) {
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "scroll", getScrollX(), newscroll);
        animator.setDuration(mAnimationDuration);
        animator.start();
    }

    /**
     * required for animation
     */
    public void setScroll(int newscroll) {
        scrollTo(newscroll, getScrollY());
    }

    /**
     * required for animation
     */
    public int getScroll() {
        return getScrollX();
    }

}
