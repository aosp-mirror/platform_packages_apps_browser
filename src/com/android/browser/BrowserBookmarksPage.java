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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.provider.BrowserContract;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebIconDatabase.IconListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import java.util.Stack;

/**
 *  View showing the user's bookmarks in the browser.
 */
public class BrowserBookmarksPage extends Fragment implements View.OnCreateContextMenuListener,
        LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener, IconListener, OnClickListener {

    static final int BOOKMARKS_SAVE = 1;
    static final String LOGTAG = "browser";

    static final int LOADER_BOOKMARKS = 1;

    static final String EXTRA_SHORTCUT = "create_shortcut";
    static final String EXTRA_DISABLE_WINDOW = "disable_new_window";

    BookmarksHistoryCallbacks mCallbacks;
    GridView mGrid;
    BrowserBookmarksAdapter mAdapter;
    boolean mDisableNewWindow;
    BookmarkItem mContextHeader;
    boolean mCanceled = false;
    boolean mCreateShortcut;
    View mEmptyView;
    View mContentView;
    Stack<Pair<String, Uri>> mFolderStack = new Stack<Pair<String, Uri>>();
    Button mUpButton;

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_BOOKMARKS: {
                int rootFolder = 0;
                if (args != null) {
                    args.getInt(BookmarksLoader.ARG_ROOT_FOLDER, 0);
                }
                return new BookmarksLoader(getActivity(), rootFolder);
            }
        }
        throw new UnsupportedOperationException("Unknown loader id " + id);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Set the visibility of the empty vs. content views
        if (data == null || data.getCount() == 0) {
            mEmptyView.setVisibility(View.VISIBLE);
            mContentView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mContentView.setVisibility(View.VISIBLE);
        }

        // Fill in the "up" button if needed
        BookmarksLoader bl = (BookmarksLoader) loader;
        boolean rootFolder =
                (BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER.equals(bl.getUri()));
        if (rootFolder) {
            mUpButton.setText(R.string.defaultBookmarksUpButton);
            mUpButton.setEnabled(false);
        } else {
            mUpButton.setText(mFolderStack.peek().first);
            mUpButton.setEnabled(true);
        }

        // Give the new data to the adapter
        mAdapter.changeCursor(data);
    }

    @Override
    public void onClick(View view) {
        if (view == mUpButton) {
            Pair<String, Uri> pair = mFolderStack.pop();
            BookmarksLoader loader =
                    (BookmarksLoader) ((Loader) getLoaderManager().getLoader(LOADER_BOOKMARKS));
            loader.setUri(pair.second);
            loader.forceLoad();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final Activity activity = getActivity();
        // It is possible that the view has been canceled when we get to
        // this point as back has a higher priority
        if (mCanceled) {
            return true;
        }
        AdapterView.AdapterContextMenuInfo i =
            (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        // If we have no menu info, we can't tell which item was selected.
        if (i == null) {
            return true;
        }

        switch (item.getItemId()) {
        case R.id.open_context_menu_id:
            loadUrl(i.position);
            break;
        case R.id.edit_context_menu_id:
            editBookmark(i.position);
            break;
        case R.id.shortcut_context_menu_id:
            activity.sendBroadcast(createShortcutIntent(i.position));
            break;
        case R.id.delete_context_menu_id:
            displayRemoveBookmarkDialog(i.position);
            break;
        case R.id.new_window_context_menu_id:
            openInNewWindow(i.position);
            break;
        case R.id.share_link_context_menu_id: {
            Cursor cursor = (Cursor) mAdapter.getItem(i.position);
            BrowserActivity.sharePage(activity,
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
            Cursor cursor = (Cursor) mAdapter.getItem(i.position);
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

    Bitmap getBitmap(Cursor cursor, int columnIndex) {
        byte[] data = cursor.getBlob(columnIndex);
        if (data == null) {
            return null;
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        final Activity activity = getActivity();
        MenuInflater inflater = activity.getMenuInflater();
        inflater.inflate(R.menu.bookmarkscontext, menu);

        if (mDisableNewWindow) {
            menu.findItem(R.id.new_window_context_menu_id).setVisible(false);
        }

        if (mContextHeader == null) {
            mContextHeader = new BookmarkItem(activity);
        } else if (mContextHeader.getParent() != null) {
            ((ViewGroup) mContextHeader.getParent()).removeView(mContextHeader);
        }

        populateBookmarkItem(mAdapter, mContextHeader, info.position);

        menu.setHeaderView(mContextHeader);
    }

    private void populateBookmarkItem(BrowserBookmarksAdapter adapter, BookmarkItem item,
            int position) {
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
        item.setUrl(url);
        item.setName(cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE));
        Bitmap bitmap = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON);
        if (bitmap == null) {
            bitmap = CombinedBookmarkHistoryActivity.getIconListenerSet().getFavicon(url);
        }
        item.setFavicon(bitmap);
    }

    /**
     *  Create a new BrowserBookmarksPage.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Bundle args = getArguments();
        mCreateShortcut = args == null ? false : args.getBoolean("create_shortcut", false);
        mDisableNewWindow = args == null ? false : args.getBoolean("disable_new_window", false);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbacks = (BookmarksHistoryCallbacks) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.bookmarks, container, false);
        mEmptyView = root.findViewById(android.R.id.empty);
        mContentView = root.findViewById(android.R.id.content);

        mGrid = (GridView) root.findViewById(R.id.grid);
        mGrid.setOnItemClickListener(this);
        mGrid.setColumnWidth(BrowserActivity.getDesiredThumbnailWidth(getActivity()));
        if (!mCreateShortcut) {
            mGrid.setOnCreateContextMenuListener(this);
        }

        mUpButton = (Button) root.findViewById(R.id.up);
        mUpButton.setEnabled(false);
        mUpButton.setOnClickListener(this);

        mAdapter = new BrowserBookmarksAdapter(getActivity());
        mGrid.setAdapter(mAdapter);

        // Start the loader for the bookmark data
        getLoaderManager().initLoader(LOADER_BOOKMARKS, null, this);

        // Add our own listener in case there are favicons that have yet to be loaded.
        CombinedBookmarkHistoryActivity.getIconListenerSet().addListener(this);

        return root;
    }

    @Override
    public void onReceivedIcon(String url, Bitmap icon) {
        // A new favicon has been loaded, so let anything attached to the adapter know about it
        // so new icons will be loaded.
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        // It is possible that the view has been canceled when we get to
        // this point as back has a higher priority
        if (mCanceled) {
            android.util.Log.e(LOGTAG, "item clicked when dismissing");
            return;
        }
        if (mCreateShortcut) {
            Intent intent = createShortcutIntent(position);
            // the activity handles the intent in startActivityFromFragment
            startActivity(intent);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(position);
        boolean isFolder = cursor.getInt(BookmarksLoader.COLUMN_INDEX_IS_FOLDER) != 0;
        if (!isFolder) {
            mCallbacks.onUrlSelected(getUrl(position), false);
        } else {
            String title;
            if (mFolderStack.size() != 0) {
                title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
            } else {
                // TODO localize
                title = "Bookmarks";
            }
            LoaderManager manager = getLoaderManager();
            BookmarksLoader loader =
                    (BookmarksLoader) ((Loader) manager.getLoader(LOADER_BOOKMARKS));
            mFolderStack.push(new Pair(title, loader.getUri()));
            Uri uri = ContentUris.withAppendedId(
                    BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER, id);
            loader.setUri(uri);
            loader.forceLoad();
        }
    }

    private Intent createShortcutIntent(int position) {
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
        String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
        Bitmap touchIcon = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_TOUCH_ICON);
        Bitmap favicon = getBitmap(cursor, BookmarksLoader.COLUMN_INDEX_FAVICON);
        return BookmarkUtils.createAddToHomeIntent(getActivity(), url, title, touchIcon, favicon);
    }

    private void loadUrl(int position) {
        mCallbacks.onUrlSelected(getUrl(position), false);
    }

    private void openInNewWindow(int position) {
        mCallbacks.onUrlSelected(getUrl(position), true);
    }

    private void editBookmark(int position) {
        Intent intent = new Intent(getActivity(), AddBookmarkPage.class);
        Cursor cursor = (Cursor) mAdapter.getItem(position);
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
        item.putInt("id", cursor.getInt(BookmarksLoader.COLUMN_INDEX_ID));
        intent.putExtra("bookmark", item);
        startActivityForResult(intent, BOOKMARKS_SAVE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case BOOKMARKS_SAVE:
                if (resultCode == Activity.RESULT_OK) {
                    Bundle extras;
                    if (data != null && (extras = data.getExtras()) != null) {
                        // If there are extras, then we need to save
                        // the edited bookmark. This is done in updateRow()
                        String title = extras.getString("title");
                        String url = extras.getString("url");
                        if (title != null && url != null) {
                            updateRow(extras);
                        }
                    }
                }
                break;
        }
    }

    /**
     *  Update a row in the database with new information.
     *  Requeries the database if the information has changed.
     *  @param map  Bundle storing id, title and url of new information
     */
    public void updateRow(Bundle map) {

        // Find the record
        int id = map.getInt("id");
        int position = -1;
        Cursor cursor = mAdapter.getCursor();
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            if (cursor.getInt(BookmarksLoader.COLUMN_INDEX_ID) == id) {
                position = cursor.getPosition();
                break;
            }
        }
        if (position < 0) {
            return;
        }

        cursor.moveToPosition(position);
        ContentValues values = new ContentValues();
        String title = map.getString(BrowserContract.Bookmarks.TITLE);
        if (!title.equals(cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE))) {
            values.put(BrowserContract.Bookmarks.TITLE, title);
        }
        String url = map.getString(BrowserContract.Bookmarks.URL);
        if (!url.equals(cursor.getString(BookmarksLoader.COLUMN_INDEX_URL))) {
            values.put(BrowserContract.Bookmarks.URL, url);
        }

        if (map.getBoolean("invalidateThumbnail") == true) {
            values.putNull(BrowserContract.Bookmarks.THUMBNAIL);
        }

        if (values.size() > 0) {
            getActivity().getContentResolver().update(
                    ContentUris.withAppendedId(BrowserContract.Bookmarks.CONTENT_URI, id),
                    values, null, null);
        }
        updateView();
    }

    private void displayRemoveBookmarkDialog(final int position) {
        // Put up a dialog asking if the user really wants to
        // delete the bookmark
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        Context context = getActivity();
        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_bookmark)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(context.getText(R.string.delete_bookmark_warning).toString().replace(
                        "%s", cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE)))
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                deleteBookmark(position);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getUrl(int position) {
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        return cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
    }

    private void copy(CharSequence text) {
        ClipboardManager cm = (ClipboardManager) getActivity().getSystemService(
                Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newRawUri(null, null, Uri.parse(text.toString())));
    }

    /**
     *  Delete the currently highlighted row.
     */
    public void deleteBookmark(int position) {
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        String url = cursor.getString(BookmarksLoader.COLUMN_INDEX_URL);
        String title = cursor.getString(BookmarksLoader.COLUMN_INDEX_TITLE);
        Bookmarks.removeFromBookmarks(null, getActivity().getContentResolver(), url, title);
        updateView();
    }

    private void updateView() {
        BookmarksLoader loader =
            (BookmarksLoader) (Loader)(getLoaderManager().getLoader(LOADER_BOOKMARKS));
        loader.forceLoad();
    }

}
