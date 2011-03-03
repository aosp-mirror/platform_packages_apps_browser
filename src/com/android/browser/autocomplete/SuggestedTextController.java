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
package com.android.browser.autocomplete;

import com.google.common.annotations.VisibleForTesting;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;

import junit.framework.Assert;


/**
 * The query editor can show a suggestion, grayed out following the query that the user has
 * entered so far. As the user types new characters, these should replace the grayed suggestion
 * text. This class manages this logic, displaying the suggestion when the user entered text is a
 * prefix of it, and hiding it otherwise.
 *
 * Note, the text in the text view will contain the entire suggestion, not just what the user
 * entered. Instead of retrieving the text from the text view, {@link #getUserText()} should be
 * called on this class.
 */
public class SuggestedTextController {
    private static final boolean DBG = false;
    private static final String TAG = "Browser.SuggestedTextController";

    private final BufferTextWatcher mBufferTextWatcher = new BufferTextWatcher();
    private final BufferSpanWatcher mBufferSpanWatcher = new BufferSpanWatcher();
    private final ArrayList<TextChangeWatcher> mTextWatchers;
    private final TextOwner mTextOwner;
    private final StringBuffer mUserEntered;
    private final SuggestedSpan mSuggested;
    private String mSuggestedText;
    private TextChangeAttributes mCurrentTextChange;
    private boolean mSuspended = false;

    /**
     * While this is non-null, any changes made to the cursor position or selection are ignored. Is
     * stored the selection state at the moment when selection change processing was disabled.
     */
    private BufferSelection mTextSelectionBeforeIgnoringChanges;

    public SuggestedTextController(final EditText textView, int color) {
        this(new TextOwner() {
            @Override
            public Editable getText() {
                return textView.getText();
            }
            @Override
            public void addTextChangedListener(TextWatcher watcher) {
                textView.addTextChangedListener(watcher);
            }
            @Override
            public void removeTextChangedListener(TextWatcher watcher) {
                textView.removeTextChangedListener(watcher);
            }
            @Override
            public void setText(String text) {
                textView.setText(text);
            }
        }, color);
    }

    private void initialize(String userText, int selStart, int selEnd, String suggested) {
        Editable text = mTextOwner.getText();

        if (userText == null) userText = "";
        String allText = userText;
        int suggestedStart = allText.length();
        if (suggested != null && userText != null) {
            if (suggested.startsWith(userText.toLowerCase())) {
                allText = suggested;
            }
        }

        // allText is at this point either "userText" (not null) or
        // "suggested" if thats not null and starts with userText.
        text.replace(0, text.length(), allText);
        Selection.setSelection(text, selStart, selEnd);
        mUserEntered.replace(0, mUserEntered.length(), userText);
        mSuggestedText = suggested;
        if (suggestedStart < text.length()) {
            text.setSpan(mSuggested, suggestedStart, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            text.removeSpan(mSuggested);
        }
        text.setSpan(mBufferSpanWatcher, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        mTextOwner.addTextChangedListener(mBufferTextWatcher);
        if (DBG) checkInvariant(text);
    }

    private void assertNotIgnoringSelectionChanges() {
        if (mTextSelectionBeforeIgnoringChanges != null) {
            throw new IllegalStateException(
                    "Illegal operation while cursor movement processing suspended");
        }
    }

    public boolean isCursorHandlingSuspended() {
        return mSuspended;
    }

    public Parcelable saveInstanceState(Parcelable superState) {
        assertNotIgnoringSelectionChanges();
        SavedState ss = new SavedState(superState);
        Editable buffer = mTextOwner.getText();
        ss.mUserText = getUserText();
        ss.mSuggestedText = mSuggestedText;
        ss.mSelStart = Selection.getSelectionStart(buffer);
        ss.mSelEnd = Selection.getSelectionEnd(buffer);
        return ss;
    }

    public Parcelable restoreInstanceState(Parcelable state) {
        assertNotIgnoringSelectionChanges();
        if (!(state instanceof SavedState)) return state;
        SavedState ss = (SavedState) state;
        if (DBG) {
            Log.d(TAG, "restoreInstanceState t='" + ss.mUserText + "' suggestion='" +
                    ss.mSuggestedText + " sel=" + ss.mSelStart + ".." + ss.mSelEnd);
        }
        // remove our listeners so we don't get notifications while re-initialising
        mTextOwner.getText().removeSpan(mBufferSpanWatcher);
        mTextOwner.removeTextChangedListener(mBufferTextWatcher);
        // and initialise will re-add the watchers
        initialize(ss.mUserText, ss.mSelStart, ss.mSelEnd, ss.mSuggestedText);
        notifyUserEnteredChanged();
        return ss.getSuperState();
    }

    /**
     * Temporarily stop processing cursor movements and selection changes. While cursor movements
     * are being ignored, the text in the buffer must NOT be changed; doing so will result in an
     * {@link IllegalStateException} being thrown.
     *
     * To stop ignoring cursor movements, call
     * {@link #resumeCursorMovementHandlingAndApplyChanges()}.
     */
    public void suspendCursorMovementHandling() {
        assertNotIgnoringSelectionChanges();
        Editable buffer = mTextOwner.getText();
        mTextSelectionBeforeIgnoringChanges = new BufferSelection(buffer);
        mSuspended = true;
    }

    /**
     * Start responding to cursor movements and selection changes again. If the cursor or selection
     * moved while it was being ignored, these changes will be processed now.
     */
    public void resumeCursorMovementHandlingAndApplyChanges() {
        Editable buffer = mTextOwner.getText();
        BufferSelection oldSelection = mTextSelectionBeforeIgnoringChanges;
        mTextSelectionBeforeIgnoringChanges = null;
        BufferSelection newSelection = new BufferSelection(buffer);
        if (oldSelection.mStart != newSelection.mStart) {
            mBufferSpanWatcher.onSpanChanged(buffer, Selection.SELECTION_START,
                    oldSelection.mStart, oldSelection.mStart,
                    newSelection.mStart, newSelection.mStart);
        }
        if (oldSelection.mEnd != newSelection.mEnd) {
            mBufferSpanWatcher.onSpanChanged(buffer, Selection.SELECTION_END,
                    oldSelection.mEnd, oldSelection.mEnd,
                    newSelection.mEnd, newSelection.mEnd);
        }
        mSuspended = false;
    }

    /**
     * Sets the current suggested text. A portion of this will be added to the user entered text if
     * that is a prefix of the suggestion.
     */
    public void setSuggestedText(String text) {
        assertNotIgnoringSelectionChanges();
        if (!TextUtils.equals(text, mSuggestedText)) {
            if (DBG) Log.d(TAG, "setSuggestedText(" + text + ")");
            mSuggestedText = text;
            if (mCurrentTextChange == null) {
                mCurrentTextChange = new TextChangeAttributes(0, 0, 0);
                Editable buffer = mTextOwner.getText();
                handleTextChanged(buffer);
            }
        }
    }

    /**
     * Gets the portion of displayed text that is not suggested.
     */
    public String getUserText() {
        assertNotIgnoringSelectionChanges();
        return mUserEntered.toString();
    }

    /**
     * Sets the given text as if it has been entered by the user.
     */
    public void setText(String text) {
        assertNotIgnoringSelectionChanges();
        if (text == null) text = "";
        Editable buffer = mTextOwner.getText();
        buffer.removeSpan(mSuggested);
        // this will cause a handleTextChanged call
        buffer.replace(0, text.length(), text);
    }

    public void addUserTextChangeWatcher(TextChangeWatcher watcher) {
        mTextWatchers.add(watcher);
    }

    private void handleTextChanged(Editable newText) {
        // When we make changes to the buffer from within this function, it results in recursive
        // calls to beforeTextChanges(), afterTextChanged(). We want to ignore the changes we're
        // making ourself:
        if (mCurrentTextChange.isHandled()) return;
        mCurrentTextChange.setHandled();
        final int pos = mCurrentTextChange.mPos;
        final int countBefore = mCurrentTextChange.mCountBefore;
        final int countAfter = mCurrentTextChange.mCountAfter;
        final int cursorPos = Selection.getSelectionEnd(newText);
        if (DBG) {
            Log.d(TAG, "pos=" + pos +"; countBefore=" + countBefore + "; countAfter=" +
                    countAfter + "; cursor=" + cursorPos);
        }
        mUserEntered.replace(pos, pos + countBefore,
                newText.subSequence(pos, pos + countAfter).toString());
        if (DBG) Log.d(TAG, "User entered: '" + mUserEntered + "' all='" + newText + "'");
        final int userLen = mUserEntered.length();
        boolean haveSuggested = newText.getSpanStart(mSuggested) != -1;
        if (mSuggestedText != null &&
                mSuggestedText.startsWith(mUserEntered.toString().toLowerCase())) {
            if (haveSuggested) {
                if (!mSuggestedText.equalsIgnoreCase(newText.toString())) {
                    if (countAfter > countBefore) {
                        // net insertion
                        int len = countAfter - countBefore;
                        newText.delete(pos + len, pos + len + len);
                    } else {
                        // net deletion
                        newText.replace(userLen, newText.length(),
                                mSuggestedText.substring(userLen));
                        if (countBefore == 0) {
                            // no change to the text - likely just suggested change
                            Selection.setSelection(newText, cursorPos);
                        }
                    }
                }
            } else {
                // no current suggested text - add it
                newText.insert(userLen, mSuggestedText.substring(userLen));
                // keep the cursor at the end of the user entered text, if that where it was
                // before.
                if (cursorPos == userLen) {
                    Selection.setSelection(newText, userLen);
                }
            }
            if (userLen == newText.length()) {
                newText.removeSpan(mSuggested);
            } else {
                newText.setSpan(mSuggested, userLen, newText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            if (newText.getSpanStart(mSuggested) != -1) {
                newText.removeSpan(mSuggested);
                newText.delete(mUserEntered.length(), newText.length());
            }
        }
        if (DBG) checkInvariant(newText);
        mCurrentTextChange = null;
        if (countBefore > 0 || countAfter > 0) {
            notifyUserEnteredChanged();
        }
    }

    private void notifyUserEnteredChanged() {
        for (TextChangeWatcher watcher : mTextWatchers) {
            watcher.onTextChanged(mUserEntered.toString());
        }
    }

    /**
     * Basic interface for being notified of changes to some text.
     */
    public interface TextChangeWatcher {
        void onTextChanged(String newText);
    }

    /**
     * Interface class to wrap required methods from {@link EditText}, or some other class used
     * to test without needing an @{link EditText}.
     */
    public interface TextOwner {
        Editable getText();
        void addTextChangedListener(TextWatcher watcher);
        void removeTextChangedListener(TextWatcher watcher);
        void setText(String text);
    }

    /**
     * This class stores the parameters passed to {@link BufferTextWatcher#beforeTextChanged},
     * together with a flag indicating if this invocation has been dealt with yet. We need this
     * information, together with the parameters passed to
     * {@link BufferTextWatcher#afterTextChanged}, to restore our internal state when the buffer is
     * edited.
     *
     * Since the changes we make from within {@link BufferTextWatcher#afterTextChanged} also trigger
     * further recursive calls to {@link BufferTextWatcher#beforeTextChanged} and
     * {@link BufferTextWatcher#afterTextChanged}, this class helps detect these recursive calls so
     * they can be ignored.
     */
    private static class TextChangeAttributes {
        public final int mPos;
        public final int mCountAfter;
        public final int mCountBefore;
        private boolean mHandled;

        public TextChangeAttributes(int pos, int countAfter, int countBefore) {
            mPos = pos;
            mCountAfter = countAfter;
            mCountBefore = countBefore;
        }

        public void setHandled() {
            mHandled = true;
        }

        public boolean isHandled() {
            return mHandled;
        }
    }

    /**
     * Encapsulates the state of the text selection (and cursor) within a text buffer.
     */
    private static class BufferSelection {
        final int mStart;
        final int mEnd;
        public BufferSelection(CharSequence text) {
            mStart = Selection.getSelectionStart(text);
            mEnd = Selection.getSelectionEnd(text);
        }
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BufferSelection)) return super.equals(other);
            BufferSelection otherSel = (BufferSelection) other;
            return this.mStart == otherSel.mStart && this.mEnd == otherSel.mEnd;
        }
    }

    private class BufferTextWatcher implements TextWatcher {
        @Override
        public void afterTextChanged(Editable newText) {
            if (DBG) {
                Log.d(TAG, "afterTextChanged('" + newText + "')");
            }
            assertNotIgnoringSelectionChanges();
            handleTextChanged(newText);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            assertNotIgnoringSelectionChanges();
            if (mCurrentTextChange == null) {
                mCurrentTextChange = new TextChangeAttributes(start, after, count);
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private class BufferSpanWatcher implements SpanWatcher {
        @Override
        public void onSpanAdded(Spannable text, Object what, int start, int end) {
        }

        @Override
        public void onSpanChanged(
                Spannable text, Object what, int ostart, int oend, int nstart, int nend) {
            if (mCurrentTextChange != null) return;
            if (mTextSelectionBeforeIgnoringChanges != null) return;
            if (what == Selection.SELECTION_END) {
                if (DBG) Log.d(TAG, "cursor move to " + nend);
                if (nend > mUserEntered.length()) {
                    mUserEntered.replace(0, mUserEntered.length(), text.toString());
                    text.removeSpan(mSuggested);
                }
                if (DBG) checkInvariant(text);
            }
        }

        @Override
        public void onSpanRemoved(Spannable text, Object what, int start, int end) {
        }
    }

    public static class SavedState extends View.BaseSavedState {
        String mUserText;
        String mSuggestedText;
        int mSelStart;
        int mSelEnd;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(mUserText);
            out.writeString(mSuggestedText);
            out.writeInt(mSelStart);
            out.writeInt(mSelEnd);
        }

        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        private SavedState(Parcel in) {
            super(in);
            mUserText = in.readString();
            mSuggestedText = in.readString();
            mSelStart = in.readInt();
            mSelEnd = in.readInt();
        }
    }

    /*
     * The remaining functions are used for testing purposes only.
     * -----------------------------------------------------------
     */

    /**
     * Verify that the internal state of this class is consistent.
     */
    @VisibleForTesting
    void checkInvariant(final Spannable s) {
        int suggestedStart = s.getSpanStart(mSuggested);
        int suggestedEnd = s.getSpanEnd(mSuggested);
        int cursorPos = Selection.getSelectionEnd(s);
        if (suggestedStart == -1 || suggestedEnd == -1) {
            suggestedStart = suggestedEnd = s.length();
        }
        String userEntered = getUserText();
        Log.d(TAG, "checkInvariant all='" + s + "' (len " + s.length() + ") sug="
                + suggestedStart + ".." + suggestedEnd + " cursor=" + cursorPos +
                " ue='" + userEntered + "' (len " + userEntered.length() + ")");
        int suggestedLen = suggestedEnd - suggestedStart;
        Assert.assertEquals("Sum of user and suggested text lengths doesn't match total length",
                s.length(), userEntered.length() + suggestedLen);
        Assert.assertEquals("End of user entered text doesn't match start of suggested",
                suggestedStart, userEntered.length());
        Assert.assertTrue("user entered text does not match start of buffer",
                userEntered.toString().equalsIgnoreCase(
                        s.subSequence(0, suggestedStart).toString()));
        if (mSuggestedText != null && suggestedStart < s.length()) {
            Assert.assertTrue("User entered is not a prefix of suggested",
                    mSuggestedText.startsWith(userEntered.toString().toLowerCase()));
            Assert.assertTrue("Suggested text does not match buffer contents",
                    mSuggestedText.equalsIgnoreCase(s.toString().toLowerCase()));
        }
        if (mSuggestedText == null) {
            Assert.assertEquals("Non-zero suggention length with null suggestion", 0, suggestedLen);
        } else {
            Assert.assertTrue("Suggestion text longer than suggestion (" + mSuggestedText.length() +
                    ">" + suggestedLen + ")", suggestedLen <= mSuggestedText.length());
        }
        Assert.assertTrue("Cursor within suggested part", cursorPos <= suggestedStart);
    }

    @VisibleForTesting
    SuggestedTextController(TextOwner textOwner, int color) {
        mUserEntered = new StringBuffer();
        mSuggested = new SuggestedSpan(color);
        mTextOwner = textOwner;
        mTextWatchers = new ArrayList<TextChangeWatcher>();
        initialize(null, 0, 0, null);
    }
}
