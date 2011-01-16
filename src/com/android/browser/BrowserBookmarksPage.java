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

import com.android.browser.BreadCrumbView.Crumb;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
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
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

interface BookmarksPageCallbacks {
    // Return true if handled
    boolean onBookmarkSelected(Cursor c, boolean isFolder);
    // Return true if handled
    boolean onOpenInNewWindow(Cursor c);
    void onFolderChanged(int level, Uri uri);
}

/**
 *  View showing the user's bookmarks in the browser.
 */
public class BrowserBookmarksPage extends Fragment implements View.OnCreateContextMenuListener,
        LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, IconListener,
        OnItemSelectedListener, BreadCrumbView.Controller, OnMenuItemClickListener {

    static final String LOGTAG = "browser";

    static final int LOADER_BOOKMARKS = 1;
    static final int LOADER_ACCOUNTS_THEN_BOOKMARKS = 2;

    static final String EXTRA_DISABLE_WINDOW = "disable_new_window";

    static final String ACCOUNT_NAME_UNSYNCED = "Unsynced";

    public static final String PREF_ACCOUNT_TYPE = "acct_type";
    public static final String PREF_ACCOUNT_NAME = "acct_name";

    static final String DEFAULT_ACCOUNT = "local";
    static final int VIEW_THUMBNAILS = 1;
    static final int VIEW_LIST = 2;
    static final String PREF_SELECTED_VIEW = "bookmarks_view";

    BookmarksPageCallbacks mCallbacks;
    GridView mGrid;
    ListView mList;
    BrowserBookmarksAdapter mAdapter;
    boolean mDisableNewWindow;
    boolean mCanceled = false;
    boolean mEnableContextMenu = true;
    boolean mShowRootFolder = false;
    View mEmptyView;
    int mCurrentView;
    View mHeader;
    ViewGroup mHeaderContainer;
    BreadCrumbView mCrumbs;
    int mCrumbVisibility = View.VISIBLE;
    int mCrumbMaxVisible = -1;
    boolean mCrumbBackButton = false;

    static BrowserBookmarksPage newInstance(BookmarksPageCallbacks cb,
            Bundle args, ViewGroup headerContainer) {
        BrowserBookmarksPage bbp = new BrowserBookmarksPage();
        bbp.mCallbacks = cb;
        bbp.mHeaderContainer = headerContainer;
        bbp.setArguments(args);
        return bbp;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_BOOKMARKS: {
                String accountType = null;
                String accountName = null;
                if (args != null) {
                    accountType = args.getString(BookmarksLoader.ARG_ACCOUNT_TYPE);
                    accountName = args.getString(BookmarksLoader.ARG_ACCOUNT_NAME);
                }
                BookmarksLoader bl = new BookmarksLoader(getActivity(), accountType, accountName);
                if (mCrumbs != null) {
                    Uri uri = (Uri) mCrumbs.getTopData();
                    if (uri != null) {
                        bl.setUri(uri);
                    }
                }
                return bl;
            }
            case LOADER_ACCOUNTS_THEN_BOOKMARKS: {
                return new CursorLoader(getActivity(), Accounts.CONTENT_URI,
                        new String[] { Accounts.ACCOUNT_TYPE, Accounts.ACCOUNT_NAME }, null, null,
                        null);
            }
        }
        throw new UnsupportedOperationException("Unknown loader id " + id);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        switch (loader.getId()) {
            case LOADER_BOOKMARKS: {
                // Set the visibility of the empty vs. content views
                if (cursor == null || cursor.getCount() == 0) {
                    mEmptyView.setVisibility(View.VISIBLE);
                    mGrid.setVisibility(View.GONE);
                    mList.setVisibility(View.GONE);
                } else {
                    mEmptyView.setVisibility(View.GONE);
                    setupBookmarkView();
                }

                // Give the new data to the adapter
                mAdapter.changeCursor(cursor);
                break;
            }

            case LOADER_ACCOUNTS_THEN_BOOKMARKS: {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                        getActivity());
                String storedAccountType = prefs.getString(PREF_ACCOUNT_TYPE, null);
                String storedAccountName = prefs.getString(PREF_ACCOUNT_NAME, null);
                String accountType =
                        TextUtils.isEmpty(storedAccountType) ? DEFAULT_ACCOUNT : storedAccountType;
                String accountName =
                        TextUtils.isEmpty(storedAccountName) ? DEFAULT_ACCOUNT : storedAccountName;

                Bundle args = null;
                if (cursor == null || !cursor.moveToFirst()) {
                    // No accounts, set the prefs to the default
                    accountType = DEFAULT_ACCOUNT;
                    accountName = DEFAULT_ACCOUNT;
                } else {
                    int accountPosition = -1;

                    if (!DEFAULT_ACCOUNT.equals(accountType) &&
                            !DEFAULT_ACCOUNT.equals(accountName)) {
                        // Check to see if the account in prefs still exists
                        cursor.moveToFirst();
                        do {
                            if (accountType.equals(cursor.getString(0))
                                    && accountName.equals(cursor.getString(1))) {
                                accountPosition = cursor.getPosition();
                                break;
                            }
                        } while (cursor.moveToNext());
                    }

                    if (accountPosition == -1) {
                        if (!(DEFAULT_ACCOUNT.equals(accountType)
                                && DEFAULT_ACCOUNT.equals(accountName))) {
                            // No account is set in prefs and there is at least one,
                            // so pick the first one as the default
                            cursor.moveToFirst();
                            accountType = cursor.getString(0);
                            accountName = cursor.getString(1);
                        }
                    }

                    args = new Bundle();
                    args.putString(BookmarksLoader.ARG_ACCOUNT_TYPE, accountType);
                    args.putString(BookmarksLoader.ARG_ACCOUNT_NAME, accountName);
                }

                // The stored account name wasn't found, update the stored account with a valid one
                if (!accountType.equals(storedAccountType)
                        || !accountName.equals(storedAccountName)) {
                    prefs.edit()
                            .putString(PREF_ACCOUNT_TYPE, accountType)
                            .putString(PREF_ACCOUNT_NAME, accountName)
                            .apply();
                }
                getLoaderManager().initLoader(LOADER_BOOKMARKS, args, this);

                break;
            }
        }
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        onLoadFinished(loader, null);
        switch (loader.getId()) {
            case LOADER_BOOKMARKS: {
                onLoadFinished(loader, null);
                break;
            }
        }
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
        // It is possible that the view has been canceled when we get to
        // this point as back has a higher priority
        if (mCanceled) {
            return false;
        }
        AdapterView.AdapterContextMenuInfo i =
            (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        // If we have no menu info, we can't tell which item was selected.
        if (i == null) {
            return false;
        }

        switch (item.getItemId()) {
        case R.id.open_context_menu_id:
            loadUrl(i.position);
            break;
        case R.id.edit_context_menu_id:
            editBookmark(i.position);
            break;
        case R.id.shortcut_context_menu_id:
            Cursor c = mAdapter.getItem(i.position);
            activity.sendBroadcast(createShortcutIntent(getActivity(), c));
            break;
        case R.id.delete_context_menu_id:
            displayRemoveBookmarkDialog(i.position);
            break;
        case R.id.new_window_context_menu_id:
            openInNewWindow(i.position);
            break;
        case R.id.share_link_context_menu_id: {
            Cursor cursor = mAdapter.getItem(i.position);
            Controller.sharePage(activity,
                    cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE),
                    cursor.getString(BookmarksLoader.COLUMN_INDEX_URL),
                    getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON),
                    getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_THUMBNAIL));
            break;
        }
        case R.id.copy_url_context_menu_id:
            copy(getUrl(i.position));
            break;
        case R.id.homepage_context_menu_id: {
            BrowserSettings.getInstance().setHomePage(activity, getUrl(i.position));
            Toast.makeText(activity, R.string.homepage_set, Toast.LENGTH_LONG).show();
            break;
        }
        // Only for the Most visited page
        case R.id.save_to_bookmarks_menu_id: {
            Cursor cursor = mAdapter.getItem(i.position);
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
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Cursor cursor = mAdapter.getItem(info.position);
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
                BitmapFactory.decodeResource(getResources(), R.drawable.ic_folder_bookmark_widget_holo_dark);
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

        setHasOptionsMenu(true);

        Bundle args = getArguments();
        mDisableNewWindow = args == null ? false : args.getBoolean(EXTRA_DISABLE_WINDOW, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getActivity();

        View root = inflater.inflate(R.layout.bookmarks, container, false);
        mEmptyView = root.findViewById(android.R.id.empty);

        mGrid = (GridView) root.findViewById(R.id.grid);
        mGrid.setOnItemClickListener(this);
        mGrid.setColumnWidth(Controller.getDesiredThumbnailWidth(getActivity()));
        mList = (ListView) root.findViewById(R.id.list);
        mList.setOnItemClickListener(this);
        setEnableContextMenu(mEnableContextMenu);

        // Prep the header
        ViewGroup hc = mHeaderContainer;
        if (hc == null) {
            hc = (ViewGroup) root.findViewById(R.id.header_container);
            hc.setVisibility(View.VISIBLE);
        }
        mHeader = inflater.inflate(R.layout.bookmarks_header, hc, false);
        hc.addView(mHeader);
        mCrumbs = (BreadCrumbView) mHeader.findViewById(R.id.crumbs);
        mCrumbs.setController(this);
        mCrumbs.setUseBackButton(mCrumbBackButton);
        mCrumbs.setMaxVisible(mCrumbMaxVisible);
        mCrumbs.setVisibility(mCrumbVisibility);
        String name = getString(R.string.bookmarks);
        mCrumbs.pushView(name, false, BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER);
        if (mCallbacks != null) {
            mCallbacks.onFolderChanged(1, BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER);
        }

        // Start the loaders
        LoaderManager lm = getLoaderManager();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mCurrentView =
            prefs.getInt(PREF_SELECTED_VIEW, BrowserBookmarksPage.VIEW_THUMBNAILS);
        mAdapter = new BrowserBookmarksAdapter(getActivity(), mCurrentView);
        String accountType = prefs.getString(PREF_ACCOUNT_TYPE, DEFAULT_ACCOUNT);
        String accountName = prefs.getString(PREF_ACCOUNT_NAME, DEFAULT_ACCOUNT);
        if (!TextUtils.isEmpty(accountType) && !TextUtils.isEmpty(accountName)) {
            // There is an account set, load up that one
            Bundle args = null;
            if (!DEFAULT_ACCOUNT.equals(accountType) && !DEFAULT_ACCOUNT.equals(accountName)) {
                args = new Bundle();
                args.putString(BookmarksLoader.ARG_ACCOUNT_TYPE, accountType);
                args.putString(BookmarksLoader.ARG_ACCOUNT_NAME, accountName);
            }
            lm.restartLoader(LOADER_BOOKMARKS, args, this);
        } else {
            // No account set, load the account list first
            lm.restartLoader(LOADER_ACCOUNTS_THEN_BOOKMARKS, null, this);
        }

        // Add our own listener in case there are favicons that have yet to be loaded.
        CombinedBookmarkHistoryView.getIconListenerSet().addListener(this);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mHeaderContainer != null) {
            mHeaderContainer.removeView(mHeader);
        }
        mCrumbs.setController(null);
        mCrumbs = null;
        getLoaderManager().destroyLoader(LOADER_BOOKMARKS);
    }

    @Override
    public void onReceivedIcon(String url, Bitmap icon) {
        // A new favicon has been loaded, so let anything attached to the adapter know about it
        // so new icons will be loaded.
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        // It is possible that the view has been canceled when we get to
        // this point as back has a higher priority
        if (mCanceled) {
            android.util.Log.e(LOGTAG, "item clicked when dismissing");
            return;
        }

        Cursor cursor = mAdapter.getItem(position);
        boolean isFolder = cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;
        if (mCallbacks != null &&
                mCallbacks.onBookmarkSelected(cursor, isFolder)) {
            return;
        }

        if (isFolder) {
            String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
            Uri uri = ContentUris.withAppendedId(
                    BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER, id);
            if (mCrumbs != null) {
                // update crumbs
                mCrumbs.pushView(title, uri);
            }
            loadFolder(uri);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Adapter adapter = parent.getAdapter();
        String accountType = "com.google";
        String accountName = adapter.getItem(position).toString();

        Bundle args = null;
        if (ACCOUNT_NAME_UNSYNCED.equals(accountName)) {
            accountType = DEFAULT_ACCOUNT;
            accountName = DEFAULT_ACCOUNT;
        } else {
            args = new Bundle();
            args.putString(BookmarksLoader.ARG_ACCOUNT_TYPE, accountType);
            args.putString(BookmarksLoader.ARG_ACCOUNT_NAME, accountName);
        }

        // Remember the selection for later
        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                .putString(PREF_ACCOUNT_TYPE, accountType)
                .putString(PREF_ACCOUNT_NAME, accountName)
                .apply();

        getLoaderManager().restartLoader(LOADER_BOOKMARKS, args, this);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }

    /* package */ static Intent createShortcutIntent(Context context, Cursor cursor) {
        String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
        String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
        Bitmap touchIcon = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_TOUCH_ICON);
        Bitmap favicon = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON);
        return BookmarkUtils.createAddToHomeIntent(context, url, title, touchIcon, favicon);
    }

    private void loadUrl(int position) {
        if (mCallbacks != null) {
            mCallbacks.onBookmarkSelected(mAdapter.getItem(position), false);
        }
    }

    private void openInNewWindow(int position) {
        if (mCallbacks != null) {
            Cursor c = mAdapter.getItem(position);
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

    private void editBookmark(int position) {
        Intent intent = new Intent(getActivity(), AddBookmarkPage.class);
        Cursor cursor = mAdapter.getItem(position);
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

    private void displayRemoveBookmarkDialog(final int position) {
        // Put up a dialog asking if the user really wants to
        // delete the bookmark
        Cursor cursor = mAdapter.getItem(position);
        long id = cursor.getLong(BookmarksLoader.COLUMN_INDEX_ID);
        String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
        Context context = getActivity();
        BookmarkUtils.displayRemoveBookmarkDialog(id, title, context, null);
    }

    private String getUrl(int position) {
        return getUrl(mAdapter.getItem(position));
    }

    /* package */ static String getUrl(Cursor c) {
        return c.getString(BookmarksLoader.COLUMN_INDEX_URL);
    }

    private void copy(CharSequence text) {
        ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(
                Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newRawUri(null, null, Uri.parse(text.toString())));
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
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.list_view).setVisible(mCurrentView != VIEW_LIST);
        menu.findItem(R.id.thumbnail_view).setVisible(mCurrentView != VIEW_THUMBNAILS);
    }

    void selectView(int view) {
        if (view == mCurrentView) {
            return;
        }
        mCurrentView = view;
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
        mAdapter.selectView(mCurrentView);
        switch (mCurrentView) {
        case VIEW_THUMBNAILS:
            mList.setAdapter(null);
            mGrid.setAdapter(mAdapter);
            mGrid.setVisibility(View.VISIBLE);
            mList.setVisibility(View.GONE);
            break;
        case VIEW_LIST:
            mGrid.setAdapter(null);
            mList.setAdapter(mAdapter);
            mGrid.setVisibility(View.GONE);
            mList.setVisibility(View.VISIBLE);
            break;
        }
    }

    /**
     * BreadCrumb controller callback
     */
    @Override
    public void onTop(int level, Object data) {
        Uri uri = (Uri) data;
        if (uri == null) {
            // top level
            uri = BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER;
        }
        loadFolder(uri);
    }

    /**
     * @param uri
     */
    private void loadFolder(Uri uri) {
        LoaderManager manager = getLoaderManager();
        BookmarksLoader loader =
                (BookmarksLoader) ((Loader<?>) manager.getLoader(LOADER_BOOKMARKS));
        loader.setUri(uri);
        loader.forceLoad();
        if (mCallbacks != null) {
            mCallbacks.onFolderChanged(mCrumbs.getTopLevel(), uri);
        }
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
        if (canGoBack()) {
            mCrumbs.popView();
            return true;
        }
        return false;
    }

    private boolean canGoBack() {
        Crumb c = mCrumbs.getTopCrumb();
        return c != null && c.canGoBack;
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

    public void setBreadCrumbVisibility(int visibility) {
        mCrumbVisibility = visibility;
        if (mCrumbs != null) {
            mCrumbs.setVisibility(mCrumbVisibility);
        }
    }

    public void setBreadCrumbUseBackButton(boolean use) {
        mCrumbBackButton = use;
        if (mCrumbs != null) {
            mCrumbs.setUseBackButton(mCrumbBackButton);
        }
    }

    public void setBreadCrumbMaxVisible(int max) {
        mCrumbMaxVisible = max;
        if (mCrumbs != null) {
            mCrumbs.setMaxVisible(mCrumbMaxVisible);
        }
    }
}
