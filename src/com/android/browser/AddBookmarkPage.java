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

import com.android.browser.provider.BrowserProvider2;
import com.android.browser.addbookmark.FolderSpinnerAdapter;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.BrowserContract;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Stack;

public class AddBookmarkPage extends Activity
        implements View.OnClickListener, TextView.OnEditorActionListener,
        AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor>,
        BreadCrumbView.Controller, AdapterView.OnItemSelectedListener {

    public static final long DEFAULT_FOLDER_ID = -1;
    public static final String TOUCH_ICON_URL = "touch_icon_url";
    // Place on an edited bookmark to remove the saved thumbnail
    public static final String REMOVE_THUMBNAIL = "remove_thumbnail";
    public static final String USER_AGENT = "user_agent";

    /* package */ static final String EXTRA_EDIT_BOOKMARK = "bookmark";
    /* package */ static final String EXTRA_IS_FOLDER = "is_folder";

    private static final int MAX_CRUMBS_SHOWN = 2;

    private final String LOGTAG = "Bookmarks";

    // IDs for the CursorLoaders that are used.
    private final int LOADER_ID_FOLDER_CONTENTS = 0;
    private final int LOADER_ID_ALL_FOLDERS = 1;

    private EditText    mTitle;
    private EditText    mAddress;
    private TextView    mButton;
    private View        mCancelButton;
    private boolean     mEditingExisting;
    private boolean     mEditingFolder;
    private Bundle      mMap;
    private String      mTouchIconUrl;
    private String      mOriginalUrl;
    private Spinner mFolder;
    private View mDefaultView;
    private View mFolderSelector;
    private EditText mFolderNamer;
    private View mFolderCancel;
    private boolean mIsFolderNamerShowing;
    private View mFolderNamerHolder;
    private View mAddNewFolder;
    private View mAddSeparator;
    private long mCurrentFolder = 0;
    private FolderAdapter mAdapter;
    private BreadCrumbView mCrumbs;
    private TextView mFakeTitle;
    private View mCrumbHolder;
    private CustomListView mListView;
    private boolean mSaveToHomeScreen;
    private long mRootFolder;
    private TextView mTopLevelLabel;
    private Drawable mHeaderIcon;
    // We manually change the spinner's selection if the edited bookmark is not
    // in the root folder.  This makes sure our listener ignores this change.
    private boolean mIgnoreSelectionChange;
    private static class Folder {
        String Name;
        long Id;
        Folder(String name, long id) {
            Name = name;
            Id = id;
        }
    }

    // Message IDs
    private static final int SAVE_BOOKMARK = 100;
    private static final int TOUCH_ICON_DOWNLOADED = 101;

    private Handler mHandler;

    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    }

    private Uri getUriForFolder(long folder) {
        Uri uri;
        if (folder == mRootFolder) {
            uri = BrowserContract.Bookmarks.CONTENT_URI_DEFAULT_FOLDER;
        } else {
            uri = BrowserContract.Bookmarks.buildFolderUri(folder);
        }
        String[] accountInfo = getAccountNameAndType(this);
        if (accountInfo != null) {
            uri = BookmarksLoader.addAccount(uri, accountInfo[1], accountInfo[0]);
        }
        return uri;
    }

    @Override
    public void onTop(int level, Object data) {
        if (null == data) return;
        Folder folderData = (Folder) data;
        long folder = folderData.Id;
        LoaderManager manager = getLoaderManager();
        CursorLoader loader = (CursorLoader) ((Loader) manager.getLoader(
                LOADER_ID_FOLDER_CONTENTS));
        loader.setUri(getUriForFolder(folder));
        loader.forceLoad();
        if (mIsFolderNamerShowing) {
            completeOrCancelFolderNaming(true);
        }
        setShowBookmarkIcon(level == 1);
    }

    /**
     * Show or hide the icon for bookmarks next to "Bookmarks" in the crumb view.
     * @param show True if the icon should visible, false otherwise.
     */
    private void setShowBookmarkIcon(boolean show) {
        Drawable drawable = show ? mHeaderIcon: null;
        mTopLevelLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (v == mFolderNamer) {
            if (v.getText().length() > 0) {
                if (actionId == EditorInfo.IME_NULL) {
                    // Only want to do this once.
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        completeOrCancelFolderNaming(false);
                    }
                }
            }
            // Steal the key press; otherwise a newline will be added
            return true;
        }
        return false;
    }

    private void switchToDefaultView(boolean changedFolder) {
        mFolderSelector.setVisibility(View.GONE);
        mDefaultView.setVisibility(View.VISIBLE);
        mCrumbHolder.setVisibility(View.GONE);
        mFakeTitle.setVisibility(View.VISIBLE);
        if (changedFolder) {
            Object data = mCrumbs.getTopData();
            if (data != null) {
                Folder folder = (Folder) data;
                mCurrentFolder = folder.Id;
                if (mCurrentFolder == mRootFolder) {
                    // The Spinner changed to show "Other folder ..."  Change
                    // it back to "Bookmarks", which is position 0 if we are
                    // editing a folder, 1 otherwise.
                    mFolder.setSelection(mEditingFolder ? 0 : 1);
                } else {
                    ((TextView) mFolder.getSelectedView()).setText(folder.Name);
                }
            }
        } else {
            // The user canceled selecting a folder.  Revert back to the earlier
            // selection.
            if (mSaveToHomeScreen) {
                mFolder.setSelection(0);
            } else {
                mFolder.setSelection(mEditingFolder ? 0 : 1);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mButton) {
            if (mFolderSelector.getVisibility() == View.VISIBLE) {
                // We are showing the folder selector.
                if (mIsFolderNamerShowing) {
                    completeOrCancelFolderNaming(false);
                } else {
                    // User has selected a folder.  Go back to the opening page
                    mSaveToHomeScreen = false;
                    switchToDefaultView(true);
                }
            } else if (save()) {
                finish();
            }
        } else if (v == mCancelButton) {
            if (mIsFolderNamerShowing) {
                completeOrCancelFolderNaming(true);
            } else if (mFolderSelector.getVisibility() == View.VISIBLE) {
                switchToDefaultView(false);
            } else {
                finish();
            }
        } else if (v == mFolderCancel) {
            completeOrCancelFolderNaming(true);
        } else if (v == mAddNewFolder) {
            setShowFolderNamer(true);
            mFolderNamer.setText(R.string.new_folder);
            mFolderNamer.requestFocus();
            mAddNewFolder.setVisibility(View.GONE);
            mAddSeparator.setVisibility(View.GONE);
            InputMethodManager imm = getInputMethodManager();
            // Set the InputMethodManager to focus on the ListView so that it
            // can transfer the focus to mFolderNamer.
            imm.focusIn(mListView);
            imm.showSoftInput(mFolderNamer, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mIgnoreSelectionChange) {
            mIgnoreSelectionChange = false;
            return;
        }
        // In response to the spinner changing.
        int intId = (int) id;
        switch (intId) {
            case FolderSpinnerAdapter.ROOT_FOLDER:
                mCurrentFolder = mRootFolder;
                mSaveToHomeScreen = false;
                break;
            case FolderSpinnerAdapter.HOME_SCREEN:
                // Create a short cut to the home screen
                mSaveToHomeScreen = true;
                break;
            case FolderSpinnerAdapter.OTHER_FOLDER:
                switchToFolderSelector();
                break;
            default:
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /**
     * Finish naming a folder, and close the IME
     * @param cancel If true, the new folder is not created.  If false, the new
     *      folder is created and the user is taken inside it.
     */
    private void completeOrCancelFolderNaming(boolean cancel) {
        if (!cancel && !TextUtils.isEmpty(mFolderNamer.getText())) {
            String name = mFolderNamer.getText().toString();
            long id = addFolderToCurrent(mFolderNamer.getText().toString());
            descendInto(name, id);
        }
        setShowFolderNamer(false);
        mAddNewFolder.setVisibility(View.VISIBLE);
        mAddSeparator.setVisibility(View.VISIBLE);
        getInputMethodManager().hideSoftInputFromWindow(
                mListView.getWindowToken(), 0);
    }

    private long addFolderToCurrent(String name) {
        // Add the folder to the database
        ContentValues values = new ContentValues();
        values.put(BrowserContract.Bookmarks.TITLE,
                name);
        values.put(BrowserContract.Bookmarks.IS_FOLDER, 1);
        String[] accountInfo = getAccountNameAndType(this);
        if (accountInfo != null) {
            values.put(BrowserContract.Bookmarks.ACCOUNT_TYPE, accountInfo[1]);
            values.put(BrowserContract.Bookmarks.ACCOUNT_NAME, accountInfo[0]);
        }
        long currentFolder;
        Object data = mCrumbs.getTopData();
        if (data != null) {
            currentFolder = ((Folder) data).Id;
        } else {
            currentFolder = mRootFolder;
        }
        values.put(BrowserContract.Bookmarks.PARENT, currentFolder);
        Uri uri = getContentResolver().insert(
                BrowserContract.Bookmarks.CONTENT_URI, values);
        if (uri != null) {
            return ContentUris.parseId(uri);
        } else {
            return -1;
        }
    }

    private void switchToFolderSelector() {
        // Set the list to the top in case it is scrolled.
        mListView.setSelection(0);
        mDefaultView.setVisibility(View.GONE);
        mFolderSelector.setVisibility(View.VISIBLE);
        mCrumbHolder.setVisibility(View.VISIBLE);
        mFakeTitle.setVisibility(View.GONE);
        mAddNewFolder.setVisibility(View.VISIBLE);
        mAddSeparator.setVisibility(View.VISIBLE);
    }

    private void descendInto(String foldername, long id) {
        if (id != DEFAULT_FOLDER_ID) {
            mCrumbs.pushView(foldername, new Folder(foldername, id));
            mCrumbs.notifyController();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection;
        switch (id) {
            case LOADER_ID_ALL_FOLDERS:
                projection = new String[] {
                        BrowserContract.Bookmarks._ID,
                        BrowserContract.Bookmarks.PARENT,
                        BrowserContract.Bookmarks.TITLE,
                        BrowserContract.Bookmarks.IS_FOLDER
                };
                return new CursorLoader(this,
                        BrowserContract.Bookmarks.CONTENT_URI,
                        projection,
                        BrowserContract.Bookmarks.IS_FOLDER + " != 0",
                        null,
                        null);
            case LOADER_ID_FOLDER_CONTENTS:
                projection = new String[] {
                        BrowserContract.Bookmarks._ID,
                        BrowserContract.Bookmarks.TITLE,
                        BrowserContract.Bookmarks.IS_FOLDER
                };
                String where = BrowserContract.Bookmarks.IS_FOLDER + " != 0";
                if (mEditingFolder) {
                    where += " AND " + BrowserContract.Bookmarks._ID + " != "
                            + mMap.getLong(BrowserContract.Bookmarks._ID);
                }
                return new CursorLoader(this,
                        getUriForFolder(mCurrentFolder),
                        projection,
                        where,
                        null,
                        BrowserContract.Bookmarks._ID + " ASC");
            default:
                throw new AssertionError("Asking for nonexistant loader!");
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        switch (loader.getId()) {
            case LOADER_ID_FOLDER_CONTENTS:
                mAdapter.changeCursor(cursor);
                break;
            case LOADER_ID_ALL_FOLDERS:
                long parent = mCurrentFolder;
                int idIndex = cursor.getColumnIndexOrThrow(
                        BrowserContract.Bookmarks._ID);
                int titleIndex = cursor.getColumnIndexOrThrow(
                        BrowserContract.Bookmarks.TITLE);
                int parentIndex = cursor.getColumnIndexOrThrow(
                        BrowserContract.Bookmarks.PARENT);
                // If the user is editing anything inside the "Other Bookmarks"
                // folder, we need to stop searching up when we reach its parent.
                // Find the root folder
                moveCursorToFolder(cursor, mRootFolder, idIndex);
                // omniparent is the folder which contains root, and therefore
                // also the parent of the "Other Bookmarks" folder.
                long omniparent = cursor.getLong(parentIndex);
                Stack<Folder> folderStack = new Stack<Folder>();
                while ((parent != mRootFolder) && (parent != 0) && (parent != omniparent)) {
                    // First, find the folder corresponding to the current
                    // folder
                    moveCursorToFolder(cursor, parent, idIndex);
                    String name = cursor.getString(titleIndex);
                    if (parent == mCurrentFolder) {
                        ((TextView) mFolder.getSelectedView()).setText(name);
                    }
                    folderStack.push(new Folder(name, parent));
                    parent = cursor.getLong(parentIndex);
                }
                while (!folderStack.isEmpty()) {
                    Folder thisFolder = folderStack.pop();
                    mCrumbs.pushView(thisFolder.Name, thisFolder);
                }
                getLoaderManager().destroyLoader(LOADER_ID_ALL_FOLDERS);
                break;
            default:
                break;
        }
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {
            case LOADER_ID_FOLDER_CONTENTS:
                mAdapter.changeCursor(null);
                break;
        }
    }

    /**
     * Move cursor to the position that has folderToFind as its "_id".
     * @param cursor Cursor containing folders in the bookmarks database
     * @param folderToFind "_id" of the folder to move to.
     * @param idIndex Index in cursor of "_id"
     * @throws AssertionError if cursor is empty or there is no row with folderToFind
     *      as its "_id".
     */
    void moveCursorToFolder(Cursor cursor, long folderToFind, int idIndex)
            throws AssertionError {
        if (!cursor.moveToFirst()) {
            throw new AssertionError("No folders in the database!");
        }
        long folder;
        do {
            folder = cursor.getLong(idIndex);
        } while (folder != folderToFind && cursor.moveToNext());
        if (cursor.isAfterLast()) {
            throw new AssertionError("Folder(id=" + folderToFind
                    + ") holding this bookmark does not exist!");
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        TextView tv = (TextView) view.findViewById(android.R.id.text1);
        // Switch to the folder that was clicked on.
        descendInto(tv.getText().toString(), id);
    }

    private void setShowFolderNamer(boolean show) {
        if (show != mIsFolderNamerShowing) {
            mIsFolderNamerShowing = show;
            if (show) {
                // Set the selection to the folder namer so it will be in
                // view.
                mListView.addFooterView(mFolderNamerHolder);
            } else {
                mListView.removeFooterView(mFolderNamerHolder);
            }
            // Refresh the list.
            mListView.setAdapter(mAdapter);
            if (show) {
                mListView.setSelection(mListView.getCount() - 1);
            }
        }
    }

    /**
     * Shows a list of names of folders.
     */
    private class FolderAdapter extends CursorAdapter {
        public FolderAdapter(Context context) {
            super(context, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((TextView) view.findViewById(android.R.id.text1)).setText(
                    cursor.getString(cursor.getColumnIndexOrThrow(
                    BrowserContract.Bookmarks.TITLE)));
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = LayoutInflater.from(context).inflate(
                    R.layout.folder_list_item, null);
            view.setBackgroundDrawable(context.getResources().
                    getDrawable(android.R.drawable.list_selector_background));
            return view;
        }

        @Override
        public boolean isEmpty() {
            // Do not show the empty view if the user is creating a new folder.
            return super.isEmpty() && !mIsFolderNamerShowing;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mMap = getIntent().getExtras();

        setContentView(R.layout.browser_add_bookmark);

        Window window = getWindow();

        String title = null;
        String url = null;

        mFakeTitle = (TextView) findViewById(R.id.fake_title);

        if (mMap != null) {
            Bundle b = mMap.getBundle(EXTRA_EDIT_BOOKMARK);
            if (b != null) {
                mEditingFolder = mMap.getBoolean(EXTRA_IS_FOLDER, false);
                mMap = b;
                mEditingExisting = true;
                mFakeTitle.setText(R.string.edit_bookmark);
                if (mEditingFolder) {
                    findViewById(R.id.row_address).setVisibility(View.GONE);
                }
            } else {
                int gravity = mMap.getInt("gravity", -1);
                if (gravity != -1) {
                    WindowManager.LayoutParams l = window.getAttributes();
                    l.gravity = gravity;
                    window.setAttributes(l);
                }
            }
            title = mMap.getString(BrowserContract.Bookmarks.TITLE);
            url = mOriginalUrl = mMap.getString(BrowserContract.Bookmarks.URL);
            mTouchIconUrl = mMap.getString(TOUCH_ICON_URL);
            mCurrentFolder = mMap.getLong(BrowserContract.Bookmarks.PARENT, DEFAULT_FOLDER_ID);
        }
        mRootFolder = getBookmarksBarId(this);
        if (mCurrentFolder == DEFAULT_FOLDER_ID) {
            mCurrentFolder = mRootFolder;
        }

        mTitle = (EditText) findViewById(R.id.title);
        mTitle.setText(title);

        mAddress = (EditText) findViewById(R.id.address);
        mAddress.setText(url);

        mButton = (TextView) findViewById(R.id.OK);
        mButton.setOnClickListener(this);

        mCancelButton = findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(this);

        mFolder = (Spinner) findViewById(R.id.folder);
        mFolder.setAdapter(new FolderSpinnerAdapter(!mEditingFolder));
        if (!mEditingFolder) {
            // Initially the "Bookmarks" folder should be showing, rather than
            // the home screen.  In the editing folder case, home screen is not
            // an option, so "Bookmarks" folder is already at the top.
            mFolder.setSelection(FolderSpinnerAdapter.ROOT_FOLDER);
        }
        mFolder.setOnItemSelectedListener(this);

        mDefaultView = findViewById(R.id.default_view);
        mFolderSelector = findViewById(R.id.folder_selector);

        mFolderNamerHolder = getLayoutInflater().inflate(R.layout.new_folder_layout, null);
        mFolderNamer = (EditText) mFolderNamerHolder.findViewById(R.id.folder_namer);
        mFolderNamer.setOnEditorActionListener(this);
        mFolderCancel = mFolderNamerHolder.findViewById(R.id.close);
        mFolderCancel.setOnClickListener(this);

        mAddNewFolder = findViewById(R.id.add_new_folder);
        mAddNewFolder.setOnClickListener(this);
        mAddSeparator = findViewById(R.id.add_divider);

        mCrumbs = (BreadCrumbView) findViewById(R.id.crumbs);
        mCrumbs.setUseBackButton(true);
        mCrumbs.setController(this);
        String name = getString(R.string.bookmarks);
        mTopLevelLabel = (TextView) mCrumbs.pushView(name, false, new Folder(name, mRootFolder));
        // To better match the other folders.
        mTopLevelLabel.setCompoundDrawablePadding(6);
        mHeaderIcon = getResources().getDrawable(R.drawable.ic_folder_bookmark_widget_holo_dark);
        mCrumbHolder = findViewById(R.id.crumb_holder);
        mCrumbs.setMaxVisible(MAX_CRUMBS_SHOWN);

        mAdapter = new FolderAdapter(this);
        mListView = (CustomListView) findViewById(R.id.list);
        View empty = findViewById(R.id.empty);
        mListView.setEmptyView(empty);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        mListView.addEditText(mFolderNamer);
        LoaderManager manager = getLoaderManager();
        if (mCurrentFolder != mRootFolder) {
            // Find all the folders
            manager.initLoader(LOADER_ID_ALL_FOLDERS, null, this);
            // Since we're not in the root folder, change the selection to other
            // folder now.  The text will get changed once we select the correct
            // folder.
            mIgnoreSelectionChange = true;
            mFolder.setSelection(mEditingFolder ? 1 : 2);
        } else {
            setShowBookmarkIcon(true);
        }
        // Find the contents of the current folder
        manager.initLoader(LOADER_ID_FOLDER_CONTENTS, null, this);


        if (!window.getDecorView().isInTouchMode()) {
            mButton.requestFocus();
        }
    }

    /**
     * Get the account name and type of the currently synced account.
     * @param context Context to access preferences.
     * @return null if no account name or type.  Otherwise, the result will be
     *      an array of two Strings, the accountName and accountType, respectively.
     */
    private String[] getAccountNameAndType(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_NAME, null);
        String accountType = prefs.getString(BrowserBookmarksPage.PREF_ACCOUNT_TYPE, null);
        if (TextUtils.isEmpty(accountName) || TextUtils.isEmpty(accountType)) {
            return null;
        }
        return new String[] { accountName, accountType };
    }

    // FIXME: Use a CursorLoader
    private long getBookmarksBarId(Context context) {
        String[] accountInfo = getAccountNameAndType(context);
        if (accountInfo == null) {
            return BrowserProvider2.FIXED_ID_ROOT;
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    BrowserContract.Bookmarks.CONTENT_URI,
                    new String[] { BrowserContract.Bookmarks._ID },
                    BrowserContract.ChromeSyncColumns.SERVER_UNIQUE + "=? AND "
                            + BrowserContract.Bookmarks.ACCOUNT_NAME + "=? AND "
                            + BrowserContract.Bookmarks.ACCOUNT_TYPE + "=?",
                    new String[] {
                            BrowserContract.ChromeSyncColumns
                                    .FOLDER_NAME_BOOKMARKS_BAR,
                            accountInfo[0],
                            accountInfo[1] },
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return BrowserProvider2.FIXED_ID_ROOT;
    }

    /**
     * Runnable to save a bookmark, so it can be performed in its own thread.
     */
    private class SaveBookmarkRunnable implements Runnable {
        // FIXME: This should be an async task.
        private Message mMessage;
        private Context mContext;
        public SaveBookmarkRunnable(Context ctx, Message msg) {
            mContext = ctx;
            mMessage = msg;
        }
        public void run() {
            // Unbundle bookmark data.
            Bundle bundle = mMessage.getData();
            String title = bundle.getString(BrowserContract.Bookmarks.TITLE);
            String url = bundle.getString(BrowserContract.Bookmarks.URL);
            boolean invalidateThumbnail = bundle.getBoolean(REMOVE_THUMBNAIL);
            Bitmap thumbnail = invalidateThumbnail ? null
                    : (Bitmap) bundle.getParcelable(BrowserContract.Bookmarks.THUMBNAIL);
            String touchIconUrl = bundle.getString(TOUCH_ICON_URL);

            // Save to the bookmarks DB.
            try {
                final ContentResolver cr = getContentResolver();
                Bookmarks.addBookmark(AddBookmarkPage.this, false, url,
                        title, thumbnail, true, mCurrentFolder);
                if (touchIconUrl != null) {
                    new DownloadTouchIcon(mContext, cr, url).execute(mTouchIconUrl);
                }
                mMessage.arg1 = 1;
            } catch (IllegalStateException e) {
                mMessage.arg1 = 0;
            }
            mMessage.sendToTarget();
        }
    }

    private static class UpdateBookmarkTask extends AsyncTask<ContentValues, Void, Void> {
        Context mContext;
        Long mId;

        public UpdateBookmarkTask(Context context, long id) {
            mContext = context;
            mId = id;
        }

        @Override
        protected Void doInBackground(ContentValues... params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("No ContentValues provided!");
            }
            Uri uri = ContentUris.withAppendedId(BookmarkUtils.getBookmarksUri(mContext), mId);
            mContext.getContentResolver().update(
                    uri,
                    params[0], null, null);
            return null;
        }
    }

    private void createHandler() {
        if (mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case SAVE_BOOKMARK:
                            if (1 == msg.arg1) {
                                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_saved,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_not_saved,
                                        Toast.LENGTH_LONG).show();
                            }
                            break;
                        case TOUCH_ICON_DOWNLOADED:
                            Bundle b = msg.getData();
                            sendBroadcast(BookmarkUtils.createAddToHomeIntent(
                                    AddBookmarkPage.this,
                                    b.getString(BrowserContract.Bookmarks.URL),
                                    b.getString(BrowserContract.Bookmarks.TITLE),
                                    (Bitmap) b.getParcelable(BrowserContract.Bookmarks.TOUCH_ICON),
                                    (Bitmap) b.getParcelable(BrowserContract.Bookmarks.FAVICON)));
                            break;
                    }
                }
            };
        }
    }

    /**
     * Parse the data entered in the dialog and post a message to update the bookmarks database.
     */
    boolean save() {
        createHandler();

        String title = mTitle.getText().toString().trim();
        String unfilteredUrl;
        unfilteredUrl = UrlUtils.fixUrl(mAddress.getText().toString());

        boolean emptyTitle = title.length() == 0;
        boolean emptyUrl = unfilteredUrl.trim().length() == 0;
        Resources r = getResources();
        if (emptyTitle || (emptyUrl && !mEditingFolder)) {
            if (emptyTitle) {
                mTitle.setError(r.getText(R.string.bookmark_needs_title));
            }
            if (emptyUrl) {
                mAddress.setError(r.getText(R.string.bookmark_needs_url));
            }
            return false;

        }
        String url = unfilteredUrl.trim();
        if (!mEditingFolder) {
            try {
                // We allow bookmarks with a javascript: scheme, but these will in most cases
                // fail URI parsing, so don't try it if that's the kind of bookmark we have.

                if (!url.toLowerCase().startsWith("javascript:")) {
                    URI uriObj = new URI(url);
                    String scheme = uriObj.getScheme();
                    if (!Bookmarks.urlHasAcceptableScheme(url)) {
                        // If the scheme was non-null, let the user know that we
                        // can't save their bookmark. If it was null, we'll assume
                        // they meant http when we parse it in the WebAddress class.
                        if (scheme != null) {
                            mAddress.setError(r.getText(R.string.bookmark_cannot_save_url));
                            return false;
                        }
                        WebAddress address;
                        try {
                            address = new WebAddress(unfilteredUrl);
                        } catch (ParseException e) {
                            throw new URISyntaxException("", "");
                        }
                        if (address.getHost().length() == 0) {
                            throw new URISyntaxException("", "");
                        }
                        url = address.toString();
                    }
                }
            } catch (URISyntaxException e) {
                mAddress.setError(r.getText(R.string.bookmark_url_not_valid));
                return false;
            }
        }

        if (mSaveToHomeScreen) {
            mEditingExisting = false;
        }

        boolean urlUnmodified = url.equals(mOriginalUrl);

        if (mEditingExisting) {
            Long id = mMap.getLong(BrowserContract.Bookmarks._ID);
            ContentValues values = new ContentValues();
            values.put(BrowserContract.Bookmarks.TITLE, title);
            values.put(BrowserContract.Bookmarks.PARENT, mCurrentFolder);
            if (!mEditingFolder) {
                values.put(BrowserContract.Bookmarks.URL, url);
                if (!urlUnmodified) {
                    values.putNull(BrowserContract.Bookmarks.THUMBNAIL);
                }
            }
            if (values.size() > 0) {
                new UpdateBookmarkTask(getApplicationContext(), id).execute(values);
            }
            setResult(RESULT_OK);
        } else {
            Bitmap thumbnail;
            Bitmap favicon;
            if (urlUnmodified) {
                thumbnail = (Bitmap) mMap.getParcelable(
                        BrowserContract.Bookmarks.THUMBNAIL);
                favicon = (Bitmap) mMap.getParcelable(
                        BrowserContract.Bookmarks.FAVICON);
            } else {
                thumbnail = null;
                favicon = null;
            }

            Bundle bundle = new Bundle();
            bundle.putString(BrowserContract.Bookmarks.TITLE, title);
            bundle.putString(BrowserContract.Bookmarks.URL, url);
            bundle.putParcelable(BrowserContract.Bookmarks.FAVICON, favicon);

            if (mSaveToHomeScreen) {
                if (mTouchIconUrl != null && urlUnmodified) {
                    Message msg = Message.obtain(mHandler,
                            TOUCH_ICON_DOWNLOADED);
                    msg.setData(bundle);
                    DownloadTouchIcon icon = new DownloadTouchIcon(this, msg,
                            mMap.getString(USER_AGENT));
                    icon.execute(mTouchIconUrl);
                } else {
                    sendBroadcast(BookmarkUtils.createAddToHomeIntent(this, url,
                            title, null /*touchIcon*/, favicon));
                }
            } else {
                bundle.putParcelable(BrowserContract.Bookmarks.THUMBNAIL, thumbnail);
                bundle.putBoolean(REMOVE_THUMBNAIL, !urlUnmodified);
                bundle.putString(TOUCH_ICON_URL, mTouchIconUrl);
                // Post a message to write to the DB.
                Message msg = Message.obtain(mHandler, SAVE_BOOKMARK);
                msg.setData(bundle);
                // Start a new thread so as to not slow down the UI
                Thread t = new Thread(new SaveBookmarkRunnable(getApplicationContext(), msg));
                t.start();
            }
            setResult(RESULT_OK);
            LogTag.logBookmarkAdded(url, "bookmarkview");
        }
        return true;
    }

    /*
     * Class used as a proxy for the InputMethodManager to get to mFolderNamer
     */
    public static class CustomListView extends ListView {
        private EditText mEditText;

        public void addEditText(EditText editText) {
            mEditText = editText;
        }

        public CustomListView(Context context) {
            super(context);
        }

        public CustomListView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public CustomListView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean checkInputConnectionProxy(View view) {
            return view == mEditText;
        }
    }
}
