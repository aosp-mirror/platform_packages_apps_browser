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
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 *  Layout representing a history item in the classic history viewer.
 */
/* package */ class HistoryItem extends LinearLayout {

    private TextView    mTitleView; // Truncated Title
    private String      mUrl;       // Full Url
    private TextView    mUrlText;   // Truncated Url
    
    /**
     *  Create a new HistoryItem.
     *  @param context  Context for this HistoryItem.
     */
    /* package */ HistoryItem(Context context) {
        super(context);

        setWillNotDraw(false);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.history_item, this);
        mTitleView = (TextView) findViewById(R.id.title);
        mUrlText = (TextView) findViewById(R.id.url);
    }
    
    void copyTo(HistoryItem item) {
        item.mTitleView.setText(mTitleView.getText());
        item.mUrlText.setText(mUrlText.getText());
    }
    
    /**
     * Return the name of this HistoryItem.
     * @return  String name of this HistoryItem.
     /
    /* package */ String getName() {
        return mTitleView.getText().toString();
    }

    /**
     * Return the url of this HistoryItem.
     * @return  String url of this HistoryItem.
     /
    /* package */ String getUrl() {
        return mUrl;
    }

    /**
     *  Set the favicon for this item.
     *
     *  @param b    The new bitmap for this item.
     *              If it is null, will use the default.
     */
    /* package */ void setFavicon(Bitmap b) {
        Drawable[] array = new Drawable[2];
        PaintDrawable p = new PaintDrawable(Color.WHITE);
        p.setCornerRadius(3f);
        array[0] = p;
        if (b != null) {
            array[1] = new BitmapDrawable(b);
        } else {
            array[1] = new BitmapDrawable(mContext.getResources().
                    openRawResource(R.drawable.app_web_browser_sm));
        }
        LayerDrawable d = new LayerDrawable(array);
        d.setLayerInset(1, 2, 2, 2, 2);
        d.setBounds(0, 0, 20, 20);
        mTitleView.setCompoundDrawables(d, null, null, null);
    }
    
    /**
     *  Set the name for this HistoryItem.
     *  If the name is longer that BrowserSettings.MAX_TEXTVIEW_LEN characters, 
     *  the name is truncated to BrowserSettings.MAX_TEXTVIEW_LEN characters. 
     *  The History activity does not expose a UI element that can show the 
     *  full title.
     *  @param  name String representing new name for this HistoryItem.
     */
    /* package */ void setName(String name) {
        if (name != null && name.length() > BrowserSettings.MAX_TEXTVIEW_LEN) {
            name = name.substring(0, BrowserSettings.MAX_TEXTVIEW_LEN);
        }
        mTitleView.setText(name);
    }
    
    /**
     *  Set the url for this HistoryItem.
     *  @param  url String representing new url for this HistoryItem.
     */
    /* package */ void setUrl(String url) {
        mUrl = url;
        // Truncate the url for the screen
        if (url.length() > BrowserSettings.MAX_TEXTVIEW_LEN) {
            mUrlText.setText(url.substring(0, BrowserSettings.MAX_TEXTVIEW_LEN));
        } else {
            mUrlText.setText(url);
        }
    }
}
