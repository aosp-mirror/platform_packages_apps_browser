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

import com.android.browser.UI.DropdownChangeListener;
import com.android.browser.UrlInputView.UrlInputListener;
import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.speech.RecognizerResultsIntent;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.AbsoluteLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

/**
 * Base class for a title bar used by the browser.
 */
public class TitleBarBase extends RelativeLayout
        implements OnClickListener, OnFocusChangeListener, UrlInputListener,
        TextChangeWatcher, DeviceAccountLogin.AutoLoginCallback {

    protected static final int PROGRESS_MAX = 100;

    // These need to be set by the subclass.
    protected ImageView mFavicon;
    protected ImageView mLockIcon;

    protected Drawable mGenericFavicon;
    protected UiController mUiController;
    protected BaseUi mBaseUi;
    protected FrameLayout mParent;
    protected PageProgressView mProgress;
    protected UrlInputView mUrlInput;
    protected boolean mInVoiceMode;
    protected View mContainer;


    // Auto-login UI
    protected View mAutoLogin;
    protected Spinner mAutoLoginAccount;
    protected Button mAutoLoginLogin;
    protected ProgressBar mAutoLoginProgress;
    protected TextView mAutoLoginError;
    protected View mAutoLoginCancel;
    protected DeviceAccountLogin mAutoLoginHandler;
    protected ArrayAdapter<String> mAccountsAdapter;
    protected boolean mUseQuickControls;

    //state
    protected boolean mShowing;
    protected boolean mInLoad;
    protected boolean mSkipTitleBarAnimations;
    private Animator mTitleBarAnimator;

    public TitleBarBase(Context context, UiController controller, BaseUi ui,
            FrameLayout parent) {
        super(context, null);
        mUiController = controller;
        mBaseUi = ui;
        mParent = parent;
        mGenericFavicon = context.getResources().getDrawable(
                R.drawable.app_web_browser_sm);
    }

    protected void initLayout(Context context, int layoutId) {
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(layoutId, this);
        mContainer = findViewById(R.id.taburlbar);
        mProgress = (PageProgressView) findViewById(R.id.progress);
        mUrlInput = (UrlInputView) findViewById(R.id.url);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mUrlInput.setUrlInputListener(this);
        mUrlInput.setController(mUiController);
        mUrlInput.setOnFocusChangeListener(this);
        mUrlInput.setSelectAllOnFocus(true);
        mUrlInput.addQueryTextWatcher(this);
        mAutoLogin = findViewById(R.id.autologin);
        mAutoLoginAccount = (Spinner) findViewById(R.id.autologin_account);
        mAutoLoginLogin = (Button) findViewById(R.id.autologin_login);
        mAutoLoginLogin.setOnClickListener(this);
        mAutoLoginProgress = (ProgressBar) findViewById(R.id.autologin_progress);
        mAutoLoginError = (TextView) findViewById(R.id.autologin_error);
        mAutoLoginCancel = mAutoLogin.findViewById(R.id.autologin_close);
        mAutoLoginCancel.setOnClickListener(this);
    }

    protected void setupUrlInput() {
    }

    protected void setUseQuickControls(boolean use) {
        mUseQuickControls = use;
        setLayoutParams(makeLayoutParams());
    }

    void setShowProgressOnly(boolean progress) {
        if (progress && !inAutoLogin()) {
            mContainer.setVisibility(View.GONE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
        }
    }

    void setSkipTitleBarAnimations(boolean skip) {
        mSkipTitleBarAnimations = skip;
    }

    void setTitleGravity(int gravity) {
        int newTop = 0;
        int newLeft = 0;
        View parent = (View) getParent();
        if (parent != null) {
            newLeft = parent.getScrollX();
        }
        if (gravity != Gravity.NO_GRAVITY) {
            if (parent != null) {
                if (gravity == Gravity.TOP) {
                    newTop = parent.getScrollY();
                }
            }
        }
        AbsoluteLayout.LayoutParams lp = (AbsoluteLayout.LayoutParams)
                getLayoutParams();
        if (lp != null) {
            lp.x = newLeft;
            lp.y = newTop;
            setLayoutParams(lp);
        }
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
    void setProgress(int newProgress) {
        if (newProgress >= PROGRESS_MAX) {
            mProgress.setProgress(PageProgressView.MAX_PROGRESS);
            mProgress.setVisibility(View.GONE);
            mInLoad = false;
            onProgressStopped();
            // check if needs to be hidden
            if (!isEditingUrl() && !inAutoLogin()) {
                hide();
                if (mUseQuickControls) {
                    setShowProgressOnly(false);
                }
            }
        } else {
            if (!mInLoad) {
                mProgress.setVisibility(View.VISIBLE);
                mInLoad = true;
                onProgressStarted();
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

    protected void onProgressStarted() {
    }

    protected void onProgressStopped() {
    }

    /* package */ void setLock(Drawable d) {
        assert mLockIcon != null;
        if (null == d) {
            mLockIcon.setVisibility(View.GONE);
        } else {
            mLockIcon.setImageDrawable(d);
            mLockIcon.setVisibility(View.VISIBLE);
        }
    }

    /* package */ void setFavicon(Bitmap icon) {
        assert mFavicon != null;
        Drawable[] array = new Drawable[3];
        array[0] = new PaintDrawable(Color.BLACK);
        PaintDrawable p = new PaintDrawable(Color.WHITE);
        array[1] = p;
        if (icon == null) {
            array[2] = mGenericFavicon;
        } else {
            array[2] = new BitmapDrawable(icon);
        }
        LayerDrawable d = new LayerDrawable(array);
        d.setLayerInset(1, 1, 1, 1, 1);
        d.setLayerInset(2, 2, 2, 2, 2);
        mFavicon.setImageDrawable(d);
    }

    public int getEmbeddedHeight() {
        int height = mContainer.getHeight();
        if (mAutoLogin.getVisibility() == View.VISIBLE) {
            height += mAutoLogin.getHeight();
        }
        return height;
    }

    protected void updateAutoLogin(Tab tab, boolean animate) {
        DeviceAccountLogin login = tab.getDeviceAccountLogin();
        if (login != null) {
            mAutoLoginHandler = login;
            ContextThemeWrapper wrapper = new ContextThemeWrapper(mContext,
                    android.R.style.Theme_Holo_Light);
            mAccountsAdapter = new ArrayAdapter<String>(wrapper,
                    android.R.layout.simple_spinner_item, login.getAccountNames());
            mAccountsAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            mAutoLoginAccount.setAdapter(mAccountsAdapter);
            mAutoLoginAccount.setSelection(0);
            mAutoLoginAccount.setEnabled(true);
            mAutoLoginLogin.setEnabled(true);
            mAutoLoginProgress.setVisibility(View.INVISIBLE);
            mAutoLoginError.setVisibility(View.GONE);
            switch (login.getState()) {
                case DeviceAccountLogin.PROCESSING:
                    mAutoLoginAccount.setEnabled(false);
                    mAutoLoginLogin.setEnabled(false);
                    mAutoLoginProgress.setVisibility(View.VISIBLE);
                    break;
                case DeviceAccountLogin.FAILED:
                    mAutoLoginProgress.setVisibility(View.INVISIBLE);
                    mAutoLoginError.setVisibility(View.VISIBLE);
                    break;
                case DeviceAccountLogin.INITIAL:
                    break;
                default:
                    throw new IllegalStateException();
            }
            showAutoLogin(animate);
        } else {
            hideAutoLogin(animate);
        }
    }

    protected void showAutoLogin(boolean animate) {
        if (mUseQuickControls) {
            mBaseUi.showTitleBar();
        }
        mAutoLogin.setVisibility(View.VISIBLE);
        if (animate) {
            mAutoLogin.startAnimation(AnimationUtils.loadAnimation(
                    getContext(), R.anim.autologin_enter));
        }
    }

    protected void hideAutoLogin(boolean animate) {
        mAutoLoginHandler = null;
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

    @Override
    public void loginFailed() {
        mAutoLoginAccount.setEnabled(true);
        mAutoLoginLogin.setEnabled(true);
        mAutoLoginProgress.setVisibility(View.INVISIBLE);
        mAutoLoginError.setVisibility(View.VISIBLE);
    }


    protected boolean inAutoLogin() {
        return mAutoLoginHandler != null;
    }

    @Override
    public void onClick(View v) {
        if (mAutoLoginCancel == v) {
            if (mAutoLoginHandler != null) {
                mAutoLoginHandler.cancel();
                mAutoLoginHandler = null;
            }
            hideAutoLogin(true);
        } else if (mAutoLoginLogin == v) {
            if (mAutoLoginHandler != null) {
                mAutoLoginAccount.setEnabled(false);
                mAutoLoginLogin.setEnabled(false);
                mAutoLoginProgress.setVisibility(View.VISIBLE);
                mAutoLoginError.setVisibility(View.GONE);
                mAutoLoginHandler.login(
                        mAutoLoginAccount.getSelectedItemPosition(), this);
            }
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        // if losing focus and not in touch mode, leave as is
        if (hasFocus || view.isInTouchMode() || mUrlInput.needsUpdate()) {
            setFocusState(hasFocus);
        }
        if (hasFocus) {
            mUrlInput.forceIme();
            if (mInVoiceMode) {
                mUrlInput.forceFilter();
            }
        } else if (!mUrlInput.needsUpdate()) {
            mUrlInput.dismissDropDown();
            mUrlInput.hideIME();
            if (mUrlInput.getText().length() == 0) {
                Tab currentTab = mUiController.getTabControl().getCurrentTab();
                if (currentTab != null) {
                    mUrlInput.setText(currentTab.getUrl(), false);
                }
            }
        }
        mUrlInput.clearNeedsUpdate();
    }

    protected void setFocusState(boolean focus) {
        if (focus) {
            updateSearchMode(false);
        }
    }

    protected void updateSearchMode(boolean userEdited) {
        setSearchMode(!userEdited || TextUtils.isEmpty(mUrlInput.getUserText()));
    }

    protected void setSearchMode(boolean voiceSearchEnabled) {}

    boolean isEditingUrl() {
        return mUrlInput.hasFocus();
    }

    void stopEditingUrl() {
        mUrlInput.clearFocus();
    }

    void setDisplayTitle(String title) {
        if (!isEditingUrl()) {
            mUrlInput.setText(title, false);
        }
    }

    // UrlInput text watcher

    @Override
    public void onTextChanged(String newText) {
        if (mUrlInput.hasFocus()) {
            // check if input field is empty and adjust voice search state
            updateSearchMode(true);
            // clear voice mode when user types
            setInVoiceMode(false, null);
        }
    }

    // voicesearch

    public void setInVoiceMode(boolean voicemode, List<String> voiceResults) {
        mInVoiceMode = voicemode;
        mUrlInput.setVoiceResults(voiceResults);
    }

    void setIncognitoMode(boolean incognito) {
        mUrlInput.setIncognitoMode(incognito);
    }

    void clearCompletions() {
        mUrlInput.setSuggestedText(null);
    }

    // UrlInputListener implementation

    /**
     * callback from suggestion dropdown
     * user selected a suggestion
     */
    @Override
    public void onAction(String text, String extra, String source) {
        mUiController.getCurrentTopWebView().requestFocus();
        mBaseUi.hideTitleBar();
        Intent i = new Intent();
        String action = null;
        if (UrlInputView.VOICE.equals(source)) {
            action = RecognizerResultsIntent.ACTION_VOICE_SEARCH_RESULTS;
            source = null;
        } else {
            action = Intent.ACTION_SEARCH;
        }
        i.setAction(action);
        i.putExtra(SearchManager.QUERY, text);
        if (extra != null) {
            i.putExtra(SearchManager.EXTRA_DATA_KEY, extra);
        }
        if (source != null) {
            Bundle appData = new Bundle();
            appData.putString(com.android.common.Search.SOURCE, source);
            i.putExtra(SearchManager.APP_DATA, appData);
        }
        mUiController.handleNewIntent(i);
        setDisplayTitle(text);
    }

    @Override
    public void onDismiss() {
        final Tab currentTab = mBaseUi.getActiveTab();
        mBaseUi.hideTitleBar();
        post(new Runnable() {
            public void run() {
                clearFocus();
                if ((currentTab != null) && !mInVoiceMode) {
                    setDisplayTitle(currentTab.getUrl());
                }
            }
        });
    }

    /**
     * callback from the suggestion dropdown
     * copy text to input field and stay in edit mode
     */
    @Override
    public void onCopySuggestion(String text) {
        mUrlInput.setText(text, true);
        if (text != null) {
            mUrlInput.setSelection(text.length());
        }
    }

    public void setCurrentUrlIsBookmark(boolean isBookmark) {
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // catch back key in order to do slightly more cleanup than usual
            mUrlInput.clearFocus();
            return true;
        }
        return super.dispatchKeyEventPreIme(evt);
    }

    protected WebView getCurrentWebView() {
        Tab t = mBaseUi.getActiveTab();
        if (t != null) {
            return t.getWebView();
        } else {
            return null;
        }
    }

    void registerDropdownChangeListener(DropdownChangeListener d) {
        mUrlInput.registerDropdownChangeListener(d);
    }

    /**
     * called from the Ui when the user wants to edit
     * @param clearInput clear the input field
     */
    void startEditingUrl(boolean clearInput) {
        // editing takes preference of progress
        mContainer.setVisibility(View.VISIBLE);
        if (mUseQuickControls) {
            mProgress.setVisibility(View.GONE);
        }
        if (!mUrlInput.hasFocus()) {
            mUrlInput.requestFocus();
        }
        if (clearInput) {
            mUrlInput.setText("");
        } else if (mInVoiceMode) {
            mUrlInput.showDropDown();
        }
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

}
