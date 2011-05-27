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

import com.android.browser.BookmarkDragHandler.BookmarkDragController;
import com.android.browser.view.BookmarkExpandableGridView;
import com.android.browser.view.BookmarkExpandableGridView.BookmarkContextMenuInfo;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BrowserContract;
import android.provider.BrowserContract.Accounts;
import android.provider.BrowserContract.ChromeSyncColumns;
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
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ListView;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import java.util.HashMap;

interface BookmarksPageCallbacks {
    // Return true if handled
    boolean onBookmarkSelected(Cursor c, boolean isFolder);
    // Return true if handled
    boolean onOpenInNewWindow(Cursor c);
}

/**
 *  View showing the user's bookmarks in the browser.
 */
public class BrowserBookmarksPage extends Fragment implements View.OnCreateContextMenuListener,
        LoaderManager.LoaderCallbacks<Cursor>, IconListener,
        BreadCrumbView.Controller, OnMenuItemClickListener, OnChildClickListener {

    static final String LOGTAG = "browser";

    static final int LOADER_ACCOUNTS = 1;
    static final int LOADER_BOOKMARKS = 100;

    static final String EXTRA_DISABLE_WINDOW = "disable_new_window";

    static final String ACCOUNT_TYPE = "account_type";
    static final String ACCOUNT_NAME = "account_name";

    static final int VIEW_THUMBNAILS = 1;
    static final int VIEW_LIST = 2;
    static final String PREF_SELECTED_VIEW = "bookmarks_view";

    BookmarksPageCallbacks mCallbacks;
    View mRoot;
    BookmarkExpandableGridView mGrid;
    ListView mList;
    boolean mDisableNewWindow;
    boolean mEnableContextMenu = true;
    View mEmptyView;
    int mCurrentView;
    View mHeader;
    HashMap<Integer, BrowserBookmarksAdapter> mBookmarkAdapters = new HashMap<Integer, BrowserBookmarksAdapter>();
    BookmarkDragHandler mDragHandler;

    static BrowserBookmarksPage newInstance(BookmarksPageCallbacks cb,
            Bundle args, ViewGroup headerContainer) {
        BrowserBookmarksPage bbp = new BrowserBookmarksPage();
        bbp.mCallbacks = cb;
        bbp.setArguments(args);
        return bbp;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_ACCOUNTS) {
            return new AccountsLoader(getActivity());
        } else if (id >= LOADER_BOOKMARKS) {
            String accountType = args.getString(ACCOUNT_TYPE);
            String accountName = args.getString(ACCOUNT_NAME);
            BookmarksLoader bl = new BookmarksLoader(getActivity(),
                    accountType, accountName);
            return bl;
        } else {
            throw new UnsupportedOperationException("Unknown loader id " + id);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (loader.getId() == LOADER_ACCOUNTS) {
            LoaderManager lm = getLoaderManager();
            int id = LOADER_BOOKMARKS;
            while (cursor.moveToNext()) {
                String accountName = cursor.getString(0);
                String accountType = cursor.getString(1);
                Bundle args = new Bundle();
                args.putString(ACCOUNT_NAME, accountName);
                args.putString(ACCOUNT_TYPE, accountType);
                BrowserBookmarksAdapter adapter = new BrowserBookmarksAdapter(
                        getActivity(), mCurrentView);
                mBookmarkAdapters.put(id, adapter);
                mGrid.addAccount(accountName, adapter);
                lm.restartLoader(id, args, this);
                id++;
            }
            // TODO: Figure out what a reload of these means
            // Currently, a reload is triggered whenever bookmarks change
            // This is less than ideal
            // It also causes UI flickering as a new adapter is created
            // instead of re-using an existing one when the account_name is the
            // same.
            // For now, this is a one-shot load
            getLoaderManager().destroyLoader(LOADER_ACCOUNTS);
        } else if (loader.getId() >= LOADER_BOOKMARKS) {
            BrowserBookmarksAdapter adapter = mBookmarkAdapters.get(loader.getId());
            adapter.changeCursor(cursor);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // TODO: Figure out what to do here (if anything?)
    }

    long getFolderId() {
        LoaderManager manager = getLoaderManager();
        BookmarksLoader loader =
                (BookmarksLoader) ((Loader<?>)manager.getLoader(LOADER_BOOKMARKS));

        Uri uri = loader.getUri();
        if (uri != null) {
            try {
                return ContentUris.parseId(uri);
            } catch (NumberFormatException nfx) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final Activity activity = getActivity();
        BookmarkContextMenuInfo i = (BookmarkContextMenuInfo)item.getMenuInfo();
        // If we have no menu info, we can't tell which item was selected.
        if (i == null) {
            return false;
        }
        BrowserBookmarksAdapter adapter = getChildAdapter(i.groupPosition);

        switch (item.getItemId()) {
        case R.id.open_context_menu_id:
            loadUrl(adapter, i.childPosition);
            break;
        case R.id.edit_context_menu_id:
            editBookmark(adapter, i.childPosition);
            break;
        case R.id.shortcut_context_menu_id:
            Cursor c = adapter.getItem(i.childPosition);
            activity.sendBroadcast(createShortcutIntent(getActivity(), c));
            break;
        case R.id.delete_context_menu_id:
            displayRemoveBookmarkDialog(adapter, i.childPosition);
            break;
        case R.id.new_window_context_menu_id:
            openInNewWindow(adapter, i.childPosition);
            break;
        case R.id.share_link_context_menu_id: {
            Cursor cursor = adapter.getItem(i.childPosition);
            Controller.sharePage(activity,
                    cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE),
                    cursor.getString(BookmarksLoader.COLUMN_INDEX_URL),
                    getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON),
                    getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_THUMBNAIL));
            break;
        }
        case R.id.copy_url_context_menu_id:
            copy(getUrl(adapter, i.childPosition));
            break;
        case R.id.homepage_context_menu_id: {
            BrowserSettings.getInstance().setHomePage(getUrl(adapter, i.childPosition));
            Toast.makeText(activity, R.string.homepage_set, Toast.LENGTH_LONG).show();
            break;
        }
        // Only for the Most visited page
        case R.id.save_to_bookmarks_menu_id: {
            Cursor cursor = adapter.getItem(i.childPosition);
            String name = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
            String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
            // If the site is bookmarked, the item becomes remove from
            // bookmarks.
            Bookmarks.removeFromBookmarks(activity, activity.getContentResolver(), url, name);
            break;
        }
        default:
            return super.onContextItemSelected(item);
        }
        return true;
    }

    static Bitmap getBitmap(Cursor cursor, int columnIndex) {
        byte[] data = cursor.getBlob(columnIndex);
        if (data == null) {
            return null;
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    private MenuItem.OnMenuItemClickListener mContextItemClickListener =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            return onContextItemSelected(item);
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bookmark, menu);
        if (!BrowserActivity.isTablet(getActivity())) {
            MenuItem item = menu.findItem(R.id.add_bookmark);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        BookmarkContextMenuInfo info = (BookmarkContextMenuInfo) menuInfo;
        BrowserBookmarksAdapter adapter = getChildAdapter(info.groupPosition);
        Cursor cursor = adapter.getItem(info.childPosition);
        if (!canEdit(cursor)) {
            return;
        }
        boolean isFolder
                = cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;

        final Activity activity = getActivity();
        MenuInflater inflater = activity.getMenuInflater();
        inflater.inflate(R.menu.bookmarkscontext, menu);
        if (isFolder) {
            menu.setGroupVisible(R.id.FOLDER_CONTEXT_MENU, true);
        } else {
            menu.setGroupVisible(R.id.BOOKMARK_CONTEXT_MENU, true);
            if (mDisableNewWindow) {
                menu.findItem(R.id.new_window_context_menu_id).setVisible(false);
            }
        }
        BookmarkItem header = new BookmarkItem(activity);
        populateBookmarkItem(cursor, header, isFolder);
        menu.setHeaderView(header);

        int count = menu.size();
        for (int i = 0; i < count; i++) {
            menu.getItem(i).setOnMenuItemClickListener(mContextItemClickListener);
        }
    }

    boolean canEdit(Cursor c) {
        String unique = c.getString(BookmarksLoader.COLUMN_INDEX_SERVER_UNIQUE);
        return !ChromeSyncColumns.FOLDER_NAME_OTHER_BOOKMARKS.equals(unique);
    }

    private void populateBookmarkItem(Cursor cursor, BookmarkItem item, boolean isFolder) {
        item.setName(cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE));
        if (isFolder) {
            item.setUrl(null);
            Bitmap bitmap =
                BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder_holo_dark);
            item.setFavicon(bitmap);
            new LookupBookmarkCount(getActivity(), item)
                    .execute(cursor.getLong(BookmarksLoader.COLUMN_INDEX_ID));
        } else {
            String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
            item.setUrl(url);
            Bitmap bitmap = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON);
            if (bitmap == null) {
                bitmap = CombinedBookmarkHistoryView.getIconListenerSet().getFavicon(url);
            }
            item.setFavicon(bitmap);
        }
    }

    /**
     *  Create a new BrowserBookmarksPage.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(getActivity());
        mCurrentView = prefs.getInt(PREF_SELECTED_VIEW, getDefaultView());
        // TODO: Support list view
        mCurrentView = VIEW_THUMBNAILS;

        Bundle args = getArguments();
        mDisableNewWindow = args == null ? false : args.getBoolean(EXTRA_DISABLE_WINDOW, false);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRoot = inflater.inflate(R.layout.bookmarks, container, false);
        mEmptyView = mRoot.findViewById(android.R.id.empty);

        mGrid = (BookmarkExpandableGridView) mRoot.findViewById(R.id.grid);
        mGrid.setOnChildClickListener(this);
        mGrid.setColumnWidthFromLayout(R.layout.bookmark_thumbnail);
        mGrid.setBreadcrumbController(this);
        mList = (ListView) mRoot.findViewById(R.id.list);
        // TODO: mList.setOnItemClickListener(this);
        setEnableContextMenu(mEnableContextMenu);
        mDragHandler = new BookmarkDragHandler(getActivity(), mDragController,
                mGrid.getDragAdapter());

        // Start the loaders
        LoaderManager lm = getLoaderManager();
        lm.restartLoader(LOADER_ACCOUNTS, null, this);

        // Add our own listener in case there are favicons that have yet to be loaded.
        CombinedBookmarkHistoryView.getIconListenerSet().addListener(this);

        return mRoot;
    }

     private int getDefaultView() {
        if (BrowserActivity.isTablet(getActivity())) {
            return VIEW_THUMBNAILS;
        }
        return VIEW_LIST;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGrid.setBreadcrumbController(null);
        LoaderManager lm = getLoaderManager();
        lm.destroyLoader(LOADER_ACCOUNTS);
        for (int id : mBookmarkAdapters.keySet()) {
            lm.destroyLoader(id);
        }
        mBookmarkAdapters.clear();

        CombinedBookmarkHistoryView.getIconListenerSet().removeListener(this);
    }

    @Override
    public void onReceivedIcon(String url, Bitmap icon) {
        // A new favicon has been loaded, so let anything attached to the adapter know about it
        // so new icons will be loaded.
        // TODO: Notify all of data set changed
        // TODO: Wait, is this even needed? Won't this trigger a DB change anyway?
    }

    private BrowserBookmarksAdapter getChildAdapter(int groupPosition) {
        if (mCurrentView == VIEW_THUMBNAILS) {
            return mGrid.getChildAdapter(groupPosition);
        } else {
            // TODO: Support expandable list
            return null;
        }
    }

    private BreadCrumbView getBreadCrumbs(int groupPosition) {
        if (mCurrentView == VIEW_THUMBNAILS) {
            return mGrid.getBreadCrumbs(groupPosition);
        } else {
            // TODO: Support expandable list
            return null;
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v,
            int groupPosition, int childPosition, long id) {
        BrowserBookmarksAdapter adapter = getChildAdapter(groupPosition);
        Cursor cursor = adapter.getItem(childPosition);
        boolean isFolder = cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;
        if (mCallbacks != null &&
                mCallbacks.onBookmarkSelected(cursor, isFolder)) {
            return true;
        }

        // TODO: Folder stuff
        if (isFolder) {
            String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
            Uri uri = ContentUris.withAppendedId(
                    BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER, id);
            BreadCrumbView crumbs = getBreadCrumbs(groupPosition);
            if (crumbs != null) {
                // update crumbs
                crumbs.pushView(title, uri);
            }
            loadFolder(groupPosition, uri);
        }
        return true;
    }

    /* package */ static Intent createShortcutIntent(Context context, Cursor cursor) {
        String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
        String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
        Bitmap touchIcon = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_TOUCH_ICON);
        Bitmap favicon = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON);
        return BookmarkUtils.createAddToHomeIntent(context, url, title, touchIcon, favicon);
    }

    private void loadUrl(BrowserBookmarksAdapter adapter, int position) {
        if (mCallbacks != null && adapter != null) {
            mCallbacks.onBookmarkSelected(adapter.getItem(position), false);
        }
    }

    private void openInNewWindow(BrowserBookmarksAdapter adapter, int position) {
        if (mCallbacks != null) {
            Cursor c = adapter.getItem(position);
            boolean isFolder = c.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) == 1;
            if (isFolder) {
                long id = c.getLong(BookmarksLoader.COLUMN_INDEX_ID);
                new OpenAllInTabsTask(id).execute();
            } else {
                mCallbacks.onOpenInNewWindow(c);
            }
        }
    }

    class OpenAllInTabsTask extends AsyncTask<Void, Void, Cursor> {
        long mFolderId;
        public OpenAllInTabsTask(long id) {
            mFolderId = id;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            Context c = getActivity();
            if (c == null) return null;
            return c.getContentResolver().query(BookmarkUtils.getBookmarksUri(c),
                    BookmarksLoader.PROJECTION, BrowserContract.Bookmarks.PARENT + "=?",
                    new String[] { Long.toString(mFolderId) }, null);
        }

        @Override
        protected void onPostExecute(Cursor result) {
            if (mCallbacks != null) {
                while (result.moveToNext()) {
                    mCallbacks.onOpenInNewWindow(result);
                }
            }
        }

    }

    private void editBookmark(BrowserBookmarksAdapter adapter, int position) {
        Intent intent = new Intent(getActivity(), AddBookmarkPage.class);
        Cursor cursor = adapter.getItem(position);
        Bundle item = new Bundle();
        item.putString(BrowserContract.Bookmarks.TITLE,
                cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE));
        item.putString(BrowserContract.Bookmarks.URL,
                cursor.getString(BookmarksLoader.COLUMN_INDEX_URL));
        byte[] data = cursor.getBlob(BookmarksLoader.COLUMN_INDEX_FAVICON);
        if (data != null) {
            item.putParcelable(BrowserContract.Bookmarks.FAVICON,
                    BitmapFactory.decodeByteArray(data, 0, data.length));
        }
        item.putLong(BrowserContract.Bookmarks._ID,
                cursor.getLong(BookmarksLoader.COLUMN_INDEX_ID));
        item.putLong(BrowserContract.Bookmarks.PARENT,
                cursor.getLong(BookmarksLoader.COLUMN_INDEX_PARENT));
        intent.putExtra(AddBookmarkPage.EXTRA_EDIT_BOOKMARK, item);
        intent.putExtra(AddBookmarkPage.EXTRA_IS_FOLDER,
                cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) == 1);
        startActivity(intent);
    }

    private void displayRemoveBookmarkDialog(BrowserBookmarksAdapter adapter,
            int position) {
        // Put up a dialog asking if the user really wants to
        // delete the bookmark
        Cursor cursor = adapter.getItem(position);
        long id = cursor.getLong(BookmarksLoader.COLUMN_INDEX_ID);
        String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
        Context context = getActivity();
        BookmarkUtils.displayRemoveBookmarkDialog(id, title, context, null);
    }

    private String getUrl(BrowserBookmarksAdapter adapter, int position) {
        return getUrl(adapter.getItem(position));
    }

    /* package */ static String getUrl(Cursor c) {
        return c.getString(BookmarksLoader.COLUMN_INDEX_URL);
    }

    private void copy(CharSequence text) {
        ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(
                Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newRawUri(null, Uri.parse(text.toString())));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.thumbnail_view:
            selectView(VIEW_THUMBNAILS);
            return true;
        case R.id.list_view:
            selectView(VIEW_LIST);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Resources res = getActivity().getResources();
        int horizontalSpacing = (int) res.getDimension(R.dimen.combo_horizontalSpacing);
        mGrid.setHorizontalSpacing(horizontalSpacing);
        mGrid.setColumnWidthFromLayout(R.layout.bookmark_thumbnail);
        int paddingLeftRight = (int) res.getDimension(R.dimen.combo_paddingLeftRight);
        int paddingTop = (int) res.getDimension(R.dimen.combo_paddingTop);
        mRoot.setPadding(paddingLeftRight, paddingTop,
                paddingLeftRight, 0);
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.list_view).setVisible(mCurrentView != VIEW_LIST);
        menu.findItem(R.id.thumbnail_view).setVisible(mCurrentView != VIEW_THUMBNAILS);
    }

    void selectView(int view) {
        // TODO: Support list view
        view = mCurrentView;
        if (view == mCurrentView) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Editor edit = prefs.edit();
        edit.putInt(PREF_SELECTED_VIEW, mCurrentView);
        edit.apply();
        if (mEmptyView.getVisibility() == View.VISIBLE) {
            return;
        }
        setupBookmarkView();
    }

    private void setupBookmarkView() {
        // TODO: Support list view
//        mAdapter.selectView(mCurrentView);
//        switch (mCurrentView) {
//        case VIEW_THUMBNAILS:
//            mList.setAdapter(null);
//            SharedPreferences prefs = PreferenceManager
//                    .getDefaultSharedPreferences(getActivity());
//            String accountName = prefs.getString(PREF_ACCOUNT_NAME, null);
//            mGrid.addAccount(accountName, mAdapter);
//            mGrid.setVisibility(View.VISIBLE);
//            mList.setVisibility(View.GONE);
//            break;
//        case VIEW_LIST:
//            mGrid.clearAccounts();
//            if (mList.getAdapter() != mAdapter) {
//                mList.setAdapter(mAdapter);
//            }
//            mGrid.setVisibility(View.GONE);
//            mList.setVisibility(View.VISIBLE);
//            break;
//        }
    }

    /**
     * BreadCrumb controller callback
     */
    @Override
    public void onTop(BreadCrumbView view, int level, Object data) {
        int groupPosition = (Integer) view.getTag(R.id.group_position);
        Uri uri = (Uri) data;
        if (uri == null) {
            // top level
            uri = BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER;
        }
        loadFolder(groupPosition, uri);
    }

    /**
     * @param uri
     */
    private void loadFolder(int groupPosition, Uri uri) {
        LoaderManager manager = getLoaderManager();
        // This assumes groups are ordered the same as loaders
        BookmarksLoader loader = (BookmarksLoader) ((Loader<?>)
                manager.getLoader(LOADER_BOOKMARKS + groupPosition));
        loader.setUri(uri);
        loader.forceLoad();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.list_view:
            selectView(BrowserBookmarksPage.VIEW_LIST);
            return true;
        case R.id.thumbnail_view:
            selectView(BrowserBookmarksPage.VIEW_THUMBNAILS);
            return true;
        }
        return false;
    }

    public boolean onBackPressed() {
        return false;
    }

    public void setCallbackListener(BookmarksPageCallbacks callbackListener) {
        mCallbacks = callbackListener;
    }

    public void setEnableContextMenu(boolean enable) {
        mEnableContextMenu = enable;
        if (mGrid != null) {
            if (mEnableContextMenu) {
                registerForContextMenu(mGrid);
            } else {
                unregisterForContextMenu(mGrid);
                mGrid.setLongClickable(false);
            }
        }
        if (mList != null) {
            if (mEnableContextMenu) {
                registerForContextMenu(mList);
            } else {
                unregisterForContextMenu(mList);
                mList.setLongClickable(false);
            }
        }
    }

    private BookmarkDragController mDragController = new BookmarkDragController() {

        @Override
        public boolean startDrag(Cursor item) {
            return canEdit(item);
        }
    };

    private static class LookupBookmarkCount extends AsyncTask<Long, Void, Integer> {
        Context mContext;
        BookmarkItem mHeader;

        public LookupBookmarkCount(Context context, BookmarkItem header) {
            mContext = context;
            mHeader = header;
        }

        @Override
        protected Integer doInBackground(Long... params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("Missing folder id!");
            }
            Uri uri = BookmarkUtils.getBookmarksUri(mContext);
            Cursor c = mContext.getContentResolver().query(uri,
                    null, BrowserContract.Bookmarks.PARENT + "=?",
                    new String[] {params[0].toString()}, null);
            return c.getCount();
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result > 0) {
                mHeader.setUrl(mContext.getString(R.string.contextheader_folder_bookmarkcount,
                        result));
            } else if (result == 0) {
                mHeader.setUrl(mContext.getString(R.string.contextheader_folder_empty));
            }
        }
    }

    static class AccountsLoader extends CursorLoader {

        static String[] ACCOUNTS_PROJECTION = new String[] {
            Accounts.ACCOUNT_NAME,
            Accounts.ACCOUNT_TYPE
        };

        public AccountsLoader(Context context) {
            super(context, Accounts.CONTENT_URI, ACCOUNTS_PROJECTION, null, null,
                    Accounts.ACCOUNT_NAME + " ASC");
        }

    }
}
