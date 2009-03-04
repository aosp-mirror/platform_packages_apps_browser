/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.Map;

/**
 * Displays a series of dots.  The selected one is highlighted.
 * No animations yet.  Nothing fancy.
 */
class Dots extends LinearLayout {

    private static final int MAX_DOTS = 8;
    private int mSelected = -1;

    public Dots(Context context) {
        this(context, null);
    }

    public Dots(Context context, AttributeSet attrs) {
        super(context, attrs);

        setGravity(Gravity.CENTER);
        setPadding(0, 4, 0, 4);

        LayoutParams lp =
                new LayoutParams(LayoutParams.WRAP_CONTENT,
                                 LayoutParams.WRAP_CONTENT);

        for (int i = 0; i < MAX_DOTS; i++) {
            ImageView dotView = new ImageView(mContext);
            dotView.setImageResource(R.drawable.page_indicator_unselected2);
            addView(dotView, lp);
        }
    }

    /**
     * @param dotCount if less than 1 or greater than MAX_DOTS, Dots
     * disappears
     */
    public void setDotCount(int dotCount) {
        if (dotCount > 1 && dotCount <= MAX_DOTS) {
            setVisibility(VISIBLE);
            for (int i = 0; i < MAX_DOTS; i++) {
                getChildAt(i).setVisibility(i < dotCount? VISIBLE : GONE);
            }
        } else {
            setVisibility(GONE);
        }
    }

    public void setSelected(int index) {
        if (index < 0 || index >= MAX_DOTS) return;

        if (mSelected >= 0) {
            // Unselect old
            ((ImageView)getChildAt(mSelected)).setImageResource(
                    R.drawable.page_indicator_unselected2);
        }
        ((ImageView)getChildAt(index)).setImageResource(R.drawable.page_indicator);
        mSelected = index;
    }
}
