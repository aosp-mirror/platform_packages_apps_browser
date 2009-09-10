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

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
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

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.isDown()) return true;
            mBrowserActivity.removeActiveTabPage(true);
            return true;
        }
        return super.dispatchKeyEvent(event);
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
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mFactory.inflate(R.layout.tab_view, null);
            }
            TextView title = (TextView) convertView.findViewById(R.id.title);
            TextView url = (TextView) convertView.findViewById(R.id.url);
            FakeWebView webView
                    = (FakeWebView) convertView.findViewById(R.id.screen_shot);
            View close = convertView.findViewById(R.id.close);

            final int tabCount = mControl.getTabCount();
            if (tabCount < TabControl.MAX_TABS) {
                position--;
            }
            if (position == -1) {
                title.setText(R.string.new_tab);
                url.setText(R.string.http);
                webView.setImageResource(R.drawable.ic_add_tab);
                close.setVisibility(View.GONE);
            } else {
                TabControl.Tab tab = mControl.getTab(position);
                mControl.populatePickerData(tab);
                title.setText(tab.getTitle());
                url.setText(tab.getUrl());
                webView.setTab(tab);
                close.setVisibility(View.VISIBLE);
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
