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
package com.android.browser;

import com.android.browser.Controller;
import com.android.browser.R;
import com.android.browser.UI.DropdownChangeListener;
import com.android.browser.provider.BrowserProvider;
import com.android.browser.search.SearchEngine;

import android.app.SearchManager;
import android.content.Context;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.webkit.SearchBox;
import android.webkit.WebView;

import java.util.Collections;
import java.util.List;

public class InstantSearchEngine implements SearchEngine, DropdownChangeListener {
    private static final String TAG = "Browser.InstantSearchEngine";
    private static final boolean DBG = false;

    private Controller mController;
    private SearchBox mSearchBox;
    private final BrowserSearchboxListener mListener = new BrowserSearchboxListener();
    private int mHeight;

    private String mInstantBaseUrl;
    private final Context mContext;
    // Used for startSearch( ) calls if for some reason instant
    // is off, or no searchbox is present.
    private final SearchEngine mWrapped;

    public InstantSearchEngine(Context context, SearchEngine wrapped) {
        mContext = context.getApplicationContext();
        mWrapped = wrapped;
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    @Override
    public String getName() {
        return SearchEngine.GOOGLE;
    }

    @Override
    public CharSequence getLabel() {
        return mContext.getResources().getString(R.string.instant_search_label);
    }

    @Override
    public void startSearch(Context context, String query, Bundle appData, String extraData) {
        if (DBG) Log.d(TAG, "startSearch(" + query + ")");

        switchSearchboxIfNeeded();

        // If for some reason we are in a bad state, ensure that the
        // user gets default search results at the very least.
        if (mSearchBox == null || !isInstantPage()) {
            mWrapped.startSearch(context, query, appData, extraData);
            return;
        }

        mSearchBox.setQuery(query);
        mSearchBox.setVerbatim(true);
        mSearchBox.onsubmit(null);
    }

    private final class BrowserSearchboxListener extends SearchBox.SearchBoxListener {
        /*
         * The maximum number of out of order suggestions we accept
         * before giving up the wait.
         */
        private static final int MAX_OUT_OF_ORDER = 5;

        /*
         * We wait for suggestions in increments of 600ms. This is primarily to
         * guard against suggestions arriving out of order.
         */
        private static final int WAIT_INCREMENT_MS = 600;

        /*
         * A cache of suggestions received, keyed by the queries they were
         * received for.
         */
        private final LruCache<String, List<String>> mSuggestions =
                new LruCache<String, List<String>>(20);

        /*
         * The last set of suggestions received. We use this reduce UI flicker
         * in case there is a delay in recieving suggestions.
         */
        private List<String> mLatestSuggestion = Collections.emptyList();

        @Override
        public synchronized void onSuggestionsReceived(String query, List<String> suggestions) {
            if (DBG) Log.d(TAG, "onSuggestionsReceived(" + query + ")");

            if (!TextUtils.isEmpty(query)) {
                mSuggestions.put(query, suggestions);
                mLatestSuggestion = suggestions;
            }

            notifyAll();
        }

        public synchronized List<String> tryWaitForSuggestions(String query) {
            if (DBG) Log.d(TAG, "tryWait(" + query + ")");

            int numWaitReturns = 0;

            // This slightly unusual waiting construct is used to safeguard
            // to some extent against suggestions arriving out of order. We
            // wait for upto 5 notifyAll( ) calls to check if we received
            // suggestions for a given query.
            while (mSuggestions.get(query) == null)  {
                try {
                    wait(WAIT_INCREMENT_MS);
                    ++numWaitReturns;
                    if (numWaitReturns > MAX_OUT_OF_ORDER) {
                        // We've waited too long for suggestions to be returned.
                        // return the last available suggestion.
                        break;
                    }
                } catch (InterruptedException e) {
                    return Collections.emptyList();
                }
            }

            List<String> suggestions = mSuggestions.get(query);
            if (suggestions == null) {
                return mLatestSuggestion;
            }

            return suggestions;
        }

        public synchronized void clear() {
            mSuggestions.evictAll();
        }
    }

    private WebView getCurrentWebview() {
        if (mController != null) {
            return mController.getTabControl().getCurrentTopWebView();
        }

        return null;
    }

    /**
     * Attaches the searchbox to the right browser page, i.e, the currently
     * visible tab.
     */
    private void switchSearchboxIfNeeded() {
        final WebView current = getCurrentWebview();
        if (current == null) {
            return;
        }

        final SearchBox searchBox = current.getSearchBox();
        if (searchBox != mSearchBox) {
            if (mSearchBox != null) {
                mSearchBox.removeSearchBoxListener(mListener);
                mListener.clear();
            }
            mSearchBox = searchBox;
            if (mSearchBox != null) {
                mSearchBox.addSearchBoxListener(mListener);
            }
        }
    }

    private boolean isInstantPage() {
        final WebView current = getCurrentWebview();
        if (current == null) {
            return false;
        }

        final String currentUrl = mController.getCurrentTab().getUrl();

        if (currentUrl != null) {
            Uri uri = Uri.parse(currentUrl);
            final String host = uri.getHost();
            final String path = uri.getPath();

            // Is there a utility class that does this ?
            if (path != null && host != null) {
                return host.startsWith("www.google.") &&
                        (path.startsWith("/search") || path.startsWith("/webhp"));
            }
            return false;
        }

        return false;
    }

    private void loadInstantPage() {
        mController.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final WebView current = getCurrentWebview();
                if (current != null) {
                    current.loadUrl(getInstantBaseUrl());
                }
            }
        });
    }

    /**
     * Queries for a given search term and returns a cursor containing
     * suggestions ordered by best match.
     */
    @Override
    public Cursor getSuggestions(Context context, String query) {
        if (DBG) Log.d(TAG, "getSuggestions(" + query + ")");
        if (query == null) {
            return null;
        }

        if (!isInstantPage()) {
            loadInstantPage();
        }

        switchSearchboxIfNeeded();

        mController.registerDropdownChangeListener(this);

        if (mSearchBox == null) {
            return mWrapped.getSuggestions(context, query);
        }

        mSearchBox.setDimensions(0, 0, 0, mHeight);
        mSearchBox.onresize(null);

        if (TextUtils.isEmpty(query)) {
            // To force the SRP to render an empty (no results) page.
            mSearchBox.setVerbatim(true);
        } else {
            mSearchBox.setVerbatim(false);
        }
        mSearchBox.setQuery(query);
        mSearchBox.onchange(null);

        // Don't bother waiting for suggestions for an empty query. We still
        // set the query so that the SRP clears itself.
        if (TextUtils.isEmpty(query)) {
            return new SuggestionsCursor(Collections.<String>emptyList());
        } else {
            return new SuggestionsCursor(mListener.tryWaitForSuggestions(query));
        }
    }

    @Override
    public boolean supportsSuggestions() {
        return true;
    }

    @Override
    public void close() {
        if (mController != null) {
            mController.registerDropdownChangeListener(null);
        }
        if (mSearchBox != null) {
            mSearchBox.removeSearchBoxListener(mListener);
        }
        mListener.clear();
        mWrapped.close();
    }

    @Override
    public boolean supportsVoiceSearch() {
        return false;
    }

    @Override
    public String toString() {
        return "InstantSearchEngine {" + hashCode() + "}";
    }

    @Override
    public boolean wantsEmptyQuery() {
        return true;
    }

    private int rescaleHeight(int height) {
        final WebView current = getCurrentWebview();
        if (current == null) {
            return 0;
        }

        final float scale = current.getScale();
        if (scale != 0) {
            return (int) (height / scale);
        }

        return height;
    }

    @Override
    public void onNewDropdownDimensions(int height) {
        final int rescaledHeight = rescaleHeight(height);

        if (rescaledHeight != mHeight) {
            mHeight = rescaledHeight;
            if (mSearchBox != null) {
                mSearchBox.setDimensions(0, 0, 0, rescaledHeight);
                mSearchBox.onresize(null);
            }
        }
    }

    private String getInstantBaseUrl() {
        if (mInstantBaseUrl == null) {
            String url = mContext.getResources().getString(R.string.instant_base);
            if (url.indexOf("{CID}") != -1) {
                url = url.replace("{CID}",
                        BrowserProvider.getClientId(mContext.getContentResolver()));
            }
            mInstantBaseUrl = url;
        }

        return mInstantBaseUrl;
    }

    // Indices of the columns in the below arrays.
    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_QUERY = 1;
    private static final int COLUMN_INDEX_ICON = 2;
    private static final int COLUMN_INDEX_TEXT_1 = 3;

    private static final String[] COLUMNS_WITHOUT_DESCRIPTION = new String[] {
        "_id",
        SearchManager.SUGGEST_COLUMN_QUERY,
        SearchManager.SUGGEST_COLUMN_ICON_1,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
    };

    private static class SuggestionsCursor extends AbstractCursor {
        private final List<String> mSuggestions;

        public SuggestionsCursor(List<String> suggestions) {
            mSuggestions = suggestions;
        }

        @Override
        public int getCount() {
            return mSuggestions.size();
        }

        @Override
        public String[] getColumnNames() {
            return COLUMNS_WITHOUT_DESCRIPTION;
        }

        private String format(String suggestion) {
            if (TextUtils.isEmpty(suggestion)) {
                return "";
            }
            return suggestion;
        }

        @Override
        public String getString(int column) {
            if (mPos >= 0 && mPos < mSuggestions.size()) {
              if ((column == COLUMN_INDEX_QUERY) || (column == COLUMN_INDEX_TEXT_1)) {
                  return format(mSuggestions.get(mPos));
              } else if (column == COLUMN_INDEX_ICON) {
                  return String.valueOf(R.drawable.magnifying_glass);
              }
            }
            return null;
        }

        @Override
        public double getDouble(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int column) {
            if (column == COLUMN_INDEX_ID) {
                return mPos;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(int column) {
            throw new UnsupportedOperationException();
        }

        @Override
        public short getShort(int column) {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNull(int column) {
            throw new UnsupportedOperationException();
        }
    }
}
