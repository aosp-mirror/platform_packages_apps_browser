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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.browser.UrlInputView.StateListener;

/**
 * Ui for regular phone screen sizes
 */
public class PhoneUi extends BaseUi {

    private static final String LOGTAG = "PhoneUi";

    private PieControlPhone mPieControl;
    private NavScreen mNavScreen;
    private NavigationBarPhone mNavigationBar;

    boolean mExtendedMenuOpen;
    boolean mOptionsMenuOpen;
    boolean mAnimating;

    /**
     * @param browser
     * @param controller
     */
    public PhoneUi(Activity browser, UiController controller) {
        super(browser, controller);
        mActivity.getActionBar().hide();
        setUseQuickControls(BrowserSettings.getInstance().useQuickControls());
        mNavigationBar = (NavigationBarPhone) mTitleBar.getNavigationBar();
    }

    @Override
    public void onDestroy() {
        hideTitleBar();
    }

    @Override
    public void editUrl(boolean clearInput) {
        if (mUseQuickControls) {
            mTitleBar.setShowProgressOnly(false);
        }
        super.editUrl(clearInput);
    }

    @Override
    public boolean onBackKey() {
        if (mNavScreen != null) {
            mNavScreen.close();
            return true;
        }
        return super.onBackKey();
    }

    @Override
    public boolean dispatchKey(int code, KeyEvent event) {
        return false;
    }

    @Override
    public void onProgressChanged(Tab tab) {
        if (tab.inForeground()) {
            int progress = tab.getLoadProgress();
            mTitleBar.setProgress(progress);
            if (progress == 100) {
                if (!mOptionsMenuOpen || !mExtendedMenuOpen) {
                    suggestHideTitleBar();
                    if (mUseQuickControls) {
                        mTitleBar.setShowProgressOnly(false);
                    }
                }
            } else {
                if (!mOptionsMenuOpen || mExtendedMenuOpen) {
                    if (mUseQuickControls && !mTitleBar.isEditingUrl()) {
                        mTitleBar.setShowProgressOnly(true);
                        setTitleGravity(Gravity.TOP);
                    }
                    showTitleBar();
                }
            }
        }
    }

    @Override
    public void setActiveTab(final Tab tab) {
        mTitleBar.cancelTitleBarAnimation(true);
        mTitleBar.setSkipTitleBarAnimations(true);
        super.setActiveTab(tab);
        BrowserWebView view = (BrowserWebView) tab.getWebView();
        // TabControl.setCurrentTab has been called before this,
        // so the tab is guaranteed to have a webview
        if (view == null) {
            Log.e(LOGTAG, "active tab with no webview detected");
            return;
        }
        // Request focus on the top window.
        if (mUseQuickControls) {
            mPieControl.forceToTop(mContentView);
        } else {
            // check if title bar is already attached by animation
            if (mTitleBar.getParent() == null) {
                view.setEmbeddedTitleBar(mTitleBar);
            }
        }
        if (tab.isInVoiceSearchMode()) {
            showVoiceTitleBar(tab.getVoiceDisplayTitle(), tab.getVoiceSearchResults());
        } else {
            revertVoiceTitleBar(tab);
        }
        // update nav bar state
        mNavigationBar.onStateChanged(StateListener.STATE_NORMAL);
        updateLockIconToLatest(tab);
        tab.getTopWindow().requestFocus();
        mTitleBar.setSkipTitleBarAnimations(false);
    }

    // menu handling callbacks

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuState(mActiveTab, menu);
        return true;
    }

    @Override
    public void updateMenuState(Tab tab, Menu menu) {
        menu.setGroupVisible(R.id.NAV_MENU, (mNavScreen == null));
        MenuItem bm = menu.findItem(R.id.bookmarks_menu_id);
        if (bm != null) {
            bm.setVisible(mNavScreen == null);
        }
        MenuItem nt = menu.findItem(R.id.new_tab_menu_id);
        if (nt != null) {
            nt.setVisible(mNavScreen == null);
        }
        MenuItem abm = menu.findItem(R.id.add_bookmark_menu_id);
        if (abm != null) {
            abm.setVisible((tab != null) && !tab.isSnapshot());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mNavScreen != null) {
            hideNavScreen(false);
        }
        return false;
    }

    @Override
    public void onContextMenuCreated(Menu menu) {
        hideTitleBar();
    }

    @Override
    public void onContextMenuClosed(Menu menu, boolean inLoad) {
        if (inLoad) {
            showTitleBar();
        }
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (!isEditingUrl()) {
            hideTitleBar();
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        if (inLoad) {
            if (mUseQuickControls) {
                mTitleBar.setShowProgressOnly(true);
            }
            showTitleBar();
        }
        mActivity.getActionBar().hide();
    }

    @Override
    protected void setTitleGravity(int gravity) {
        if (mUseQuickControls) {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) mTitleBar.getLayoutParams();
            lp.gravity = gravity;
            mTitleBar.setLayoutParams(lp);
        } else {
            super.setTitleGravity(gravity);
        }
    }

    @Override
    public void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        mTitleBar.setUseQuickControls(mUseQuickControls);
        if (useQuickControls) {
            mPieControl = new PieControlPhone(mActivity, mUiController, this);
            mPieControl.attachToContainer(mContentView);
            WebView web = getWebView();
            if (web != null) {
                web.setEmbeddedTitleBar(null);
            }
        } else {
            if (mPieControl != null) {
                mPieControl.removeFromContainer(mContentView);
            }
            WebView web = getWebView();
            if (web != null) {
                // make sure we can re-parent titlebar
                if ((mTitleBar != null) && (mTitleBar.getParent() != null)) {
                    ((ViewGroup) mTitleBar.getParent()).removeView(mTitleBar);
                }
                web.setEmbeddedTitleBar(mTitleBar);
            }
            setTitleGravity(Gravity.NO_GRAVITY);
        }
        updateUrlBarAutoShowManagerTarget();
    }

    @Override
    public boolean isWebShowing() {
        return super.isWebShowing() && mNavScreen == null;
    }

    @Override
    public void showWeb(boolean animate) {
        super.showWeb(animate);
        hideNavScreen(animate);
    }

    void showNavScreen() {
        mUiController.setBlockEvents(true);
        mNavScreen = new NavScreen(mActivity, mUiController, this);
        mActiveTab.capture();
        // Add the custom view to its container
        mCustomViewContainer.addView(mNavScreen, COVER_SCREEN_PARAMS);
        AnimScreen ascreen = new AnimScreen(mActivity, getTitleBar(), getWebView());
        final View animView = ascreen.mMain;
        mCustomViewContainer.addView(animView, COVER_SCREEN_PARAMS);
        mCustomViewContainer.setVisibility(View.VISIBLE);
        mCustomViewContainer.bringToFront();
        View target = ((NavTabView) mNavScreen.mScroller.getSelectedView()).mImage;
        int fromLeft = 0;
        int fromTop = getTitleBar().getHeight();
        int fromRight = mContentView.getWidth();
        int fromBottom = mContentView.getHeight();
        int width = target.getWidth();
        int height = target.getHeight();
        int toLeft = (mContentView.getWidth() - width) / 2;
        int toTop = fromTop + (mContentView.getHeight() - fromTop - height) / 2;
        int toRight = toLeft + width;
        int toBottom = toTop + height;
        float scaleFactor = width / (float) mContentView.getWidth();
        detachTab(mActiveTab);
        mContentView.setVisibility(View.GONE);
        AnimatorSet inanim = new AnimatorSet();
        ObjectAnimator tx = ObjectAnimator.ofInt(ascreen.mContent, "left",
                fromLeft, toLeft);
        ObjectAnimator ty = ObjectAnimator.ofInt(ascreen.mContent, "top",
                fromTop, toTop);
        ObjectAnimator tr = ObjectAnimator.ofInt(ascreen.mContent, "right",
                fromRight, toRight);
        ObjectAnimator tb = ObjectAnimator.ofInt(ascreen.mContent, "bottom",
                fromBottom, toBottom);
        ObjectAnimator title = ObjectAnimator.ofFloat(ascreen.mTitle, "alpha",
                1f, 0f);
        ObjectAnimator content = ObjectAnimator.ofFloat(ascreen.mContent, "alpha",
                1f, 0f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(ascreen, "scaleFactor",
                1f, scaleFactor);
        inanim.playTogether(tx, ty, tr, tb, title, content, sx);
        inanim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator anim) {
                mCustomViewContainer.removeView(animView);
                finishAnimationIn();
                mUiController.setBlockEvents(false);
            }
        });
        inanim.setInterpolator(new DecelerateInterpolator(2f));
        inanim.setDuration(300);
        inanim.start();
    }

    private void finishAnimationIn() {
        if (mNavScreen != null) {
            // notify accessibility manager about the screen change
            mNavScreen.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            mTabControl.setOnThumbnailUpdatedListener(mNavScreen);
        }
    }

    void hideNavScreen(boolean animate) {
        if (mNavScreen == null) return;
        final Tab tab = mNavScreen.getSelectedTab();
        if ((tab == null) || !animate) {
            if (tab != null) {
                setActiveTab(tab);
            } else if (mTabControl.getTabCount() > 0) {
                // use a fallback tab
                setActiveTab(mTabControl.getCurrentTab());
            }
            mContentView.setVisibility(View.VISIBLE);
            finishAnimateOut();
            return;
        }
        NavTabView tabview = (NavTabView) mNavScreen.getSelectedTabView();
        if (tabview == null) {
            if (mTabControl.getTabCount() > 0) {
                // use a fallback tab
                setActiveTab(mTabControl.getCurrentTab());
            }
            mContentView.setVisibility(View.VISIBLE);
            finishAnimateOut();
            return;
        }
        mUiController.setBlockEvents(true);
        mUiController.setActiveTab(tab);
        mContentView.setVisibility(View.VISIBLE);
        final AnimScreen screen = new AnimScreen(mActivity, tab.getScreenshot());
        View target = ((NavTabView) mNavScreen.mScroller.getSelectedView()).mImage;
        int toLeft = 0;
        int toTop = getTitleBar().getHeight();
        int toRight = mContentView.getWidth();
        int width = target.getWidth();
        int height = target.getHeight();
        int[] pos = new int[2];
        tabview.mImage.getLocationInWindow(pos);
        int fromLeft = pos[0];
        int fromTop = pos[1];
        int fromRight = fromLeft + width;
        int fromBottom = fromTop + height;
        float scaleFactor = mContentView.getWidth() / (float) width;
        int toBottom = (int) (height * scaleFactor);
        screen.mMain.setAlpha(0f);
        mCustomViewContainer.addView(screen.mMain, COVER_SCREEN_PARAMS);
        AnimatorSet animSet = new AnimatorSet();
        ObjectAnimator l = ObjectAnimator.ofInt(screen.mContent, "left",
                fromLeft, toLeft);
        ObjectAnimator t = ObjectAnimator.ofInt(screen.mContent, "top",
                fromTop, toTop);
        ObjectAnimator r = ObjectAnimator.ofInt(screen.mContent, "right",
                fromRight, toRight);
        ObjectAnimator b = ObjectAnimator.ofInt(screen.mContent, "bottom",
                fromBottom, toBottom);
        ObjectAnimator scale = ObjectAnimator.ofFloat(screen, "scaleFactor",
                1f, scaleFactor);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(screen.mMain, "alpha", 1f, 1f);
        ObjectAnimator otheralpha = ObjectAnimator.ofFloat(mCustomViewContainer, "alpha", 1f, 0f);
        alpha.setStartDelay(100);
        animSet.playTogether(l, t, r, b, scale, alpha, otheralpha);
        animSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator anim) {
                mCustomViewContainer.removeView(screen.mMain);
                finishAnimateOut();
                mUiController.setBlockEvents(false);
            }
        });
        animSet.setDuration(250);
        animSet.start();
    }

    private void finishAnimateOut() {
        mTabControl.setOnThumbnailUpdatedListener(null);
        mCustomViewContainer.removeView(mNavScreen);
        mCustomViewContainer.setAlpha(1f);
        mNavScreen = null;
        mCustomViewContainer.setVisibility(View.GONE);
    }

    @Override
    public boolean needsRestoreAllTabs() {
        return false;
    }

    public void toggleNavScreen() {
        if (mNavScreen == null) {
            showNavScreen();
        } else {
            hideNavScreen(false);
        }
    }

    @Override
    public boolean shouldCaptureThumbnails() {
        return true;
    }

    static class AnimScreen {

        private View mMain;
        private ImageView mTitle;
        private ImageView mContent;
        private float mScale;

        public AnimScreen(Context ctx, TitleBar tbar, WebView web) {
            mMain = LayoutInflater.from(ctx).inflate(R.layout.anim_screen,
                    null);
            mContent = (ImageView) mMain.findViewById(R.id.content);
            mContent.setTop(tbar.getHeight());

            mTitle = (ImageView) mMain.findViewById(R.id.title);
            Bitmap bm1 = Bitmap.createBitmap(tbar.getWidth(), tbar.getHeight(),
                    Bitmap.Config.RGB_565);
            Canvas c1 = new Canvas(bm1);
            tbar.draw(c1);
            mTitle.setImageBitmap(bm1);
            int h = web.getHeight() - tbar.getHeight();
            Bitmap bm2 = Bitmap.createBitmap(web.getWidth(), h,
                    Bitmap.Config.RGB_565);
            Canvas c2 = new Canvas(bm2);
            int tx = web.getScrollX();
            int ty = web.getScrollY();
            c2.translate(-tx, -ty - tbar.getHeight());
            web.draw(c2);
            mContent.setImageBitmap(bm2);
            mContent.setScaleType(ImageView.ScaleType.MATRIX);
            mContent.setImageMatrix(new Matrix());
            mScale = 1.0f;
            setScaleFactor(getScaleFactor());
        }

        public AnimScreen(Context ctx, Bitmap image) {
            mMain = LayoutInflater.from(ctx).inflate(R.layout.anim_screen,
                    null);
            mContent = (ImageView) mMain.findViewById(R.id.content);
            mContent.setImageBitmap(image);
            mContent.setScaleType(ImageView.ScaleType.MATRIX);
            mContent.setImageMatrix(new Matrix());
            mScale = 1.0f;
            setScaleFactor(getScaleFactor());
        }

        public void setScaleFactor(float sf) {
            mScale = sf;
            Matrix m = new Matrix();
            m.postScale(sf,sf);
            mContent.setImageMatrix(m);
        }

        public float getScaleFactor() {
            return mScale;
        }

    }

}
