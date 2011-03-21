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
import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
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
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TitleBarXLarge extends TitleBarBase
        implements OnClickListener, OnFocusChangeListener, TextChangeWatcher,
        DeviceAccountLogin.AutoLoginCallback {

    private XLargeUi mUi;

    private Drawable mStopDrawable;
    private Drawable mReloadDrawable;

    private View mContainer;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageView mStar;
    private ImageView mUrlIcon;
    private ImageView mSearchButton;
    private View mUrlContainer;
    private View mGoButton;
    private ImageView mStopButton;
    private View mAllButton;
    private View mClearButton;
    private ImageView mVoiceSearch;
    private PageProgressView mProgressView;
    private Drawable mFocusDrawable;
    private Drawable mUnfocusDrawable;
    // Auto-login UI
    private View mAutoLogin;
    private Spinner mAutoLoginAccount;
    private Button mAutoLoginLogin;
    private ProgressBar mAutoLoginProgress;
    private TextView mAutoLoginError;
    private ImageButton mAutoLoginCancel;
    private DeviceAccountLogin mAutoLoginHandler;
    private ArrayAdapter<String> mAccountsAdapter;

    private boolean mInLoad;
    private boolean mUseQuickControls;

    public TitleBarXLarge(Activity activity, UiController controller,
            XLargeUi ui) {
        super(activity, controller, ui);
        mUi = ui;
        Resources resources = activity.getResources();
        mStopDrawable = resources.getDrawable(R.drawable.ic_stop_holo_dark);
        mReloadDrawable = resources.getDrawable(R.drawable.ic_refresh_holo_dark);
        mFocusDrawable = resources.getDrawable(
                R.drawable.textfield_active_holo_dark);
        mUnfocusDrawable = resources.getDrawable(
                R.drawable.textfield_default_holo_dark);
        mInVoiceMode = false;
        initLayout(activity);
    }

    @Override
    void setTitleGravity(int gravity) {
        if (mUseQuickControls) {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) getLayoutParams();
            lp.gravity = gravity;
            setLayoutParams(lp);
        } else {
            super.setTitleGravity(gravity);
        }
    }

    private void initLayout(Context context) {
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.url_bar, this);

        mContainer = findViewById(R.id.taburlbar);
        mUrlInput = (UrlInputView) findViewById(R.id.url_focused);
        mAllButton = findViewById(R.id.all_btn);
        // TODO: Change enabled states based on whether you can go
        // back/forward.  Probably should be done inside onPageStarted.
        mBackButton = (ImageButton) findViewById(R.id.back);
        mForwardButton = (ImageButton) findViewById(R.id.forward);
        mUrlIcon = (ImageView) findViewById(R.id.url_icon);
        mStar = (ImageView) findViewById(R.id.star);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mSearchButton = (ImageView) findViewById(R.id.search);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mGoButton = findViewById(R.id.go);
        mClearButton = findViewById(R.id.clear);
        mVoiceSearch = (ImageView) findViewById(R.id.voicesearch);
        mProgressView = (PageProgressView) findViewById(R.id.progress);
        mUrlContainer = findViewById(R.id.urlbar_focused);
        mBackButton.setOnClickListener(this);
        mForwardButton.setOnClickListener(this);
        mStar.setOnClickListener(this);
        mAllButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mSearchButton.setOnClickListener(this);
        mGoButton.setOnClickListener(this);
        mClearButton.setOnClickListener(this);
        mVoiceSearch.setOnClickListener(this);
        mUrlInput.setUrlInputListener(this);
        mUrlInput.setContainer(mUrlContainer);
        mUrlInput.setController(mUiController);
        mUrlInput.setOnFocusChangeListener(this);
        mUrlInput.setSelectAllOnFocus(true);
        mUrlInput.addQueryTextWatcher(this);
        mAutoLogin = findViewById(R.id.autologin);
        mAutoLoginAccount = (Spinner) findViewById(R.id.autologin_account);
        mAutoLoginLogin = (Button) findViewById(R.id.autologin_login);
        mAutoLoginLogin.setOnClickListener(this);
        mAutoLoginProgress =
                (ProgressBar) findViewById(R.id.autologin_progress);
        mAutoLoginError = (TextView) findViewById(R.id.autologin_error);
        mAutoLoginCancel =
                (ImageButton) mAutoLogin.findViewById(R.id.autologin_close);
        mAutoLoginCancel.setOnClickListener(this);

        setFocusState(false);
    }

    void updateNavigationState(Tab tab) {
        WebView web = tab.getWebView();
        if (web != null) {
            mBackButton.setImageResource(web.canGoBack()
                    ? R.drawable.ic_back_holo_dark
                    : R.drawable.ic_back_disabled_holo_dark);
            mForwardButton.setImageResource(web.canGoForward()
                    ? R.drawable.ic_forward_holo_dark
                    : R.drawable.ic_forward_disabled_holo_dark);
        }
    }

    void updateAutoLogin(Tab tab, boolean animate) {
        DeviceAccountLogin login = tab.getDeviceAccountLogin();
        if (login != null) {
            mAutoLoginHandler = login;
            mAutoLogin.setVisibility(View.VISIBLE);
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
            mAutoLoginProgress.setVisibility(View.GONE);
            mAutoLoginError.setVisibility(View.GONE);
            switch (login.getState()) {
                case DeviceAccountLogin.PROCESSING:
                    mAutoLoginAccount.setEnabled(false);
                    mAutoLoginLogin.setEnabled(false);
                    mAutoLoginProgress.setVisibility(View.VISIBLE);
                    break;
                case DeviceAccountLogin.FAILED:
                    mAutoLoginProgress.setVisibility(View.GONE);
                    mAutoLoginError.setVisibility(View.VISIBLE);
                    break;
                case DeviceAccountLogin.INITIAL:
                    break;
                default:
                    throw new IllegalStateException();
            }
            if (mUseQuickControls) {
                mUi.showTitleBar();
            } else {
                if (animate) {
                    mAutoLogin.startAnimation(AnimationUtils.loadAnimation(
                            getContext(), R.anim.autologin_enter));
                }
            }
        } else {
            mAutoLoginHandler = null;
            if (mUseQuickControls) {
                mUi.hideTitleBar();
                mAutoLogin.setVisibility(View.GONE);
                mUi.refreshWebView();
            } else {
                if (animate) {
                    hideAutoLogin();
                } else if (mAutoLogin.getAnimation() == null) {
                    mAutoLogin.setVisibility(View.GONE);
                    mUi.refreshWebView();
                }
            }
        }
    }

    boolean inAutoLogin() {
        return mAutoLoginHandler != null;
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
    public int getEmbeddedHeight() {
        int height = mContainer.getHeight();
        if (mAutoLogin.getVisibility() == View.VISIBLE) {
            height += mAutoLogin.getHeight();
        }
        return height;
    }

    void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        setLayoutParams(makeLayoutParams());
    }

    void setShowProgressOnly(boolean progress) {
        if (progress && !inAutoLogin()) {
            mContainer.setVisibility(View.GONE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        // if losing focus and not in touch mode, leave as is
        if (hasFocus || view.isInTouchMode() || mUrlInput.needsUpdate()) {
            setFocusState(hasFocus);
            mUrlContainer.setBackgroundDrawable(hasFocus
                    ? mFocusDrawable : mUnfocusDrawable);
        }
        if (hasFocus) {
            mUrlInput.forceIme();
            if (mInVoiceMode) {
                mUrlInput.forceFilter();
            }
        } else if (!mUrlInput.needsUpdate()) {
            mUrlInput.dismissDropDown();
            mUrlInput.hideIME();
            if (mUseQuickControls) {
                mUi.hideTitleBar();
            }

            if (mUrlInput.getText().length() == 0) {
                Tab currentTab = mUiController.getTabControl().getCurrentTab();
                if (currentTab != null) {
                    mUrlInput.setText(currentTab.getUrl(), false);
                }
            }
        }
        mUrlInput.clearNeedsUpdate();
    }

    @Override
    public void setCurrentUrlIsBookmark(boolean isBookmark) {
        mStar.setActivated(isBookmark);
    }

    /**
     * called from the Ui when the user wants to edit
     * @param clearInput clear the input field
     */
    void startEditingUrl(boolean clearInput) {
        // editing takes preference of progress
        mContainer.setVisibility(View.VISIBLE);
        if (mUseQuickControls) {
            mProgressView.setVisibility(View.GONE);
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

    boolean isEditingUrl() {
        return mUrlInput.hasFocus();
    }

    void stopEditingUrl() {
        mUrlInput.clearFocus();
    }

    private void hideAutoLogin() {
        Animation anim = AnimationUtils.loadAnimation(
                getContext(), R.anim.autologin_exit);
        anim.setAnimationListener(new AnimationListener() {
            @Override public void onAnimationEnd(Animation a) {
                mAutoLogin.setVisibility(View.GONE);
                mUi.refreshWebView();
            }
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
        });
        mAutoLogin.startAnimation(anim);
    }

    @Override
    public void onClick(View v) {
        if (mBackButton == v) {
            mUiController.getCurrentTopWebView().goBack();
        } else if (mForwardButton == v) {
            mUiController.getCurrentTopWebView().goForward();
        } else if (mStar == v) {
            mUiController.bookmarkCurrentPage(
                    AddBookmarkPage.DEFAULT_FOLDER_ID, true);
        } else if (mAllButton == v) {
            mUiController.bookmarksOrHistoryPicker(false);
        } else if (mSearchButton == v) {
            mUi.editUrl(true);
        } else if (mStopButton == v) {
            stopOrRefresh();
        } else if (mGoButton == v) {
            if (!TextUtils.isEmpty(mUrlInput.getText())) {
                onAction(mUrlInput.getText().toString(), null,
                        UrlInputView.TYPED);
            }
        } else if (mClearButton == v) {
            clearOrClose();
        } else if (mVoiceSearch == v) {
            mUiController.startVoiceSearch();
        } else if (mAutoLoginCancel == v) {
            if (mAutoLoginHandler != null) {
                mAutoLoginHandler.cancel();
                mAutoLoginHandler = null;
            }
            hideAutoLogin();
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
    public void loginFailed() {
        mAutoLoginAccount.setEnabled(true);
        mAutoLoginLogin.setEnabled(true);
        mAutoLoginProgress.setVisibility(View.GONE);
        mAutoLoginError.setVisibility(View.VISIBLE);
    }

    @Override
    void setFavicon(Bitmap icon) { }

    private void clearOrClose() {
        if (TextUtils.isEmpty(mUrlInput.getUserText())) {
            // close
            mUrlInput.clearFocus();
        } else {
            // clear
            mUrlInput.setText("");
        }
    }

    private void setFocusState(boolean focus) {
        if (focus) {
            mUrlInput.setDropDownWidth(mUrlContainer.getWidth());
            mUrlInput.setDropDownHorizontalOffset(-mUrlInput.getLeft());
            mSearchButton.setVisibility(View.GONE);
            mStar.setVisibility(View.GONE);
            mClearButton.setVisibility(View.VISIBLE);
            mUrlIcon.setImageResource(R.drawable.ic_search_holo_dark);
            updateSearchMode(false);
        } else {
            mGoButton.setVisibility(View.GONE);
            mVoiceSearch.setVisibility(View.GONE);
            mStar.setVisibility(View.VISIBLE);
            mClearButton.setVisibility(View.GONE);
            if (mUseQuickControls) {
                mSearchButton.setVisibility(View.GONE);
            } else {
                mSearchButton.setVisibility(View.VISIBLE);
            }
            mUrlIcon.setImageResource(mInVoiceMode ?
                    R.drawable.ic_search_holo_dark
                    : R.drawable.ic_web_holo_dark);
        }
    }

    private void stopOrRefresh() {
        if (mInLoad) {
            mUiController.stopLoading();
        } else {
            mUiController.getCurrentTopWebView().reload();
        }
    }

    /**
     * Update the progress, from 0 to 100.
     */
    @Override
    void setProgress(int newProgress) {
        boolean blockvisuals = mUseQuickControls && isEditingUrl();
        if (newProgress >= PROGRESS_MAX) {
            if (!blockvisuals) {
                mProgressView.setProgress(PageProgressView.MAX_PROGRESS);
                mProgressView.setVisibility(View.GONE);
                mStopButton.setImageDrawable(mReloadDrawable);
            }
            mInLoad = false;
        } else {
            if (!mInLoad) {
                if (!blockvisuals) {
                    mProgressView.setVisibility(View.VISIBLE);
                    mStopButton.setImageDrawable(mStopDrawable);
                }
                mInLoad = true;
            }
            mProgressView.setProgress(newProgress * PageProgressView.MAX_PROGRESS
                    / PROGRESS_MAX);
        }
    }

    private void updateSearchMode(boolean userEdited) {
        setSearchMode(!userEdited || TextUtils.isEmpty(mUrlInput.getUserText()));
    }

    private void setSearchMode(boolean voiceSearchEnabled) {
        boolean showvoicebutton = voiceSearchEnabled &&
                mUiController.supportsVoiceSearch();
        mVoiceSearch.setVisibility(showvoicebutton ? View.VISIBLE :
                View.GONE);
        mGoButton.setVisibility(voiceSearchEnabled ? View.GONE :
                View.VISIBLE);
    }

    @Override
    /* package */ void setDisplayTitle(String title) {
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

    @Override
    public void setInVoiceMode(boolean voicemode) {
        setInVoiceMode(voicemode, null);
    }

    public void setInVoiceMode(boolean voicemode, List<String> voiceResults) {
        mInVoiceMode = voicemode;
        mUrlInput.setVoiceResults(voiceResults);
        if (voicemode) {
            mUrlIcon.setImageDrawable(mSearchButton.getDrawable());
        }
    }

    @Override
    void setIncognitoMode(boolean incognito) {
        mUrlInput.setIncognitoMode(incognito);
    }

    @Override
    public View focusSearch(View focused, int dir) {
        if (FOCUS_DOWN == dir && hasFocus()) {
            return getCurrentWebView();
        }
        return super.focusSearch(focused, dir);
    }

    void clearCompletions() {
        mUrlInput.setSuggestedText(null);
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

    private WebView getCurrentWebView() {
        Tab t = mUi.getActiveTab();
        if (t != null) {
            return t.getWebView();
        } else {
            return null;
        }
    }

    void registerDropdownChangeListener(DropdownChangeListener d) {
        mUrlInput.registerDropdownChangeListener(d);
    }
}
