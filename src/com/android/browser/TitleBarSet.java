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
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Gallery;
import android.widget.SpinnerAdapter;

import java.util.Vector;

/**
 * The TitleBarSet holds a TitleBar for each open "tab" in the browser.
 */
public class TitleBarSet extends Gallery
        implements AdapterView.OnItemSelectedListener {
    private Vector<TitleBar>    mTitleBars;
    private BrowserActivity     mBrowserActivity;
    private int                 mCount;
    private TitleAdapter        mTitleAdapter;
    private boolean             mIgnoreSelectedListener;
    private MotionEvent         mLastTouchUp;

    public TitleBarSet(Context context) {
        this(context, null);
    }

    public TitleBarSet(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTitleBars = new Vector<TitleBar>(TabControl.MAX_TABS);
        mCount = 0;
        mTitleAdapter = new TitleAdapter();
        setAdapter(mTitleAdapter);
        setCallbackDuringFling(false);
        setCallbackOnUnselectedItemClick(true);
        setSpacing(0);
        setOnItemSelectedListener(this);
    }

    /**
     * Add a tab/titlebar to our set.  Called when BrowserActivity adds a new
     * Tab to its TabControl.
     * @param view WebView associated with this tab.  Used to determine whether
     *             updates are going to the correct place.
     * @param selected Whether to set the new tab to be selected.
     */
    /* package */ void addTab(WebView view, boolean selected) {
        if (TabControl.MAX_TABS == mCount) {
            return;
        }
        int newSelection = mCount;
        TitleBar titleBar = new TitleBar(getContext(), view, mBrowserActivity);
        mTitleBars.add(titleBar);
        mCount++;
        // Need to refresh our list
        setAdapter(mTitleAdapter);
        mIgnoreSelectedListener = true;
        // No need to call onItemSelected, since the Tab in BrowserActivity has
        // already been changed.
        if (selected) {
            setSelection(newSelection);
        }
        mIgnoreSelectedListener = false;
    }

    /**
     * Convenience method to get a particular title bar.
     */
    private TitleBar getTitleBarAt(int position) {
        if (position < 0 || position >= mCount) {
            return null;
        }
        return (TitleBar) mTitleBars.elementAt(position);
    }

    /**
     * Implementation for OnItemSelectedListener
     */
    public void onItemSelected(AdapterView<?> parent, View view, int position,
            long id) {
        if (mIgnoreSelectedListener || !(view instanceof TitleBar)) {
            return;
        }
        mBrowserActivity.switchToTab(position);
        // In case the WebView finished loading while this TitleBar was out of
        // focus, make sure all its data is up to date
        TitleBar titleBar = getTitleBarAt(position);
        WebView webview = titleBar.getWebView();
        if (webview == null) {
            // FIXME: Possible that the tab needs to be restored.
            return;
        }
        if (webview.getProgress() == 100) {
            titleBar.setProgress(100);
            titleBar.setTitleAndUrl(webview.getTitle(), webview.getUrl());
            // FIXME: Pass in a bitmap, so we can always update the bitmap
            // properly
            //titleBar.setFavicon(webview.getFavicon());
        }
    }

    /**
     * Implementation for OnItemSelectedListener
     */
    public void onNothingSelected(AdapterView<?> parent) {
        // do nothing
    }

    /**
     * Override from GestureDetector.OnGestureListener.  Store the MotionEvent
     * so performItemClick can know how to handle the click.
     */
    public boolean onSingleTapUp(MotionEvent e) {
        mLastTouchUp = e;
        // super.onSingleTapUp will call performItemClick
        boolean result = super.onSingleTapUp(e);
        mLastTouchUp = null;
        return result;
    }

    /**
     * Override from View to ensure that the TitleBars get resized to match
     * the new screen width
     */
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int selection = getSelectedItemPosition();
        // Need to make sure getView gets called again
        setAdapter(mTitleAdapter);
        // Stay on the same tab
        setCurrentTab(selection);
    }

    /**
     * Override from AdapterView.  Using simple OnClickListeners overrides
     * the GestureDetector.OnGestureListener, so we handle it here.
     */
    public boolean performItemClick(View view, int position, long id) {
        if (!(view instanceof TitleBar)) {
            return super.performItemClick(view, position, id);
        }
        // If we have no mLastTouchUp, this was not called from onSingleTapUp,
        // so ignore it.
        if (null == mLastTouchUp) {
            return false;
        }
        TitleBar titleBar = (TitleBar) view;
        // If the user clicks on a view which is not selected, the Gallery will
        // take care of making it selected.
        if (titleBar != getTitleBarAt(position)) {
            return false;
        }
        mBrowserActivity.onSearchRequested();
        return true;
    }

    /**
     * Remove the tab at the given position.
     */
    /* package */ void removeTab(int position) {
        int selection = getSelectedItemPosition();
        mTitleBars.remove(position);
        mCount--;
        // Need to refresh our list
        setAdapter(mTitleAdapter);
        setCurrentTab(selection);
    }

    /**
     * Convenience method to get the currently selected title bar.
     */
    private TitleBar selectedTitleBar() {
        return getTitleBarAt(getSelectedItemPosition());
    }

    /**
     * Set the owning BrowserActivity.  Necessary so that we can call methods
     * on it.  Only called once before adding any title bars.
     */
    /* package */ void init(final BrowserActivity ba) {
        mBrowserActivity = ba;
    }

    /**
     * Change to the tab at the new position.
     */
    /* package */ void setCurrentTab(int position) {
        if (position < 0 || position >= mCount) return;
        mIgnoreSelectedListener = true;
        setSelection(position);
        mIgnoreSelectedListener = false;
    }

    /**
     * Update the Favicon of the currently selected tab.
     * @param d The new Drawable for the Favicon
     * @param topWindow The WebView which posted the update.  If it does not
     *                  match the WebView of the currently selected tab, do
     *                  nothing, since that tab is not being displayed.
     */
    /* package */ void setFavicon(Drawable d, WebView topWindow) {
        TitleBar current = selectedTitleBar();
        if (current != null && current.getWebView() == topWindow) {
            current.setFavicon(d);
        }
    }

    /**
     * Update the lock icon of the currently selected tab.
     * @param d The new Drawable for the lock icon
     * @param topWindow The WebView which posted the update.  If it does not
     *                  match the WebView of the currently selected tab, do
     *                  nothing, since that tab is not being displayed.
     */
    /* package */ void setLock(Drawable d, WebView topWindow) {
        TitleBar current = selectedTitleBar();
        if (current != null && current.getWebView() == topWindow) {
            current.setLock(d);
        }
    }
    /**
     * Update the progress of the currently selected tab.
     * @param newProgress The progress, between 0 and 100, of the current tab.
     * @param topWindow The WebView which posted the update.  If it does not
     *                  match the WebView of the currently selected tab, do
     *                  nothing, since that tab is not being displayed.
     */
    /* package */ void setProgress(int newProgress, WebView topWindow) {
        TitleBar current = selectedTitleBar();
        if (current != null && current.getWebView() == topWindow) {
            current.setProgress(newProgress);
        }
    }
    /**
     * Update the title and URL of the currently selected tab.
     * @param title The title of the webpage
     * @param url   The URL of the webpage
     * @param topWindow The WebView which posted the update.  If it does not
     *                  match the WebView of the currently selected tab, do
     *                  nothing, since that tab is not being displayed.
     */
    /* package */ void setTitleAndUrl(CharSequence title, CharSequence url,
            WebView topWindow) {
        TitleBar current = selectedTitleBar();
        if (current != null && current.getWebView() == topWindow) {
            current.setTitleAndUrl(title, url);
        }
    }

    // FIXME: Remove
    /* package */ void setToTabPicker() {
        TitleBar current = selectedTitleBar();
        if (current != null) {
            current.setToTabPicker();
        }
    }

    /**
     * Custom adapter which provides the TitleBars and the NewButton to the
     * Gallery.
     */
    private class TitleAdapter implements SpinnerAdapter {
        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            return null;
        }
        public void registerDataSetObserver(DataSetObserver observer) {}
        public void unregisterDataSetObserver(DataSetObserver observer) {}
        public int getCount() {
            return mCount;
        }
        public Object getItem(int position) {
            return null;
        }
        public long getItemId(int position) {
            return position;
        }
        public boolean hasStableIds() {
            return true;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            TitleBar titleBar = getTitleBarAt(position);
            Gallery.LayoutParams lp;
            int desiredWidth = TitleBarSet.this.getWidth();
            ViewGroup.LayoutParams old = titleBar.getLayoutParams();
            if (old == null || !(old instanceof Gallery.LayoutParams)) {
                lp = new Gallery.LayoutParams(desiredWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                titleBar.setLayoutParams(lp);
            } else {
                lp = (Gallery.LayoutParams) old;
                if (lp.width != desiredWidth) {
                    lp.width = desiredWidth;
                    titleBar.setLayoutParams(lp);
                    requestLayout();
                }
            }
            return titleBar;
        }
        public int getItemViewType(int position) {
            // We are managing our own views.
            return AdapterView.ITEM_VIEW_TYPE_IGNORE;
        }
        public int getViewTypeCount() {
            return 1;
        }
        public boolean isEmpty() {
            // Will never be empty, because the NewButton is always there
            // (though sometimes disabled).
            return false;
        }
    }
}
