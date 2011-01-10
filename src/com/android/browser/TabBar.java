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

import android.graphics.Matrix;
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

    private Map<Tab, TabView> mTabMap;

    private int mVisibleTitleHeight;

    private Drawable mGenericFavicon;

    private int mCurrentTextureWidth = 0;
    private int mCurrentTextureHeight = 0;

    private Drawable mActiveDrawable;
    private Drawable mInactiveDrawable;

    private final Paint mActiveShaderPaint = new Paint();
    private final Paint mInactiveShaderPaint = new Paint();
    private final Matrix mActiveMatrix = new Matrix();
    private final Matrix mInactiveMatrix = new Matrix();

    private BitmapShader mActiveShader;
    private BitmapShader mInactiveShader;

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

        mTabMap = new HashMap<Tab, TabView>();
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

        mVisibleTitleHeight = 1;
        mButtonWidth = -1;
        // tab dimensions
        mTabOverlap = (int) res.getDimension(R.dimen.tab_overlap);
        mTabSliceWidth = (int) res.getDimension(R.dimen.tab_slice);
        mTabPadding = (int) res.getDimension(R.dimen.tab_padding);

        mActiveShaderPaint.setStyle(Paint.Style.FILL);
        mActiveShaderPaint.setAntiAlias(true);

        mInactiveShaderPaint.setStyle(Paint.Style.FILL);
        mInactiveShaderPaint.setAntiAlias(true);
        
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
            TabView tv = buildTabView(tab);
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
    }

    void showTitleBarIndicator(boolean show) {
        Tab tab = mTabControl.getCurrentTab();
        if (tab != null) {
            TabView tv = mTabMap.get(tab);
            if (tv != null) {
                tv.showIndicator(show);
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

    private TabView buildTabView(Tab tab) {
        TabView tabview = new TabView(mActivity, tab);
        mTabMap.put(tab, tabview);
        tabview.setOnClickListener(this);
        mTabs.addTab(tabview);
        return tabview;
    }

    @Override
    protected int getChildDrawingOrder(int count, int i) {
        // reverse
        return count - 1 - i;
    }

    private static Bitmap getDrawableAsBitmap(Drawable drawable, int width, int height) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(c);
        return b;
    }    
    
    /**
     * View used in the tab bar
     */
    class TabView extends LinearLayout implements OnClickListener {

        Tab mTab;
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
        public TabView(Context context, Tab tab) {
            super(context);
            setWillNotDraw(false);
            mPath = new Path();
            mWindowPos = new int[2];
            mTab = tab;
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
            updateFromTab();
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

        private void updateFromTab() {
            String displayTitle = mTab.getTitle();
            if (displayTitle == null) {
                displayTitle = mTab.getUrl();
            }
            setDisplayTitle(displayTitle);
            setProgress(mTab.getLoadProgress());
            if (mTab.getFavicon() != null) {
                setFavicon(renderFavicon(mTab.getFavicon()));
            }
            if (mTab != null) {
                mIncognito.setVisibility(
                        mTab.isPrivateBrowsingEnabled() ?
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
            if (mTab == mTabControl.getCurrentTab()) {
                mUiController.closeCurrentTab();
            } else {
                mUiController.closeTab(mTab);
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            setTabPath(mPath, 0, 0, r - l, b - t);
        }
        
        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (mCurrentTextureWidth != mUi.getContentWidth() ||
                    mCurrentTextureHeight != getHeight()) {
                mCurrentTextureWidth = mUi.getContentWidth();
                mCurrentTextureHeight = getHeight();

                if (mCurrentTextureWidth > 0 && mCurrentTextureHeight > 0) {
                    Bitmap activeTexture = getDrawableAsBitmap(mActiveDrawable,
                            mCurrentTextureWidth, mCurrentTextureHeight);
                    Bitmap inactiveTexture = getDrawableAsBitmap(mInactiveDrawable,
                            mCurrentTextureWidth, mCurrentTextureHeight);

                    mActiveShader = new BitmapShader(activeTexture,
                            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    mActiveShaderPaint.setShader(mActiveShader);
    
                    mInactiveShader = new BitmapShader(inactiveTexture,
                            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    mInactiveShaderPaint.setShader(mInactiveShader);
                }
            }
            
            int state = canvas.save();
            getLocationInWindow(mWindowPos);
            Paint paint = mSelected ? mActiveShaderPaint : mInactiveShaderPaint;
            drawClipped(canvas, paint, mPath, mWindowPos[0]);
            canvas.restoreToCount(state);
            super.dispatchDraw(canvas);
        }

        private void drawClipped(Canvas canvas, Paint paint, Path clipPath, int left) {
            // TODO: We should change the matrix/shader only when needed
            final Matrix matrix = mSelected ? mActiveMatrix : mInactiveMatrix;
            matrix.setTranslate(-left, 0.0f);
            (mSelected ? mActiveShader : mInactiveShader).setLocalMatrix(matrix);
            canvas.drawPath(clipPath, paint);
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

    private Drawable renderFavicon(Bitmap icon) {
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
        return d;
    }

    // TabChangeListener implementation

    public void onSetActiveTab(Tab tab) {
        mTabs.setSelectedTab(mTabControl.getTabIndex(tab));
        TabView tv = mTabMap.get(tab);
        if (tv != null) {
            tv.setProgress(tv.mTab.getLoadProgress());
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
        TabView tv = mTabMap.get(tab);
        if (tv != null) {
            tv.setFavicon(renderFavicon(favicon));
        }
    }

    public void onNewTab(Tab tab) {
        TabView tv = buildTabView(tab);
    }

    public void onProgress(Tab tab, int progress) {
        TabView tv = mTabMap.get(tab);
        if (tv != null) {
            tv.setProgress(progress);
        }
    }

    public void onRemoveTab(Tab tab) {
        TabView tv = mTabMap.get(tab);
        if (tv != null) {
            mTabs.removeTab(tv);
        }
        mTabMap.remove(tab);
    }

    public void onUrlAndTitle(Tab tab, String url, String title) {
        TabView tv = mTabMap.get(tab);
        if (tv != null) {
            if (title != null) {
                tv.setDisplayTitle(title);
            } else if (url != null) {
                tv.setDisplayTitle(UrlUtils.stripUrl(url));
            }
        }
    }

    private boolean isLoading() {
        TabView tv = mTabMap.get(mTabControl.getCurrentTab());
        if (tv != null) {
            return tv.mInLoad;
        } else {
            return false;
        }
    }

}
