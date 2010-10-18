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
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple bread crumb view
 * Use setController to receive callbacks from user interactions
 * Use pushView, popView, clear, and getTopData to change/access the view stack
 */
public class BreadCrumbView extends LinearLayout implements OnClickListener {

    interface Controller {
        public void onTop(int level, Object data);
    }

    private ImageButton mBackButton;
    private Controller mController;
    private List<Crumb> mCrumbs;
    private boolean mUseBackButton;
    private Drawable mSeparatorDrawable;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public BreadCrumbView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public BreadCrumbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public BreadCrumbView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mUseBackButton = false;
        mCrumbs = new ArrayList<Crumb>();
        mSeparatorDrawable = ctx.getResources().getDrawable(
                R.drawable.crumb_divider);
    }

    public void setUseBackButton(boolean useflag) {
        mUseBackButton = useflag;
        if (mUseBackButton && (mBackButton == null)) {
            addBackButton();
        } else if (!mUseBackButton && (mBackButton != null)) {
            removeView(mBackButton);
            mBackButton = null;
        }
    }

    public void setController(Controller ctl) {
        mController = ctl;
    }

    public Object getTopData() {
        Crumb c = getTopCrumb();
        if (c != null) {
            return c.data;
        }
        return null;
    }

    public int size() {
        return mCrumbs.size();
    }

    public void clear() {
        while (mCrumbs.size() > 1) {
            pop(false);
        }
        pop(true);
    }

    public void notifyController() {
        if (mController != null) {
            if (mCrumbs.size() > 0) {
                mController.onTop(mCrumbs.size(), getTopCrumb().data);
            } else {
                mController.onTop(0, null);
            }
        }
    }

    public void pushView(String name, Object data) {
        pushView(name, true, data);
    }

    public void pushView(String name, boolean canGoBack, Object data) {
        Crumb crumb = new Crumb(name, canGoBack, data);
        pushCrumb(crumb);
    }

    public void pushView(View view, Object data) {
        Crumb crumb = new Crumb(view, true, data);
        pushCrumb(crumb);
    }

    public void popView() {
        pop(true);
    }

    private void addBackButton() {
        mBackButton = new ImageButton(mContext);
        mBackButton.setImageResource(R.drawable.ic_back_normal);
        mBackButton.setBackgroundResource(R.drawable.browserbarbutton);
        mBackButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        mBackButton.setOnClickListener(this);
        mBackButton.setVisibility(View.INVISIBLE);
        addView(mBackButton, 0);
    }

    private void pushCrumb(Crumb crumb) {
        if (!mUseBackButton || (mCrumbs.size() > 0)) {
            addSeparator();
        }
        mCrumbs.add(crumb);
        addView(crumb.crumbView);
        if (mUseBackButton) {
            mBackButton.setVisibility(crumb.canGoBack ? View.VISIBLE : View.INVISIBLE);
        }
        crumb.crumbView.setOnClickListener(this);
    }

    private void addSeparator() {
        ImageView sep = new ImageView(mContext);
        sep.setImageDrawable(mSeparatorDrawable);
        sep.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT));
        addView(sep);
    }

    private void pop(boolean notify) {
        int n = mCrumbs.size();
        if (n > 0) {
            removeLastView();
            if (!mUseBackButton || (n > 1)) {
                // remove separator
                removeLastView();
            }
            mCrumbs.remove(n - 1);
            if (mUseBackButton) {
                Crumb top = getTopCrumb();
                if (top != null && top.canGoBack) {
                    mBackButton.setVisibility(View.VISIBLE);
                } else {
                    mBackButton.setVisibility(View.INVISIBLE);
                }
            }
            if (notify) {
                notifyController();
            }
        }
    }

    private void removeLastView() {
        int ix = getChildCount();
        if (ix > 0) {
            removeViewAt(ix-1);
        }
    }

    private Crumb getTopCrumb() {
        Crumb crumb = null;
        if (mCrumbs.size() > 0) {
            crumb = mCrumbs.get(mCrumbs.size() - 1);
        }
        return crumb;
    }

    @Override
    public void onClick(View v) {
        if (mBackButton == v) {
            popView();
            notifyController();
        } else {
            // pop until view matches crumb view
            while (v != getTopCrumb().crumbView) {
                pop(false);
            }
            notifyController();
        }
    }
    @Override
    public int getBaseline() {
        int ix = getChildCount();
        if (ix > 0) {
            // If there is at least one crumb, the baseline will be its
            // baseline.
            return getChildAt(ix-1).getBaseline();
        }
        return super.getBaseline();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = mSeparatorDrawable.getIntrinsicHeight();
        if (mMeasuredHeight < height) {
            // This should only be an issue if there are currently no separators
            // showing; i.e. if there is one crumb and no back button.
            int mode = View.MeasureSpec.getMode(heightMeasureSpec);
            switch(mode) {
                case View.MeasureSpec.AT_MOST:
                    if (View.MeasureSpec.getSize(heightMeasureSpec) < height) {
                        return;
                    }
                    break;
                case View.MeasureSpec.EXACTLY:
                    return;
                default:
                    break;
            }
            setMeasuredDimension(mMeasuredWidth, height);
        }
    }

    class Crumb {

        public View crumbView;
        public boolean canGoBack;
        public Object data;

        public Crumb(String title, boolean backEnabled, Object tag) {
            init(makeCrumbView(title), backEnabled, tag);
        }

        public Crumb(View view, boolean backEnabled, Object tag) {
            init(view, backEnabled, tag);
        }

        private void init(View view, boolean back, Object tag) {
            canGoBack = back;
            crumbView = view;
            data = tag;
        }

        private TextView makeCrumbView(String name) {
            TextView tv = new TextView(mContext);
            tv.setTextAppearance(mContext, android.R.style.TextAppearance_Medium);
            tv.setPadding(16, 0, 16, 0);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setText(name);
            tv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT));
            tv.setMaxWidth(mContext.getResources().getInteger(
                    R.integer.max_width_crumb));
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            return tv;
        }

    }

}
