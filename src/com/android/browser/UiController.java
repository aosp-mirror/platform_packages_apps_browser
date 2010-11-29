/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Intent;
import android.webkit.WebView;

import java.util.List;


/**
 * UI aspect of the controller
 */
public interface UiController extends BookmarksHistoryCallbacks {

    UI getUi();

    WebView getCurrentWebView();

    WebView getCurrentTopWebView();

    TabControl getTabControl();

    List<Tab> getTabs();

    Tab openTabToHomePage();

    Tab openIncognitoTab();

    boolean switchToTab(int tabIndex);

    void closeCurrentTab();

    void closeTab(Tab tab);

    void stopLoading();

    void bookmarkCurrentPage(long folderId);

    void bookmarksOrHistoryPicker(boolean openHistory);

    void showVoiceSearchResults(String title);

    void editUrl();

    void removeActiveTabsPage(boolean attach);

    void handleNewIntent(Intent intent);

    boolean shouldShowErrorConsole();

    void removeComboView();

    void hideCustomView();

    void attachSubWindow(Tab tab);

    void removeSubWindow(Tab tab);

    boolean isInCustomActionMode();

    void endActionMode();

    void shareCurrentPage();

}
