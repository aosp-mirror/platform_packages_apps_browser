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

import com.android.browser.search.SearchEngine;

import android.app.SearchManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BrowserContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * adapter to wrap multiple cursors for url/search completions
 */
public class SuggestionsAdapter extends BaseAdapter implements Filterable, OnClickListener {

    static final int TYPE_SEARCH = 0;
    static final int TYPE_SUGGEST = 1;
    static final int TYPE_BOOKMARK = 2;
    static final int TYPE_SUGGEST_URL = 3;
    static final int TYPE_HISTORY = 4;

    private static final String[] COMBINED_PROJECTION =
            {BrowserContract.Combined._ID, BrowserContract.Combined.TITLE,
                    BrowserContract.Combined.URL, BrowserContract.Combined.IS_BOOKMARK};

    private static final String[] SEARCHES_PROJECTION = {BrowserContract.Searches.SEARCH};

    private static final String COMBINED_SELECTION =
            "(url LIKE ? OR url LIKE ? OR url LIKE ? OR url LIKE ? OR title LIKE ?)";

    // Regular expression which matches http://, followed by some stuff, followed by
    // optionally a trailing slash, all matched as separate groups.
    private static final Pattern STRIP_URL_PATTERN = Pattern.compile("^(http://)(.*?)(/$)?");

    Context mContext;
    Filter mFilter;
    SuggestionResults mResults;
    List<CursorSource> mSources;
    boolean mLandscapeMode;
    CompletionListener mListener;
    int mLinesPortrait;
    int mLinesLandscape;

    interface CompletionListener {

        public void onSearch(String txt);

        public void onSelect(String txt);

        public void onFilterComplete(int count);

    }

    public SuggestionsAdapter(Context ctx, CompletionListener listener) {
        mContext = ctx;
        mListener = listener;
        mLinesPortrait = mContext.getResources().
                getInteger(R.integer.max_suggest_lines_portrait);
        mLinesLandscape = mContext.getResources().
                getInteger(R.integer.max_suggest_lines_landscape);
        mFilter = new SuggestFilter();
        addSource(new SuggestCursor());
        addSource(new SearchesCursor());
        addSource(new CombinedCursor());
    }

    public void setLandscapeMode(boolean mode) {
        mLandscapeMode = mode;
    }

    public int getLeftCount() {
        return mResults.getLeftCount();
    }

    public int getRightCount() {
        return mResults.getRightCount();
    }

    public void addSource(CursorSource c) {
        if (mSources == null) {
            mSources = new ArrayList<CursorSource>(5);
        }
        mSources.add(c);
    }

    @Override
    public void onClick(View v) {
        if (R.id.icon2 == v.getId()) {
            // replace input field text with suggestion text
            SuggestItem item = (SuggestItem) ((View) v.getParent()).getTag();
            mListener.onSearch(item.title);
        } else {
            SuggestItem item = (SuggestItem) v.getTag();
            mListener.onSelect((TextUtils.isEmpty(item.url)? item.title : item.url));
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    @Override
    public int getCount() {
        return (mResults == null) ? 0 : mResults.getLineCount();
    }

    @Override
    public SuggestItem getItem(int position) {
        if (mResults == null) {
            return null;
        }
        if (mLandscapeMode) {
            if (position >= mResults.getLineCount()) {
                // right column
                position = position - mResults.getLineCount();
                // index in column
                if (position >= mResults.getRightCount()) {
                    return null;
                }
                return mResults.items.get(position + mResults.getLeftCount());
            } else {
                // left column
                if (position >= mResults.getLeftCount()) {
                    return null;
                }
                return mResults.items.get(position);
            }
        } else {
            return mResults.items.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        if (mLandscapeMode) {
            View view = inflater.inflate(R.layout.suggestion_two_column, parent, false);
            SuggestItem item = getItem(position);
            View iv = view.findViewById(R.id.suggest1);
            LayoutParams lp = new LayoutParams(iv.getLayoutParams());
            lp.weight = 0.5f;
            iv.setLayoutParams(lp);
            if (item != null) {
                bindView(iv, item);
            } else {
                iv.setVisibility((mResults.getLeftCount() == 0) ? View.GONE :
                        View.INVISIBLE);
            }
            item = getItem(position + mResults.getLineCount());
            iv = view.findViewById(R.id.suggest2);
            lp = new LayoutParams(iv.getLayoutParams());
            lp.weight = 0.5f;
            iv.setLayoutParams(lp);
            if (item != null) {
                bindView(iv, item);
            } else {
                iv.setVisibility((mResults.getRightCount() == 0) ? View.GONE :
                        View.INVISIBLE);
            }
            return view;
        } else {
            View view = inflater.inflate(R.layout.suggestion_item, parent, false);
            bindView(view, getItem(position));
            return view;
        }
    }

    private void bindView(View view, SuggestItem item) {
        // store item for click handling
        view.setTag(item);
        TextView tv1 = (TextView) view.findViewById(android.R.id.text1);
        TextView tv2 = (TextView) view.findViewById(android.R.id.text2);
        ImageView ic1 = (ImageView) view.findViewById(R.id.icon1);
        View spacer = view.findViewById(R.id.spacer);
        View ic2 = view.findViewById(R.id.icon2);
        View div = view.findViewById(R.id.divider);
        tv1.setText(item.title);
        tv2.setText(item.url);
        int id = -1;
        switch (item.type) {
            case TYPE_SUGGEST:
            case TYPE_SEARCH:
                id = R.drawable.ic_search_category_suggest;
                break;
            case TYPE_BOOKMARK:
                id = R.drawable.ic_search_category_bookmark;
                break;
            case TYPE_HISTORY:
                id = R.drawable.ic_search_category_history;
                break;
            case TYPE_SUGGEST_URL:
                id = R.drawable.ic_search_category_browser;
                break;
            default:
                id = -1;
        }
        if (id != -1) {
            ic1.setImageDrawable(mContext.getResources().getDrawable(id));
        }
        ic2.setVisibility(((TYPE_SUGGEST == item.type) || (TYPE_SEARCH == item.type))
                ? View.VISIBLE : View.GONE);
        div.setVisibility(ic2.getVisibility());
        spacer.setVisibility(((TYPE_SUGGEST == item.type) || (TYPE_SEARCH == item.type))
                ? View.GONE : View.INVISIBLE);
        view.setOnClickListener(this);
        ic2.setOnClickListener(this);
    }

    class SuggestFilter extends Filter {

        SuggestionResults results;

        @Override
        public CharSequence convertResultToString(Object item) {
            if (item == null) {
                return "";
            }
            SuggestItem sitem = (SuggestItem) item;
            if (sitem.title != null) {
                return sitem.title;
            } else {
                return sitem.url;
            }
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults res = new FilterResults();
            if (TextUtils.isEmpty(constraint)) {
                res.count = 0;
                res.values = null;
                return res;
            }
            results = new SuggestionResults();
            if (constraint != null) {
                for (CursorSource sc : mSources) {
                    sc.runQuery(constraint);
                }
                mixResults();
            }
            res.count = results.getLineCount();
            res.values = results;
            return res;
        }

        void mixResults() {
            for (int i = 0; i < mSources.size(); i++) {
                CursorSource s = mSources.get(i);
                int n = Math.min(s.getCount(), (mLandscapeMode ? mLinesLandscape
                        : mLinesPortrait));
                boolean more = false;
                for (int j = 0; j < n; j++) {
                    results.addResult(s.getItem());
                    more = s.moveToNext();
                }
                if (s instanceof SuggestCursor) {
                    int k = n;
                    while (more && (k < mLinesPortrait)) {
                        SuggestItem item  = s.getItem();
                        if (item.type == TYPE_SUGGEST_URL) {
                            results.addResult(item);
                            break;
                        }
                        more = s.moveToNext();
                        k++;

                    }
                }
            }
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults fresults) {
            mResults = (SuggestionResults) fresults.values;
            mListener.onFilterComplete(fresults.count);
            notifyDataSetChanged();
        }

    }

    /**
     * sorted list of results of a suggestion query
     *
     */
    class SuggestionResults {

        ArrayList<SuggestItem> items;
        // count per type
        int[] counts;

        SuggestionResults() {
            items = new ArrayList<SuggestItem>(24);
            // n of types:
            counts = new int[5];
        }

        int getTypeCount(int type) {
            return counts[type];
        }

        void addResult(SuggestItem item) {
            int ix = 0;
            while ((ix < items.size()) && (item.type >= items.get(ix).type))
                ix++;
            items.add(ix, item);
            counts[item.type]++;
        }

        int getLineCount() {
            if (mLandscapeMode) {
                return Math.max(getLeftCount(), getRightCount());
            } else {
                return getLeftCount() + getRightCount();
            }
        }

        int getLeftCount() {
            return counts[TYPE_SEARCH] + counts[TYPE_SUGGEST];
        }

        int getRightCount() {
            return counts[TYPE_BOOKMARK] + counts[TYPE_HISTORY] + counts[TYPE_SUGGEST_URL];
        }

        public String toString() {
            if (items == null) return null;
            if (items.size() == 0) return "[]";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                SuggestItem item = items.get(i);
                sb.append(item.type + ": " + item.title);
                if (i < items.size() - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }
    }

    /**
     * data object to hold suggestion values
     */
    class SuggestItem {
        String title;
        String url;
        int type;

        public SuggestItem(String text, String u, int t) {
            title = text;
            url = u;
            type = t;
        }
    }

    abstract class CursorSource {

        Cursor mCursor;

        boolean moveToNext() {
            return mCursor.moveToNext();
        }

        public abstract void runQuery(CharSequence constraint);

        public abstract SuggestItem getItem();

        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }

        public void close() {
            if (mCursor != null) {
                mCursor.close();
            }
        }
    }

    /**
     * combined bookmark & history source
     */
    class CombinedCursor extends CursorSource {

        @Override
        public SuggestItem getItem() {
            if ((mCursor != null) && (!mCursor.isAfterLast())) {
                String title = mCursor.getString(1);
                String url = mCursor.getString(2);
                boolean isBookmark = (mCursor.getInt(3) == 1);
                return new SuggestItem(getTitle(title, url), getUrl(title, url),
                        isBookmark ? TYPE_BOOKMARK : TYPE_HISTORY);
            }
            return null;
        }

        @Override
        public void runQuery(CharSequence constraint) {
            // constraint != null
            if (mCursor != null) {
                mCursor.close();
            }
            String like = constraint + "%";
            String[] args = null;
            String selection = null;
            if (like.startsWith("http") || like.startsWith("file")) {
                args = new String[1];
                args[0] = like;
                selection = "url LIKE ?";
            } else {
                args = new String[5];
                args[0] = "http://" + like;
                args[1] = "http://www." + like;
                args[2] = "https://" + like;
                args[3] = "https://www." + like;
                // To match against titles.
                args[4] = like;
                selection = COMBINED_SELECTION;
            }
            Uri.Builder ub = BrowserContract.Combined.CONTENT_URI.buildUpon();
            ub.appendQueryParameter(BrowserContract.PARAM_LIMIT,
                    Integer.toString(mLinesPortrait));
            mCursor =
                    mContext.getContentResolver().query(ub.build(), COMBINED_PROJECTION,
                            selection,
                            (constraint != null) ? args : null,
                            BrowserContract.Combined.VISITS + " DESC, " +
                            BrowserContract.Combined.DATE_LAST_VISITED + " DESC");
            if (mCursor != null) {
                mCursor.moveToFirst();
            }
        }

        /**
         * Provides the title (text line 1) for a browser suggestion, which should be the
         * webpage title. If the webpage title is empty, returns the stripped url instead.
         *
         * @return the title string to use
         */
        private String getTitle(String title, String url) {
            if (TextUtils.isEmpty(title) || TextUtils.getTrimmedLength(title) == 0) {
                title = stripUrl(url);
            }
            return title;
        }

        /**
         * Provides the subtitle (text line 2) for a browser suggestion, which should be the
         * webpage url. If the webpage title is empty, then the url should go in the title
         * instead, and the subtitle should be empty, so this would return null.
         *
         * @return the subtitle string to use, or null if none
         */
        private String getUrl(String title, String url) {
            if (TextUtils.isEmpty(title)
                    || TextUtils.getTrimmedLength(title) == 0
                    || title.equals(url)) {
                return null;
            } else {
                return stripUrl(url);
            }
        }

        /**
         * Strips the provided url of preceding "http://" and any trailing "/". Does not
         * strip "https://". If the provided string cannot be stripped, the original string
         * is returned.
         *
         * TODO: Put this in TextUtils to be used by other packages doing something similar.
         *
         * @param url a url to strip, like "http://www.google.com/"
         * @return a stripped url like "www.google.com", or the original string if it could
         *         not be stripped
         */
        private String stripUrl(String url) {
            if (url == null) return null;
            Matcher m = STRIP_URL_PATTERN.matcher(url);
            if (m.matches() && m.groupCount() == 3) {
                return m.group(2);
            } else {
                return url;
            }
        }

    }

    class SearchesCursor extends CursorSource {

        @Override
        public SuggestItem getItem() {
            if ((mCursor != null) && (!mCursor.isAfterLast())) {
                return new SuggestItem(mCursor.getString(0), null, TYPE_SEARCH);
            }
            return null;
        }

        @Override
        public void runQuery(CharSequence constraint) {
            // constraint != null
            if (mCursor != null) {
                mCursor.close();
            }
            String like = constraint + "%";
            String[] args = new String[] {constraint.toString()};
            String selection = BrowserContract.Searches.SEARCH + " LIKE ?";
            Uri.Builder ub = BrowserContract.Searches.CONTENT_URI.buildUpon();
            ub.appendQueryParameter(BrowserContract.PARAM_LIMIT,
                    Integer.toString(mLinesPortrait));
            mCursor =
                    mContext.getContentResolver().query(ub.build(), SEARCHES_PROJECTION,
                            selection,
                            args, BrowserContract.Searches.DATE + " DESC");
            if (mCursor != null) {
                mCursor.moveToFirst();
            }
        }

    }

    class SuggestCursor extends CursorSource {

        @Override
        public SuggestItem getItem() {
            if (mCursor != null) {
                String title = mCursor.getString(
                        mCursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1));
                String text2 = mCursor.getString(
                        mCursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2));
                String url = mCursor.getString(
                        mCursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2_URL));
                String uri = mCursor.getString(
                        mCursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA));
                int type = (TextUtils.isEmpty(url)) ? TYPE_SUGGEST : TYPE_SUGGEST_URL;
                return new SuggestItem(title, url, type);
            }
            return null;
        }

        @Override
        public void runQuery(CharSequence constraint) {
            if (mCursor != null) {
                mCursor.close();
            }
            if (!TextUtils.isEmpty(constraint)) {
                SearchEngine searchEngine = BrowserSettings.getInstance().getSearchEngine();
                if (searchEngine != null && searchEngine.supportsSuggestions()) {
                    mCursor = searchEngine.getSuggestions(mContext, constraint.toString());
                    if (mCursor != null) {
                        mCursor.moveToFirst();
                    }
                }
            } else {
                mCursor = null;
            }
        }

    }

}
