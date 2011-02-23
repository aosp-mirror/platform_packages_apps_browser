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

import com.android.browser.autocomplete.SuggestedTextController.TextOwner;

import android.graphics.Color;
import android.os.Parcelable;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.AbsSavedState;

/**
 * Test cases for {@link SuggestedTextController}.
 */
@SmallTest
public class SuggestedTextControllerTest extends AndroidTestCase {

    // these two must have a common prefix (but not be identical):
    private static final String RUBY_MURRAY = "ruby murray";
    private static final String RUBY_TUESDAY = "ruby tuesday";
    private static final String EXTRA_USER_TEXT = " curry";
    // no common prefix with the top two above:
    private static final String TOD_SLOAN = "tod sloan";

    private SuggestedTextController mController;
    private SpannableStringBuilder mString;

    private SuggestedTextController m2ndController;
    private SpannableStringBuilder m2ndString;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mString = new SpannableStringBuilder();
        Selection.setSelection(mString, 0); // position cursor
        mController = new SuggestedTextController(new BufferTextOwner(mString), Color.GRAY);
        checkInvariant();
    }

    private void create2ndController() {
        m2ndString = new SpannableStringBuilder();
        Selection.setSelection(m2ndString, 0); // position cursor
        m2ndController = new SuggestedTextController(new BufferTextOwner(m2ndString), Color.GRAY);
        check2ndInvariant();
    }

    private int cursorPos(Spannable string) {
        int selStart = Selection.getSelectionStart(string);
        int selEnd = Selection.getSelectionEnd(string);
        assertEquals("Selection has non-zero length", selStart, selEnd);
        return selEnd;
    }

    private int cursorPos() {
        return cursorPos(mString);
    }

    private void insertAtCursor(String text) {
        mString.insert(cursorPos(), text);
        checkInvariant();
    }

    private void insertAtCursor(char ch) {
        insertAtCursor(Character.toString(ch));
    }

    private void insertAt2ndCursor(String text) {
        m2ndString.insert(cursorPos(m2ndString), text);
        check2ndInvariant();
    }

    private void insertAt2ndCursor(char ch) {
        insertAt2ndCursor(Character.toString(ch));
    }

    private void deleteBeforeCursor(int count) {
        int pos = cursorPos();
        count = Math.min(pos, count);
        mString.delete(pos - count, pos);
        checkInvariant();
    }

    private void replaceSelection(String withThis) {
        mString.replace(Selection.getSelectionStart(mString),
                Selection.getSelectionEnd(mString), withThis);
        checkInvariant();
    }

    private void setSuggested(String suggested) {
        mController.setSuggestedText(suggested);
        checkInvariant();
    }

    private void set2ndSuggested(String suggested) {
        m2ndController.setSuggestedText(suggested);
        check2ndInvariant();
    }

    private void checkInvariant() {
        mController.checkInvariant(mString);
    }

    private void check2ndInvariant() {
        m2ndController.checkInvariant(m2ndString);
    }

    private void assertUserEntered(String expected, SuggestedTextController controller) {
        assertEquals("User entered text not as expected", expected, controller.getUserText());
    }

    private void assertUserEntered(String expected) {
        assertUserEntered(expected, mController);
    }

    private void assertBuffer(String expected, Editable string) {
        assertEquals("Buffer contents not as expected", expected, string.toString());
    }

    private void assertBuffer(String expected) {
        assertBuffer(expected, mString);
    }

    private void assertCursorPos(int where, Spannable string) {
        assertEquals("Cursor not at expected position", where, cursorPos(string));
    }

    private void assertCursorPos(int where) {
        assertCursorPos(where, mString);
    }

    private static final String commonPrefix(String a, String b) {
        int pos = 0;
        while (a.charAt(pos) == b.charAt(pos)) {
            pos++;
        }
        assertTrue("No common prefix between '" + a + "' and '" + b + "'", pos > 0);
        return a.substring(0, pos);
    }

    public void testTypeNoSuggested() {
        for (int i = 0; i < RUBY_MURRAY.length(); ++i) {
            assertCursorPos(i);
            assertUserEntered(RUBY_MURRAY.substring(0, i));
            assertBuffer(RUBY_MURRAY.substring(0, i));
            insertAtCursor(RUBY_MURRAY.substring(i, i + 1));
        }
    }

    public void testTypeSuggested() {
        setSuggested(RUBY_MURRAY);
        assertCursorPos(0);
        assertBuffer(RUBY_MURRAY);
        for (int i = 0; i < RUBY_MURRAY.length(); ++i) {
            assertCursorPos(i);
            assertUserEntered(RUBY_MURRAY.substring(0, i));
            assertBuffer(RUBY_MURRAY);
            insertAtCursor(RUBY_MURRAY.charAt(i));
        }
    }

    public void testSetSuggestedAfterTextEntry() {
        final int count = RUBY_MURRAY.length() / 2;
        for (int i = 0; i < count; ++i) {
            assertCursorPos(i);
            assertUserEntered(RUBY_MURRAY.substring(0, i));
            insertAtCursor(RUBY_MURRAY.substring(i, i + 1));
        }
        setSuggested(RUBY_MURRAY);
        assertUserEntered(RUBY_MURRAY.substring(0, count));
        assertBuffer(RUBY_MURRAY);
    }

    public void testTypeSuggestedUpperCase() {
        setSuggested(RUBY_MURRAY);
        assertCursorPos(0);
        for (int i = 0; i < RUBY_MURRAY.length(); ++i) {
            assertCursorPos(i);
            assertUserEntered(RUBY_MURRAY.substring(0, i).toUpperCase());
            assertTrue("Buffer doesn't contain suggested text",
                    RUBY_MURRAY.equalsIgnoreCase(mString.toString()));
            insertAtCursor(Character.toUpperCase(RUBY_MURRAY.charAt(i)));
        }
    }

    public void testChangeSuggestedText() {
        String pref = commonPrefix(RUBY_MURRAY, RUBY_TUESDAY);
        setSuggested(RUBY_MURRAY);
        insertAtCursor(pref);
        assertBuffer(RUBY_MURRAY);
        assertUserEntered(pref);
        setSuggested(RUBY_TUESDAY);
        assertBuffer(RUBY_TUESDAY);
        assertUserEntered(pref);
    }

    public void testTypeNonSuggested() {
        setSuggested(RUBY_MURRAY);
        insertAtCursor(RUBY_MURRAY.charAt(0));
        assertBuffer(RUBY_MURRAY);
        insertAtCursor('x');
        assertBuffer("rx");
    }

    public void testTypeNonSuggestedThenNewSuggestion() {
        final String pref = commonPrefix(RUBY_MURRAY, RUBY_TUESDAY);
        setSuggested(RUBY_MURRAY);
        assertCursorPos(0);
        insertAtCursor(pref);
        assertCursorPos(pref.length());
        assertUserEntered(pref);
        insertAtCursor(RUBY_TUESDAY.charAt(pref.length()));
        assertBuffer(RUBY_TUESDAY.substring(0, pref.length() + 1));
        setSuggested(RUBY_TUESDAY);
        assertBuffer(RUBY_TUESDAY);
    }

    public void testChangeSuggestedToNonUserEntered() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        setSuggested(RUBY_MURRAY);
        insertAtCursor(half);
        setSuggested(TOD_SLOAN);
        assertUserEntered(half);
        assertBuffer(half);
    }

    public void testChangeSuggestedToUserEntered() {
        setSuggested(RUBY_MURRAY);
        insertAtCursor(TOD_SLOAN);
        setSuggested(TOD_SLOAN);
        assertUserEntered(TOD_SLOAN);
        assertBuffer(TOD_SLOAN);
    }

    public void testChangeSuggestedToEmpty() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        setSuggested(RUBY_MURRAY);
        insertAtCursor(half);
        setSuggested(null);
        assertUserEntered(half);
        assertBuffer(half);
    }

    public void testChangeSuggestedToEmptyFromUserEntered() {
        setSuggested(RUBY_MURRAY);
        insertAtCursor(RUBY_MURRAY);
        setSuggested(null);
        assertUserEntered(RUBY_MURRAY);
        assertBuffer(RUBY_MURRAY);
    }

    public void typeNonSuggestedThenDelete() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        assertCursorPos(0);
        insertAtCursor(half);
        assertCursorPos(half.length());
        setSuggested(RUBY_MURRAY);
        insertAtCursor('x');
        assertBuffer(half + "x");
        deleteBeforeCursor(1);
        assertUserEntered(half);
        assertBuffer(RUBY_MURRAY);
    }

    public void testDeleteMultipleFromSuggested() {
        final String twoThirds = RUBY_MURRAY.substring(0, (RUBY_MURRAY.length() * 2) / 3);
        setSuggested(RUBY_MURRAY);
        insertAtCursor(twoThirds);
        assertCursorPos(twoThirds.length());
        // select some of the text just entered:
        Selection.setSelection(mString, RUBY_MURRAY.length() / 3, twoThirds.length());
        // and delete it:
        replaceSelection("");
        assertCursorPos(RUBY_MURRAY.length() / 3);
        assertUserEntered(RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 3));
        assertBuffer(RUBY_MURRAY);
    }

    public void testDeleteMultipleToFormSuggested() {
        final String pref = commonPrefix(RUBY_TUESDAY, RUBY_MURRAY);
        final int extra = (RUBY_TUESDAY.length() - pref.length()) / 2;
        setSuggested(RUBY_MURRAY);
        insertAtCursor(RUBY_TUESDAY.substring(0, pref.length() + extra));
        assertCursorPos(pref.length() + extra);
        // select and delete extra characters, leaving just prefix
        Selection.setSelection(mString, pref.length(), pref.length() + extra);
        replaceSelection("");
        assertCursorPos(pref.length());
        assertBuffer(RUBY_MURRAY);
        assertUserEntered(pref);
    }

    public void testBackspaceWithinUserTextFromSuggested() {
        StringBuffer half = new StringBuffer(RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2));
        insertAtCursor(half.toString());
        int backSpaceFrom = half.length() / 2;
        Selection.setSelection(mString, backSpaceFrom);
        deleteBeforeCursor(1);
        assertCursorPos(backSpaceFrom - 1);
        half.delete(backSpaceFrom - 1, backSpaceFrom);
        assertUserEntered(half.toString());
        assertBuffer(half.toString());
    }

    public void testInsertWithinUserTextToFormSuggested() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        StringBuffer initial = new StringBuffer(half);
        int pos = initial.length() / 2;
        char toInsert = initial.charAt(pos);
        initial.delete(pos, pos + 1);
        insertAtCursor(initial.toString());
        setSuggested(RUBY_MURRAY);
        assertUserEntered(initial.toString());
        assertBuffer(initial.toString());
        Selection.setSelection(mString, pos);
        insertAtCursor(toInsert);
        assertCursorPos(pos + 1);
        assertUserEntered(half);
        assertBuffer(RUBY_MURRAY);
    }

    public void testEnterTextBeyondSuggested() {
        setSuggested(RUBY_MURRAY);
        int i = RUBY_MURRAY.length() / 2;
        insertAtCursor(RUBY_MURRAY.substring(0, i));
        String query = RUBY_MURRAY + EXTRA_USER_TEXT;
        for (; i < query.length(); ++i) {
            assertUserEntered(query.substring(0, i));
            if (i <= RUBY_MURRAY.length()) {
                assertBuffer(RUBY_MURRAY);
            }
            insertAtCursor(query.charAt(i));
        }
        assertUserEntered(query);
    }

    public void testDeleteFromLongerThanSuggested() {
        setSuggested(RUBY_MURRAY);
        final String entered = RUBY_MURRAY + EXTRA_USER_TEXT;
        insertAtCursor(entered);
        for (int i = entered.length(); i > (RUBY_MURRAY.length() / 2); --i) {
            assertCursorPos(i);
            assertUserEntered(entered.substring(0, i));
            if (i <= RUBY_MURRAY.length()) {
                assertBuffer(RUBY_MURRAY);
            }
            deleteBeforeCursor(1);
        }
    }

    public void testReplaceWithShorterToFormSuggested() {
        final String pref = commonPrefix(RUBY_TUESDAY, RUBY_MURRAY);
        final int extra = (RUBY_TUESDAY.length() - pref.length()) / 2;
        setSuggested(RUBY_MURRAY);
        insertAtCursor(RUBY_TUESDAY.substring(0, pref.length() + extra));
        assertCursorPos(pref.length() + extra);
        // select and replace extra characters, to match suggested
        Selection.setSelection(mString, pref.length(), pref.length() + extra);
        replaceSelection(RUBY_MURRAY.substring(pref.length(), pref.length() + extra - 1));
        assertBuffer(RUBY_MURRAY);
        assertUserEntered(RUBY_MURRAY.substring(0, pref.length() + extra - 1));
    }

    public void testReplaceWithSameLengthToFormSuggested() {
        final String pref = commonPrefix(RUBY_TUESDAY, RUBY_MURRAY);
        final int extra = (RUBY_TUESDAY.length() - pref.length()) / 2;
        setSuggested(RUBY_MURRAY);
        insertAtCursor(RUBY_TUESDAY.substring(0, pref.length() + extra));
        assertCursorPos(pref.length() + extra);
        // select and replace extra characters, to match suggested
        Selection.setSelection(mString, pref.length(), pref.length() + extra);
        replaceSelection(RUBY_MURRAY.substring(pref.length(), pref.length() + extra));
        assertBuffer(RUBY_MURRAY);
        assertUserEntered(RUBY_MURRAY.substring(0, pref.length() + extra));
    }

    public void testReplaceWithLongerToFormSuggested() {
        final String pref = commonPrefix(RUBY_TUESDAY, RUBY_MURRAY);
        final int extra = (RUBY_TUESDAY.length() - pref.length()) / 2;
        setSuggested(RUBY_MURRAY);
        insertAtCursor(RUBY_TUESDAY.substring(0, pref.length() + extra));
        assertCursorPos(pref.length() + extra);
        // select and replace extra characters, to match suggested
        Selection.setSelection(mString, pref.length(), pref.length() + extra);
        replaceSelection(RUBY_MURRAY.substring(pref.length(), pref.length() + extra + 1));
        assertBuffer(RUBY_MURRAY);
        assertUserEntered(RUBY_MURRAY.substring(0, pref.length() + extra + 1));
    }

    public void testMoveCursorIntoSuggested() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        insertAtCursor(half);
        setSuggested(RUBY_MURRAY);
        assertCursorPos(half.length());
        Selection.setSelection(mString, half.length() + 1);
        checkInvariant();
        assertUserEntered(RUBY_MURRAY);
    }

    public void testMoveCursorWithinUserEntered() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        insertAtCursor(half);
        setSuggested(RUBY_MURRAY);
        assertCursorPos(half.length());
        Selection.setSelection(mString, half.length() - 1);
        checkInvariant();
        assertUserEntered(half);
    }

    public void testSelectWithinSuggested() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        insertAtCursor(half);
        setSuggested(RUBY_MURRAY);
        assertCursorPos(half.length());
        Selection.setSelection(mString, half.length() + 1, half.length() + 2);
        checkInvariant();
        assertUserEntered(RUBY_MURRAY);
    }

    public void testSelectStraddlingSuggested() {
        final String half = RUBY_MURRAY.substring(0, RUBY_MURRAY.length() / 2);
        insertAtCursor(half);
        setSuggested(RUBY_MURRAY);
        assertCursorPos(half.length());
        Selection.setSelection(mString, half.length() - 1, half.length() + 1);
        checkInvariant();
        assertUserEntered(RUBY_MURRAY);
    }

    public void testSaveAndRestoreNoText() {
        create2ndController();
        Parcelable state = mController.saveInstanceState(AbsSavedState.EMPTY_STATE);
        m2ndController.restoreInstanceState(state);
        check2ndInvariant();
        assertBuffer("", m2ndString);
    }

    public void testSaveAndRestoreWithSuggestedText() {
        create2ndController();
        setSuggested(TOD_SLOAN);
        Parcelable state = mController.saveInstanceState(AbsSavedState.EMPTY_STATE);
        m2ndController.restoreInstanceState(state);
        check2ndInvariant();
        assertBuffer(TOD_SLOAN, m2ndString);
        assertUserEntered("", m2ndController);
    }

    public void testSaveAndRestoreWithUserEnteredAndSuggestedText() {
        final String half = TOD_SLOAN.substring(0, TOD_SLOAN.length() / 2);
        create2ndController();
        setSuggested(TOD_SLOAN);
        insertAtCursor(half);
        Parcelable state = mController.saveInstanceState(AbsSavedState.EMPTY_STATE);
        m2ndController.restoreInstanceState(state);
        check2ndInvariant();
        assertBuffer(TOD_SLOAN, m2ndString);
        assertUserEntered(half, m2ndController);
        assertCursorPos(half.length(), m2ndString);
    }

    public void testSaveAndRestoreWithNonSuggested() {
        final String half = TOD_SLOAN.substring(0, TOD_SLOAN.length() / 2);
        create2ndController();
        setSuggested(RUBY_MURRAY);
        insertAtCursor(half);
        Parcelable state = mController.saveInstanceState(AbsSavedState.EMPTY_STATE);
        m2ndController.restoreInstanceState(state);
        check2ndInvariant();
        assertBuffer(half, m2ndString);
        assertUserEntered(half, m2ndController);
        assertCursorPos(half.length(), m2ndString);
    }

    public void testSaveAndRestoreThenTypeSuggested() {
        final String half = TOD_SLOAN.substring(0, TOD_SLOAN.length() / 2);
        create2ndController();
        set2ndSuggested(TOD_SLOAN);
        insertAt2ndCursor(half);
        insertAt2ndCursor('x');
        Parcelable state = m2ndController.saveInstanceState(AbsSavedState.EMPTY_STATE);
        mController.restoreInstanceState(state);
        assertCursorPos(half.length() + 1);
        // delete the x
        deleteBeforeCursor(1);
        assertCursorPos(half.length());
        assertBuffer(TOD_SLOAN);
        assertUserEntered(half);
    }

    public void testSuspendAndResumeCursorProcessing() {
        final String half = TOD_SLOAN.substring(0, TOD_SLOAN.length() / 2);
        setSuggested(TOD_SLOAN);
        insertAtCursor(half);
        mController.suspendCursorMovementHandling();
        Selection.setSelection(mString, TOD_SLOAN.length());
        Selection.setSelection(mString, half.length());
        mController.resumeCursorMovementHandlingAndApplyChanges();
        assertCursorPos(half.length());
        assertUserEntered(half);
        assertBuffer(TOD_SLOAN);
    }

    private static class BufferTextOwner implements TextOwner {

        private final Editable mBuffer;

        public BufferTextOwner(Editable buffer) {
            mBuffer = buffer;
        }

        public void addTextChangedListener(TextWatcher watcher) {
            mBuffer.setSpan(watcher , 0, mBuffer.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        public void removeTextChangedListener(TextWatcher watcher) {
            mBuffer.removeSpan(watcher);
        }

        public Editable getText() {
            return mBuffer;
        }

        public void setText(String text) {
            mBuffer.replace(0, mBuffer.length(), text);
        }

    }

}
