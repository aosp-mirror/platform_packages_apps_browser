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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnDismissListener;

import com.android.browser.UrlInputView.StateListener;

public class NavigationBarPhone extends NavigationBarBase implements
        StateListener, OnDismissListener {

    private ImageView mStopButton;
    private ImageView mVoiceButton;
    private Drawable mStopDrawable;
    private Drawable mRefreshDrawable;
    private View mTabSwitcher;
    private View mComboIcon;
    private View mTitleContainer;
    private View mMore;
    private Drawable mTextfieldBgDrawable;
    private PopupMenu mPopupMenu;
    private boolean mNeedsMenu;

    public NavigationBarPhone(Context context) {
        super(context);
    }

    public NavigationBarPhone(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NavigationBarPhone(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
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
        Resources res = getContext().getResources();
        mStopDrawable = res.getDrawable(R.drawable.ic_stop_holo_dark);
        mRefreshDrawable = res.getDrawable(R.drawable.ic_refresh_holo_dark);
        mTextfieldBgDrawable = res.getDrawable(R.drawable.textfield_active_holo_dark);
        setUaSwitcher(mComboIcon);
        mUrlInput.setContainer(this);
        mUrlInput.setStateListener(this);
        mNeedsMenu = !ViewConfiguration.get(getContext()).hasPermanentMenuKey();
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        Activity activity = mBaseUi.getActivity();
        MenuInflater inflater = activity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        activity.onCreateContextMenu(menu, this, null);
    }

    @Override
    protected void setSearchMode(boolean voiceSearchEnabled) {
        boolean showvoicebutton = voiceSearchEnabled &&
                mUiController.supportsVoiceSearch();
        mVoiceButton.setVisibility(showvoicebutton ? View.VISIBLE :
                View.GONE);
    }

    @Override
    public void onProgressStarted() {
        super.onProgressStarted();
        if (mStopButton.getDrawable() != mStopDrawable) {
            mStopButton.setImageDrawable(mStopDrawable);
            if (mStopButton.getVisibility() != View.VISIBLE) {
                mComboIcon.setVisibility(View.GONE);
                mStopButton.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onProgressStopped() {
        super.onProgressStopped();
        mStopButton.setVisibility(View.GONE);
        mStopButton.setImageDrawable(mRefreshDrawable);
        if (!isEditingUrl()) {
            mComboIcon.setVisibility(View.VISIBLE);
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
    public void onClick(View v) {
        if (v == mStopButton) {
            if (mTitleBar.isInLoad()) {
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
            showMenu(mMore);
        } else {
            super.onClick(v);
        }
    }

    public boolean isMenuShowing() {
        return (mPopupMenu != null);
    }

    void showMenu() {
        // called from menu key, use tab switcher as anchor
        showMenu(mTabSwitcher);
    }

    void showMenu(View anchor) {
        mPopupMenu = new PopupMenu(mContext, anchor);
        Menu menu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(R.menu.browser, menu);
        mUiController.updateMenuState(mBaseUi.getActiveTab(), menu);
        mPopupMenu.setOnMenuItemClickListener(this);
        mPopupMenu.setOnDismissListener(this);
        mPopupMenu.show();
    }

    void dismissMenu() {
       if (mPopupMenu != null) {
           mPopupMenu.dismiss();
       }
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
        mPopupMenu = null;
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
