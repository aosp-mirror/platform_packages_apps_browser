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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.browser.view.PieItem;
import com.android.browser.view.PieMenu;
import com.android.browser.view.PieStackView.OnCurrentListener;

import java.util.ArrayList;
import java.util.List;

/**
 * base controller for Quick Controls pie menu
 */
public abstract class PieControlBase implements PieMenu.PieController {

    protected Activity mActivity;
    protected UiController mUiController;
    protected PieMenu mPie;
    protected int mItemSize;
    protected TextView mTabsCount;

    public PieControlBase(Activity activity, UiController controller) {
        mActivity = activity;
        mUiController = controller;
        mItemSize = (int) activity.getResources().getDimension(R.dimen.qc_item_size);
    }

    protected void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mActivity);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            populateMenu();
            mPie.setController(this);
        }
        container.addView(mPie);
    }

    protected void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    protected void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    protected abstract void populateMenu();

    protected void setClickListener(OnClickListener listener, PieItem... items) {
        for (PieItem item : items) {
            item.getView().setOnClickListener(listener);
        }
    }

    @Override
    public boolean onOpen() {
        int n = mUiController.getTabControl().getTabCount();
        mTabsCount.setText(Integer.toString(n));
        return true;
    }

    protected PieItem makeItem(int image, int l) {
        ImageView view = new ImageView(mActivity);
        view.setImageResource(image);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return new PieItem(view, l);
    }

    protected View makeTabsView() {
        View v = mActivity.getLayoutInflater().inflate(R.layout.qc_tabs_view, null);
        mTabsCount = (TextView) v.findViewById(R.id.label);
        mTabsCount.setText("1");
        ImageView image = (ImageView) v.findViewById(R.id.icon);
        image.setImageResource(R.drawable.ic_windows_holo_dark);
        image.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        v.setLayoutParams(lp);
        return v;
    }

    static class TabAdapter extends BaseAdapter implements OnCurrentListener {

        LayoutInflater mInflater;
        UiController mUiController;
        private List<Tab> mTabs;
        private int mCurrent;

        public TabAdapter(Context ctx, UiController ctl) {
            mInflater = LayoutInflater.from(ctx);
            mUiController = ctl;
            mTabs = new ArrayList<Tab>();
            mCurrent = -1;
        }

        public void setTabs(List<Tab> tabs) {
            mTabs = tabs;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Tab getItem(int position) {
            return mTabs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Tab tab = mTabs.get(position);
            View view = mInflater.inflate(R.layout.qc_tab,
                    null);
            ImageView thumb = (ImageView) view.findViewById(R.id.thumb);
            TextView title1 = (TextView) view.findViewById(R.id.title1);
            TextView title2 = (TextView) view.findViewById(R.id.title2);
            Bitmap b = tab.getScreenshot();
            if (b != null) {
                thumb.setImageBitmap(b);
            }
            if (position > mCurrent) {
                title1.setVisibility(View.GONE);
                title2.setText(tab.getTitle());
            } else {
                title2.setVisibility(View.GONE);
                title1.setText(tab.getTitle());
            }
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mUiController.switchToTab(tab);
                }
            });
            return view;
        }

        @Override
        public void onSetCurrent(int index) {
            mCurrent = index;
        }

    }

}
