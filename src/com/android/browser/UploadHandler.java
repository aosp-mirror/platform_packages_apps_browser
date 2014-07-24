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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebChromeClient.UploadHelper;
import android.webkit.ValueCallback;
import android.widget.Toast;

import java.io.File;
import java.util.Vector;

/**
 * Handle the file upload. This does not support selecting multiple files yet.
 */
public class UploadHandler {

    /*
     * The Object used to inform the WebView of the file to upload.
     */
    private ValueCallback<Uri[]> mUploadMessage;
    private UploadHelper mUploadHelper;

    private boolean mHandled;
    private Controller mController;

    public UploadHandler(Controller controller) {
        mController = controller;
    }

    boolean handled() {
        return mHandled;
    }

    void onResult(int resultCode, Intent intent) {
        mUploadMessage.onReceiveValue(mUploadHelper.parseResult(resultCode, intent));
        mHandled = true;
    }

    void openFileChooser(ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {

        if (mUploadMessage != null) {
            // Already a file picker operation in progress.
            return;
        }

        mUploadMessage = callback;
        mUploadHelper = fileChooserParams.getUploadHelper();
        startActivity(mUploadHelper.buildIntent());
    }

    private void startActivity(Intent intent) {
        try {
            mController.getActivity().startActivityForResult(intent, Controller.FILE_SELECTED);
        } catch (ActivityNotFoundException e) {
            // No installed app was able to handle the intent that
            // we sent, so file upload is effectively disabled.
            Toast.makeText(mController.getActivity(), R.string.uploads_disabled,
                    Toast.LENGTH_LONG).show();
        }
    }
}
