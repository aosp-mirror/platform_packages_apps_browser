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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Browser;
import android.util.Log;
import android.view.View;
import android.webkit.WebIconDatabase;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

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
                ContentResolver cr = mContext.getContentResolver();
                Cursor cursor = cr.query(
                        Browser.BOOKMARKS_URI,
                        Browser.HISTORY_PROJECTION,
                        "url = ?",
                        new String[] { mUrl },
                        null);
                boolean first = cursor.moveToFirst();
                // Should be in the database no matter what
                if (!first) {
                    throw new AssertionError("URL is not in the database!");
                }
                if (isChecked) {
                    // Add to bookmarks
                    // FIXME: Share code with AddBookmarkPage.java
                    ContentValues map = new ContentValues();
                    map.put(Browser.BookmarkColumns.CREATED,
                            new Date().getTime());
                    map.put(Browser.BookmarkColumns.TITLE, getName());
                    map.put(Browser.BookmarkColumns.BOOKMARK, 1);
                    try {
                        cr.update(Browser.BOOKMARKS_URI, map, 
                                "_id = " + cursor.getInt(0), null);
                    } catch (IllegalStateException e) {
                        Log.e("HistoryItem", "no database!");
                    }
                    WebIconDatabase.getInstance().retainIconForPageUrl(mUrl);
                    // catch IllegalStateException?
                    Toast.makeText(mContext, R.string.added_to_bookmarks,
                            Toast.LENGTH_LONG).show();
                } else {
                    // Remove from bookmarks
                    // FIXME: This code should be shared with
                    // BrowserBookmarksAdapter.java
                    WebIconDatabase.getInstance().releaseIconForPageUrl(mUrl);
                    Uri uri = ContentUris.withAppendedId(Browser.BOOKMARKS_URI,
                            cursor.getInt(Browser.HISTORY_PROJECTION_ID_INDEX));
                    // It is no longer a bookmark, but it is still a visited
                    // site.
                    ContentValues values = new ContentValues();
                    values.put(Browser.BookmarkColumns.BOOKMARK, 0);
                    try {
                        cr.update(uri, values, null, null);
                    } catch (IllegalStateException e) {
                        Log.e("HistoryItem", "no database!");
                    }
                    Toast.makeText(mContext, R.string.removed_from_bookmarks,
                            Toast.LENGTH_LONG).show();
                }
                cursor.deactivate();
            }
        };
    }
    
    void copyTo(HistoryItem item) {
        item.mTextView.setText(mTextView.getText());
        item.mUrlText.setText(mUrlText.getText());
        item.setIsBookmark(mStar.isChecked());
        item.mImageView.setImageDrawable(mImageView.getDrawable());
    }

    /**
     *  Set whether or not this represents a bookmark, and make sure the star
     *  behaves appropriately.
     */
    void setIsBookmark(boolean isBookmark) {
        mStar.setOnCheckedChangeListener(null);
        mStar.setChecked(isBookmark);
        mStar.setOnCheckedChangeListener(mListener);
    }
}
