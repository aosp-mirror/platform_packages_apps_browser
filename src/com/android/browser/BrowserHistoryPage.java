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

import android.app.Activity;
import android.app.ExpandableListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.ServiceManager;
import android.provider.Browser;
import android.text.IClipboard;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewStub;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.Toast;

/**
 * Activity for displaying the browser's history, divided into
 * days of viewing.
 */
public class BrowserHistoryPage extends ExpandableListActivity {
    private HistoryAdapter          mAdapter;
    private boolean                 mDisableNewWindow;
    private HistoryItem             mContextHeader;

    private final static String LOGTAG = "browser";

    // Implementation of WebIconDatabase.IconListener
    private class IconReceiver implements IconListener {
        public void onReceivedIcon(String url, Bitmap icon) {
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
        setResultToParent(RESULT_OK, intent);
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

        final String whereClause = Browser.BookmarkColumns.VISITS + " > 0"
                // In AddBookmarkPage, where we save new bookmarks, we add
                // three visits to newly created bookmarks, so that
                // bookmarks that have not been visited will show up in the
                // most visited, and higher in the goto search box.
                // However, this puts the site in the history, unless we
                // ignore sites with a DATE of 0, which the next line does.
                + " AND " + Browser.BookmarkColumns.DATE + " > 0";
        final String orderBy = Browser.BookmarkColumns.DATE + " DESC";

        Cursor cursor = managedQuery(
                Browser.BOOKMARKS_URI,
                Browser.HISTORY_PROJECTION,
                whereClause, null, orderBy);

        mAdapter = new HistoryAdapter(this, cursor,
                Browser.HISTORY_PROJECTION_DATE_INDEX);
        setListAdapter(mAdapter);
        final ExpandableListView list = getExpandableListView();
        list.setOnCreateContextMenuListener(this);
        View v = new ViewStub(this, R.layout.empty_history);
        addContentView(v, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        list.setEmptyView(v);
        // Do not post the runnable if there is nothing in the list.
        if (list.getExpandableListAdapter().getGroupCount() > 0) {
            list.post(new Runnable() {
                public void run() {
                    // In case the history gets cleared before this event
                    // happens.
                    if (list.getExpandableListAdapter().getGroupCount() > 0) {
                        list.expandGroup(0);
                    }
                }
            });
        }
        mDisableNewWindow = getIntent().getBooleanExtra("disable_new_window",
                false);

        // Register to receive icons in case they haven't all been loaded.
        CombinedBookmarkHistoryActivity.getIconListenerSet()
                .addListener(mIconReceiver);

        Activity parent = getParent();
        if (null == parent
                || !(parent instanceof CombinedBookmarkHistoryActivity)) {
            throw new AssertionError("history page can only be viewed as a tab"
                    + "in CombinedBookmarkHistoryActivity");
        }
        // initialize the result to canceled, so that if the user just presses
        // back then it will have the correct result
        setResultToParent(RESULT_CANCELED, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CombinedBookmarkHistoryActivity.getIconListenerSet()
                .removeListener(mIconReceiver);
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
                Browser.clearHistory(getContentResolver());
                // BrowserHistoryPage is always a child of
                // CombinedBookmarkHistoryActivity
                ((CombinedBookmarkHistoryActivity) getParent())
                        .removeParentChildRelationShips();
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
        ExpandableListContextMenuInfo i = 
            (ExpandableListContextMenuInfo) menuInfo;
        // Do not allow a context menu to come up from the group views.
        if (!(i.targetView instanceof HistoryItem)) {
            return;
        }

        // Inflate the menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.historycontext, menu);

        HistoryItem historyItem = (HistoryItem) i.targetView;

        // Setup the header
        if (mContextHeader == null) {
            mContextHeader = new HistoryItem(this);
        } else if (mContextHeader.getParent() != null) {
            ((ViewGroup) mContextHeader.getParent()).removeView(mContextHeader);
        }
        historyItem.copyTo(mContextHeader);
        menu.setHeaderView(mContextHeader);

        // Only show open in new tab if it was not explicitly disabled
        if (mDisableNewWindow) {
            menu.findItem(R.id.new_window_context_menu_id).setVisible(false);
        }
        // For a bookmark, provide the option to remove it from bookmarks
        if (historyItem.isBookmark()) {
            MenuItem item = menu.findItem(R.id.save_to_bookmarks_menu_id);
            item.setTitle(R.string.remove_from_bookmarks);
        }
        // decide whether to show the share link option
        PackageManager pm = getPackageManager();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        ResolveInfo ri = pm.resolveActivity(send, PackageManager.MATCH_DEFAULT_ONLY);
        menu.findItem(R.id.share_link_context_menu_id).setVisible(ri != null);
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListContextMenuInfo i = 
            (ExpandableListContextMenuInfo) item.getMenuInfo();
        HistoryItem historyItem = (HistoryItem) i.targetView;
        String url = historyItem.getUrl();
        String title = historyItem.getName();
        switch (item.getItemId()) {
            case R.id.open_context_menu_id:
                loadUrl(url, false);
                return true;
            case R.id.new_window_context_menu_id:
                loadUrl(url, true);
                return true;
            case R.id.save_to_bookmarks_menu_id:
                if (historyItem.isBookmark()) {
                    Bookmarks.removeFromBookmarks(this, getContentResolver(),
                            url, title);
                } else {
                    Browser.saveBookmark(this, title, url);
                }
                return true;
            case R.id.share_link_context_menu_id:
                Browser.sendString(this, url,
                        getText(R.string.choosertitle_sharevia).toString());
                return true;
            case R.id.copy_url_context_menu_id:
                copy(url);
                return true;
            case R.id.delete_context_menu_id:
                Browser.deleteFromHistory(getContentResolver(), url);
                mAdapter.refreshData();
                return true;
            case R.id.homepage_context_menu_id:
                BrowserSettings.getInstance().setHomePage(this, url);
                Toast.makeText(this, R.string.homepage_set,
                    Toast.LENGTH_LONG).show();
                return true;
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }
    
    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        if (v instanceof HistoryItem) {
            loadUrl(((HistoryItem) v).getUrl(), false);
            return true;
        }
        return false;
    }

    // This Activity is always a sub-Activity of
    // CombinedBookmarkHistoryActivity. Therefore, we need to pass our
    // result code up to our parent.
    private void setResultToParent(int resultCode, Intent data) {
        ((CombinedBookmarkHistoryActivity) getParent()).setResultFromChild(
                resultCode, data);
    }

    private class HistoryAdapter extends DateSortedExpandableListAdapter {
        HistoryAdapter(Context context, Cursor cursor, int index) {
            super(context, cursor, index);
            
        }

        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            HistoryItem item;
            if (null == convertView || !(convertView instanceof HistoryItem)) {
                item = new HistoryItem(BrowserHistoryPage.this);
                // Add padding on the left so it will be indented from the
                // arrows on the group views.
                item.setPadding(item.getPaddingLeft() + 10,
                        item.getPaddingTop(),
                        item.getPaddingRight(),
                        item.getPaddingBottom());
            } else {
                item = (HistoryItem) convertView;
            }
            // Bail early if the Cursor is closed.
            if (!moveCursorToChildPosition(groupPosition, childPosition)) {
                return item;
            }
            item.setName(getString(Browser.HISTORY_PROJECTION_TITLE_INDEX));
            String url = getString(Browser.HISTORY_PROJECTION_URL_INDEX);
            item.setUrl(url);
            byte[] data = getBlob(Browser.HISTORY_PROJECTION_FAVICON_INDEX);
            if (data != null) {
                item.setFavicon(BitmapFactory.decodeByteArray(data, 0,
                        data.length));
            } else {
                item.setFavicon(CombinedBookmarkHistoryActivity
                        .getIconListenerSet().getFavicon(url));
            }
            item.setIsBookmark(1 ==
                    getInt(Browser.HISTORY_PROJECTION_BOOKMARK_INDEX));
            return item;
        }
    }
}
