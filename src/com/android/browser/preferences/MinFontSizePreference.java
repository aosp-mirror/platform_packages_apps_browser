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

package com.android.browser.preferences;

import com.android.browser.R;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MinFontSizePreference extends Preference implements OnSeekBarChangeListener {

    // range from 1:6..24
    static final int MIN = 5;
    static final int MAX = 23;
    private int mProgress;
    View mRoot;

    public MinFontSizePreference(
            Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MinFontSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MinFontSizePreference(Context context) {
        super(context);
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        if (mRoot == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            mRoot = inflater.inflate(R.layout.min_font_size, parent, false);
            SeekBar seek = (SeekBar) mRoot.findViewById(R.id.seekbar);
            seek.setMax((MAX - MIN));
            seek.setProgress(mProgress);
            seek.setOnSeekBarChangeListener(this);
        }
        return mRoot;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        mProgress = restoreValue ? getPersistedInt(mProgress)
                : (Integer) defaultValue;
        mProgress -= 1;
    }

    @Override
    public void onProgressChanged(
            SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            if (progress == 0) {
                persistInt(1);
            } else {
                persistInt(progress + MIN + 1);
            }
        }
        mRoot.invalidate();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }


    @Override
    protected Parcelable onSaveInstanceState() {
        /*
         * Suppose a client uses this preference type without persisting. We
         * must save the instance state so it is able to, for example, survive
         * orientation changes.
         */

        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState myState = new SavedState(superState);
        myState.progress = mProgress;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        // Restore the instance state
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mProgress = myState.progress;
        notifyChanged();
    }

    /**
     * SavedState, a subclass of {@link BaseSavedState}, will store the state
     * of MyPreference, a subclass of Preference.
     * <p>
     * It is important to always call through to super methods.
     */
    private static class SavedState extends BaseSavedState {
        int progress;

        public SavedState(Parcel source) {
            super(source);

            // Restore the click counter
            progress = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            // Save the click counter
            dest.writeInt(progress);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}
