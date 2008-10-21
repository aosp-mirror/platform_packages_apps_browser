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

import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.ServiceManager;
import android.provider.Browser;
import android.text.IClipboard;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ContextMenu.ContextMenuInfo;
import android.webkit.DateSorter;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * Activity for displaying the browser's history, divided into
 * days of viewing.
 */
public class BrowserHistoryPage extends ListActivity {
    private HistoryAdapter          mAdapter;
    private DateSorter              mDateSorter;
    private boolean                 mMaxTabsOpen;

    private final static String LOGTAG = "browser";

    // FIXME: Make this a part of Browser so we do not have more than one
    // copy (other copy is in BrowserBookmarksAdapter).
    // Used to store favicons as we get them from the database
    private final HashMap<String, Bitmap> mUrlsToIcons =
            new HashMap<String, Bitmap>();
    // Implementation of WebIconDatabase.IconListener
    private class IconReceiver implements IconListener {
        public void onReceivedIcon(String url, Bitmap icon) {
            mUrlsToIcons.put(url, icon);
            setListAdapter(mAdapter);
        }
    }
    // Instance of IconReceiver
    private final IconReceiver mIconReceiver = new IconReceiver();

    /**
     * Report back to the calling activity to load a site.
     * @param url   Site to load.
     * @param newWindow True if the URL should be loaded in a new window
     */
    private void loadUrl(String url, boolean newWindow) {
        Intent intent = new Intent().setAction(url);
        if (newWindow) {
            Bundle b = new Bundle();
            b.putBoolean("new_window", true);
            intent.putExtras(b);
        }
        setResult(RESULT_OK, intent);
        finish();
    }
    
    private void copy(CharSequence text) {
        try {
            IClipboard clip = IClipboard.Stub.asInterface(ServiceManager.getService("clipboard"));
            if (clip != null) {
                clip.setClipboardText(text);
            }
        } catch (android.os.RemoteException e) {
            Log.e(LOGTAG, "Copy failed", e);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setTitle(R.string.browser_history);
        
        mDateSorter = new DateSorter(this);

        mAdapter = new HistoryAdapter();
        setListAdapter(mAdapter);
        ListView list = getListView();
        list.setOnCreateContextMenuListener(this);
        LayoutInflater factory = LayoutInflater.from(this);
        View v = factory.inflate(R.layout.empty_history, null);
        addContentView(v, new LayoutParams(LayoutParams.FILL_PARENT,
                LayoutParams.FILL_PARENT));
        list.setEmptyView(v);

        mMaxTabsOpen = getIntent().getBooleanExtra("maxTabsOpen", false);
        Browser.requestAllIcons(getContentResolver(), null, mIconReceiver);
        
        // initialize the result to canceled, so that if the user just presses
        // back then it will have the correct result
        setResult(RESULT_CANCELED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.history, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.clear_history_menu_id).setVisible(Browser.canClearHistory(this.getContentResolver()));
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_history_menu_id:
                // FIXME: Need to clear the tab control in browserActivity 
                // as well
                Browser.clearHistory(getContentResolver());
                mAdapter.refreshData();
                return true;
                
            default:
                break;
        }  
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo i = 
            (AdapterView.AdapterContextMenuInfo)
            menuInfo;

        // Inflate the menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.historycontext, menu);

        // Setup the header
        menu.setHeaderTitle(((HistoryItem)i.targetView).getUrl());

        // Only show open in new tab if we have not maxed out available tabs
        menu.findItem(R.id.new_window_context_menu_id).setVisible(!mMaxTabsOpen);
        
     // decide whether to show the share link option
        PackageManager pm = getPackageManager();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        List<ResolveInfo> list = pm.queryIntentActivities(send,
                PackageManager.MATCH_DEFAULT_ONLY);
        menu.findItem(R.id.share_link_context_menu_id).setVisible(
                list.size() > 0);
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo i = 
            (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        String url = ((HistoryItem)i.targetView).getUrl();
        String title = ((HistoryItem)i.targetView).getName();
        switch (item.getItemId()) {
            case R.id.open_context_menu_id:
                loadUrl(url, false);
                return true;
            case R.id.new_window_context_menu_id:
                loadUrl(url, true);
                return true;
            case R.id.save_to_bookmarks_menu_id:
                Browser.saveBookmark(this, title, url);
                return true;
            case R.id.share_link_context_menu_id:
                Browser.sendString(this, url);
                return true;
            case R.id.copy_context_menu_id:
                copy(url);
                return true;
            case R.id.delete_context_menu_id:
                Browser.deleteFromHistory(getContentResolver(), url);
                mAdapter.refreshData();
                return true;
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (v instanceof HistoryItem) {
            loadUrl(((HistoryItem) v).getUrl(), false);
        }
    }

    private class HistoryAdapter implements ListAdapter {
        
        // Map of items. Negative values are labels, positive values
        // and zero are cursor offsets.
        int mItemMap[];
        Vector<DataSetObserver> mObservers;
        Cursor mCursor;
        
        HistoryAdapter() {
            mObservers = new Vector<DataSetObserver>();
            
            String whereClause = Browser.BookmarkColumns.VISITS + " > 0 ";
            String orderBy = Browser.BookmarkColumns.DATE + " DESC";
           
            mCursor = managedQuery(
                    Browser.BOOKMARKS_URI,
                    Browser.HISTORY_PROJECTION,
                    whereClause, null, orderBy);
            
            buildMap();
        }
        
        void refreshData() {
            mCursor.requery();
            buildMap();
            for (DataSetObserver o : mObservers) {
                o.onChanged();
            }
        }
        
        public void buildMap() {
            // The cursor is sorted by date
            // Make one pass to build up the ItemMap with the inserted 
            // section separators. 
            int array[] = new int[mCursor.getCount() + DateSorter.DAY_COUNT];
            int dateIndex = -1;
            if (mCursor.moveToFirst() && mCursor.getCount() > 0) {
                int itemIndex = 0;
                while (!mCursor.isAfterLast()) {
                    long date = mCursor.getLong(Browser.HISTORY_PROJECTION_DATE_INDEX);
                    int index = mDateSorter.getIndex(date);
                    if (index > dateIndex) {
                        dateIndex = index;
                        array[itemIndex] = dateIndex - DateSorter.DAY_COUNT;
                        itemIndex++;
                    }
                    array[itemIndex] = mCursor.getPosition();
                    itemIndex++;
                    mCursor.moveToNext();
                }
            } else {
                // The db is empty, just add the heading for the first item
                dateIndex = 0;
                array[0] = dateIndex - DateSorter.DAY_COUNT;
            }
            // Compress the array as the trailing date sections may be
            // empty
            int extraEntries = DateSorter.DAY_COUNT - dateIndex - 1;
            if (extraEntries > 0) {
                int newArraySize = array.length - extraEntries;
                mItemMap = new int[newArraySize];
                System.arraycopy(array, 0, mItemMap, 0, newArraySize);
            } else {
                mItemMap = array;
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (mItemMap[position] < 0) {
                return getHeaderView(position, convertView);
            }
            return getHistoryItem(position, convertView);
        }
        
        View getHistoryItem(int position, View convertView) {
            HistoryItem item;
            if (null == convertView || !(convertView instanceof HistoryItem)) {
                item = new HistoryItem(BrowserHistoryPage.this);
            } else {
                item = (HistoryItem) convertView;
            }
            mCursor.moveToPosition(mItemMap[position]);
            item.setName(mCursor.getString(Browser.HISTORY_PROJECTION_TITLE_INDEX));
            String url = mCursor.getString(Browser.HISTORY_PROJECTION_URL_INDEX);
            item.setUrl(url);
            item.setFavicon((Bitmap) mUrlsToIcons.get(url));
            return item;
        }
        
        View getHeaderView(int position, View convertView) {
            TextView item;
            if (null == convertView || !(convertView instanceof TextView)) {
                LayoutInflater factory = 
                        LayoutInflater.from(BrowserHistoryPage.this);
                item = (TextView) 
                        factory.inflate(android.R.layout.preference_category, null);
            } else {
                item = (TextView) convertView;
            }
            item.setText(mDateSorter.getLabel(
                    mItemMap[position] + DateSorter.DAY_COUNT));
            return item;
        }

        public boolean areAllItemsEnabled() {
            return false;
        }

        public boolean isEnabled(int position) {
            return mItemMap[position] >= 0;
        }

        public int getCount() {
            return mItemMap.length;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return mItemMap[position];
        }

        // 0 for TextView, 1 for HistoryItem
        public int getItemViewType(int position) {
            return mItemMap[position] < 0 ? 0 : 1;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public boolean hasStableIds() {
            return true;
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mObservers.add(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mObservers.remove(observer);
        }

        public boolean isEmpty() {
            return getCount() == 1;
        }
    }
}
