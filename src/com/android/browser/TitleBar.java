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
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;


/**
 * Base class for a title bar used by the browser.
 */
public class TitleBar extends RelativeLayout {

    private static final int PROGRESS_MAX = 100;
    private static final float ANIM_TITLEBAR_DECELERATE = 2.5f;

    private UiController mUiController;
    private BaseUi mBaseUi;
    private FrameLayout mParent;
    private PageProgressView mProgress;

    private AutologinBar mAutoLogin;
    private NavigationBarBase mNavBar;
    private boolean mUseQuickControls;
    private SnapshotBar mSnapshotBar;

    //state
    private boolean mShowing;
    private boolean mInLoad;
    private boolean mSkipTitleBarAnimations;
    private Animator mTitleBarAnimator;

    public TitleBar(Context context, UiController controller, BaseUi ui,
            FrameLayout parent) {
        super(context, null);
        mUiController = controller;
        mBaseUi = ui;
        mParent = parent;
        initLayout(context);
    }

    private void initLayout(Context context) {
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar, this);
        mProgress = (PageProgressView) findViewById(R.id.progress);
        mNavBar = (NavigationBarBase) findViewById(R.id.taburlbar);
        mNavBar.setTitleBar(this);
    }

    private void inflateAutoLoginBar() {
        if (mAutoLogin != null) {
            return;
        }

        ViewStub stub = (ViewStub) findViewById(R.id.autologin_stub);
        mAutoLogin = (AutologinBar) stub.inflate();
        mAutoLogin.setTitleBar(this);
    }

    private void inflateSnapshotBar() {
        if (mSnapshotBar != null) {
            return;
        }

        ViewStub stub = (ViewStub) findViewById(R.id.snapshotbar_stub);
        mSnapshotBar = (SnapshotBar) stub.inflate();
        mSnapshotBar.setTitleBar(this);
    }

    public BaseUi getUi() {
        return mBaseUi;
    }

    public UiController getUiController() {
        return mUiController;
    }

    public void setUseQuickControls(boolean use) {
        mUseQuickControls = use;
        setLayoutParams(makeLayoutParams());
    }

    void setShowProgressOnly(boolean progress) {
        if (progress && !wantsToBeVisible()) {
            mNavBar.setVisibility(View.GONE);
        } else {
            mNavBar.setVisibility(View.VISIBLE);
        }
    }

    void setSkipTitleBarAnimations(boolean skip) {
        mSkipTitleBarAnimations = skip;
    }

    void setupTitleBarAnimator(Animator animator) {
        Resources res = mContext.getResources();
        int duration = res.getInteger(R.integer.titlebar_animation_duration);
        animator.setInterpolator(new DecelerateInterpolator(
                ANIM_TITLEBAR_DECELERATE));
        animator.setDuration(duration);
    }

    void show() {
        if (mUseQuickControls) {
            mParent.addView(this);
        } else {
            if (!mSkipTitleBarAnimations) {
                cancelTitleBarAnimation(false);
                int visibleHeight = getVisibleTitleHeight();
                float startPos = (-getEmbeddedHeight() + visibleHeight);
                if (getTranslationY() != 0) {
                    startPos = Math.max(startPos, getTranslationY());
                }
                mTitleBarAnimator = ObjectAnimator.ofFloat(this,
                        "translationY",
                        startPos, 0);
                setupTitleBarAnimator(mTitleBarAnimator);
                mTitleBarAnimator.start();
            }
            mBaseUi.setTitleGravity(Gravity.TOP);
        }
        mShowing = true;
    }

    void hide() {
        if (mUseQuickControls) {
            mParent.removeView(this);
        } else {
            if (!mSkipTitleBarAnimations) {
                cancelTitleBarAnimation(false);
                int visibleHeight = getVisibleTitleHeight();
                mTitleBarAnimator = ObjectAnimator.ofFloat(this,
                        "translationY", getTranslationY(),
                        (-getEmbeddedHeight() + visibleHeight));
                mTitleBarAnimator.addListener(mHideTileBarAnimatorListener);
                setupTitleBarAnimator(mTitleBarAnimator);
                mTitleBarAnimator.start();
            } else {
                mBaseUi.setTitleGravity(Gravity.NO_GRAVITY);
            }
        }
        mShowing = false;
    }

    boolean isShowing() {
        return mShowing;
    }

    void cancelTitleBarAnimation(boolean reset) {
        if (mTitleBarAnimator != null) {
            mTitleBarAnimator.cancel();
            mTitleBarAnimator = null;
        }
        if (reset) {
            setTranslationY(0);
        }
    }

    private AnimatorListener mHideTileBarAnimatorListener = new AnimatorListener() {

        boolean mWasCanceled;
        @Override
        public void onAnimationStart(Animator animation) {
            mWasCanceled = false;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mWasCanceled) {
                setTranslationY(0);
            }
            mBaseUi.setTitleGravity(Gravity.NO_GRAVITY);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mWasCanceled = true;
        }
    };

    private int getVisibleTitleHeight() {
        Tab tab = mBaseUi.getActiveTab();
        WebView webview = tab != null ? tab.getWebView() : null;
        return webview != null ? webview.getVisibleTitleHeight() : 0;
    }

    /**
     * Update the progress, from 0 to 100.
     */
    public void setProgress(int newProgress) {
        if (newProgress >= PROGRESS_MAX) {
            mProgress.setProgress(PageProgressView.MAX_PROGRESS);
            mProgress.setVisibility(View.GONE);
            mInLoad = false;
            mNavBar.onProgressStopped();
            // check if needs to be hidden
            if (!isEditingUrl() && !wantsToBeVisible()) {
                hide();
                if (mUseQuickControls) {
                    setShowProgressOnly(false);
                }
            }
        } else {
            if (!mInLoad) {
                mProgress.setVisibility(View.VISIBLE);
                mInLoad = true;
                mNavBar.onProgressStarted();
            }
            mProgress.setProgress(newProgress * PageProgressView.MAX_PROGRESS
                    / PROGRESS_MAX);
            if (!mShowing) {
                if (mUseQuickControls && !isEditingUrl()) {
                    setShowProgressOnly(true);
                }
                show();
            }
        }
    }

    public int getEmbeddedHeight() {
        int height = mNavBar.getHeight();
        if (mAutoLogin != null && mAutoLogin.getVisibility() == View.VISIBLE) {
            height += mAutoLogin.getHeight();
        }
        return height;
    }

    public void updateAutoLogin(Tab tab, boolean animate) {
        if (mAutoLogin == null) {
            if  (tab.getDeviceAccountLogin() == null) {
                return;
            }
            inflateAutoLoginBar();
        }
        mAutoLogin.updateAutoLogin(tab, animate);
    }

    public void showAutoLogin(boolean animate) {
        if (mUseQuickControls) {
            mBaseUi.showTitleBar();
        }
        if (mAutoLogin == null) {
            inflateAutoLoginBar();
        }
        mAutoLogin.setVisibility(View.VISIBLE);
        if (animate) {
            mAutoLogin.startAnimation(AnimationUtils.loadAnimation(
                    getContext(), R.anim.autologin_enter));
        }
    }

    public void hideAutoLogin(boolean animate) {
        if (mUseQuickControls) {
            mBaseUi.hideTitleBar();
            mAutoLogin.setVisibility(View.GONE);
            mBaseUi.refreshWebView();
        } else {
            if (animate) {
                Animation anim = AnimationUtils.loadAnimation(getContext(),
                        R.anim.autologin_exit);
                anim.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation a) {
                        mAutoLogin.setVisibility(View.GONE);
                        mBaseUi.refreshWebView();
                    }

                    @Override
                    public void onAnimationStart(Animation a) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation a) {
                    }
                });
                mAutoLogin.startAnimation(anim);
            } else if (mAutoLogin.getAnimation() == null) {
                mAutoLogin.setVisibility(View.GONE);
                mBaseUi.refreshWebView();
            }
        }
    }

    public boolean wantsToBeVisible() {
        return inAutoLogin()
            || (mSnapshotBar != null && mSnapshotBar.getVisibility() == View.VISIBLE
                    && mSnapshotBar.isAnimating());
    }

    private boolean inAutoLogin() {
        return mAutoLogin != null && mAutoLogin.getVisibility() == View.VISIBLE;
    }

    public boolean isEditingUrl() {
        return mNavBar.isEditingUrl();
    }

    public WebView getCurrentWebView() {
        Tab t = mBaseUi.getActiveTab();
        if (t != null) {
            return t.getWebView();
        } else {
            return null;
        }
    }

    public PageProgressView getProgressView() {
        return mProgress;
    }

    public NavigationBarBase getNavigationBar() {
        return mNavBar;
    }

    public boolean useQuickControls() {
        return mUseQuickControls;
    }

    public boolean isInLoad() {
        return mInLoad;
    }

    private ViewGroup.LayoutParams makeLayoutParams() {
        if (mUseQuickControls) {
            return new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
        } else {
            return new AbsoluteLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                    0, 0);
        }
    }

    @Override
    public View focusSearch(View focused, int dir) {
        if (FOCUS_DOWN == dir && hasFocus()) {
            return getCurrentWebView();
        }
        return super.focusSearch(focused, dir);
    }

    public void onTabDataChanged(Tab tab) {
        if (mSnapshotBar != null) {
            mSnapshotBar.onTabDataChanged(tab);
        }

        if (tab.isSnapshot()) {
            inflateSnapshotBar();
            mSnapshotBar.setVisibility(VISIBLE);
            mNavBar.setVisibility(GONE);
        } else {
            if (mSnapshotBar != null) {
                mSnapshotBar.setVisibility(GONE);
            }
            mNavBar.setVisibility(VISIBLE);
        }
    }

}
