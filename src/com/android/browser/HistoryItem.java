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
import android.graphics.Bitmap;
import android.provider.Browser;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 *  Layout representing a history item in the classic history viewer.
 */
/* package */ class HistoryItem extends BookmarkItem {

    private CompoundButton  mStar;      // Star for bookmarking
    private CompoundButton.OnCheckedChangeListener  mListener;
    /**
     *  Create a new HistoryItem.
     *  @param context  Context for this HistoryItem.
     */
    /* package */ HistoryItem(Context context) {
        super(context);

        mStar = (CompoundButton) findViewById(R.id.star);
        mStar.setVisibility(View.VISIBLE);
        mListener = new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                if (isChecked) {
                    Bookmarks.addBookmark(mContext,
                            mContext.getContentResolver(), mUrl, getName(), null, true);
                    LogTag.logBookmarkAdded(mUrl, "history");
                } else {
                    Bookmarks.removeFromBookmarks(mContext,
                            mContext.getContentResolver(), mUrl, getName());
                }
            }
        };
    }
    
    /* package */ void copyTo(HistoryItem item) {
        item.mTextView.setText(mTextView.getText());
        item.mUrlText.setText(mUrlText.getText());
        item.setIsBookmark(mStar.isChecked());
        item.mImageView.setImageDrawable(mImageView.getDrawable());
    }

    /**
     * Whether or not this item represents a bookmarked site
     */
    /* package */ boolean isBookmark() {
        return mStar.isChecked();
    }

    /**
     *  Set whether or not this represents a bookmark, and make sure the star
     *  behaves appropriately.
     */
    /* package */ void setIsBookmark(boolean isBookmark) {
        mStar.setOnCheckedChangeListener(null);
        mStar.setChecked(isBookmark);
        mStar.setOnCheckedChangeListener(mListener);
    }
}
