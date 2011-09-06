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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.browser.SuggestionsAdapter.CompletionListener;
import com.android.browser.SuggestionsAdapter.SuggestItem;
import com.android.browser.UI.DropdownChangeListener;
import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;
import com.android.browser.autocomplete.SuggestiveAutoCompleteTextView;
import com.android.browser.search.SearchEngine;
import com.android.browser.search.SearchEngineInfo;
import com.android.browser.search.SearchEngines;
import com.android.internal.R;

import java.util.List;

/**
 * url/search input view
 * handling suggestions
 */
public class UrlInputView extends SuggestiveAutoCompleteTextView
        implements OnEditorActionListener,
        CompletionListener, OnItemClickListener, TextChangeWatcher {

    static final String TYPED = "browser-type";
    static final String SUGGESTED = "browser-suggest";
    static final String VOICE = "voice-search";

    static final int POST_DELAY = 100;

    static interface StateListener {
        static final int STATE_NORMAL = 0;
        static final int STATE_HIGHLIGHTED = 1;
        static final int STATE_EDITED = 2;

        public void onStateChanged(int state);
    }

    private UrlInputListener   mListener;
    private InputMethodManager mInputManager;
    private SuggestionsAdapter mAdapter;
    private View mContainer;
    private boolean mLandscape;
    private boolean mIncognitoMode;
    private boolean mNeedsUpdate;
    private DropdownChangeListener mDropdownListener;

    private int mState;
    private StateListener mStateListener;
    private Rect mPopupPadding;

    public UrlInputView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(
                attrs, com.android.internal.R.styleable.PopupWindow,
                R.attr.autoCompleteTextViewStyle, 0);

        Drawable popupbg = a.getDrawable(R.styleable.PopupWindow_popupBackground);
        a.recycle();
        mPopupPadding = new Rect();
        popupbg.getPadding(mPopupPadding);
        init(context);
    }

    public UrlInputView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.autoCompleteTextViewStyle);
    }

    public UrlInputView(Context context) {
        this(context, null);
    }

    private void init(Context ctx) {
        mInputManager = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        setOnEditorActionListener(this);
        mAdapter = new SuggestionsAdapter(ctx, this);
        setAdapter(mAdapter);
        setSelectAllOnFocus(true);
        onConfigurationChanged(ctx.getResources().getConfiguration());
        setThreshold(1);
        setOnItemClickListener(this);
        mNeedsUpdate = false;
        mDropdownListener = null;
        addQueryTextWatcher(this);

        mAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if (!isPopupShowing()) {
                    return;
                }
                dispatchChange();
            }

            @Override
            public void onInvalidated() {
                dispatchChange();
            }
        });
        mState = StateListener.STATE_NORMAL;
    }

    protected void onFocusChanged(boolean focused, int direction, Rect prevRect) {
        super.onFocusChanged(focused, direction, prevRect);
        int state = -1;
        if (focused) {
            if (hasSelection()) {
                state = StateListener.STATE_HIGHLIGHTED;
            } else {
                state = StateListener.STATE_EDITED;
            }
        } else {
            // reset the selection state
            state = StateListener.STATE_NORMAL;
        }
        final int s = state;
        post(new Runnable() {
            public void run() {
                changeState(s);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        boolean hasSelection = hasSelection();
        boolean res = super.onTouchEvent(evt);
        if ((MotionEvent.ACTION_DOWN == evt.getActionMasked())
              && hasSelection) {
            postDelayed(new Runnable() {
                public void run() {
                    changeState(StateListener.STATE_EDITED);
                }}, POST_DELAY);
        }
        return res;
    }

    /**
     * check if focus change requires a title bar update
     */
    boolean needsUpdate() {
        return mNeedsUpdate;
    }

    /**
     * clear the focus change needs title bar update flag
     */
    void clearNeedsUpdate() {
        mNeedsUpdate = false;
    }

    void setController(UiController controller) {
        UrlSelectionActionMode urlSelectionMode
                = new UrlSelectionActionMode(controller);
        setCustomSelectionActionModeCallback(urlSelectionMode);
    }

    void setContainer(View container) {
        mContainer = container;
    }

    public void setUrlInputListener(UrlInputListener listener) {
        mListener = listener;
    }

    public void setStateListener(StateListener listener) {
        mStateListener = listener;
        // update listener
        changeState(mState);
    }

    private void changeState(int newState) {
        mState = newState;
        if (mStateListener != null) {
            mStateListener.onStateChanged(mState);
        }
    }

    int getState() {
        return mState;
    }

    void setVoiceResults(List<String> voiceResults) {
        mAdapter.setVoiceResults(voiceResults);
    }

    @Override
    protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mLandscape = (config.orientation &
                Configuration.ORIENTATION_LANDSCAPE) != 0;
        mAdapter.setLandscapeMode(mLandscape);
        if (isPopupShowing() && (getVisibility() == View.VISIBLE)) {
            setupDropDown();
            performFiltering(getUserText(), 0);
        }
    }

    @Override
    public void showDropDown() {
        setupDropDown();
        super.showDropDown();
    }

    @Override
    public void dismissDropDown() {
        super.dismissDropDown();
        mAdapter.clearCache();
    }

    private void setupDropDown() {
        int width = mContainer != null ? mContainer.getWidth() : getWidth();
        width += mPopupPadding.left + mPopupPadding.right;
        if (width != getDropDownWidth()) {
            setDropDownWidth(width);
        }
        int left = getLeft();
        left += mPopupPadding.left;
        if (left != -getDropDownHorizontalOffset()) {
            setDropDownHorizontalOffset(-left);
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (BrowserSettings.getInstance().useInstantSearch() &&
                (actionId == EditorInfo.IME_ACTION_NEXT)) {
            // When instant is turned on AND the user chooses to complete
            // using the tab key, then use the completion rather than the
            // text that the user has typed.
            finishInput(getText().toString(), null, TYPED);
        } else {
            finishInput(getUserText(), null, TYPED);
        }

        return true;
    }

    void forceFilter() {
        performForcedFiltering();
        showDropDown();
    }

    void forceIme() {
        mInputManager.focusIn(this);
        mInputManager.showSoftInput(this, 0);
    }

    void hideIME() {
        mInputManager.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    private void finishInput(String url, String extra, String source) {
        mNeedsUpdate = true;
        dismissDropDown();
        mInputManager.hideSoftInputFromWindow(getWindowToken(), 0);
        if (TextUtils.isEmpty(url)) {
            mListener.onDismiss();
        } else {
            if (mIncognitoMode && isSearch(url)) {
                // To prevent logging, intercept this request
                // TODO: This is a quick hack, refactor this
                SearchEngine searchEngine = BrowserSettings.getInstance()
                        .getSearchEngine();
                if (searchEngine == null) return;
                SearchEngineInfo engineInfo = SearchEngines
                        .getSearchEngineInfo(mContext, searchEngine.getName());
                if (engineInfo == null) return;
                url = engineInfo.getSearchUriForQuery(url);
                // mLister.onAction can take it from here without logging
            }
            mListener.onAction(url, extra, source);
        }
    }

    boolean isSearch(String inUrl) {
        String url = UrlUtils.fixUrl(inUrl).trim();
        if (TextUtils.isEmpty(url)) return false;

        if (Patterns.WEB_URL.matcher(url).matches()
                || UrlUtils.ACCEPTED_URI_SCHEMA.matcher(url).matches()) {
            return false;
        }
        return true;
    }

    // Completion Listener

    @Override
    public void onSearch(String search) {
        mListener.onCopySuggestion(search);
    }

    @Override
    public void onSelect(String url, int type, String extra) {
        finishInput(url, extra, (type == SuggestionsAdapter.TYPE_VOICE_SEARCH)
                ? VOICE : SUGGESTED);
    }

    @Override
    public void onItemClick(
            AdapterView<?> parent, View view, int position, long id) {
        SuggestItem item = mAdapter.getItem(position);
        onSelect(SuggestionsAdapter.getSuggestionUrl(item), item.type, item.extra);
    }

    interface UrlInputListener {

        public void onDismiss();

        public void onAction(String text, String extra, String source);

        public void onCopySuggestion(String text);

    }

    public void setIncognitoMode(boolean incognito) {
        mIncognitoMode = incognito;
        mAdapter.setIncognitoMode(mIncognitoMode);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent evt) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && !isInTouchMode()) {
            finishInput(null, null, null);
            return true;
        }
        return super.onKeyDown(keyCode, evt);
    }

    public SuggestionsAdapter getAdapter() {
        return mAdapter;
    }

    private void dispatchChange() {
        final Rect popupRect = new Rect();
        getPopupDrawableRect(popupRect);

        if (mDropdownListener != null) {
            mDropdownListener.onNewDropdownDimensions(popupRect.height());
        }
    }

    void registerDropdownChangeListener(DropdownChangeListener d) {
        mDropdownListener = d;
    }

    /*
     * no-op to prevent scrolling of webview when embedded titlebar
     * gets edited
     */
    @Override
    public boolean requestRectangleOnScreen(Rect rect, boolean immediate) {
        return false;
    }

    @Override
    public void onTextChanged(String newText) {
        if (StateListener.STATE_HIGHLIGHTED == mState) {
            changeState(StateListener.STATE_EDITED);
        }
    }

}
