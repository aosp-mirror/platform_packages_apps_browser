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
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.provider.Browser;
import android.text.IClipboard;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 *  View showing the user's bookmarks in the browser.
 */
public class BrowserBookmarksPage extends Activity implements 
        View.OnCreateContextMenuListener {

    private BrowserBookmarksAdapter mBookmarksAdapter;
    private static final int        BOOKMARKS_SAVE = 1;
    private boolean                 mMaxTabsOpen;
    private BookmarkItem            mContextHeader;
    private AddNewBookmark          mAddHeader;
    private boolean                 mCanceled = false;
    private boolean                 mCreateShortcut;
    // XXX: There is no public string defining this intent so if Home changes
    // the value, we have to update this string.
    private static final String     INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";
    
    private final static String LOGTAG = "browser";


    @Override
    public boolean onContextItemSelected(MenuItem item) {
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
        case R.id.new_context_menu_id:
            saveCurrentPage();
            break;
        case R.id.open_context_menu_id:
            loadUrl(i.position);
            break;
        case R.id.edit_context_menu_id:
            editBookmark(i.position);
            break;
        case R.id.shortcut_context_menu_id:
            final Intent send = createShortcutIntent(getUrl(i.position),
                    getBookmarkTitle(i.position), getFavicon(i.position));
            send.setAction(INSTALL_SHORTCUT);
            sendBroadcast(send);
            break;
        case R.id.delete_context_menu_id:
            displayRemoveBookmarkDialog(i.position);
            break;
        case R.id.new_window_context_menu_id:
            openInNewWindow(i.position);
            break;
        case R.id.send_context_menu_id:
            Browser.sendString(BrowserBookmarksPage.this, getUrl(i.position));
            break;
        case R.id.copy_url_context_menu_id:
            copy(getUrl(i.position));
            
        default:
            return super.onContextItemSelected(item);
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            AdapterView.AdapterContextMenuInfo i = 
                    (AdapterView.AdapterContextMenuInfo) menuInfo;

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.bookmarkscontext, menu);

            if (0 == i.position) {
                menu.setGroupVisible(R.id.CONTEXT_MENU, false);
                if (mAddHeader == null) {
                    mAddHeader = new AddNewBookmark(BrowserBookmarksPage.this);
                } else if (mAddHeader.getParent() != null) {
                    ((ViewGroup) mAddHeader.getParent()).
                            removeView(mAddHeader);
                }
                ((AddNewBookmark) i.targetView).copyTo(mAddHeader);
                menu.setHeaderView(mAddHeader);
                return;
            }
            menu.setGroupVisible(R.id.ADD_MENU, false);
            BookmarkItem b = (BookmarkItem) i.targetView;
            if (mContextHeader == null) {
                mContextHeader = new BookmarkItem(BrowserBookmarksPage.this);
            } else if (mContextHeader.getParent() != null) {
                ((ViewGroup) mContextHeader.getParent()).
                        removeView(mContextHeader);
            }
            b.copyTo(mContextHeader);
            menu.setHeaderView(mContextHeader);

            if (mMaxTabsOpen) {
                menu.findItem(R.id.new_window_context_menu_id).setVisible(
                        false);
            }
        }

    /**
     *  Create a new BrowserBookmarksPage.
     */  
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.browser_bookmarks_page);
        setTitle(R.string.browser_bookmarks_page_bookmarks_text);

        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            mCreateShortcut = true;
        }

        mBookmarksAdapter = new BrowserBookmarksAdapter(this, 
                getIntent().getStringExtra("url"), mCreateShortcut);
        mMaxTabsOpen = getIntent().getBooleanExtra("maxTabsOpen", false);

        ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(mBookmarksAdapter);
        listView.setDrawSelectorOnTop(false);
        listView.setVerticalScrollBarEnabled(true);
        listView.setOnItemClickListener(mListener);

        if (!mCreateShortcut) {
            listView.setOnCreateContextMenuListener(this);
        }
    }

    private static final int SAVE_CURRENT_PAGE = 1000;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SAVE_CURRENT_PAGE) {
                saveCurrentPage();
            }
        }
    };

    private AdapterView.OnItemClickListener mListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            // It is possible that the view has been canceled when we get to
            // this point as back has a higher priority 
            if (mCanceled) {
                android.util.Log.e("browser", "item clicked when dismising");
                return;
            }
            if (!mCreateShortcut) {
                if (0 == position) {
                    // XXX: Work-around for a framework issue.
                    mHandler.sendEmptyMessage(SAVE_CURRENT_PAGE);
                } else {
                    loadUrl(position);
                }
            } else {
                final Intent intent = createShortcutIntent(getUrl(position),
                        getBookmarkTitle(position), getFavicon(position));
                setResultToParent(RESULT_OK, intent);
                finish();
            }
        }
    };

    private Intent createShortcutIntent(String url, String title,
            Bitmap favicon) {
        final Intent i = new Intent();
        final Intent shortcutIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(url));
        long urlHash = url.hashCode();
        long uniqueId = (urlHash << 32) | shortcutIntent.hashCode();
        shortcutIntent.putExtra(Browser.EXTRA_APPLICATION_ID,
                Long.toString(uniqueId));
        i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        i.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        if (favicon == null) {
            i.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(
                            BrowserBookmarksPage.this,
                            R.drawable.ic_launcher_shortcut_browser_bookmark));
        } else {
            Bitmap icon = BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_launcher_shortcut_browser_bookmark);

            // Make a copy of the regular icon so we can modify the pixels.
            Bitmap copy = icon.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(copy);

            // Make a Paint for the white background rectangle and for
            // filtering the favicon.
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG
                    | Paint.FILTER_BITMAP_FLAG);
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setColor(Color.WHITE);

            // Create a rectangle that is slightly wider than the favicon
            final float iconSize = 16; // 16x16 favicon
            final float padding = 2;   // white padding around icon
            final float rectSize = iconSize + 2 * padding;
            final float y = icon.getHeight() - rectSize;
            RectF r = new RectF(0, y, rectSize, y + rectSize);

            // Draw a white rounded rectangle behind the favicon
            canvas.drawRoundRect(r, 2, 2, p);

            // Draw the favicon in the same rectangle as the rounded rectangle
            // but inset by the padding (results in a 16x16 favicon).
            r.inset(padding, padding);
            canvas.drawBitmap(favicon, null, r, p);
            i.putExtra(Intent.EXTRA_SHORTCUT_ICON, copy);
        }
        // Do not allow duplicate items
        i.putExtra("duplicate", false);
        return i;
    }

    private void saveCurrentPage() {
        Intent i = new Intent(BrowserBookmarksPage.this,
                AddBookmarkPage.class);
        i.putExtras(getIntent());
        startActivityForResult(i, BOOKMARKS_SAVE);
    }

    private void loadUrl(int position) {
        Intent intent = (new Intent()).setAction(getUrl(position));
        setResultToParent(RESULT_OK, intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        if (!mCreateShortcut) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.bookmarks, menu);
            return true;
        }
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_context_menu_id:
                saveCurrentPage();
                break;
                
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void openInNewWindow(int position) {
        Bundle b = new Bundle();
        b.putBoolean("new_window", true);
        setResultToParent(RESULT_OK,
                (new Intent()).setAction(getUrl(position)).putExtras(b));

        finish();
    }
    

    private void editBookmark(int position) {
        Intent intent = new Intent(BrowserBookmarksPage.this, 
            AddBookmarkPage.class);
        intent.putExtra("bookmark", getRow(position));
        startActivityForResult(intent, BOOKMARKS_SAVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        switch(requestCode) {
            case BOOKMARKS_SAVE:
                if (resultCode == RESULT_OK) {
                    Bundle extras;
                    if (data != null && (extras = data.getExtras()) != null) {
                        // If there are extras, then we need to save
                        // the edited bookmark. This is done in updateRow()
                        String title = extras.getString("title");
                        String url = extras.getString("url");
                        if (title != null && url != null) {
                            mBookmarksAdapter.updateRow(extras);
                        }
                    } else {
                        // extras == null then a new bookmark was added to
                        // the database.
                        refreshList();
                    }
                }
                break;
            default:
                break;
        }
    }
    
    private void displayRemoveBookmarkDialog(int position) {
        // Put up a dialog asking if the user really wants to
        // delete the bookmark
        final int deletePos = position;
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_bookmark)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(getText(R.string.delete_bookmark_warning).toString().replace(
                        "%s", getBookmarkTitle(deletePos)))
                .setPositiveButton(R.string.ok, 
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                deleteBookmark(deletePos);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     *  Refresh the shown list after the database has changed.
     */
    public void refreshList() {
        mBookmarksAdapter.refreshList();
    }
    
    /**
     *  Return a hashmap representing the currently highlighted row.
     */
    public Bundle getRow(int position) {
        return mBookmarksAdapter.getRow(position);
    }

    /**
     *  Return the url of the currently highlighted row.
     */
    public String getUrl(int position) {
        return mBookmarksAdapter.getUrl(position);
    }

    /**
     * Return the favicon of the currently highlighted row.
     */
    public Bitmap getFavicon(int position) {
        return mBookmarksAdapter.getFavicon(position);
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
    
    public String getBookmarkTitle(int position) {
        return mBookmarksAdapter.getTitle(position);
    }

    /**
     *  Delete the currently highlighted row.
     */
    public void deleteBookmark(int position) {
        mBookmarksAdapter.deleteRow(position);
    }
    
    public boolean dispatchKeyEvent(KeyEvent event) {    
        if (event.getKeyCode() ==  KeyEvent.KEYCODE_BACK && event.isDown()) {
            setResultToParent(RESULT_CANCELED, null);
            mCanceled = true;
        }
        return super.dispatchKeyEvent(event);
    }

    // This Activity is generally a sub-Activity of CombinedHistoryActivity. In
    // that situation, we need to pass our result code up to our parent.
    // However, if someone calls this Activity directly, then this has no
    // parent, and it needs to set it on itself.
    private void setResultToParent(int resultCode, Intent data) {
        Activity a = getParent() == null ? this : getParent();
        a.setResult(resultCode, data);
    }
}
