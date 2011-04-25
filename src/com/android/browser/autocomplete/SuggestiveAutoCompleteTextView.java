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
package com.android.browser.autocomplete;

import com.android.browser.BrowserSettings;
import com.android.browser.SuggestionsAdapter;
import com.android.browser.SuggestionsAdapter.SuggestItem;
import com.android.browser.autocomplete.SuggestedTextController.TextChangeWatcher;
import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Parcelable;
import android.text.Editable;
import android.text.Html;
import android.text.Selection;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.AbsSavedState;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;


/**
 * This is a stripped down version of the framework AutoCompleteTextView
 * class with added support for displaying completions in-place. Note that
 * this cannot be implemented as a subclass of the above without making
 * substantial changes to it and its descendants.
 *
 * @see android.widget.AutoCompleteTextView
 */
public class SuggestiveAutoCompleteTextView extends EditText implements Filter.FilterListener {
    private static final boolean DEBUG = false;
    private static final String TAG = "SuggestiveAutoCompleteTextView";

    private CharSequence mHintText;
    private TextView mHintView;
    private int mHintResource;

    private SuggestionsAdapter mAdapter;
    private Filter mFilter;
    private int mThreshold;

    private ListPopupWindow mPopup;
    private int mDropDownAnchorId;

    private AdapterView.OnItemClickListener mItemClickListener;

    private boolean mDropDownDismissedOnCompletion = true;

    private int mLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;

    // Set to true when text is set directly and no filtering shall be performed
    private boolean mBlockCompletion;

    // When set, an update in the underlying adapter will update the result list popup.
    // Set to false when the list is hidden to prevent asynchronous updates to popup the list again.
    private boolean mPopupCanBeUpdated = true;

    private PassThroughClickListener mPassThroughClickListener;
    private PopupDataSetObserver mObserver;
    private SuggestedTextController mController;

    public SuggestiveAutoCompleteTextView(Context context) {
        this(context, null);
    }

    public SuggestiveAutoCompleteTextView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.autoCompleteTextViewStyle);
    }

    public SuggestiveAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // The completions are always shown in the same color as the hint
        // text.
        mController = new SuggestedTextController(this, getHintTextColors().getDefaultColor());
        mPopup = new ListPopupWindow(context, attrs,
                 R.attr.autoCompleteTextViewStyle);
        mPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        mPopup.setPromptPosition(ListPopupWindow.POSITION_PROMPT_BELOW);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.AutoCompleteTextView, defStyle, 0);

        mThreshold = a.getInt(
                R.styleable.AutoCompleteTextView_completionThreshold, 2);

        mPopup.setListSelector(a.getDrawable(R.styleable.AutoCompleteTextView_dropDownSelector));
        mPopup.setVerticalOffset((int)
                a.getDimension(R.styleable.AutoCompleteTextView_dropDownVerticalOffset, 0.0f));
        mPopup.setHorizontalOffset((int)
                a.getDimension(R.styleable.AutoCompleteTextView_dropDownHorizontalOffset, 0.0f));

        // Get the anchor's id now, but the view won't be ready, so wait to actually get the
        // view and store it in mDropDownAnchorView lazily in getDropDownAnchorView later.
        // Defaults to NO_ID, in which case the getDropDownAnchorView method will simply return
        // this TextView, as a default anchoring point.
        mDropDownAnchorId = a.getResourceId(
                R.styleable.AutoCompleteTextView_dropDownAnchor, View.NO_ID);

        // For dropdown width, the developer can specify a specific width, or MATCH_PARENT
        // (for full screen width) or WRAP_CONTENT (to match the width of the anchored view).
        mPopup.setWidth(a.getLayoutDimension(
                R.styleable.AutoCompleteTextView_dropDownWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mPopup.setHeight(a.getLayoutDimension(
                R.styleable.AutoCompleteTextView_dropDownHeight,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mHintResource = a.getResourceId(R.styleable.AutoCompleteTextView_completionHintView,
                R.layout.simple_dropdown_hint);

        mPopup.setOnItemClickListener(new DropDownItemClickListener());
        setCompletionHint(a.getText(R.styleable.AutoCompleteTextView_completionHint));

        // Always turn on the auto complete input type flag, since it
        // makes no sense to use this widget without it.
        int inputType = getInputType();
        if ((inputType&EditorInfo.TYPE_MASK_CLASS)
                == EditorInfo.TYPE_CLASS_TEXT) {
            inputType |= EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE;
            setRawInputType(inputType);
        }

        a.recycle();

        setFocusable(true);

        mController.addUserTextChangeWatcher(new MyWatcher());

        mPassThroughClickListener = new PassThroughClickListener();
        super.setOnClickListener(mPassThroughClickListener);
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        mPassThroughClickListener.mWrapped = listener;
    }

    /**
     * Private hook into the on click event, dispatched from {@link PassThroughClickListener}
     */
    private void onClickImpl() {
        // If the dropdown is showing, bring the keyboard to the front
        // when the user touches the text field.
        if (isPopupShowing()) {
            ensureImeVisible(true);
        }
    }

    /**
     * <p>Sets the optional hint text that is displayed at the bottom of the
     * the matching list.  This can be used as a cue to the user on how to
     * best use the list, or to provide extra information.</p>
     *
     * @param hint the text to be displayed to the user
     *
     * @attr ref android.R.styleable#AutoCompleteTextView_completionHint
     */
    private void setCompletionHint(CharSequence hint) {
        mHintText = hint;
        if (hint != null) {
            if (mHintView == null) {
                final TextView hintView = (TextView) LayoutInflater.from(getContext()).inflate(
                        mHintResource, null).findViewById(R.id.text1);
                hintView.setText(mHintText);
                mHintView = hintView;
                mPopup.setPromptView(hintView);
            } else {
                mHintView.setText(hint);
            }
        } else {
            mPopup.setPromptView(null);
            mHintView = null;
        }
    }

    protected int getDropDownWidth() {
        return mPopup.getWidth();
    }

    public void setDropDownWidth(int width) {
        mPopup.setWidth(width);
    }

    protected void setDropDownVerticalOffset(int offset) {
        mPopup.setVerticalOffset(offset);
    }

    public void setDropDownHorizontalOffset(int offset) {
        mPopup.setHorizontalOffset(offset);
    }

    protected int getDropDownHorizontalOffset() {
        return mPopup.getHorizontalOffset();
    }

    public void setThreshold(int threshold) {
        if (threshold <= 0) {
            threshold = 1;
        }

        mThreshold = threshold;
    }

    protected void setOnItemClickListener(AdapterView.OnItemClickListener l) {
        mItemClickListener = l;
    }

    public void setAdapter(SuggestionsAdapter adapter) {
        if (mObserver == null) {
            mObserver = new PopupDataSetObserver();
        } else if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            mFilter = mAdapter.getFilter();
            adapter.registerDataSetObserver(mObserver);
        } else {
            mFilter = null;
        }

        mPopup.setAdapter(mAdapter);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isPopupShowing()
                && !mPopup.isDropDownAlwaysVisible()) {
            // special case for the back key, we do not even try to send it
            // to the drop down list but instead, consume it immediately
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null) {
                    state.startTracking(event, this);
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null) {
                    state.handleUpEvent(event);
                }
                if (event.isTracking() && !event.isCanceled()) {
                    dismissDropDown();
                    return true;
                }
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean consumed = mPopup.onKeyUp(keyCode, event);
        if (consumed) {
            switch (keyCode) {
            // if the list accepts the key events and the key event
            // was a click, the text view gets the selected item
            // from the drop down as its content
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers()) {
                    performCompletion();
                }
                return true;
            }
        }

        if (isPopupShowing() && keyCode == KeyEvent.KEYCODE_TAB && event.hasNoModifiers()) {
            performCompletion();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mPopup.onKeyDown(keyCode, event)) {
            return true;
        }

        if (!isPopupShowing()) {
            switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.hasNoModifiers()) {
                    performValidation();
                }
            }
        }

        if (isPopupShowing() && keyCode == KeyEvent.KEYCODE_TAB && event.hasNoModifiers()) {
            return true;
        }

        mLastKeyCode = keyCode;
        boolean handled = super.onKeyDown(keyCode, event);
        mLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;

        if (handled && isPopupShowing()) {
            clearListSelection();
        }

        return handled;
    }

    /**
     * Returns <code>true</code> if the amount of text in the field meets
     * or exceeds the {@link #getThreshold} requirement.  You can override
     * this to impose a different standard for when filtering will be
     * triggered.
     */
    private boolean enoughToFilter() {
        if (DEBUG) Log.v(TAG, "Enough to filter: len=" + getUserText().length()
                + " threshold=" + mThreshold);
        return getUserText().length() >= mThreshold;
    }

    /**
     * This is used to watch for edits to the text view.  Note that we call
     * to methods on the auto complete text view class so that we can access
     * private vars without going through thunks.
     */
    private class MyWatcher implements TextChangeWatcher {
        @Override
        public void onTextChanged(String newText) {
            doAfterTextChanged();
        }
    }

    /**
     * @hide
     */
    protected void setBlockCompletion(boolean block) {
        mBlockCompletion = block;
    }

    void doAfterTextChanged() {
        if (DEBUG) Log.d(TAG, "doAfterTextChanged(" + getText() + ")");
        if (mBlockCompletion) return;

        // the drop down is shown only when a minimum number of characters
        // was typed in the text view
        if (enoughToFilter()) {
            if (mFilter != null) {
                mPopupCanBeUpdated = true;
                performFiltering(getUserText(), mLastKeyCode);
                buildImeCompletions();
            }
        } else {
            // drop down is automatically dismissed when enough characters
            // are deleted from the text view
            if (!mPopup.isDropDownAlwaysVisible()) {
                dismissDropDown();
            }
            if (mFilter != null) {
                performFiltering(null, mLastKeyCode);
            }
        }
    }

    /**
     * <p>Indicates whether the popup menu is showing.</p>
     *
     * @return true if the popup menu is showing, false otherwise
     */
    public boolean isPopupShowing() {
        return mPopup.isShowing();
    }

    /**
     * <p>Converts the selected item from the drop down list into a sequence
     * of character that can be used in the edit box.</p>
     *
     * @param selectedItem the item selected by the user for completion
     *
     * @return a sequence of characters representing the selected suggestion
     */
    protected CharSequence convertSelectionToString(Object selectedItem) {
        return mFilter.convertResultToString(selectedItem);
    }

    /**
     * <p>Clear the list selection.  This may only be temporary, as user input will often bring
     * it back.
     */
    private void clearListSelection() {
        mPopup.clearListSelection();
    }

    /**
     * <p>Starts filtering the content of the drop down list. The filtering
     * pattern is the content of the edit box. Subclasses should override this
     * method to filter with a different pattern, for instance a substring of
     * <code>text</code>.</p>
     *
     * @param text the filtering pattern
     * @param keyCode the last character inserted in the edit box; beware that
     * this will be null when text is being added through a soft input method.
     */
    @SuppressWarnings({ "UnusedDeclaration" })
    protected void performFiltering(CharSequence text, int keyCode) {
        if (DEBUG) Log.d(TAG, "performFiltering(" + text + ")");

        mFilter.filter(text, this);
    }

    protected void performForcedFiltering() {
        boolean wasSuspended = false;
        if (mController.isCursorHandlingSuspended()) {
            mController.resumeCursorMovementHandlingAndApplyChanges();
            wasSuspended = true;
        }

        mFilter.filter(getUserText().toString(), this);

        if (wasSuspended) {
            mController.suspendCursorMovementHandling();
        }
    }

    /**
     * <p>Performs the text completion by converting the selected item from
     * the drop down list into a string, replacing the text box's content with
     * this string and finally dismissing the drop down menu.</p>
     */
    private void performCompletion() {
        performCompletion(null, -1, -1);
    }

    @Override
    public void onCommitCompletion(CompletionInfo completion) {
        if (isPopupShowing()) {
            mBlockCompletion = true;
            replaceText(completion.getText());
            mBlockCompletion = false;

            mPopup.performItemClick(completion.getPosition());
        }
    }

    private void performCompletion(View selectedView, int position, long id) {
        if (isPopupShowing()) {
            Object selectedItem;
            if (position < 0) {
                selectedItem = mPopup.getSelectedItem();
            } else {
                selectedItem = mAdapter.getItem(position);
            }
            if (selectedItem == null) {
                Log.w(TAG, "performCompletion: no selected item");
                return;
            }

            mBlockCompletion = true;
            replaceText(convertSelectionToString(selectedItem));
            mBlockCompletion = false;

            if (mItemClickListener != null) {
                final ListPopupWindow list = mPopup;

                if (selectedView == null || position < 0) {
                    selectedView = list.getSelectedView();
                    position = list.getSelectedItemPosition();
                    id = list.getSelectedItemId();
                }
                mItemClickListener.onItemClick(list.getListView(), selectedView, position, id);
            }
        }

        if (mDropDownDismissedOnCompletion && !mPopup.isDropDownAlwaysVisible()) {
            dismissDropDown();
        }
    }

    /**
     * <p>Performs the text completion by replacing the current text by the
     * selected item. Subclasses should override this method to avoid replacing
     * the whole content of the edit box.</p>
     *
     * @param text the selected suggestion in the drop down list
     */
    protected void replaceText(CharSequence text) {
        clearComposingText();

        setText(text);
        // make sure we keep the caret at the end of the text view
        Editable spannable = getText();
        Selection.setSelection(spannable, spannable.length());
    }

    /** {@inheritDoc} */
    @Override
    public void onFilterComplete(int count) {
        updateDropDownForFilter(count);
    }

    private void updateDropDownForFilter(int count) {
        // Not attached to window, don't update drop-down
        if (getWindowVisibility() == View.GONE) return;

        /*
         * This checks enoughToFilter() again because filtering requests
         * are asynchronous, so the result may come back after enough text
         * has since been deleted to make it no longer appropriate
         * to filter.
         */

        final boolean dropDownAlwaysVisible = mPopup.isDropDownAlwaysVisible();
        if ((count > 0 || dropDownAlwaysVisible) && enoughToFilter() &&
                getUserText().length() > 0) {
            if (hasFocus() && hasWindowFocus() && mPopupCanBeUpdated) {
                showDropDown();
            }
        } else if (!dropDownAlwaysVisible && isPopupShowing()) {
            dismissDropDown();
            // When the filter text is changed, the first update from the adapter may show an empty
            // count (when the query is being performed on the network). Future updates when some
            // content has been retrieved should still be able to update the list.
            mPopupCanBeUpdated = true;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus && !mPopup.isDropDownAlwaysVisible()) {
            dismissDropDown();
        }
    }

    @Override
    protected void onDisplayHint(int hint) {
        super.onDisplayHint(hint);
        switch (hint) {
            case INVISIBLE:
                if (!mPopup.isDropDownAlwaysVisible()) {
                    dismissDropDown();
                }
                break;
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // TextView makes several cursor movements when gaining focus, and this interferes with
        // the suggested vs user entered text. Tell the controller to temporarily ignore cursor
        // movements while this is going on.
        mController.suspendCursorMovementHandling();

        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        // Perform validation if the view is losing focus.
        if (!focused) {
            performValidation();
        }
        if (!focused && !mPopup.isDropDownAlwaysVisible()) {
            dismissDropDown();
        }

        mController.resumeCursorMovementHandlingAndApplyChanges();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissDropDown();
        super.onDetachedFromWindow();
    }

    /**
     * <p>Closes the drop down if present on screen.</p>
     */
    protected void dismissDropDown() {
        InputMethodManager imm = InputMethodManager.peekInstance();
        if (imm != null) {
            imm.displayCompletions(this, null);
        }
        mPopup.dismiss();
        mPopupCanBeUpdated = false;
    }

    @Override
    protected boolean setFrame(final int l, int t, final int r, int b) {
        boolean result = super.setFrame(l, t, r, b);

        if (isPopupShowing()) {
            showDropDown();
        }

        return result;
    }

    /**
     * Ensures that the drop down is not obscuring the IME.
     * @param visible whether the ime should be in front. If false, the ime is pushed to
     * the background.
     * @hide internal used only here and SearchDialog
     */
    private void ensureImeVisible(boolean visible) {
        mPopup.setInputMethodMode(visible
                ? ListPopupWindow.INPUT_METHOD_NEEDED : ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        showDropDown();
    }

    /**
     * <p>Displays the drop down on screen.</p>
     */
    protected void showDropDown() {
        if (mPopup.getAnchorView() == null) {
            if (mDropDownAnchorId != View.NO_ID) {
                mPopup.setAnchorView(getRootView().findViewById(mDropDownAnchorId));
            } else {
                mPopup.setAnchorView(this);
            }
        }
        if (!isPopupShowing()) {
            // Make sure the list does not obscure the IME when shown for the first time.
            mPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NEEDED);
        }
        mPopup.show();
    }

    private void buildImeCompletions() {
        final ListAdapter adapter = mAdapter;
        if (adapter != null) {
            InputMethodManager imm = InputMethodManager.peekInstance();
            if (imm != null) {
                final int count = Math.min(adapter.getCount(), 20);
                CompletionInfo[] completions = new CompletionInfo[count];
                int realCount = 0;

                for (int i = 0; i < count; i++) {
                    if (adapter.isEnabled(i)) {
                        realCount++;
                        Object item = adapter.getItem(i);
                        long id = adapter.getItemId(i);
                        completions[i] = new CompletionInfo(id, i,
                                convertSelectionToString(item));
                    }
                }

                if (realCount != count) {
                    CompletionInfo[] tmp = new CompletionInfo[realCount];
                    System.arraycopy(completions, 0, tmp, 0, realCount);
                    completions = tmp;
                }

                imm.displayCompletions(this, completions);
            }
        }
    }

    private void performValidation() {
    }

    /**
     * Returns the Filter obtained from {@link Filterable#getFilter},
     * or <code>null</code> if {@link #setAdapter} was not called with
     * a Filterable.
     */
    protected Filter getFilter() {
        return mFilter;
    }

    private class DropDownItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            performCompletion(v, position, id);
        }
    }

    /**
     * Allows us a private hook into the on click event without preventing users from setting
     * their own click listener.
     */
    private class PassThroughClickListener implements OnClickListener {

        private View.OnClickListener mWrapped;

        /** {@inheritDoc} */
        @Override
        public void onClick(View v) {
            onClickImpl();

            if (mWrapped != null) mWrapped.onClick(v);
        }
    }

    private class PopupDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            if (mAdapter != null) {
                // If the popup is not showing already, showing it will cause
                // the list of data set observers attached to the adapter to
                // change. We can't do it from here, because we are in the middle
                // of iterating through the list of observers.
                post(new Runnable() {
                    @Override
                    public void run() {
                        final SuggestionsAdapter adapter = mAdapter;
                        if (adapter != null) {
                            // This will re-layout, thus resetting mDataChanged, so that the
                            // listView click listener stays responsive
                            updateDropDownForFilter(adapter.getCount());
                        }

                        updateText(adapter);
                    }
                });
            }
        }
    }

    public String getUserText() {
        return mController.getUserText();
    }

    private void updateText(SuggestionsAdapter adapter) {
        if (!BrowserSettings.getInstance().useInstantSearch()) {
            return;
        }

        if (!isPopupShowing()) {
            setSuggestedText(null);
            return;
        }

        if (mAdapter.getCount() > 0 && !TextUtils.isEmpty(getUserText())) {
            for (int i = 0; i < mAdapter.getCount(); ++i) {
                SuggestItem current = mAdapter.getItem(i);
                if (current.type == SuggestionsAdapter.TYPE_SUGGEST) {
                    setSuggestedText(current.title);
                    break;
                }
            }
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        Editable buffer = getEditableText();
        if (text == null) text = "";
        // if we already have a buffer, we must not replace it with a new one as this will break
        // mController. Write the new text into the existing buffer instead.
        if (buffer == null) {
            super.setText(text, type);
        } else {
            buffer.replace(0, buffer.length(), text);
            invalidate();
        }
    }

    public void setText(CharSequence text, boolean filter) {
        if (filter) {
            setText(text);
        } else {
            mBlockCompletion = true;
            // If cursor movement handling was suspended (the view is
            // not in focus), resume it and apply the pending change.
            // Since we don't want to perform any filtering, this change
            // is safe.
            boolean wasSuspended = false;
            if (mController.isCursorHandlingSuspended()) {
                mController.resumeCursorMovementHandlingAndApplyChanges();
                wasSuspended = true;
            }

            setText(text);

            if (wasSuspended) {
                mController.suspendCursorMovementHandling();
            }
            mBlockCompletion = false;
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        if (superState instanceof TextView.SavedState) {
            // get rid of TextView's saved state, we override it.
            superState = ((TextView.SavedState) superState).getSuperState();
        }
        if (superState == null) {
            superState = AbsSavedState.EMPTY_STATE;
        }
        return mController.saveInstanceState(superState);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(mController.restoreInstanceState(state));
    }

    public void addQueryTextWatcher(final SuggestedTextController.TextChangeWatcher watcher) {
        mController.addUserTextChangeWatcher(watcher);
    }

    public void setSuggestedText(String text) {
        if (!TextUtils.isEmpty(text)) {
            String htmlStripped = Html.fromHtml(text).toString();
            mController.setSuggestedText(htmlStripped);
        } else {
            mController.setSuggestedText(null);
        }
    }

    public void getPopupDrawableRect(Rect rect) {
        if (mPopup.getListView() != null) {
            mPopup.getListView().getDrawingRect(rect);
        }
    }
}
