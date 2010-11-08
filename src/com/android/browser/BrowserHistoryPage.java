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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.Browser;
import android.provider.BrowserContract.History;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.Toast;

/**
 * Activity for displaying the browser's history, divided into
 * days of viewing.
 */
public class BrowserHistoryPage extends Fragment
        implements LoaderCallbacks<Cursor>, OnChildClickListener {

    static final int LOADER_HISTORY = 1;

    BookmarksHistoryCallbacks mCallbacks;
    ExpandableListView mList;
    View mEmptyView;
    HistoryAdapter mAdapter;
    boolean mDisableNewWindow;
    HistoryItem mContextHeader;

    // Implementation of WebIconDatabase.IconListener
    class IconReceiver implements IconListener {
        @Override
        public void onReceivedIcon(String url, Bitmap icon) {
            mAdapter.notifyDataSetChanged();
        }
    }

    // Instance of IconReceiver
    final IconReceiver mIconReceiver = new IconReceiver();

    static interface HistoryQuery {
        static final String[] PROJECTION = new String[] {
                History._ID, // 0
                History.DATE_LAST_VISITED, // 1
                History.TITLE, // 2
                History.URL, // 3
                History.FAVICON, // 4
        };

        static final int INDEX_ID = 0;
        static final int INDEX_DATE_LAST_VISITED = 1;
        static final int INDEX_TITE = 2;
        static final int INDEX_URL = 3;
        static final int INDEX_FAVICON = 4;
    }

    private void copy(CharSequence text) {
        ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(
                Context.CLIPBOARD_SERVICE);
        cm.setText(text);
    }

    static BrowserHistoryPage newInstance(BookmarksHistoryCallbacks cb, Bundle args) {
        BrowserHistoryPage bhp = new BrowserHistoryPage();
        bhp.mCallbacks = cb;
        bhp.setArguments(args);
        return bhp;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_HISTORY: {
                CursorLoader loader = new CursorLoader(getActivity(), History.CONTENT_URI,
                        HistoryQuery.PROJECTION, null, null, null);
                return loader;
            }

            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
            case LOADER_HISTORY: {
                mAdapter.changeCursor(data);

                // Add an empty view late, so it does not claim an empty
                // history before the adapter is present
                mList.setEmptyView(mEmptyView);

                // Do not post the runnable if there is nothing in the list.
                if (mList.getExpandableListAdapter().getGroupCount() > 0) {
                    mList.post(new Runnable() {
                        @Override
                        public void run() {
                            // In case the history gets cleared before this
                            // event happens
                            if (mList.getExpandableListAdapter().getGroupCount() > 0) {
                                mList.expandGroup(0);
                            }
                        }
                    });
                }
                break;
            }

            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setHasOptionsMenu(true);

        Bundle args = getArguments();
        mDisableNewWindow = args.getBoolean(BrowserBookmarksPage.EXTRA_DISABLE_WINDOW, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.history, container, false);
        mList = (ExpandableListView) root.findViewById(android.R.id.list);
        mList.setCacheColorHint(0);
        mList.setOnCreateContextMenuListener(this);
        mList.setOnChildClickListener(this);
        mAdapter = new HistoryAdapter(getActivity());
        mList.setAdapter(mAdapter);

        mEmptyView = root.findViewById(android.R.id.empty);

        // Start the loader
        getLoaderManager().initLoader(LOADER_HISTORY, null, this);

        // Register to receive icons in case they haven't all been loaded.
        CombinedBookmarkHistoryView.getIconListenerSet().addListener(mIconReceiver);
        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CombinedBookmarkHistoryView.getIconListenerSet().removeListener(mIconReceiver);
        getLoaderManager().stopLoader(LOADER_HISTORY);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.history, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.clear_history_menu_id).setVisible(
                Browser.canClearHistory(getActivity().getContentResolver()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_history_menu_id:
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.clear)
                        .setMessage(R.string.pref_privacy_clear_history_dlg)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(DialogInterface dialog, int which) {
                                 if (which == DialogInterface.BUTTON_POSITIVE) {
                                     Browser.clearHistory(getActivity().getContentResolver());
                                     mCallbacks.onRemoveParentChildRelationships();
                                 }
                             }
                        });
                final Dialog dialog = builder.create();
                dialog.show();
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        ExpandableListContextMenuInfo i = (ExpandableListContextMenuInfo) menuInfo;
        // Do not allow a context menu to come up from the group views.
        if (!(i.targetView instanceof HistoryItem)) {
            return;
        }

        // Inflate the menu
        Activity parent = getActivity();
        MenuInflater inflater = parent.getMenuInflater();
        inflater.inflate(R.menu.historycontext, menu);

        HistoryItem historyItem = (HistoryItem) i.targetView;

        // Setup the header
        if (mContextHeader == null) {
            mContextHeader = new HistoryItem(parent);
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
        PackageManager pm = parent.getPackageManager();
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
        if (i == null) {
            return false;
        }
        HistoryItem historyItem = (HistoryItem) i.targetView;
        String url = historyItem.getUrl();
        String title = historyItem.getName();
        Activity activity = getActivity();
        switch (item.getItemId()) {
            case R.id.open_context_menu_id:
                mCallbacks.onUrlSelected(url, false);
                return true;
            case R.id.new_window_context_menu_id:
                mCallbacks.onUrlSelected(url, true);
                return true;
            case R.id.save_to_bookmarks_menu_id:
                if (historyItem.isBookmark()) {
                    Bookmarks.removeFromBookmarks(activity, activity.getContentResolver(),
                            url, title);
                } else {
                    Browser.saveBookmark(activity, title, url);
                }
                return true;
            case R.id.share_link_context_menu_id:
                Browser.sendString(activity, url,
                        activity.getText(R.string.choosertitle_sharevia).toString());
                return true;
            case R.id.copy_url_context_menu_id:
                copy(url);
                return true;
            case R.id.delete_context_menu_id:
                Browser.deleteFromHistory(activity.getContentResolver(), url);
                return true;
            case R.id.homepage_context_menu_id:
                BrowserSettings.getInstance().setHomePage(activity, url);
                Toast.makeText(activity, R.string.homepage_set, Toast.LENGTH_LONG).show();
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
            mCallbacks.onUrlSelected(((HistoryItem) v).getUrl(), false);
            return true;
        }
        return false;
    }

    private class HistoryAdapter extends DateSortedExpandableListAdapter {
        HistoryAdapter(Context context) {
            super(context, HistoryQuery.INDEX_DATE_LAST_VISITED);
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            HistoryItem item;
            if (null == convertView || !(convertView instanceof HistoryItem)) {
                item = new HistoryItem(getContext());
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

            item.setName(getString(HistoryQuery.INDEX_TITE));
            String url = getString(HistoryQuery.INDEX_URL);
            item.setUrl(url);
            byte[] data = getBlob(HistoryQuery.INDEX_FAVICON);
            if (data != null) {
                item.setFavicon(BitmapFactory.decodeByteArray(data, 0,
                        data.length));
            } else {
                item.setFavicon(CombinedBookmarkHistoryView
                        .getIconListenerSet().getFavicon(url));
            }
            return item;
        }
    }
}
