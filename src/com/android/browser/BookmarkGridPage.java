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
import android.widget.GridView;

import java.util.ArrayList;

public class BookmarkGridPage extends GridView {
    private final static int            SPACING = 10;
    private BrowserBookmarksAdapter     mAdapter;

    public BookmarkGridPage(Context context, BrowserBookmarksAdapter adapter) {
        super(context);
        setNumColumns(3);
        mAdapter = adapter;
        setAdapter(mAdapter);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSelector(android.R.drawable.gallery_thumb);
        setVerticalSpacing(SPACING);
        setHorizontalSpacing(SPACING);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int thumbHeight = (h - 2 * (SPACING + getListPaddingTop()
                + getListPaddingBottom())) / 3;
        mAdapter.heightChanged(thumbHeight);
        super.onSizeChanged(w, h, oldw, oldh);
    }
}
