/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.CursorAdapter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * url/search input view
 * handling suggestions
 */
public class UrlInputView extends AutoCompleteTextView {

    private UrlInputListener   mListener;
    private InputMethodManager mInputManager;
    private SuggestionsAdapter mAdapter;
    private Drawable           mFocusDrawable;
    private Drawable           mNoFocusDrawable;


    public UrlInputView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public UrlInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UrlInputView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        mFocusDrawable = ctx.getResources().getDrawable(R.drawable.textfield_stroke);
        mNoFocusDrawable = ctx.getResources().getDrawable(R.drawable.textfield_nostroke);
        mInputManager = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                finishInput(getText().toString());
                return true;
            }
        });
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setBackgroundDrawable(hasFocus ? mFocusDrawable : mNoFocusDrawable);
            }
        });
        final ContentResolver cr = mContext.getContentResolver();
        mAdapter = new SuggestionsAdapter(mContext,
                BrowserProvider.getBookmarksSuggestions(cr, null));
        setAdapter(mAdapter);
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String url = mAdapter.getViewString(view);
                finishInput(url);
            }
        });
        setSelectAllOnFocus(true);
    }

    public void setUrlInputListener(UrlInputListener listener) {
        mListener = listener;
    }

    public void forceIme() {
        mInputManager.showSoftInput(this, 0);
    }

    private void finishInput(String url) {
        this.dismissDropDown();
        mInputManager.hideSoftInputFromWindow(getWindowToken(), 0);
        if (url == null) {
            mListener.onDismiss();
        } else {
            mListener.onAction(url);
        }

    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent evt) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // catch back key in order to do slightly more cleanup than usual
            finishInput(null);
            return true;
        }
        return super.onKeyPreIme(keyCode, evt);
    }

    interface UrlInputListener {

        public void onDismiss();

        public void onAction(String text);

    }

    /**
     * adapter used by suggestion dropdown
     */
    class SuggestionsAdapter extends CursorAdapter implements Filterable {

        private Cursor          mLastCursor;
        private ContentResolver mContent;
        private int             mIndexText1;
        private int             mIndexText2;
        private int             mIndexIcon;

        public SuggestionsAdapter(Context context, Cursor c) {
            super(context, c);
            mContent = context.getContentResolver();
            mIndexText1 = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1);
            mIndexText2 = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2_URL);
            mIndexIcon = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_1);
        }

        public String getViewString(View view) {
            TextView tv2 = (TextView) view.findViewById(android.R.id.text2);
            if (tv2.getText().length() > 0) {
                return tv2.getText().toString();
            } else {
                TextView tv1 = (TextView) view.findViewById(android.R.id.text1);
                return tv1.getText().toString();
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            final View view = inflater.inflate(
                    R.layout.simple_dropdown_item_2line, parent, false);
            bindView(view, context, cursor);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView tv1 = (TextView) view.findViewById(android.R.id.text1);
            TextView tv2 = (TextView) view.findViewById(android.R.id.text2);
            ImageView ic1 = (ImageView) view.findViewById(R.id.icon1);
            tv1.setText(cursor.getString(mIndexText1));
            String url = cursor.getString(mIndexText2);
            tv2.setText((url != null) ? url : "");
            // assume an id
            try {
                int id = Integer.parseInt(cursor.getString(mIndexIcon));
                Drawable d = context.getResources().getDrawable(id);
                ic1.setImageDrawable(d);
            } catch (NumberFormatException nfx) {
            }
        }

        @Override
        public String convertToString(Cursor cursor) {
            return cursor.getString(mIndexText1);
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            if (getFilterQueryProvider() != null) {
                return getFilterQueryProvider().runQuery(constraint);
            }
            mLastCursor = BrowserProvider.getBookmarksSuggestions(mContent,
                    (constraint != null) ? constraint.toString() : null);
            return mLastCursor;
        }

    }

}
