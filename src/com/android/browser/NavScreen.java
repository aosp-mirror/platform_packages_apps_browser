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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class NavScreen extends LinearLayout implements OnClickListener {

    UiController mUiController;
    PhoneUi mUi;
    Tab mTab;
    Activity mActivity;

    View mTopPanel;
    ImageButton mBack;
    ImageButton mRefresh;
    ImageButton mForward;
    ImageButton mTabs;
    ImageButton mBookmarks;
    ImageButton mMore;
    ImageButton mNewTab;
    ImageButton mNewIncognito;
    FrameLayout mHolder;

    Gallery mFlipper;
    int mTabWidth;
    int mTabHeight;
    TabAdapter mAdapter;
    ListPopupWindow mPopup;

    public NavScreen(Activity activity, UiController ctl, PhoneUi ui) {
        super(activity);
        mActivity = activity;
        mUiController = ctl;
        mUi = ui;
        init();
    }

    protected Tab getSelectedTab() {
        return (Tab) mFlipper.getSelectedItem();
    }

    protected void setTabDimensions(int w, int h) {
        mTabWidth = w;
        mTabHeight = h;
        requestLayout();
    }

    protected void showMenu() {
        Menu menu = mUi.getMenu();
        menu.setGroupVisible(R.id.NAV_MENU, false);

        MenuAdapter menuAdapter = new MenuAdapter(mContext);
        menuAdapter.setMenu(menu);
        ListPopupWindow popup = new ListPopupWindow(mContext);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAdapter(menuAdapter);
        popup.setModal(true);
        popup.setAnchorView(mMore);
        popup.setWidth((int) mContext.getResources().getDimension(
                R.dimen.menu_width));
        popup.show();
        mPopup = popup;
    }

    protected float getToolbarHeight() {
        return mActivity.getResources().getDimension(R.dimen.toolbar_height);
    }

    protected void dismissMenu() {
        if (mPopup != null) {
            mPopup.dismiss();
        }
    }

    private void init() {
        LayoutInflater.from(mContext).inflate(R.layout.nav_screen, this);
        LinearLayout content = (LinearLayout) findViewById(R.id.nav_screen);
        mTopPanel = findViewById(R.id.navtop);
        mBack = (ImageButton) findViewById(R.id.back);
        mForward = (ImageButton) findViewById(R.id.forward);
        mRefresh = (ImageButton) findViewById(R.id.refresh);
        mTabs = (ImageButton) findViewById(R.id.tabs);
        mBookmarks = (ImageButton) findViewById(R.id.bookmarks);
        mNewTab = (ImageButton) findViewById(R.id.newtab);
        mNewIncognito = (ImageButton) findViewById(R.id.newincognito);
        mMore = (ImageButton) findViewById(R.id.more);
        mBack.setOnClickListener(this);
        mForward.setOnClickListener(this);
        mRefresh.setOnClickListener(this);
        mTabs.setOnClickListener(this);
        mBookmarks.setOnClickListener(this);
        mNewTab.setOnClickListener(this);
        mNewIncognito.setOnClickListener(this);
        mMore.setOnClickListener(this);
        mHolder = (FrameLayout) findViewById(R.id.galleryholder);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mFlipper = new TabGallery(mContext);
        mFlipper.setSpacing((int)(mContext.getResources()
                .getDimension(R.dimen.nav_tab_spacing)));
        mFlipper.setLayoutParams(lp);
        mHolder.addView(mFlipper, 0);
        mAdapter = new TabAdapter(mContext, mUiController.getTabControl());
        mFlipper.setAdapter(mAdapter);
        setTab(mUi.getActiveTab(), true);
        mFlipper.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                // post as runnable to prevent bug in gesturedetector
                // when view is removed in click handler
                // sends action_cancel before action_up
                mFlipper.post(new Runnable() {
                    public void run() {
                        close();
                    }
                });
            }
        });
        mFlipper.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                final Tab tab = mAdapter.getItem(position);
                setTab(tab, false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setTab(Tab tab, boolean updateFlipper) {
        mTab = tab;
        // refresh state from tab
        WebView web = tab.getWebView();
        if (web != null) {
            mBack.setImageResource(web.canGoBack()
                    ? R.drawable.ic_back_holo_dark
                    : R.drawable.ic_back_disabled_holo_dark);
            mForward.setImageResource(web.canGoForward()
                    ? R.drawable.ic_forward_holo_dark
                    : R.drawable.ic_forward_disabled_holo_dark);
        }
        if (updateFlipper) {
            mFlipper.setSelection(mUiController.getTabControl().getTabIndex(tab));
        }
    }

    @Override
    public void onClick(View v) {
        WebView web = (mTab != null) ? mTab.getWebView() : null;
        if (web != null) {
            if (mBack == v) {
                mUi.hideNavScreen(true);
                web.goBack();
            } else if (mForward == v) {
                mUi.hideNavScreen(true);
                web.goForward();
            } else if (mRefresh == v) {
                mUi.hideNavScreen(true);
                web.reload();
            }
        }
        if (mBookmarks == v) {
            mUi.hideNavScreen(false);
            mUiController.bookmarksOrHistoryPicker(false);
        } else if (mTabs == v) {
            mUi.hideNavScreen(false);
            mUi.showActiveTabsPage();
        } else if (mNewTab == v) {
            mUi.hideNavScreen(true);
            mUiController.openTabToHomePage();
        } else if (mMore == v) {
            showMenu();
        } else if (mNewIncognito == v) {
            mUi.hideNavScreen(true);
            mUiController.openIncognitoTab();
        }
    }

    protected void close() {
        close(true);
    }

    protected void close(boolean animate) {
        mUi.hideNavScreen(animate);
        Tab tab = (Tab) mFlipper.getSelectedItem();
        if (tab != mUi.getActiveTab()) {
            mUiController.setActiveTab(tab);
        }
    }

    class TabGallery extends Gallery {

        public TabGallery(Context ctx) {
            super(ctx);
            setUnselectedAlpha(0.3f);
        }

       @Override
       protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
           return new Gallery.LayoutParams(
                   LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
       }

       @Override
       protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
           return generateDefaultLayoutParams();
       }

    }

    class TabAdapter extends BaseAdapter {

        Context context;
        TabControl tabControl;

        public TabAdapter(Context ctx, TabControl tc) {
            context = ctx;
            tabControl = tc;
        }

        void onCloseTab(Tab tab) {
            if (tab != null) {
                mUiController.closeTab(tab);
                if (tabControl.getTabCount() == 0) {
                    mUiController.openTabToHomePage();
                    mUi.hideNavScreen(false);
                } else {
                    notifyDataSetChanged();
                }
            }
        }

        @Override
        public int getCount() {
            return tabControl.getTabCount();
        }

        @Override
        public Tab getItem(int position) {
            return tabControl.getTab(position);
        }

        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView content = null;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.nav_tab_view,
                        null);
                content = (ImageView) convertView.findViewById(R.id.content);
                content.setLayoutParams(new LayoutParams(mTabWidth, mTabHeight));
            } else {
                content = (ImageView) convertView.findViewById(R.id.content);
            }
            View tbar = convertView.findViewById(R.id.titlebar);
            TextView title = (TextView) convertView.findViewById(R.id.title);
            ImageView icon = (ImageView) convertView.findViewById(R.id.favicon);
            ImageButton close = (ImageButton) convertView.findViewById(R.id.closetab);
            final Tab tab = getItem(position);
            icon.setImageDrawable(mUi.getFaviconDrawable(tab.getFavicon()));
            title.setText(tab.getUrl());
            Bitmap screen = tab.getScreenshot();
            content.setImageBitmap(screen);
            close.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onCloseTab(tab);
                }
            });
            tbar.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    close(false);
                    mUi.editUrl(false);
                }
            });
            return convertView;
        }
    }

    private class MenuAdapter extends BaseAdapter implements OnClickListener {

        List<MenuItem> mItems;
        LayoutInflater mInflater;

        public MenuAdapter(Context ctx) {
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
                dismissMenu();
                mActivity.closeOptionsMenu();
                mUi.hideNavScreen(false);
                mUiController.onOptionsItemSelected((MenuItem) v.getTag());
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final MenuItem item = mItems.get(position);
            View view = mInflater.inflate(R.layout.qc_menu_item, null);
            TextView label = (TextView) view.findViewById(R.id.title);
            label.setText(item.getTitle());
            label.setTag(item);
            label.setOnClickListener(this);
            return label;
        }

    }

}
