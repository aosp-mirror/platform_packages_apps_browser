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

import com.android.browser.view.HorizontalScrollView;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;

/**
 * custom view for displaying tabs in the nav screen
 */
public class NavTabScroller extends HorizontalScrollView {

    private static final float DEFAULT_ALPHA = 0.5f;

    private LinearLayout mContentView;
    private int mSelected;
    private BaseAdapter mAdapter;
    private boolean mSnapScroll;

    public NavTabScroller(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public NavTabScroller(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NavTabScroller(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        setHorizontalScrollBarEnabled(false);
        mContentView = new LinearLayout(ctx);
        mContentView.setOrientation(LinearLayout.HORIZONTAL);
        int pad = ctx.getResources().getDimensionPixelSize(R.dimen.nav_scroller_padding);
        mContentView.setPadding(pad, 0, pad, 0);
        mContentView.setLayoutParams(
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(mContentView);
        mSelected = -1;
    }

    protected void setAdapter(BaseAdapter adapter) {
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(new DataSetObserver() {

            @Override
            public void onChanged() {
                super.onChanged();
                populateList();
            }

            @Override
            public void onInvalidated() {
                super.onInvalidated();
            }
        });
        populateList();
    }

    protected void setSelection(int ix) {
        mSelected = ix;
        updateViewAlpha();
    }

    private void updateViewAlpha() {
        final int n = mContentView.getChildCount();
        for (int i = 0; i < n; i ++) {
            View v = mContentView.getChildAt(i);
            v.setAlpha((i == mSelected) ? 1.0f : DEFAULT_ALPHA);
        }
    }

    protected int getSelectionIndex() {
        return mSelected;
    }

    protected Tab getSelectedItem() {
        return (Tab) mAdapter.getItem(mSelected);
    }

    protected ViewGroup getContentView() {
        return mContentView;
    }

    private void populateList() {
        clearTabs();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            View v = mAdapter.getView(i, null, mContentView);
            mContentView.addView(v);
            v.setAlpha((i == mSelected) ? 1.0f : DEFAULT_ALPHA);
        }
    }

    View getSelectedTab() {
        if ((mSelected >= 0) && (mSelected < mContentView.getChildCount())) {
            return mContentView.getChildAt(mSelected);
        } else {
            return null;
        }
    }

    void clearTabs() {
        for (int i = 0; i < mContentView.getChildCount(); i++) {
            ViewGroup vg = (ViewGroup) mContentView.getChildAt(i);
            vg.removeViewAt(0);
        }
        mContentView.removeAllViews();
    }

    protected void onScrollChanged(int sl, int st, int ol, int ot) {
        int midx = getScrollX() + getWidth() / 2;
        int sel = -1;
        for (int i = 0; i < mContentView.getChildCount(); i++) {
            View child = mContentView.getChildAt(i);
            if (child.getLeft() < midx && child.getRight() > midx) {
                sel = i;
                break;
            }
        }
        if (sel != -1 && sel != mSelected) {
            setSelection(sel);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        boolean dragged = mIsBeingDragged;
        boolean result = super.onTouchEvent(evt);
        if (MotionEvent.ACTION_UP == evt.getActionMasked()) {
            if (mScroller.isFinished() && dragged) {
                snapToSelected();
            }
        }
        return result;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.isFinished() && !mIsBeingDragged) {
            if (!mSnapScroll) {
                snapToSelected();
            } else {
                // reset snap scrolling flag
                mSnapScroll = false;
            }
        }
    }

    private void snapToSelected() {
        // snap to selected
        mSnapScroll = true;
        View v = mContentView.getChildAt(mSelected);
        int left = (v.getLeft() + v.getRight()) / 2;
        left -= getWidth() / 2;
        scrollTo(left,0);
    }

}
