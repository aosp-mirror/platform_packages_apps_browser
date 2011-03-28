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
import com.android.browser.view.StopProgressView;

import android.app.Activity;
import android.content.Context;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout.LayoutParams;

import java.util.List;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBarPhone extends TitleBarBase implements OnFocusChangeListener,
        OnClickListener, TextChangeWatcher {

    private Activity mActivity;
    private StopProgressView mStopButton;
    private ImageView mVoiceButton;
    private boolean mInLoad;
    private View mContainer;
    private boolean mHasLockIcon;

    public TitleBarPhone(Activity activity, UiController controller, PhoneUi ui) {
        super(activity, controller, ui);
        mActivity = activity;
        initLayout(activity, R.layout.title_bar);
    }

    @Override
    protected void initLayout(Context context, int layoutId) {
        super.initLayout(context, layoutId);
        mContainer = findViewById(R.id.taburlbar);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mStopButton = (StopProgressView) findViewById(R.id.stop);
        mStopButton.setOnClickListener(this);
        mVoiceButton = (ImageView) findViewById(R.id.voice);
        mVoiceButton.setOnClickListener(this);
        setFocusState(false);
    }

    @Override
    public int getEmbeddedHeight() {
        int height = mContainer.getHeight();
        return height;
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
            mFavicon.setVisibility(View.GONE);
            mLockIcon.setVisibility(View.GONE);
            mStopButton.setVisibility(View.GONE);
            mVoiceButton.setVisibility(View.VISIBLE);
        } else {
            mFavicon.setVisibility(View.VISIBLE);
            mLockIcon.setVisibility(mHasLockIcon ? View.VISIBLE : View.GONE);
            if (mInLoad) {
                mStopButton.setVisibility(View.VISIBLE);
            } else {
                mStopButton.setVisibility(View.GONE);
            }
            mVoiceButton.setVisibility(View.GONE);
        }
    }

    /**
     * Update the progress, from 0 to 100.
     */
    @Override
    void setProgress(int newProgress) {
        if (newProgress >= PROGRESS_MAX) {
            mInLoad = false;
            setFocusState(mUrlInput.hasFocus());
        } else {
            if (!mInLoad) {
                mInLoad = true;
                setFocusState(mUrlInput.hasFocus());
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
        if (mUseQuickControls && !hasFocus) {
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

    @Override
    void startEditingUrl(boolean clearInput) {
        // editing takes preference of progress
        mContainer.setVisibility(View.VISIBLE);
        if (!mUrlInput.hasFocus()) {
            mUrlInput.requestFocus();
        }
        if (clearInput) {
            mUrlInput.setText("");
        } else if (mInVoiceMode) {
            mUrlInput.showDropDown();
        }
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

    @Override
    protected void setUseQuickControls(boolean useQuickControls) {
        mUseQuickControls = useQuickControls;
        setLayoutParams(makeLayoutParams());
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
