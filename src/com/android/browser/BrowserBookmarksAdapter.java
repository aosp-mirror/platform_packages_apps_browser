/*
 * Copyright (C) 2006 The Android Open Source Project
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

class BrowserBookmarksAdapter extends ResourceCursorAdapter {
    /**
     *  Create a new BrowserBookmarksAdapter.
     */
    public BrowserBookmarksAdapter(Context context) {
        // Make sure to tell the CursorAdapter to avoid the observer and auto-requery
        // since the Loader will do that for us.
        super(context, R.layout.bookmark_thumbnail, null);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        View holder = view.findViewById(R.id.holder);
        ImageView thumb = (ImageView) view.findViewById(R.id.thumb);
        TextView tv = (TextView) view.findViewById(R.id.label);

        holder.setVisibility(View.GONE);
        tv.setText(cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE));

        Bitmap thumbnail = null;
        byte[] data = cursor.getBlob(BookmarksLoader.COLUMN_INDEX_THUMBNAIL);
        if (data != null) {
            thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length);
        }

        if (thumbnail == null) {
            thumb.setImageResource(R.drawable.browser_thumbnail);
        } else {
            thumb.setImageBitmap(thumbnail);
        }
    }
}
