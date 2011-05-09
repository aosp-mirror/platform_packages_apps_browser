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
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Base class for a title bar used by the browser.
 */
public class TitleBarBase extends LinearLayout implements UrlInputListener {

    protected static final int PROGRESS_MAX = 100;

    // These need to be set by the subclass.
    protected ImageView mFavicon;
    protected ImageView mLockIcon;

    protected Drawable mGenericFavicon;
    protected UiController mUiController;
    protected BaseUi mBaseUi;
    protected UrlInputView mUrlInput;
    protected boolean mInVoiceMode;

    public TitleBarBase(Context context, UiController controller, BaseUi ui) {
        super(context, null);
        mUiController = controller;
        mBaseUi = ui;
        mGenericFavicon = context.getResources().getDrawable(
                R.drawable.app_web_browser_sm);
    }

    /* package */ void setProgress(int newProgress) {}
    /* package */ void setDisplayTitle(String title) {}

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

    /* package */ void setInVoiceMode(boolean inVoiceMode) {}

    /* package */ void setIncognitoMode(boolean incognito) {}

    public int getEmbeddedHeight() {
        return getHeight();
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

}
