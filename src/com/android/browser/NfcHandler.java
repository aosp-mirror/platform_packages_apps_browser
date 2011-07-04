/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;

/** This class implements sharing the URL of the currently
  * shown browser page over NFC. Sharing is only active
  * when the activity is in the foreground and resumed.
  * Incognito tabs will not be shared over NFC.
  */
public class NfcHandler implements NfcAdapter.NdefPushCallback {
    private NfcAdapter mNfcAdapter;
    private Activity mActivity;
    private Controller mController;

    /** We need an async task to check whether the tab is private
      * on the UI thread.
      */
    private class CreateMessageTask extends AsyncTask<Void, Void, NdefMessage> {
        private boolean mIsPrivate = false;
        private Tab mCurrentTab;

        @Override
        protected void onPreExecute() {
            mCurrentTab = mController.getCurrentTab();
            if ((mCurrentTab != null) && (mCurrentTab.getWebView() != null)) {
                mIsPrivate = mCurrentTab.getWebView().isPrivateBrowsingEnabled();
            }
        }

        @Override
        protected NdefMessage doInBackground(Void... params) {
            if ((mCurrentTab == null) || mIsPrivate) {
                return null;
            }
            String currentUrl = mCurrentTab.getUrl();
            if (currentUrl != null) {
                NdefRecord record = NdefRecord.createUri(currentUrl);
                NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
                return msg;
            } else {
                return null;
            }
        }
    }

    public NfcHandler(Activity browser, Controller controller) {
        mActivity = browser;
        mController = controller;
        mNfcAdapter = NfcAdapter.getDefaultAdapter(mActivity);
    }

    void onPause() {
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundNdefPush(mActivity);
        }
    }

    void onResume() {
        if (mNfcAdapter != null) {
            mNfcAdapter.enableForegroundNdefPush(mActivity, this);
        }
    }

    @Override
    public NdefMessage createMessage() {
        CreateMessageTask task = new CreateMessageTask();
        task.execute();
        try {
            return task.get();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onMessagePushed() {
    }
}
