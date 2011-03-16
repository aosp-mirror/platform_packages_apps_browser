/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.browser.view;

import com.android.browser.view.PieMenu.PieView;

import android.graphics.Path;
import android.view.View;

/**
 * Pie menu item
 */
public class PieItem {

    private View mView;
    private PieView mPieView;
    private int level;
    private float start;
    private float sweep;
    private int inner;
    private int outer;
    private boolean mSelected;
    private Path mPath;

    public PieItem(View view, int level) {
        mView = view;
        this.level = level;
    }

    public PieItem(View view, int level, PieView sym) {
        mView = view;
        this.level = level;
        mPieView = sym;
    }

    public void setSelected(boolean s) {
        mSelected = s;
        if (mView != null) {
            mView.setSelected(s);
        }
    }

    public boolean isSelected() {
        return mSelected;
    }

    public int getLevel() {
        return level;
    }

    public void setGeometry(float st, float sw, int inside, int outside, Path p) {
        start = st;
        sweep = sw;
        inner = inside;
        outer = outside;
        mPath = p;
    }

    public float getStartAngle() {
        return start;
    }

    public float getSweep() {
        return sweep;
    }

    public int getInnerRadius() {
        return inner;
    }

    public int getOuterRadius() {
        return outer;
    }

    public boolean isPieView() {
        return (mPieView != null);
    }

    public View getView() {
        return mView;
    }

    public void setPieView(PieView sym) {
        mPieView = sym;
    }

    public PieView getPieView() {
        return mPieView;
    }

    public Path getPath() {
        return mPath;
    }

}
