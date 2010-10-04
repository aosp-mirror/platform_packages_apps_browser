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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * custom view for displaying tabs in the tabbed title bar
 */
public class TabScrollView extends HorizontalScrollView {

    private BrowserActivity mBrowserActivity;
    private LinearLayout mContentView;
    private int mSelected;

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
        mBrowserActivity = (BrowserActivity)ctx;
        setHorizontalScrollBarEnabled(false);
        mContentView = new LinearLayout(mBrowserActivity);
        mContentView.setOrientation(LinearLayout.HORIZONTAL);
        mContentView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        addView(mContentView);
        mSelected = -1;
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
        tab.setActivated(false);
    }

    void removeTab(View tab) {
        int ix = mContentView.indexOfChild(tab);
        if (ix == mSelected) {
            mSelected = -1;
        } else if (ix < mSelected) {
            mSelected--;
        }
        mContentView.removeView(tab);
    }

    void ensureChildVisible(View child) {
        if (child != null) {
            int childl = child.getLeft();
            int childr = childl + child.getWidth();
            int viewl = getScrollX();
            int viewr = viewl + getWidth();
            if (childl < viewl) {
                // need scrolling to left
                scrollTo(childl, 0);
            } else if (childr > viewr) {
                // need scrolling to right
                scrollTo(childr - viewr + viewl, 0);
            }
        }
    }

}
