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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.speech.RecognizerIntent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBar extends TitleBarBase implements OnFocusChangeListener,
        OnClickListener {

    private Activity mActivity;
    private ImageButton mBookmarkButton;
    private Drawable mCircularProgress;
    private ProgressBar mHorizontalProgress;
    private ImageButton mStopButton;
    private Drawable mBookmarkDrawable;
    private Drawable mVoiceDrawable;
    private boolean mInLoad;
    private View mTitleBg;
    private Intent mVoiceSearchIntent;
    private Drawable mVoiceModeBackground;
    private Drawable mNormalBackground;
    private ImageSpan mArcsSpan;
    private int mLeftMargin;
    private int mRightMargin;

    public TitleBar(Activity activity, UiController controller, PhoneUi ui) {
        super(activity, controller, ui);
        LayoutInflater factory = LayoutInflater.from(activity);
        factory.inflate(R.layout.title_bar, this);
        mActivity = activity;

        mUrlInput = (UrlInputView) findViewById(R.id.url_input);
        mUrlInput.setCompoundDrawablePadding(5);
        mUrlInput.setContainer(this);
        mUrlInput.setSelectAllOnFocus(true);
        mUrlInput.setController(mUiController);
        mUrlInput.setUrlInputListener(this);
        mUrlInput.setOnFocusChangeListener(this);

        mTitleBg = findViewById(R.id.title_bg);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mStopButton = (ImageButton) findViewById(R.id.stop);
        mBookmarkButton = (ImageButton) findViewById(R.id.bookmark);
        mStopButton.setOnClickListener(this);
        mBookmarkButton.setOnClickListener(this);

        Resources resources = activity.getResources();
        mCircularProgress = resources.getDrawable(
                com.android.internal.R.drawable.search_spinner);
        DisplayMetrics metrics = resources.getDisplayMetrics();
        mLeftMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, metrics);
        mRightMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, metrics);
        int iconDimension = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f, metrics);
        mCircularProgress.setBounds(0, 0, iconDimension, iconDimension);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mVoiceSearchIntent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        mVoiceSearchIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        // This extra tells voice search not to send the application id in its
        // results intent - http://b/2546173
        //
        // TODO: Make a constant for this extra.
        mVoiceSearchIntent.putExtra("android.speech.extras.SEND_APPLICATION_ID_EXTRA",
                false);
        PackageManager pm = activity.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(mVoiceSearchIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null) {
            mVoiceSearchIntent = null;
        } else {
            mVoiceDrawable = resources.getDrawable(
                    android.R.drawable.ic_btn_speak_now);
        }
        mBookmarkDrawable = mBookmarkButton.getDrawable();
        mVoiceModeBackground = resources.getDrawable(
                R.drawable.title_voice);
        mNormalBackground = mTitleBg.getBackground();
        mArcsSpan = new ImageSpan(activity, R.drawable.arcs,
                ImageSpan.ALIGN_BASELINE);
    }

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mActivity.onCreateContextMenu(menu, this, null);
    }

    /**
     * Change the TitleBar to or from voice mode.  If there is no package to
     * handle voice search, the TitleBar cannot be set to voice mode.
     */
    @Override
    void setInVoiceMode(boolean inVoiceMode) {
        if (mInVoiceMode == inVoiceMode) return;
        mInVoiceMode = inVoiceMode && mVoiceSearchIntent != null;
        Drawable titleDrawable;
        if (mInVoiceMode) {
            mBookmarkButton.setImageDrawable(mVoiceDrawable);
            titleDrawable = mVoiceModeBackground;
            mUrlInput.setEllipsize(null);
            mBookmarkButton.setVisibility(View.VISIBLE);
            mStopButton.setVisibility(View.GONE);
            mTitleBg.setBackgroundDrawable(titleDrawable);
            mTitleBg.setPadding(mLeftMargin, mTitleBg.getPaddingTop(),
                    mRightMargin, mTitleBg.getPaddingBottom());
        } else {
            if (mInLoad) {
                mBookmarkButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.VISIBLE);
            } else {
                mBookmarkButton.setVisibility(View.VISIBLE);
                mStopButton.setVisibility(View.GONE);
                mBookmarkButton.setImageDrawable(mBookmarkDrawable);
            }
            mUrlInput.setEllipsize(TextUtils.TruncateAt.END);
            mTitleBg.setPadding(mLeftMargin, 0, mRightMargin, 0);
        }
        mUrlInput.setSingleLine(!mInVoiceMode);
    }

    /**
     * Update the progress, from 0 to 100.
     */
    @Override
    void setProgress(int newProgress) {
        if (newProgress >= mHorizontalProgress.getMax()) {
            mUrlInput.setCompoundDrawables(null, null, null, null);
            ((Animatable) mCircularProgress).stop();
            mHorizontalProgress.setVisibility(View.INVISIBLE);
            if (!mInVoiceMode) {
                mBookmarkButton.setImageDrawable(mBookmarkDrawable);
                mBookmarkButton.setVisibility(View.VISIBLE);
                mStopButton.setVisibility(View.GONE);
                mTitleBg.setBackgroundDrawable(mNormalBackground);
                mTitleBg.setPadding(mLeftMargin, 0, mRightMargin, 0);
            }
            mInLoad = false;
        } else {
            mHorizontalProgress.setProgress(newProgress);
            if (!mInLoad && getWindowToken() != null) {
                // checking the window token lets us be sure that we
                // are attached to a window before starting the animation,
                // preventing a potential race condition
                // (fix for bug http://b/2115736)
                mUrlInput.setCompoundDrawables(null, null, mCircularProgress,
                        null);
                ((Animatable) mCircularProgress).start();
                mHorizontalProgress.setVisibility(View.VISIBLE);
                if (!mInVoiceMode) {
                    mTitleBg.setPadding(mLeftMargin, 0, mRightMargin, 0);
                    mBookmarkButton.setVisibility(View.GONE);
                    mStopButton.setVisibility(View.VISIBLE);
                }
                mInLoad = true;
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
            if (mInVoiceMode) {
                // Add two spaces.  The second one will be replaced with an
                // image, and the first one will put space between it and the
                // text
                SpannableString spannable = new SpannableString(title + "  ");
                int end = spannable.length();
                spannable.setSpan(mArcsSpan, end - 1, end,
                        Spanned.SPAN_MARK_POINT);
                mUrlInput.setText(spannable);
            } else {
                mUrlInput.setText(title);
            }
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mUrlInput && hasFocus) {
            mActivity.closeOptionsMenu();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mStopButton) {
            mUiController.stopLoading();
        } else if (v == mBookmarkButton) {
            mUiController.bookmarkCurrentPage(AddBookmarkPage.DEFAULT_FOLDER_ID,
                    true);
        }
    }
}
