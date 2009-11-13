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
    private Drawable        mStopDrawable;
    private Drawable        mBookmarkDrawable;
    private boolean         mInLoad;
    private BrowserActivity mBrowserActivity;
    private Drawable        mGenericFavicon;
    private int             mIconDimension;
    private View            mTitleBg;
    private MyHandler       mHandler;

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

        mRtButton = (ImageView) findViewById(R.id.rt_btn);
        Resources resources = context.getResources();
        mCircularProgress = (Drawable) resources.getDrawable(
                com.android.internal.R.drawable.search_spinner);
        mIconDimension = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f,
                resources.getDisplayMetrics());
        mCircularProgress.setBounds(0, 0, mIconDimension, mIconDimension);
        mHorizontalProgress = (ProgressBar) findViewById(
                R.id.progress_horizontal);
        mGenericFavicon = context.getResources().getDrawable(
                R.drawable.app_web_browser_sm);
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
    protected void onCreateContextMenu(ContextMenu menu) {
        MenuInflater inflater = mBrowserActivity.getMenuInflater();
        inflater.inflate(R.menu.title_context, menu);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Make all touches hit either the textfield or the button,
                // depending on which side of the right edge of the textfield
                // they hit.
                if ((int) event.getX() > mTitleBg.getRight()) {
                    mRtButton.setPressed(true);
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
                    mRtButton.setPressed(false);
                    mHandler.removeMessages(LONG_PRESS);
                    break;
                }
                int x = (int) event.getX();
                int titleRight = mTitleBg.getRight();
                if (mTitleBg.isPressed() && x > titleRight + slop) {
                    mTitleBg.setPressed(false);
                    mHandler.removeMessages(LONG_PRESS);
                } else if (mRtButton.isPressed() && x < titleRight - slop) {
                    mRtButton.setPressed(false);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mRtButton.setPressed(false);
                mTitleBg.setPressed(false);
                mHandler.removeMessages(LONG_PRESS);
                break;
            case MotionEvent.ACTION_UP:
                if (mRtButton.isPressed()) {
                    if (mInLoad) {
                        mBrowserActivity.stopLoading();
                    } else {
                        mBrowserActivity.bookmarksOrHistoryPicker(false);
                    }
                    mRtButton.setPressed(false);
                } else if (mTitleBg.isPressed()) {
                    mHandler.removeMessages(LONG_PRESS);
                    mBrowserActivity.onSearchRequested();
                    mTitleBg.setPressed(false);
                }
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Return whether the associated WebView is currently loading.  Needed to
     * determine whether a click should stop the load or close the tab.
     */
    /* package */ boolean isInLoad() {
        return mInLoad;
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
            if (mBookmarkDrawable != null) {
                mRtButton.setImageDrawable(mBookmarkDrawable);
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
                if (mBookmarkDrawable == null) {
                    mBookmarkDrawable = mRtButton.getDrawable();
                }
                if (mStopDrawable == null) {
                    mRtButton.setImageResource(R.drawable.ic_btn_stop_v2);
                    mStopDrawable = mRtButton.getDrawable();
                } else {
                    mRtButton.setImageDrawable(mStopDrawable);
                }
                mInLoad = true;
            }
        }
    }

    /**
     * Update the title and url.
     */
    /* package */ void setTitleAndUrl(CharSequence title, CharSequence url) {
        if (url == null) {
            mTitle.setText(R.string.title_bar_loading);
        } else {
            mTitle.setText(url.toString());
        }
    }

    /* package */ void setToTabPicker() {
        mTitle.setText(R.string.tab_picker_title);
        setFavicon(null);
        setLock(null);
        mHorizontalProgress.setVisibility(View.GONE);
    }
}
