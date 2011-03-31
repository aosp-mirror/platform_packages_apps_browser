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

import com.android.browser.view.PieItem;
import com.android.browser.view.PieListView;
import com.android.browser.view.PieMenu;
import com.android.browser.view.PieMenu.PieView.OnLayoutListener;
import com.android.browser.view.PieStackView;
import com.android.browser.view.PieStackView.OnCurrentListener;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * controller for Quick Controls pie menu
 */
public class PieControl implements OnClickListener, PieMenu.PieController {

    private Activity mActivity;
    private UiController mUiController;
    private BaseUi mUi;
    private PieMenu mPie;
    private PieItem mBack;
    private PieItem mForward;
    private PieItem mRefresh;
    private PieItem mUrl;
    private PieItem mOptions;
    private PieItem mBookmarks;
    private PieItem mNewTab;
    private PieItem mClose;
    private MenuAdapter mMenuAdapter;
    private PieItem mShowTabs;
    private TabAdapter mTabAdapter;
    private TextView mTabsCount;
    private int mItemSize;

    public PieControl(Activity activity, UiController controller, BaseUi ui) {
        mActivity = activity;
        mUiController = controller;
        mUi = ui;
        mItemSize = (int) activity.getResources().getDimension(R.dimen.qc_item_size);
    }

    protected void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mActivity);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            mBack = makeItem(R.drawable.ic_back_holo_dark, 1);
            mUrl = makeItem(R.drawable.ic_web_holo_dark, 1);
            mBookmarks = makeItem(R.drawable.ic_bookmarks_holo_dark, 1);
            mRefresh = makeItem(R.drawable.ic_refresh_holo_dark, 2);
            mForward = makeItem(R.drawable.ic_forward_holo_dark, 2);
            mNewTab = makeItem(R.drawable.ic_new_window_holo_dark, 2);
            mClose = makeItem(R.drawable.ic_close_window_holo_dark, 2);
            View tabs = makeTabsView();
            mShowTabs = new PieItem(tabs, 2);
            mOptions = makeItem(
                    com.android.internal.R.drawable.ic_menu_moreoverflow_normal_holo_dark,
                    2);
            mMenuAdapter = new MenuAdapter(mActivity, mUiController);
            mTabAdapter = new TabAdapter(mActivity, mUiController);
            PieStackView stack = new PieStackView(mActivity);
            stack.setLayoutListener(new OnLayoutListener() {
                @Override
                public void onLayout(int ax, int ay, boolean left) {
                    buildTabs();
                }
            });
            stack.setOnCurrentListener(mTabAdapter);
            stack.setAdapter(mTabAdapter);
            mShowTabs.setPieView(stack);
            PieListView menuview = new PieListView(mActivity);
            menuview.setLayoutListener(new OnLayoutListener() {
                @Override
                public void onLayout(int ax, int ay, boolean left) {
                    mActivity.openOptionsMenu();
                }
            });

            mOptions.setPieView(menuview);
            menuview.setAdapter(mMenuAdapter);
            setClickListener(mBack,
                    mRefresh,
                    mForward,
                    mUrl,
                    mBookmarks,
                    mNewTab,
                    mClose
                    );
            // level 1
            mPie.addItem(mBack);
            mPie.addItem(mUrl);
            mPie.addItem(mBookmarks);
            // level 2
            mPie.addItem(mForward);
            mPie.addItem(mRefresh);
            mPie.addItem(mOptions);
            mPie.addItem(mShowTabs);
            mPie.addItem(mNewTab);
            mPie.addItem(mClose);
            mPie.setController(this);
        }
        container.addView(mPie);
    }

    private void buildTabs() {
        final List<Tab> tabs = mUiController.getTabs();
        mUi.captureTab(mUi.getActiveTab());
        mTabAdapter.setTabs(tabs);
        PieStackView sym = (PieStackView) mShowTabs.getPieView();
        sym.setCurrent(mUiController.getTabControl().getCurrentIndex());

    }

    protected void onMenuOpened(Menu menu) {
        mMenuAdapter.setMenu(menu);
    }

    protected void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    private View makeTabsView() {
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

    private PieItem makeItem(int image, int l) {
        ImageView view = new ImageView(mActivity);
        view.setImageResource(image);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return new PieItem(view, l);
    }

    private void setClickListener(PieItem... items) {
        for (PieItem item : items) {
            item.getView().setOnClickListener(this);
        }
    }

    protected void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    @Override
    public void onClick(View v) {
        Tab tab = mUiController.getTabControl().getCurrentTab();
        WebView web = tab.getWebView();
        if (mBack.getView() == v) {
            web.goBack();
        } else if (mForward.getView() == v) {
            web.goForward();
        } else if (mRefresh.getView() == v) {
            if (tab.inPageLoad()) {
                web.stopLoading();
            } else {
                web.reload();
            }
        } else if (mUrl.getView() == v) {
            mUi.showTitleBarAndEdit();
        } else if (mBookmarks.getView() == v) {
            mUiController.bookmarksOrHistoryPicker(false);
        } else if (mNewTab.getView() == v) {
            mUiController.openTabToHomePage();
            mUi.showTitleBarAndEdit();
        } else if (mClose.getView() == v) {
            mUiController.closeCurrentTab();
        }
    }

    @Override
    public boolean onOpen() {
        int n = mUiController.getTabControl().getTabCount();
        mTabsCount.setText(Integer.toString(n));
        return true;
    }

    private static class TabAdapter extends BaseAdapter implements OnCurrentListener {

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
                    mUiController.switchToTab(mUiController.getTabControl()
                            .getTabIndex(tab));
                }
            });
            return view;
        }

        @Override
        public void onSetCurrent(int index) {
            mCurrent = index;
        }

    }

    private static class MenuAdapter extends BaseAdapter
            implements OnClickListener {

        List<MenuItem> mItems;
        UiController mUiController;
        LayoutInflater mInflater;

        public MenuAdapter(Context ctx, UiController ctl) {
            mUiController = ctl;
            mInflater = LayoutInflater.from(ctx);
            mItems = new ArrayList<MenuItem>();
        }

        public void setMenu(Menu menu) {
            mItems.clear();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.isEnabled() && item.isVisible()) {
                    mItems.add(item);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public MenuItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public void onClick(View v) {
            if (v.getTag() != null) {
                mUiController.onOptionsItemSelected((MenuItem) v.getTag());
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final MenuItem item = mItems.get(position);
            View view = mInflater.inflate(
                    R.layout.qc_menu_item, null);
            TextView label =
                    (TextView) view.findViewById(R.id.title);
            label.setText(item.getTitle());
            label.setTag(item);
            label.setOnClickListener(this);
            label.setLayoutParams(new LayoutParams(240, 32));
            return label;
        }

    }

}
