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
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.browser.view.HorizontalScrollView;
import com.android.browser.view.ScrollView;

/**
 * custom view for displaying tabs in the nav screen
 */
public class NavTabScroller extends FrameLayout {

    private LinearLayout mContentView;
    private BaseAdapter mAdapter;
    private SelectableSroller mScroller;
    private int mOrientation;

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
        mOrientation = ctx.getResources().getConfiguration().orientation;
        mScroller = (mOrientation == Configuration.ORIENTATION_LANDSCAPE) ?
                new HorizontalScroller(ctx) : new VerticalScroller(ctx);
        mContentView = mScroller.getContentView();
        View sview = (View) mScroller;
        sview.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        addView(sview);
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
        mScroller.setSelection(ix);
    }

    protected int getSelectionIndex() {
        return mScroller.getSelection();
    }

    protected Tab getSelectedItem() {
        return (Tab) mAdapter.getItem(mScroller.getSelection());
    }

    protected ViewGroup getContentView() {
        return mContentView;
    }

    private void populateList() {
        mContentView.removeAllViewsInLayout();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            NavTabView v = (NavTabView) mAdapter.getView(i, null, mContentView);
            mContentView.addView(v);
        }
    }

    View getSelectedTab() {
        int selected = mScroller.getSelection();
        if ((selected >= 0) && (selected < mContentView.getChildCount())) {
            return mContentView.getChildAt(selected);
        } else {
            return null;
        }
    }

    static interface SelectableSroller {
        void setSelection(int index);
        int getSelection();
        LinearLayout getContentView();

    }

    static class VerticalScroller extends ScrollView implements SelectableSroller  {

        private LinearLayout mContentView;
        private int mSelected;
        private boolean mSnapScroll;

        public VerticalScroller(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            init(context);
        }

        public VerticalScroller(Context context, AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        public VerticalScroller(Context context) {
            super(context);
            init(context);
        }

        private void init(Context ctx) {
            setHorizontalScrollBarEnabled(false);
            mContentView = new LinearLayout(ctx);
            mContentView.setOrientation(LinearLayout.VERTICAL);
            setVerticalScrollBarEnabled(false);
            setSmoothScrollingEnabled(true);
            int pad = ctx.getResources().getDimensionPixelSize(R.dimen.nav_scroller_padding);
            mContentView.setPadding(0, pad, 0, pad);
            mContentView.setLayoutParams(
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addView(mContentView);

        }

        public LinearLayout getContentView() {
            return mContentView;
        }

        public void setSelection(int ix) {
            mSelected = ix;
        }

        public int getSelection() {
            return mSelected;
        }

        protected void onScrollChanged(int sl, int st, int ol, int ot) {
            int midy = getScrollY() + getHeight() / 2;
            int sel = -1;
            for (int i = 0; i < mContentView.getChildCount(); i++) {
                View child = mContentView.getChildAt(i);
                if (child.getTop() <= midy && child.getBottom() >= midy) {
                    sel = i;
                    break;
                }
            }
            if (sel != -1) {
                if (sel != mSelected) {
                    setSelection(sel);
                }
                if (!isCentered(mSelected)) {
                    NavTabView ntv = (NavTabView) getSelectedView();
                    ntv.setHighlighted(false);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent evt) {
            // save drag state before super call
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
                    NavTabView ntv = (NavTabView) getSelectedView();
                    ntv.setHighlighted(isCentered(mSelected));
                }
            }
        }

        private boolean isCentered(int ix) {
            int midy = getScrollY() + (getTop() + getBottom()) / 2;
            View v = mContentView.getChildAt(ix);
            return (v.getTop() + v.getBottom()) / 2 == midy;
        }

        private void snapToSelected() {
            View v = mContentView.getChildAt(mSelected);
            int top = (v.getTop() + v.getBottom()) / 2;
            top -= getHeight() / 2;
            if (top != getScrollY()) {
                // snap to selected
                mSnapScroll = true;
                smoothScrollTo(0, top);
            }
        }

        protected View getSelectedView() {
            return mContentView.getChildAt(mSelected);
        }

    }

    static class HorizontalScroller extends HorizontalScrollView implements SelectableSroller  {

        private LinearLayout mContentView;
        private int mSelected;
        private boolean mSnapScroll;

        public HorizontalScroller(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            init(context);
        }

        public HorizontalScroller(Context context, AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        public HorizontalScroller(Context context) {
            super(context);
            init(context);
        }

        private void init(Context ctx) {
            setHorizontalScrollBarEnabled(false);
            mContentView = new LinearLayout(ctx);
            mContentView.setOrientation(LinearLayout.HORIZONTAL);
            setVerticalScrollBarEnabled(false);
            setSmoothScrollingEnabled(true);
            int pad = ctx.getResources().getDimensionPixelSize(R.dimen.nav_scroller_padding);
            mContentView.setPadding(pad, 0, pad, 0);
            mContentView.setLayoutParams(
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            addView(mContentView);

        }

        public LinearLayout getContentView() {
            return mContentView;
        }

        public void setSelection(int ix) {
            mSelected = ix;
        }

        public int getSelection() {
            return mSelected;
        }

        protected void onScrollChanged(int sl, int st, int ol, int ot) {
            int midx = getScrollX() + getWidth() / 2;
            int sel = -1;
            for (int i = 0; i < mContentView.getChildCount(); i++) {
                View child = mContentView.getChildAt(i);
                if (child.getLeft() <= midx && child.getRight() >= midx) {
                    sel = i;
                    break;
                }
            }
            if (sel != -1) {
                if (sel != mSelected) {
                    setSelection(sel);
                }
                if (!isCentered(mSelected)) {
                    NavTabView ntv = (NavTabView) getSelectedView();
                    ntv.setHighlighted(false);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent evt) {
            // save drag state before super call
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
                    NavTabView ntv = (NavTabView) getSelectedView();
                    ntv.setHighlighted(isCentered(mSelected));
                }
            }
        }

        private boolean isCentered(int ix) {
            int midx = getScrollX() + getWidth() / 2;
            View v = mContentView.getChildAt(ix);
            return (v.getLeft() + v.getRight()) / 2 == midx;
        }

        private void snapToSelected() {
            View v = mContentView.getChildAt(mSelected);
            int left = (v.getLeft() + v.getRight()) / 2;
            left -= getWidth() / 2;
            if (left != getScrollX()) {
                // snap to selected
                mSnapScroll = true;
                smoothScrollTo(left, 0);
            }
        }

        protected View getSelectedView() {
            return mContentView.getChildAt(mSelected);
        }

    }

}
