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

import com.android.browser.BrowserSettings;
import com.android.browser.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class WebViewPreview extends Preference implements OnSharedPreferenceChangeListener {

    // 80 char line width limit? Rules are made to be broken.
    static final String HTML_FORMAT = "<html><head><style type=\"text/css\">p { margin: 2px auto;}</style><body><p style=\"font-size: .4em\">%s</p><p style=\"font-size: .7em\">%s</p><p style=\"font-size: 1em\">%s</p><p style=\"font-size: 1.3em\">%s</p><p style=\"font-size: 1.6em\">%s</p></body></html>";

    String HTML;
    private View mRoot;
    private WebView mWebView;

    public WebViewPreview(
            Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public WebViewPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WebViewPreview(Context context) {
        super(context);
        init(context);
    }

    void init(Context context) {
        Resources res = context.getResources();
        Object[] visualNames = res.getStringArray(R.array.pref_text_size_choices);
        HTML = String.format(HTML_FORMAT, visualNames);
    }

    void updatePreview() {
        if (mWebView == null) return;

        BrowserSettings.getInstance().syncSharedPreferences(getContext(),
                PreferenceManager.getDefaultSharedPreferences(getContext()));
        WebSettings ws = mWebView.getSettings();
        BrowserSettings bs = BrowserSettings.getInstance();
        ws.setMinimumFontSize(bs.getMinimumFontSize());
        ws.setTextSize(bs.getTextSize());
        mWebView.loadData(HTML, "text/html", "utf-8");
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        if (mWebView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            mRoot = inflater.inflate(R.layout.webview_preview, parent, false);
            mWebView = (WebView) mRoot.findViewById(R.id.webview);
        }
        updatePreview();
        return mRoot;
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPrepareForRemoval() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPrepareForRemoval();
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {
        updatePreview();
    }

}
