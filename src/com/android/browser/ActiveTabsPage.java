/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class ActiveTabsPage extends LinearLayout {
    private final BrowserActivity   mBrowserActivity;
    private final LayoutInflater    mFactory;
    private final TabControl        mControl;
    private final TabsListAdapter   mAdapter;
    private final ListView          mListView;

    public ActiveTabsPage(BrowserActivity context, TabControl control) {
        super(context);
        mBrowserActivity = context;
        mControl = control;
        mFactory = LayoutInflater.from(context);
        mFactory.inflate(R.layout.active_tabs, this);
        mListView = (ListView) findViewById(R.id.list);
        mAdapter = new TabsListAdapter();
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view,
                        int position, long id) {
                    if (mControl.getTabCount() < TabControl.MAX_TABS) {
                        position--;
                    }
                    boolean needToAttach = false;
                    if (position == -1) {
                        // Create a new tab
                        mBrowserActivity.openTabToHomePage();
                    } else {
                        // Open the corresponding tab
                        // If the tab is the current one, switchToTab will
                        // do nothing and return, so we need to make sure
                        // it gets attached back to its mContentView in
                        // removeActiveTabPage
                        needToAttach = !mBrowserActivity.switchToTab(position);
                    }
                    mBrowserActivity.removeActiveTabPage(needToAttach);
                }
        });
    }

    /**
     * Special class to hold the close drawable.  Its sole purpose is to allow
     * the parent to be pressed without being pressed itself.  This way the line
     * of a tab can be pressed, but the close button itself is not.
     */
    private static class CloseHolder extends ImageView {
        public CloseHolder(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void setPressed(boolean pressed) {
            // If the parent is pressed, do not set to pressed.
            if (pressed && ((View) getParent()).isPressed()) {
                return;
            }
            super.setPressed(pressed);
        }
    }

    private class TabsListAdapter extends BaseAdapter {
        public int getCount() {
            int count = mControl.getTabCount();
            if (count < TabControl.MAX_TABS) {
                count++;
            }
            return count;
        }
        public Object getItem(int position) {
            return null;
        }
        public long getItemId(int position) {
            return position;
        }
        public int getViewTypeCount() {
            return 2;
        }
        public int getItemViewType(int position) {
            if (mControl.getTabCount() < TabControl.MAX_TABS) {
                position--;
            }
            // Do not recycle the "add new tab" item.
            return position == -1 ? IGNORE_ITEM_VIEW_TYPE : 1;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            final int tabCount = mControl.getTabCount();
            if (tabCount < TabControl.MAX_TABS) {
                position--;
            }

            if (convertView == null) {
                convertView = mFactory.inflate(position == -1 ?
                        R.layout.tab_view_add_tab : R.layout.tab_view, null);
            }

            if (position != -1) {
                TextView title =
                        (TextView) convertView.findViewById(R.id.title);
                TextView url = (TextView) convertView.findViewById(R.id.url);
                ImageView favicon =
                        (ImageView) convertView.findViewById(R.id.favicon);
                View close = convertView.findViewById(R.id.close);
                TabControl.Tab tab = mControl.getTab(position);
                mControl.populatePickerData(tab);
                title.setText(tab.getTitle());
                url.setText(tab.getUrl());
                Bitmap icon = tab.getFavicon();
                if (icon != null) {
                    favicon.setImageBitmap(icon);
                } else {
                    favicon.setImageResource(R.drawable.app_web_browser_sm);
                }
                final int closePosition = position;
                close.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            mBrowserActivity.closeTab(
                                    mControl.getTab(closePosition));
                            if (tabCount == 1) {
                                mBrowserActivity.openTabToHomePage();
                                mBrowserActivity.removeActiveTabPage(false);
                            } else {
                                mListView.setAdapter(mAdapter);
                            }
                        }
                });
            }
            return convertView;
        }
    }
}
