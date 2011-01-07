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

package com.android.browser.addbookmark;

import android.content.Context;
import android.view.View;
import android.util.AttributeSet;
import android.widget.Spinner;

/**
 * Special Spinner class which calls onItemSelected even if the item selected
 * was already selected.  In that case, it passes null for the View.
 */
public class FolderSpinner extends Spinner {
    public FolderSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setSelection(int position) {
        int oldPosition = getSelectedItemPosition();
        super.setSelection(position);
        if (oldPosition == position) {
            // Normally this is not called because the item did not actually
            // change, but in this case, we still want it to be called.
            long id = getAdapter().getItemId(position);
            getOnItemSelectedListener().onItemSelected(this, null, position, id);
        }
    }
}

