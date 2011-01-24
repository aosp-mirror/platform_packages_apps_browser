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

import com.android.browser.UrlInputView.UrlInputListener;
import com.android.browser.search.SearchEngine;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.speech.RecognizerResultsIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.List;

/**
 * tabbed title bar for xlarge screen browser
 */
public class TitleBarXLarge extends TitleBarBase
    implements UrlInputListener, OnClickListener, OnFocusChangeListener,
    TextWatcher {

    private static final int PROGRESS_MAX = 100;

    private UiController mUiController;
    private XLargeUi mUi;

    private Drawable mStopDrawable;
    private Drawable mReloadDrawable;

    private View mContainer;
    private ImageButton mBackButton;
    private ImageButton mForwardButton;
    private ImageView mStar;
    private View mSearchButton;
    private View mUrlContainer;
    private View mGoButton;
    private ImageView mStopButton;
    private View mAllButton;
    private View mClearButton;
    private View mVoiceSearch;
    private View mVoiceSearchIndicator;
    private PageProgressView mProgressView;
    private UrlInputView mUrlInput;
    private Drawable mFocusDrawable;
    private Drawable mUnfocusDrawable;
    private boolean mInVoiceMode;

    private boolean mInLoad;
    private boolean mEditable;
    private boolean mUseQuickControls;

    public TitleBarXLarge(Activity activity, UiController controller,
            XLargeUi ui) {
        super(activity);
        mUiController = controller;
        mUi = ui;
        Resources resources = activity.getResources();
        mStopDrawable = resources.getDrawable(R.drawable.ic_stop_holo_dark);
        mReloadDrawable = resources.getDrawable(R.drawable.ic_refresh_holo_dark);
        mFocusDrawable = resources.getDrawable(
                R.drawable.textfield_active_holo_dark);
        mUnfocusDrawable = resources.getDrawable(
                R.drawable.textfield_default_holo_dark);
        initLayout(activity);
        mInVoiceMode = false;
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
        mStar = (ImageView) findViewById(R.id.star);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mSearchButton = findViewById(R.id.search);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mGoButton = findViewById(R.id.go);
        mClearButton = findViewById(R.id.clear);
        mVoiceSearch = findViewById(R.id.voicesearch);
        mProgressView = (PageProgressView) findViewById(R.id.progress);
        mUrlContainer = findViewById(R.id.urlbar_focused);
        mVoiceSearchIndicator = findViewById(R.id.voice_icon);
        mBackButton.setOnClickListener(this);
        mForwardButton.setOnClickListener(this);
        mStar.setOnClickListener(this);
        mAllButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        mSearchButton.setOnClickListener(this);
        mGoButton.setOnClickListener(this);
        mClearButton.setOnClickListener(this);
        mVoiceSearch.setOnClickListener(this);
        mUrlContainer.setOnClickListener(this);
        mUrlInput.setUrlInputListener(this);
        mUrlInput.setContainer(mUrlContainer);
        mUrlInput.setController(mUiController);
        mUrlInput.setOnFocusChangeListener(this);
        mUrlInput.setSelectAllOnFocus(true);
        mUrlInput.addTextChangedListener(this);
        setUrlMode(false);
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

    public void setEditable(boolean editable) {
        mEditable = editable;
        mUrlInput.setFocusable(mEditable);
        if (!mEditable) {
            mUrlInput.setOnClickListener(this);
        } else {
            mUrlContainer.setOnClickListener(null);
        }
    }

    void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        mUrlInput.setReverseResults(mUseQuickControls);
        if (mUseQuickControls) {
            mBackButton.setVisibility(View.GONE);
            mForwardButton.setVisibility(View.GONE);
            mStopButton.setVisibility(View.GONE);
            mAllButton.setVisibility(View.GONE);
        } else {
            mBackButton.setVisibility(View.VISIBLE);
            mForwardButton.setVisibility(View.VISIBLE);
            mStopButton.setVisibility(View.VISIBLE);
            mAllButton.setVisibility(View.VISIBLE);
        }
    }

    void setShowProgressOnly(boolean progress) {
        if (progress) {
            mContainer.setVisibility(View.GONE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (!mEditable && hasFocus) {
            mUi.editUrl(false);
        } else {
            setUrlMode(hasFocus);
        }
        mUrlContainer.setBackgroundDrawable(hasFocus
                ? mFocusDrawable : mUnfocusDrawable);
    }

    public void setCurrentUrlIsBookmark(boolean isBookmark) {
        mStar.setActivated(isBookmark);
    }

    /**
     * called from the Ui when the user wants to edit
     * Note: only the fake titlebar will get this callback
     * independent of which input field started the edit mode
     * @param clearInput clear the input field
     */
    void onEditUrl(boolean clearInput) {
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

    @Override
    public void onClick(View v) {
        if (mUrlInput == v) {
            mUi.editUrl(false);
        } else if (mUrlContainer == v) {
            if (!mUrlInput.hasFocus()) {
                mUi.editUrl(false);
            }
        } else if (mBackButton == v) {
            mUiController.getCurrentTopWebView().goBack();
        } else if (mForwardButton == v) {
            mUiController.getCurrentTopWebView().goForward();
        } else if (mStar == v) {
            mUiController.bookmarkCurrentPage(
                    AddBookmarkPage.DEFAULT_FOLDER_ID);
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
        }
    }

    int getHeightWithoutProgress() {
        return mContainer.getHeight();
    }

    @Override
    void setFavicon(Bitmap icon) { }

    private void clearOrClose() {
        if (TextUtils.isEmpty(mUrlInput.getText())) {
            // close
            setUrlMode(false);
        } else {
            // clear
            mUrlInput.setText("");
        }
    }

    // UrlInputListener implementation

    /**
     * callback from suggestion dropdown
     * user selected a suggestion
     */
    @Override
    public void onAction(String text, String extra, String source) {
        mUiController.getCurrentTopWebView().requestFocus();
        mUi.hideFakeTitleBar();
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
        setUrlMode(false);
        setDisplayTitle(text);
    }

    @Override
    public void onDismiss() {
        WebView top = mUiController.getCurrentTopWebView();
        if (top != null) {
            mUiController.getCurrentTopWebView().requestFocus();
        }
        mUi.hideFakeTitleBar();
        setUrlMode(false);
        // if top != null current must be set
        if ((top != null) && !mInVoiceMode) {
            setDisplayTitle(mUiController.getCurrentWebView().getUrl());
        }
    }

    /**
     * callback from the suggestion dropdown
     * copy text to input field and stay in edit mode
     */
    @Override
    public void onEdit(String text) {
        mUrlInput.setText(text, true);
        if (text != null) {
            mUrlInput.setSelection(text.length());
        }
    }

    void setUrlMode(boolean focused) {
        if (focused) {
            mUrlInput.setDropDownWidth(mUrlContainer.getWidth());
            mUrlInput.setDropDownHorizontalOffset(-mUrlInput.getLeft());
            mSearchButton.setVisibility(View.GONE);
            mStar.setVisibility(View.GONE);
            mClearButton.setVisibility(View.VISIBLE);
            if (mInVoiceMode) {
                mVoiceSearchIndicator.setVisibility(View.VISIBLE);
            }
            updateSearchMode();
        } else {
            mUrlInput.clearFocus();
            mGoButton.setVisibility(View.GONE);
            mVoiceSearch.setVisibility(View.GONE);
            mStar.setVisibility(View.VISIBLE);
            mClearButton.setVisibility(View.GONE);
            mVoiceSearchIndicator.setVisibility(View.GONE);
            if (mUseQuickControls) {
                mSearchButton.setVisibility(View.GONE);
            } else {
                mSearchButton.setVisibility(View.VISIBLE);
            }
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

    private void updateSearchMode() {
        setSearchMode(TextUtils.isEmpty(mUrlInput.getText()));
    }

    private void setSearchMode(boolean voiceSearchEnabled) {
        SearchEngine searchEngine = BrowserSettings.getInstance()
                .getSearchEngine();
        boolean showvoicebutton = voiceSearchEnabled &&
                (searchEngine != null && searchEngine.supportsVoiceSearch());
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
    public void afterTextChanged(Editable s) {
        if (mUrlInput.hasFocus()) {
            // check if input field is empty and adjust voice search state
            updateSearchMode();
            // clear voice mode when user types
            setInVoiceMode(false, null);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    // voicesearch

    @Override
    public void setInVoiceMode(boolean voicemode) {
        setInVoiceMode(voicemode, null);
    }

    public void setInVoiceMode(boolean voicemode, List<String> voiceResults) {
        mInVoiceMode = voicemode;
        mUrlInput.setVoiceResults(voiceResults);
        mVoiceSearchIndicator.setVisibility(mInVoiceMode
                ? View.VISIBLE : View.GONE);
    }

    @Override
    void setIncognitoMode(boolean incognito) {
        mUrlInput.setIncognitoMode(incognito);
    }
}
