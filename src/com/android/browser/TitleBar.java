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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Handler;
import android.os.Message;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.common.speech.LoggingEvents;

/**
 * This class represents a title bar for a particular "tab" or "window" in the
 * browser.
 */
public class TitleBar extends LinearLayout {
    private TextView        mTitle;
    private Drawable        mCloseDrawable;
    private ImageView       mRtButton;
    private Drawable        mCircularProgress;
    private ProgressBar     mHorizontalProgress;
    private ImageView       mFavicon;
    private ImageView       mLockIcon;
    private ImageView       mStopButton;
    private Drawable        mBookmarkDrawable;
    private Drawable        mVoiceDrawable;
    private boolean         mInLoad;
    private BrowserActivity mBrowserActivity;
    private Drawable        mGenericFavicon;
    private int             mIconDimension;
    private View            mTitleBg;
    private MyHandler       mHandler;
    private Intent          mVoiceSearchIntent;
    private boolean         mInVoiceMode;
    private Drawable        mVoiceModeBackground;
    private Drawable        mNormalBackground;
    private Drawable        mLoadingBackground;
    private ImageSpan       mArcsSpan;
    private int             mLeftMargin;
    private int             mRightMargin;

    private static int LONG_PRESS = 1;

    public TitleBar(BrowserActivity context) {
        super(context, null);
        mHandler = new MyHandler();
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.title_bar, this);
        mBrowserActivity = context;

        mTitle = (TextView) findViewById(R.id.title);
        mTitle.setCompoundDrawablePadding(5);

        mTitleBg = findViewById(R.id.title_bg);
        mLockIcon = (ImageView) findViewById(R.id.lock);
        mFavicon = (ImageView) findViewById(R.id.favicon);
        mStopButton = (ImageView) findViewById(R.id.stop);

        mRtButton = (ImageView) findViewById(R.id.rt_btn);
        Resources resources = context.getResources();
        mCircularProgress = (Drawable) resources.getDrawable(
                com.android.internal.R.drawable.search_spinner);
        DisplayMetrics metrics = resources.getDisplayMetrics();
        mLeftMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, metrics);
        mRightMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, metrics);
        mIconDimension = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f, metrics);
        mCircularProgress.setBounds(0, 0, mIconDimension, mIconDimension);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mGenericFavicon = context.getResources().getDrawable(
                R.drawable.app_web_browser_sm);
        mVoiceSearchIntent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        mVoiceSearchIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        // This extra tells voice search not to send the application id in its
        // results intent - http://b/2546173
        //
        // TODO: Make a constant for this extra.
        mVoiceSearchIntent.putExtra("android.speech.extras.SEND_APPLICATION_ID_EXTRA", false);
        PackageManager pm = context.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(mVoiceSearchIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null) {
            mVoiceSearchIntent = null;
        } else {
            mVoiceDrawable = resources.getDrawable(
                    android.R.drawable.ic_btn_speak_now);
        }
        mBookmarkDrawable = mRtButton.getDrawable();
        mVoiceModeBackground = resources.getDrawable(
                R.drawable.title_voice);
        mNormalBackground = mTitleBg.getBackground();
        mLoadingBackground = resources.getDrawable(R.drawable.title_loading);
        mArcsSpan = new ImageSpan(context, R.drawable.arcs,
                ImageSpan.ALIGN_BASELINE);
    }

    private class MyHandler extends Handler {
        public void handleMessage(Message msg) {
            if (msg.what == LONG_PRESS) {
                // Prevent the normal action from happening by setting the title
                // bar's state to false.
                mTitleBg.setPressed(false);
                // Need to call a special method on BrowserActivity for when the
                // fake title bar is up, because its ViewGroup does not show a
                // context menu.
                mBrowserActivity.showTitleBarContextMenu();
            }
        }
    };

    @Override
    public void createContextMenu(ContextMenu menu) {
        MenuInflater inflater = mBrowserActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
        mBrowserActivity.onCreateContextMenu(menu, this, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ImageView button = mInLoad ? mStopButton : mRtButton;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Make all touches hit either the textfield or the button,
                // depending on which side of the right edge of the textfield
                // they hit.
                if ((int) event.getX() > mTitleBg.getRight()) {
                    button.setPressed(true);
                } else {
                    mTitleBg.setPressed(true);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(
                            LONG_PRESS),
                            ViewConfiguration.getLongPressTimeout());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int slop = ViewConfiguration.get(mBrowserActivity)
                        .getScaledTouchSlop();
                if ((int) event.getY() > getHeight() + slop) {
                    // We only trigger the actions in ACTION_UP if one or the
                    // other is pressed.  Since the user moved off the title
                    // bar, mark both as not pressed.
                    mTitleBg.setPressed(false);
                    button.setPressed(false);
                    mHandler.removeMessages(LONG_PRESS);
                    break;
                }
                int x = (int) event.getX();
                int titleRight = mTitleBg.getRight();
                if (mTitleBg.isPressed() && x > titleRight + slop) {
                    mTitleBg.setPressed(false);
                    mHandler.removeMessages(LONG_PRESS);
                } else if (button.isPressed() && x < titleRight - slop) {
                    button.setPressed(false);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                button.setPressed(false);
                mTitleBg.setPressed(false);
                mHandler.removeMessages(LONG_PRESS);
                break;
            case MotionEvent.ACTION_UP:
                if (button.isPressed()) {
                    if (mInVoiceMode) {
                        if (mBrowserActivity.getTabControl().getCurrentTab()
                                .voiceSearchSourceIsGoogle()) {
                            Intent i = new Intent(
                                    LoggingEvents.ACTION_LOG_EVENT);
                            i.putExtra(LoggingEvents.EXTRA_EVENT,
                                    LoggingEvents.VoiceSearch.RETRY);
                            mBrowserActivity.sendBroadcast(i);
                        }
                        mBrowserActivity.startActivity(mVoiceSearchIntent);
                    } else if (mInLoad) {
                        mBrowserActivity.stopLoading();
                    } else {
                        mBrowserActivity.bookmarksOrHistoryPicker(false);
                    }
                    button.setPressed(false);
                } else if (mTitleBg.isPressed()) {
                    mHandler.removeMessages(LONG_PRESS);
                    if (mInVoiceMode) {
                        if (mBrowserActivity.getTabControl().getCurrentTab()
                                .voiceSearchSourceIsGoogle()) {
                            Intent i = new Intent(
                                    LoggingEvents.ACTION_LOG_EVENT);
                            i.putExtra(LoggingEvents.EXTRA_EVENT,
                                    LoggingEvents.VoiceSearch.N_BEST_REVEAL);
                            mBrowserActivity.sendBroadcast(i);
                        }
                        mBrowserActivity.showVoiceSearchResults(
                                mTitle.getText().toString().trim());
                    } else {
                        mBrowserActivity.editUrl();
                    }
                    mTitleBg.setPressed(false);
                }
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Set a new Bitmap for the Favicon.
     */
    /* package */ void setFavicon(Bitmap icon) {
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

    /**
     * Change the TitleBar to or from voice mode.  If there is no package to
     * handle voice search, the TitleBar cannot be set to voice mode.
     */
    /* package */ void setInVoiceMode(boolean inVoiceMode) {
        if (mInVoiceMode == inVoiceMode) return;
        mInVoiceMode = inVoiceMode && mVoiceSearchIntent != null;
        Drawable titleDrawable;
        if (mInVoiceMode) {
            mRtButton.setImageDrawable(mVoiceDrawable);
            titleDrawable = mVoiceModeBackground;
            mTitle.setEllipsize(null);
            mRtButton.setVisibility(View.VISIBLE);
            mStopButton.setVisibility(View.GONE);
            mTitleBg.setBackgroundDrawable(titleDrawable);
            mTitleBg.setPadding(mLeftMargin, mTitleBg.getPaddingTop(),
                    mRightMargin, mTitleBg.getPaddingBottom());
        } else {
            if (mInLoad) {
                titleDrawable = mLoadingBackground;
                mRtButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.VISIBLE);
            } else {
                titleDrawable = mNormalBackground;
                mRtButton.setVisibility(View.VISIBLE);
                mStopButton.setVisibility(View.GONE);
                mRtButton.setImageDrawable(mBookmarkDrawable);
            }
            mTitle.setEllipsize(TextUtils.TruncateAt.END);
            mTitleBg.setBackgroundDrawable(titleDrawable);
            mTitleBg.setPadding(mLeftMargin, 0, mRightMargin, 0);
        }
        mTitle.setSingleLine(!mInVoiceMode);
    }

    /**
     * Set the Drawable for the lock icon, or null to hide it.
     */
    /* package */ void setLock(Drawable d) {
        if (null == d) {
            mLockIcon.setVisibility(View.GONE);
        } else {
            mLockIcon.setImageDrawable(d);
            mLockIcon.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Update the progress, from 0 to 100.
     */
    /* package */ void setProgress(int newProgress) {
        if (newProgress >= mHorizontalProgress.getMax()) {
            mTitle.setCompoundDrawables(null, null, null, null);
            ((Animatable) mCircularProgress).stop();
            mHorizontalProgress.setVisibility(View.INVISIBLE);
            if (!mInVoiceMode) {
                mRtButton.setImageDrawable(mBookmarkDrawable);
                mRtButton.setVisibility(View.VISIBLE);
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
                mTitle.setCompoundDrawables(null, null, mCircularProgress,
                        null);
                ((Animatable) mCircularProgress).start();
                mHorizontalProgress.setVisibility(View.VISIBLE);
                if (!mInVoiceMode) {
                    mTitleBg.setBackgroundDrawable(mLoadingBackground);
                    mTitleBg.setPadding(mLeftMargin, 0, mRightMargin, 0);
                    mRtButton.setVisibility(View.GONE);
                    mStopButton.setVisibility(View.VISIBLE);
                }
                mInLoad = true;
            }
        }
    }

    /**
     * Update the text displayed in the title bar.
     * @param title String to display.  If null, the loading string will be
     *      shown.
     */
    /* package */ void setDisplayTitle(String title) {
        if (title == null) {
            mTitle.setText(R.string.title_bar_loading);
        } else {
            if (mInVoiceMode) {
                // Add two spaces.  The second one will be replaced with an
                // image, and the first one will put space between it and the
                // text
                SpannableString spannable = new SpannableString(title + "  ");
                int end = spannable.length();
                spannable.setSpan(mArcsSpan, end - 1, end,
                        Spanned.SPAN_MARK_POINT);
                mTitle.setText(spannable);
            } else {
                mTitle.setText(title);
            }
        }
    }

    /* package */ void setToTabPicker() {
        mTitle.setText(R.string.tab_picker_title);
        setFavicon(null);
        setLock(null);
        mHorizontalProgress.setVisibility(View.GONE);
    }
}
