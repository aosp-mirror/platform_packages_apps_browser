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

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.browser.TabControl.TabChangeListener;
import com.android.browser.UrlInputView.UrlInputListener;

import java.util.HashMap;
import java.util.Map;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TitleBarXLarge extends TitleBarBase
    implements TabChangeListener, UrlInputListener {

    private static final int PROGRESS_MAX = 100;

    private static final int TAB_WIDTH_SELECTED = 400;
    private static final int TAB_WIDTH_UNSELECTED = 150;

    private BrowserActivity       mBrowserActivity;
    private Drawable              mStopDrawable;
    private Drawable              mReloadDrawable;
    private Drawable              mSelectedBackground;
    private Drawable              mUnselectedBackground;

    private View                  mBackButton;
    private View                  mForwardButton;
    private View                  mStar;
    private View                  mMenu;
    private View                  mAllButton;
    private TabScrollView         mTabs;
    private View                  mNewButton;
    private TabControl            mControl;
    private UrlInputView          mUrlView;

    private boolean               mIsInLandscape;
    private Map<Tab, TabViewData> mTabMap;

    private float                 mDensityScale;

    public TitleBarXLarge(BrowserActivity context, TabControl tabcontrol) {
        super(context);
        mDensityScale = context.getResources().getDisplayMetrics().density;
        mTabMap = new HashMap<Tab, TabViewData>();
        mBrowserActivity = context;
        mControl = tabcontrol;
        Resources resources = context.getResources();
        mSelectedBackground = resources.getDrawable(R.drawable.tab_selected_bg);
        mUnselectedBackground = resources.getDrawable(R.drawable.tab_unselected_bg);
        mStopDrawable = resources.getDrawable(R.drawable.progress_stop);
        mReloadDrawable = resources.getDrawable(R.drawable.ic_reload);
        rebuildLayout(context, true);
        // register the tab change listener
        mControl.setOnTabChangeListener(this);
    }

    void rebuildLayout() {
        rebuildLayout(mBrowserActivity, false);
    }

    private void rebuildLayout(Context context, boolean rebuildData) {
        removeAllViews();
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar_tabbed, this);

        mTabs = (TabScrollView) findViewById(R.id.tabs);
        mNewButton = findViewById(R.id.newtab);
        mUrlView = (UrlInputView) findViewById(R.id.editurl);
        mAllButton = findViewById(R.id.all_btn);
        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.
        mBackButton = findViewById(R.id.back);
        mForwardButton = findViewById(R.id.forward);
        mStar = findViewById(R.id.star);
        mMenu = findViewById(R.id.menu);
        View.OnClickListener listener = new View.OnClickListener() {
            public void onClick(View v) {
                if (mBackButton == v) {
                    mBrowserActivity.getTopWindow().goBack();
                } else if (mForwardButton == v) {
                    mBrowserActivity.getTopWindow().goForward();
                } else if (mStar == v) {
                    mBrowserActivity.promptAddOrInstallBookmark();
                } else if (mMenu == v) {
                    mBrowserActivity.openOptionsMenu();
                } else if (mAllButton == v) {
                    // TODO: Show the new bookmarks/windows view.
                    mBrowserActivity.bookmarksOrHistoryPicker(false);
                } else if (mNewButton == v) {
                    mBrowserActivity.openTabToHomePage();
                }
            }
        };
        mBackButton.setOnClickListener(listener);
        mForwardButton.setOnClickListener(listener);
        mStar.setOnClickListener(listener);
        mAllButton.setOnClickListener(listener);
        mMenu.setOnClickListener(listener);
        mNewButton.setOnClickListener(listener);

        mIsInLandscape = mBrowserActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        mUrlView.setVisibility(mIsInLandscape ? View.GONE : View.VISIBLE);
        mUrlView.setUrlInputListener(this);
        buildTabs(rebuildData);
        // ensure title bar state
        onCurrentTab(mControl.getCurrentTab());
    }

    void showUrlEditor(TabViewData tabdata) {
        mUrlView.setVisibility(View.VISIBLE);
        if (mIsInLandscape) {
            mTabs.setVisibility(View.GONE);
            mUrlView.requestFocus();
            mUrlView.forceIme();
        }
    }

    void hideUrlEditor() {
        Tab tab = mControl.getCurrentTab();
        if (mIsInLandscape) {
            mUrlView.setVisibility(View.GONE);
            mTabs.setVisibility(View.VISIBLE);
        } else {
            // portrait mode
            mUrlView.setText(tab.getWebView().getUrl());
        }
        tab.getWebView().requestFocus();
    }


    // UrlInputListener implementation

    @Override
    public void onAction(String text) {
        hideUrlEditor();
        Intent i = new Intent();
        i.setAction(Intent.ACTION_SEARCH);
        i.putExtra(SearchManager.QUERY, text);
        mBrowserActivity.onNewIntent(i);
    }

    @Override
    public void onDismiss() {
        hideUrlEditor();
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mBrowserActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mBrowserActivity.onCreateContextMenu(menu, this, null);
    }

    @Override
    /* package */ void setLock(Drawable d) {
        // TODO: handle in tab specific callback
    }

    @Override
    /* package */ void setFavicon(Bitmap icon) {
        // this is handled in the tab specific callback
    }

    /**
     * Update the progress, from 0 to 100.
     */
    @Override
    /* package */ void setProgress(int newProgress) {
        // this is handled in tab specific callback
    }

    @Override
    /* package */ void setDisplayTitle(String title) {
        // this is done in tab specific callback
    }

    private void buildTabs(boolean needsRebuilding) {
        mTabs.clearTabs();
        for (int i = 0; i < mControl.getTabCount(); i++) {
            Tab tab = mControl.getTab(i);
            TabViewData data = buildTab(needsRebuilding, tab);
            TabView tv = buildView(data);
        }
        mTabs.setSelectedTab(mControl.getCurrentIndex());
    }

    private TabViewData buildTab(boolean needsRebuilding, Tab tab) {
        TabViewData data = null;
        if (needsRebuilding) {
            data = new TabViewData(tab);
            mTabMap.put(tab, data);
        } else {
            data = mTabMap.get(tab);
        }
        return data;
    }

    private TabView buildView(final TabViewData data) {
        TabView tv = new TabView(mBrowserActivity, data);
        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTabs.getSelectedTab() == v) {
                    showUrlEditor(data);
                } else {
                    int ix = mControl.getTabIndex(data.mTab);
                    mTabs.setSelectedTab(ix);
                    mBrowserActivity.switchToTab(ix);
                }
            }
        });
        mTabs.addTab(tv);
        return tv;
    }

    /**
     * the views used in the tab bar
     */
    class TabView extends LinearLayout {

        TabViewData          mTabData;
        View                 mTabContent;
        TextView             mTitle;
        ImageView            mIconView;
        ImageView            mLock;
        CircularProgressView mStop;
        ImageView            mClose;
        boolean              mSelected;
        boolean              mInLoad;

        /**
         * @param context
         */
        public TabView(Context context, TabViewData tab) {
            super(context);
            mTabData = tab;
            LayoutInflater inflater = LayoutInflater.from(mContext);
            mTabContent = inflater.inflate(R.layout.tab_title, this);
            mTitle = (TextView) mTabContent.findViewById(R.id.title);
            mIconView = (ImageView) mTabContent.findViewById(R.id.favicon);
            mLock = (ImageView) mTabContent.findViewById(R.id.lock);
            mStop = (CircularProgressView) mTabContent.findViewById(R.id.stop);
            mStop.setMaxProgress(PROGRESS_MAX);
            mClose = (ImageView) mTabContent.findViewById(R.id.close);
            mClose.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeTab();
                }
            });
            mStop.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mInLoad) {
                        mBrowserActivity.stopLoading();
                    } else {
                        mBrowserActivity.getTopWindow().reload();
                    }
                }
            });
            mSelected = false;
            mInLoad = false;
            // update the status
            updateFromData();
        }

        private void updateFromData() {
            mTabData.mTabView = this;
            if (mTabData.mUrl != null) {
                setDisplayTitle(mTabData.mUrl);
            }
            if (mTabData.mTitle != null) {
                setDisplayTitle(mTabData.mTitle);
            }
            setProgress(mTabData.mProgress);
            if (mTabData.mIcon != null) {
                setFavicon(mTabData.mIcon);
            }
            if (mTabData.mLock != null) {
                setLock(mTabData.mLock);
            }
        }

        @Override
        public void setSelected(boolean selected) {
            mSelected = selected;
            mStop.setVisibility(mSelected ? View.VISIBLE : View.GONE);
            mIconView.setVisibility(mSelected ? View.VISIBLE : View.GONE);
            super.setSelected(selected);
            setBackgroundDrawable(selected ? mSelectedBackground
                    : mUnselectedBackground);
            setLayoutParams(new LayoutParams(selected ?
                    (int) (TAB_WIDTH_SELECTED * mDensityScale)
                    : (int) (TAB_WIDTH_UNSELECTED * mDensityScale),
                    LayoutParams.WRAP_CONTENT));
        }

        void setDisplayTitle(String title) {
            mTitle.setText(title);
        }

        void setFavicon(Drawable d) {
            mIconView.setImageDrawable(d);
        }

        void setLock(Drawable d) {
            if (null == d) {
                mLock.setVisibility(View.GONE);
            } else {
                mLock.setImageDrawable(d);
                mLock.setVisibility(View.VISIBLE);
            }
        }

        void setTitleCompoundDrawables(Drawable left, Drawable top,
                                              Drawable right, Drawable bottom) {
            mTitle.setCompoundDrawables(left, top, right, bottom);
        }

        void setProgress(int newProgress) {
            mStop.setProgress(newProgress);
            if (newProgress >= PROGRESS_MAX) {
                mInLoad = false;
                mStop.setImageDrawable(mReloadDrawable);
            } else {
                if (!mInLoad && getWindowToken() != null) {
                    // checking the window token lets us be sure that we
                    // are attached to a window before starting the animation,
                    // preventing a potential race condition
                    // (fix for bug http://b/2115736)
                    mInLoad = true;
                    mStop.setImageDrawable(mStopDrawable);
                }
            }
        }

        private void closeTab() {
            if (mTabData.mTab == mControl.getCurrentTab()) {
                mBrowserActivity.closeCurrentWindow();
            } else {
                mBrowserActivity.closeTab(mTabData.mTab);
            }
        }

    }

    /**
     * class to store tab state within the title bar
     */
    class TabViewData {

        Tab mTab;
        TabView mTabView;
        int mProgress;
        Drawable mIcon;
        Drawable mLock;
        String mTitle;
        String mUrl;

        TabViewData(Tab tab) {
            mTab = tab;
        }

        void setUrlAndTitle(String url, String title) {
            mUrl = url;
            mTitle = title;
            if (mTabView != null) {
                if (title != null) {
                    mTabView.setDisplayTitle(title);
                } else if (url != null) {
                    mTabView.setDisplayTitle(url);
                }
            }
        }

        void setProgress(int newProgress) {
            mProgress = newProgress;
            if (mTabView != null) {
                mTabView.setProgress(mProgress);
            }
        }

        void setFavicon(Bitmap icon) {
            Drawable[] array = new Drawable[3];
            array[0] = new PaintDrawable(Color.BLACK);
            array[1] = new PaintDrawable(Color.WHITE);
            if (icon == null) {
                array[2] = mGenericFavicon;
            } else {
                array[2] = new BitmapDrawable(icon);
            }
            LayerDrawable d = new LayerDrawable(array);
            d.setLayerInset(1, 1, 1, 1, 1);
            d.setLayerInset(2, 2, 2, 2, 2);
            mIcon = d;
            if (mTabView != null) {
                mTabView.setFavicon(mIcon);
            }
        }

    }

    // TabChangeListener implementation

    @Override
    public void onCurrentTab(Tab tab) {
        mTabs.setSelectedTab(mControl.getCurrentIndex());
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            if (tvd.mUrl != null) {
                mUrlView.setText(tvd.mUrl);
            }
            setProgress(tvd.mProgress);
        }
    }

    @Override
    public void onFavicon(Tab tab, Bitmap favicon) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setFavicon(favicon);
        }
    }

    @Override
    public void onNewTab(Tab tab) {
        TabViewData tvd = buildTab(true, tab);
        buildView(tvd);
    }

    @Override
    public void onProgress(Tab tab, int progress) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setProgress(progress);
        }
        if (tab == mControl.getCurrentTab()) {
            setProgress(progress);
        }
    }

    @Override
    public void onRemoveTab(Tab tab) {
        TabViewData tvd = mTabMap.get(tab);
        TabView tv = tvd.mTabView;
        if (tv != null) {
            mTabs.removeTab(tv);
        }
        mTabMap.remove(tab);
    }

    @Override
    public void onUrlAndTitle(Tab tab, String url, String title) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setUrlAndTitle(url, title);
        }
        if ((url != null) && (tab == mControl.getCurrentTab())) {
            mUrlView.setText(url);
        }
    }

    @Override
    public void onPageFinished(Tab tab) {
    }

    @Override
    public void onPageStarted(Tab tab) {
    }

}
