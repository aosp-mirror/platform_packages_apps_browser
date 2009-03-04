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
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.Plugin;
import android.webkit.PluginList;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

// Manages the list of installed (and loaded) plugins.
public class BrowserPluginList extends ListActivity {
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // The list of plugins can change under us, as the plugins are
        // loaded and unloaded in a different thread. We make a copy
        // of the list here.
        List loadedPlugins = WebView.getPluginList().getList();
        ArrayList localLoadedPluginList = new ArrayList();
        synchronized (loadedPlugins) {
            localLoadedPluginList.addAll(loadedPlugins);
        }
        setListAdapter(new ArrayAdapter(this,
                                        android.R.layout.simple_list_item_1,
                                        localLoadedPluginList));
        setTitle(R.string.pref_plugin_installed);
        // Add a text view to this ListActivity. This text view
        // will be displayed when the list of plugins is empty.
        TextView textView = new TextView(this);
        textView.setId(android.R.id.empty);
        textView.setText(R.string.pref_plugin_installed_empty_list);
        addContentView(textView, new LinearLayout.LayoutParams(
                               ViewGroup.LayoutParams.FILL_PARENT,
                               ViewGroup.LayoutParams.WRAP_CONTENT));

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        WebView.getPluginList().pluginClicked(this, position);
    }
}
