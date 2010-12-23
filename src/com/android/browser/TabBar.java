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

import com.android.browser.ScrollWebView.ScrollListener;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TabBar extends LinearLayout
        implements ScrollListener, OnClickListener {

    private static final int PROGRESS_MAX = 100;

    private Activity mActivity;
    private UiController mUiController;
    private TabControl mTabControl;
    private XLargeUi mUi;

    private final int mTabWidthSelected;
    private final int mTabWidthUnselected;

    private TabScrollView mTabs;

    private ImageButton mNewTab;
    private int mButtonWidth;

    private Map<Tab, TabViewData> mTabMap;

    private boolean mUserRequestedUrlbar;
    private int mVisibleTitleHeight;

    private Drawable mGenericFavicon;

    private Drawable mActiveDrawable;
    private Drawable mInactiveDrawable;

    private Bitmap mShaderBuffer;
    private Canvas mShaderCanvas;
    private Paint mShaderPaint;
    private int mTabHeight;
    private int mTabOverlap;
    private int mTabSliceWidth;
    private int mTabPadding;
    private boolean mUseQuickControls;

    public TabBar(Activity activity, UiController controller, XLargeUi ui) {
        super(activity);
        mActivity = activity;
        mUiController = controller;
        mTabControl = mUiController.getTabControl();
        mUi = ui;
        Resources res = activity.getResources();
        mTabWidthSelected = (int) res.getDimension(R.dimen.tab_width_selected);
        mTabWidthUnselected = (int) res.getDimension(R.dimen.tab_width_unselected);
        mActiveDrawable = res.getDrawable(R.drawable.bg_urlbar);
        mInactiveDrawable = res.getDrawable(R.drawable.browsertab_inactive);

        mTabMap = new HashMap<Tab, TabViewData>();
        Resources resources = activity.getResources();
        LayoutInflater factory = LayoutInflater.from(activity);
        factory.inflate(R.layout.tab_bar, this);
        setPadding(12, 12, 0, 0);
        mTabs = (TabScrollView) findViewById(R.id.tabs);
        mNewTab = (ImageButton) findViewById(R.id.newtab);
        mNewTab.setOnClickListener(this);
        mGenericFavicon = res.getDrawable(R.drawable.app_web_browser_sm);
        setChildrenDrawingOrderEnabled(true);

        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.

        updateTabs(mUiController.getTabs());

        mUserRequestedUrlbar = false;
        mVisibleTitleHeight = 1;
        mButtonWidth = -1;
        // tab dimensions
        mTabHeight = (int) res.getDimension(R.dimen.tab_height);
        mTabOverlap = (int) res.getDimension(R.dimen.tab_overlap);
        mTabSliceWidth = (int) res.getDimension(R.dimen.tab_slice);
        mTabPadding = (int) res.getDimension(R.dimen.tab_padding);
        int maxTabWidth = (int) res.getDimension(R.dimen.max_tab_width);
        // shader initialization
        mShaderBuffer = Bitmap.createBitmap(maxTabWidth, mTabHeight,
                Bitmap.Config.ARGB_8888);
        mShaderCanvas = new Canvas(mShaderBuffer);
        mShaderPaint = new Paint();
        BitmapShader shader = new BitmapShader(mShaderBuffer,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mShaderPaint.setShader(shader);
        mShaderPaint.setStyle(Paint.Style.FILL);
        mShaderPaint.setAntiAlias(true);
    }

    void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
    }

    int getTabCount() {
        return mTabMap.size();
    }

    void updateTabs(List<Tab> tabs) {
        mTabs.clearTabs();
        mTabMap.clear();
        for (Tab tab : tabs) {
            TabViewData data = buildTab(tab);
            TabView tv = buildView(data);
        }
        mTabs.setSelectedTab(mTabControl.getCurrentIndex());
    }

    @Override
    protected void onMeasure(int hspec, int vspec) {
        super.onMeasure(hspec, vspec);
        int w = getMeasuredWidth();
        // adjust for new tab overlap
        w -= mTabOverlap;
        setMeasuredDimension(w, getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // use paddingLeft and paddingTop
        int pl = getPaddingLeft();
        int pt = getPaddingTop();
        if (mButtonWidth == -1) {
            mButtonWidth = mNewTab.getMeasuredWidth() - mTabOverlap;
        }
        int sw = mTabs.getMeasuredWidth();
        int w = right - left - pl;
        if (w-sw < mButtonWidth) {
            sw = w - mButtonWidth;
        }
        mTabs.layout(pl, pt, pl + sw, bottom - top);
        // adjust for overlap
        mNewTab.layout(pl + sw - mTabOverlap, pt,
                pl + sw + mButtonWidth - mTabOverlap, bottom - top);
    }

    public void onClick(View view) {
        mUi.hideComboView();
        if (mNewTab == view) {
            mUiController.openTabToHomePage();
        } else if (mTabs.getSelectedTab() == view) {
            if (mUseQuickControls) return;
            if (mUi.isFakeTitleBarShowing() && !isLoading()) {
                mUi.hideFakeTitleBar();
            } else {
                showUrlBar();
            }
        } else {
            int ix = mTabs.getChildIndex(view);
            if (ix >= 0) {
                mTabs.setSelectedTab(ix);
                mUiController.switchToTab(ix);
            }
        }
    }

    private void showUrlBar() {
        mUi.stopWebViewScrolling();
        mUi.showFakeTitleBar();
        mUserRequestedUrlbar = true;
    }

    void showTitleBarIndicator(boolean show) {
        Tab tab = mTabControl.getCurrentTab();
        if (tab != null) {
            TabViewData tvd = mTabMap.get(tab);
            if (tvd != null) {
                tvd.mTabView.showIndicator(show);
            }
        }
    }

    // callback after fake titlebar is shown
    void onShowTitleBar() {
        showTitleBarIndicator(false);
    }

    // callback after fake titlebar is hidden
    void onHideTitleBar() {
        showTitleBarIndicator(mVisibleTitleHeight == 0);
        Tab tab = mTabControl.getCurrentTab();
        tab.getWebView().requestFocus();
        mUserRequestedUrlbar = false;
    }

    // webview scroll listener

    @Override
    public void onScroll(int visibleTitleHeight) {
        if (mUseQuickControls) return;
        // isLoading is using the current tab, which initially might not be set yet
        if (mTabControl.getCurrentTab() != null
                && !isLoading()) {
            if (visibleTitleHeight == 0) {
                mUi.hideFakeTitleBar();
                showTitleBarIndicator(true);
            } else {
                showTitleBarIndicator(false);
            }
        }
        mVisibleTitleHeight = visibleTitleHeight;
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mActivity.onCreateContextMenu(menu, this, null);
    }

    private TabViewData buildTab(Tab tab) {
        TabViewData data = new TabViewData(tab);
        mTabMap.put(tab, data);
        return data;
    }

    private TabView buildView(final TabViewData data) {
        TabView tv = new TabView(mActivity, data);
        tv.setTag(data);
        tv.setOnClickListener(this);
        mTabs.addTab(tv);
        return tv;
    }

    @Override
    protected int getChildDrawingOrder(int count, int i) {
        // reverse
        return count - 1 - i;
    }

    /**
     * View used in the tab bar
     */
    class TabView extends LinearLayout implements OnClickListener {

        TabViewData mTabData;
        View mTabContent;
        TextView mTitle;
        View mIndicator;
        View mIncognito;
        ImageView mIconView;
        ImageView mLock;
        ImageView mClose;
        boolean mSelected;
        boolean mInLoad;
        Path mPath;
        int[] mWindowPos;

        /**
         * @param context
         */
        public TabView(Context context, TabViewData tab) {
            super(context);
            setWillNotDraw(false);
            mPath = new Path();
            mWindowPos = new int[2];
            mTabData = tab;
            setGravity(Gravity.CENTER_VERTICAL);
            setOrientation(LinearLayout.HORIZONTAL);
            setPadding(mTabPadding, 0, 0, 0);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            mTabContent = inflater.inflate(R.layout.tab_title, this, true);
            mTitle = (TextView) mTabContent.findViewById(R.id.title);
            mIconView = (ImageView) mTabContent.findViewById(R.id.favicon);
            mLock = (ImageView) mTabContent.findViewById(R.id.lock);
            mClose = (ImageView) mTabContent.findViewById(R.id.close);
            mClose.setOnClickListener(this);
            mIncognito = mTabContent.findViewById(R.id.incognito);
            mIndicator = mTabContent.findViewById(R.id.chevron);
            mSelected = false;
            mInLoad = false;
            // update the status
            updateFromData();
        }

        void showIndicator(boolean show) {
            if (mSelected) {
                mIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
            } else {
                mIndicator.setVisibility(View.GONE);
            }
        }

        @Override
        public void onClick(View v) {
            if (v == mClose) {
                closeTab();
            }
        }

        private void updateFromData() {
            mTabData.mTabView = this;
            Tab tab = mTabData.mTab;
            String displayTitle = tab.getTitle();
            if (displayTitle == null) {
                displayTitle = tab.getUrl();
            }
            setDisplayTitle(displayTitle);
            setProgress(mTabData.mProgress);
            if (mTabData.mIcon != null) {
                setFavicon(mTabData.mIcon);
            }
            if (mTabData.mTab != null) {
                mIncognito.setVisibility(
                        mTabData.mTab.isPrivateBrowsingEnabled() ?
                        View.VISIBLE : View.GONE);
            }
        }

        @Override
        public void setActivated(boolean selected) {
            mSelected = selected;
            mClose.setVisibility(mSelected ? View.VISIBLE : View.GONE);
            mIndicator.setVisibility(View.GONE);
            mTitle.setTextAppearance(mActivity, mSelected ?
                    R.style.TabTitleSelected : R.style.TabTitleUnselected);
            setHorizontalFadingEdgeEnabled(!mSelected);
            super.setActivated(selected);
            LayoutParams lp = (LinearLayout.LayoutParams) getLayoutParams();
            lp.width = selected ? mTabWidthSelected : mTabWidthUnselected;
            lp.height =  LayoutParams.MATCH_PARENT;
            setLayoutParams(lp);
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

        void setProgress(int newProgress) {
            if (newProgress >= PROGRESS_MAX) {
                mInLoad = false;
            } else {
                if (!mInLoad && getWindowToken() != null) {
                    mInLoad = true;
                }
            }
        }

        private void closeTab() {
            if (mTabData.mTab == mTabControl.getCurrentTab()) {
                mUiController.closeCurrentTab();
            } else {
                mUiController.closeTab(mTabData.mTab);
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            setTabPath(mPath, 0, 0, r - l, b - t);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int state = canvas.save();
            int[] pos = new int[2];
            getLocationInWindow(mWindowPos);
            Drawable drawable = mSelected ? mActiveDrawable : mInactiveDrawable;
            drawable.setBounds(0, 0, mUi.getContentWidth(), getHeight());
            drawClipped(canvas, drawable, mPath, mWindowPos[0]);
            canvas.restoreToCount(state);
            super.dispatchDraw(canvas);
        }

        private void drawClipped(Canvas canvas, Drawable drawable,
                Path clipPath, int left) {
            mShaderCanvas.drawColor(Color.TRANSPARENT);
            mShaderCanvas.translate(-left, 0);
            drawable.draw(mShaderCanvas);
            canvas.drawPath(clipPath, mShaderPaint);
            mShaderCanvas.translate(left, 0);
        }

        private void setTabPath(Path path, int l, int t, int r, int b) {
            path.reset();
            path.moveTo(l, b);
            path.lineTo(l, t);
            path.lineTo(r - mTabSliceWidth, t);
            path.lineTo(r, b);
            path.close();
        }

    }

    /**
     * Store tab state within the title bar
     */
    class TabViewData {

        Tab mTab;
        TabView mTabView;
        int mProgress;
        Drawable mIcon;

        TabViewData(Tab tab) {
            mTab = tab;
            setUrlAndTitle(mTab.getUrl(), mTab.getTitle());
        }

        void setUrlAndTitle(String url, String title) {
            if (mTabView != null) {
                if (title != null) {
                    mTabView.setDisplayTitle(title);
                } else if (url != null) {
                    mTabView.setDisplayTitle(UrlUtils.stripUrl(url));
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

    public void onSetActiveTab(Tab tab) {
        mTabs.setSelectedTab(mTabControl.getTabIndex(tab));
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setProgress(tvd.mProgress);
            // update the scroll state
            WebView webview = tab.getWebView();
            if (webview != null) {
                int h = webview.getVisibleTitleHeight();
                mVisibleTitleHeight = h -1;
                onScroll(h);
            }
        }
    }

    public void onFavicon(Tab tab, Bitmap favicon) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setFavicon(favicon);
        }
    }

    public void onNewTab(Tab tab) {
        TabViewData tvd = buildTab(tab);
        buildView(tvd);
    }

    public void onProgress(Tab tab, int progress) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setProgress(progress);
        }
    }

    public void onRemoveTab(Tab tab) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            TabView tv = tvd.mTabView;
            if (tv != null) {
                mTabs.removeTab(tv);
            }
        }
        mTabMap.remove(tab);
    }

    public void onUrlAndTitle(Tab tab, String url, String title) {
        TabViewData tvd = mTabMap.get(tab);
        if (tvd != null) {
            tvd.setUrlAndTitle(url, title);
        }
    }

    private boolean isLoading() {
        TabViewData tvd = mTabMap.get(mTabControl.getCurrentTab());
        if ((tvd != null) && (tvd.mTabView != null)) {
            return tvd.mTabView.mInLoad;
        } else {
            return false;
        }
    }

}
