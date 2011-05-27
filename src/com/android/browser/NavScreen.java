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
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class NavScreen extends RelativeLayout implements OnClickListener {

    UiController mUiController;
    PhoneUi mUi;
    Tab mTab;
    Activity mActivity;

    ImageButton mRefresh;
    ImageButton mForward;
    ImageButton mBookmarks;
    ImageButton mMore;
    ImageButton mNewTab;
    ImageButton mNewIncognito;
    FrameLayout mHolder;

    TextView mTitle;
    ImageView mFavicon;
    ImageButton mCloseTab;

    NavTabScroller mScroller;
    float mTabAspect = 0.66f;
    int mTabWidth;
    int mTabHeight;
    TabAdapter mAdapter;
    ListPopupWindow mPopup;
    int mOrientation;

    public NavScreen(Activity activity, UiController ctl, PhoneUi ui) {
        super(activity);
        mActivity = activity;
        mUiController = ctl;
        mUi = ui;
        mOrientation = activity.getResources().getConfiguration().orientation;
        init();
    }

    protected Tab getSelectedTab() {
        return (Tab) mScroller.getSelectedItem();
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

    // for configuration changes
    @Override
    protected void onConfigurationChanged(Configuration newconfig) {
        if (newconfig.orientation != mOrientation) {
            int selIx = mScroller.getSelectionIndex();
            removeAllViews();
            init();
            mScroller.setSelection(selIx);
            mOrientation = newconfig.orientation;
            mAdapter.notifyDataSetChanged();
        }
    }

    private void init() {
        LayoutInflater.from(mContext).inflate(R.layout.nav_screen, this);
        mBookmarks = (ImageButton) findViewById(R.id.bookmarks);
        mNewTab = (ImageButton) findViewById(R.id.newtab);
        mNewIncognito = (ImageButton) findViewById(R.id.newincognito);
        mMore = (ImageButton) findViewById(R.id.more);
        mBookmarks.setOnClickListener(this);
        mNewTab.setOnClickListener(this);
        mNewIncognito.setOnClickListener(this);
        mMore.setOnClickListener(this);
        mScroller = (NavTabScroller) findViewById(R.id.scroller);
        mAdapter = new TabAdapter(mContext, mUiController.getTabControl());
        mScroller.setAdapter(mAdapter);

        // update state for active tab
        mScroller.setSelection(mUiController.getTabControl().getTabPosition(mUi.getActiveTab()));
    }

    @Override
    public void onClick(View v) {
        WebView web = (mTab != null) ? mTab.getWebView() : null;
        if (web != null) {
            if (mForward == v) {
                mUi.hideNavScreen(true);
                web.goForward();
            } else if (mRefresh == v) {
                mUi.hideNavScreen(true);
                web.reload();
            }
        }
        if (mBookmarks == v) {
            mUi.hideNavScreen(false);
            switchToSelected();
            mUiController.bookmarksOrHistoryPicker(false);
        } else if (mNewTab == v) {
            openNewTab();
        } else if (mMore == v) {
            showMenu();
        } else if (mNewIncognito == v) {
            mUi.hideNavScreen(true);
            mUiController.openIncognitoTab();
        } else if (mTitle == v) {
            mUi.getTitleBar().setSkipTitleBarAnimations(true);
            close(false);
            mUi.editUrl(false);
            mUi.getTitleBar().setSkipTitleBarAnimations(false);
        }
    }

    private void onCloseTab(Tab tab) {
        if (tab != null) {
            mUiController.closeTab(tab);
            if (mUiController.getTabControl().getTabCount() == 0) {
                openNewTab();
            } else {
                mAdapter.notifyDataSetChanged();
            }
        }
    }


    private void openNewTab() {
        // need to call openTab explicitely with setactive false
        Tab tab = mUiController.openTab(BrowserSettings.getInstance().getHomePage(),
                false, false, false);
        mAdapter.notifyDataSetChanged();
        if (tab != null) {
            // set tab as the selected in flipper, then hide
            final int tix = mUi.mTabControl.getTabPosition(tab);
            mScroller.setSelection(tix);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    mUi.hideNavScreen(true);
                    switchToSelected();
                }
            }, 100);
        }
    }

    private void switchToSelected() {
        Tab tab = (Tab) mScroller.getSelectedItem();
        if (tab != mUi.getActiveTab()) {
            mUiController.setActiveTab(tab);
        }
    }

    protected void close() {
        close(true);
    }

    protected void close(boolean animate) {
        mUi.hideNavScreen(animate);
    }

    class TabGallery extends Gallery {

        public TabGallery(Context ctx) {
            super(ctx);
            setUnselectedAlpha(0.3f);
        }

       @Override
       protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
           return new Gallery.LayoutParams(mTabWidth, mTabHeight);
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
        public View getView(final int position, View convertView, ViewGroup parent) {
            final NavTabView tabview = new NavTabView(mActivity);
            final Tab tab = getItem(position);
            final BrowserWebView web = (BrowserWebView) tab.getWebView();
            tabview.setWebView(mUi, tab);
            tabview.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tabview.isRefresh(v)) {
                        mUi.hideNavScreen(true);
                        web.reload();
                    } else if (tabview.isClose(v)) {
                        onCloseTab((Tab) (mScroller.getSelectedItem()));
                    } else if (tabview.isTitle(v)) {
                        mUi.getTitleBar().setSkipTitleBarAnimations(true);
                        close(false);
                        mUi.editUrl(false);
                        mUi.getTitleBar().setSkipTitleBarAnimations(false);
                    } else if (tabview.isForward(v)) {
                        mUi.hideNavScreen(true);
                        web.goForward();
                    } else if (tabview.isWebView(v)) {
                        mScroller.setSelection(position);
                        close();

                    }
                }
            });
            return tabview;
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
