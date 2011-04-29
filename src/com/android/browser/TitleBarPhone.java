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

import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;

import android.app.Activity;
import android.content.Context;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBarPhone extends TitleBarBase implements OnFocusChangeListener,
        OnClickListener, TextChangeWatcher {

    private Activity mActivity;
    private ImageView mStopButton;
    private ImageView mVoiceButton;
    private boolean mHasLockIcon;

    public TitleBarPhone(Activity activity, UiController controller, PhoneUi ui,
            FrameLayout parent) {
        super(activity, controller, ui, parent);
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
        setFocusState(false);
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
    protected void setFocusState(boolean focus) {
        super.setFocusState(focus);
        if (focus) {
            mHasLockIcon = (mLockIcon.getVisibility() == View.VISIBLE);
            mLockIcon.setVisibility(View.GONE);
            mStopButton.setVisibility(View.GONE);
            mVoiceButton.setVisibility(View.VISIBLE);
        } else {
            mLockIcon.setVisibility(mHasLockIcon ? View.VISIBLE : View.GONE);
            if (mInLoad) {
                mStopButton.setVisibility(View.VISIBLE);
            } else {
                mStopButton.setVisibility(View.GONE);
            }
            mVoiceButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onProgressStarted() {
        setFocusState(mUrlInput.hasFocus());
    }

    @Override
    protected void onProgressStopped() {
        setFocusState(mUrlInput.hasFocus());
    }

    /**
     * Update the text displayed in the title bar.
     * @param title String to display.  If null, the new tab string will be
     *      shown.
     */
    @Override
    void setDisplayTitle(String title) {
        if (title == null) {
            mUrlInput.setText(R.string.new_tab);
        } else {
            mUrlInput.setText(title);
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
        if (!hasFocus) {
            mBaseUi.hideTitleBar();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mStopButton) {
            mUiController.stopLoading();
        } else if (v == mVoiceButton) {
            mUiController.startVoiceSearch();
        } else {
            super.onClick(v);
        }
    }

}
