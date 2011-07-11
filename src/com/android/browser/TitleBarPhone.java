/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnDismissListener;

import com.android.browser.UrlInputView.StateListener;
import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;

import java.util.List;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBarPhone extends TitleBarBase implements OnFocusChangeListener,
        OnClickListener, TextChangeWatcher, StateListener, OnDismissListener {

    private Activity mActivity;
    private ImageView mStopButton;
    private ImageView mVoiceButton;
    private Drawable mStopDrawable;
    private Drawable mRefreshDrawable;
    private View mTabSwitcher;
    private View mComboIcon;
    private View mTitleContainer;
    private View mMore;
    private Drawable mTextfieldBgDrawable;
    private boolean mMenuShowing;
    private boolean mNeedsMenu;

    public TitleBarPhone(Activity activity, UiController controller, PhoneUi ui,
            FrameLayout parent) {
        super(activity, controller, ui, parent);
        mNeedsMenu = !ViewConfiguration.get(activity).hasPermanentMenuKey();
        mActivity = activity;
        initLayout(activity, R.layout.title_bar);
    }

    @Override
    protected void initLayout(Context context, int layoutId) {
        super.initLayout(context, layoutId);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mStopButton = (ImageView) findViewById(R.id.stop);
        mStopButton.setOnClickListener(this);
        mVoiceButton = (ImageView) findViewById(R.id.voice);
        mVoiceButton.setOnClickListener(this);
        mTabSwitcher = findViewById(R.id.tab_switcher);
        mTabSwitcher.setOnClickListener(this);
        mMore = findViewById(R.id.more);
        mMore.setOnClickListener(this);
        mComboIcon = findViewById(R.id.iconcombo);
        mTitleContainer = findViewById(R.id.title_bg);
        setFocusState(false);
        Resources res = context.getResources();
        mStopDrawable = res.getDrawable(R.drawable.ic_stop_holo_dark);
        mRefreshDrawable = res.getDrawable(R.drawable.ic_refresh_holo_dark);
        mTextfieldBgDrawable = res.getDrawable(R.drawable.textfield_active_holo_dark);
        setUaSwitcher(mComboIcon);
        mUrlInput.setContainer(this);
        mUrlInput.setStateListener(this);
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mActivity.onCreateContextMenu(menu, this, null);
    }

    @Override
    public void setInVoiceMode(boolean voicemode, List<String> voiceResults) {
        super.setInVoiceMode(voicemode, voiceResults);
    }

    @Override
    protected void setSearchMode(boolean voiceSearchEnabled) {
        boolean showvoicebutton = voiceSearchEnabled &&
                mUiController.supportsVoiceSearch();
        mVoiceButton.setVisibility(showvoicebutton ? View.VISIBLE :
                View.GONE);
    }

    @Override
    void setProgress(int progress) {
        super.setProgress(progress);
        if (progress == 100) {
            mStopButton.setVisibility(View.GONE);
            mStopButton.setImageDrawable(mRefreshDrawable);
            if (!isEditingUrl()) {
                mComboIcon.setVisibility(View.VISIBLE);
            }
        } else {
            if (mStopButton.getDrawable() != mStopDrawable) {
                mStopButton.setImageDrawable(mStopDrawable);
                if (mStopButton.getVisibility() != View.VISIBLE) {
                    mComboIcon.setVisibility(View.GONE);
                    mStopButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Update the text displayed in the title bar.
     * @param title String to display.  If null, the new tab string will be
     *      shown.
     */
    @Override
    void setDisplayTitle(String title) {
        if (!isEditingUrl()) {
            if (title == null) {
                mUrlInput.setText(R.string.new_tab);
            } else {
                mUrlInput.setText(title);
            }
            mUrlInput.setSelection(0);
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mUrlInput) {
            if (hasFocus) {
                mActivity.closeOptionsMenu();
            }
        }
        super.onFocusChange(v, hasFocus);
    }

    @Override
    public void onClick(View v) {
        if (v == mStopButton) {
            if (mInLoad) {
                mUiController.stopLoading();
            } else {
                WebView web = mBaseUi.getWebView();
                if (web != null) {
                    stopEditingUrl();
                    web.reload();
                }
            }
        } else if (v == mVoiceButton) {
            mUiController.startVoiceSearch();
        } else if (v == mTabSwitcher) {
            mBaseUi.onMenuKey();
        } else if (mMore == v) {
            showMenu();
        } else {
            super.onClick(v);
        }
    }

    public boolean isMenuShowing() {
        return mMenuShowing;
    }

    private void showMenu() {
        mMenuShowing = true;
        PopupMenu popup = new PopupMenu(mContext, mMore);
        Menu menu = popup.getMenu();
        popup.getMenuInflater().inflate(R.menu.browser, menu);
        menu.setGroupVisible(R.id.NAV_MENU, false);
        popup.setOnMenuItemClickListener(this);
        popup.setOnDismissListener(this);
        popup.show();
    }

    @Override
    public void onDismiss(PopupMenu menu) {
        onMenuHidden();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        onMenuHidden();
        boolean res = mUiController.onOptionsItemSelected(item);
        if (!res) {
            return super.onMenuItemClick(item);
        }
        return res;
    }

    private void onMenuHidden() {
        mMenuShowing = false;
        mBaseUi.showTitleBarForDuration();
    }

    @Override
    public void onStateChanged(int state) {
        switch(state) {
        case StateListener.STATE_NORMAL:
            mComboIcon.setVisibility(View.VISIBLE);
            mStopButton.setVisibility(View.GONE);
            setSearchMode(false);
            mTabSwitcher.setVisibility(View.VISIBLE);
            mTitleContainer.setBackgroundDrawable(null);
            mMore.setVisibility(mNeedsMenu ? View.VISIBLE : View.GONE);
            break;
        case StateListener.STATE_HIGHLIGHTED:
            mComboIcon.setVisibility(View.GONE);
            mStopButton.setVisibility(View.VISIBLE);
            setSearchMode(true);
            mTabSwitcher.setVisibility(View.GONE);
            mMore.setVisibility(View.GONE);
            mTitleContainer.setBackgroundDrawable(mTextfieldBgDrawable);
            break;
        case StateListener.STATE_EDITED:
            mComboIcon.setVisibility(View.GONE);
            mStopButton.setVisibility(View.GONE);
            setSearchMode(false);
            mTabSwitcher.setVisibility(View.GONE);
            mMore.setVisibility(View.GONE);
            mTitleContainer.setBackgroundDrawable(mTextfieldBgDrawable);
            break;
        }
    }

}
