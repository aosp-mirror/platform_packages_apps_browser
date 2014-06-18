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
    private String mCameraFilePath;

    private boolean mHandled;
    private boolean mCaughtActivityNotFoundException;

    private Controller mController;

    public UploadHandler(Controller controller) {
        mController = controller;
    }

    String getFilePath() {
        return mCameraFilePath;
    }

    boolean handled() {
        return mHandled;
    }

    void onResult(int resultCode, Intent intent) {

        if (resultCode == Activity.RESULT_CANCELED && mCaughtActivityNotFoundException) {
            // Couldn't resolve an activity, we are going to try again so skip
            // this result.
            mCaughtActivityNotFoundException = false;
            return;
        }

        Uri result = intent == null || resultCode != Activity.RESULT_OK ? null
                : intent.getData();

        // As we ask the camera to save the result of the user taking
        // a picture, the camera application does not return anything other
        // than RESULT_OK. So we need to check whether the file we expected
        // was written to disk in the in the case that we
        // did not get an intent returned but did get a RESULT_OK. If it was,
        // we assume that this result has came back from the camera.
        if (result == null && intent == null && resultCode == Activity.RESULT_OK) {
            File cameraFile = new File(mCameraFilePath);
            if (cameraFile.exists()) {
                result = Uri.fromFile(cameraFile);
                // Broadcast to the media scanner that we have a new photo
                // so it will be added into the gallery for the user.
                mController.getActivity().sendBroadcast(
                        new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, result));
            }
        }

        Uri[] uris = null;
        if (result != null) {
            uris = new Uri[1];
            uris[0] = result;
        }
        mUploadMessage.onReceiveValue(uris);
        mHandled = true;
        mCaughtActivityNotFoundException = false;
    }

    void openFileChooser(ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {

        final String imageMimeType = "image/*";
        final String videoMimeType = "video/*";
        final String audioMimeType = "audio/*";

        final String mediaSourceValueCamera = "camera";
        final String mediaSourceValueCamcorder = "camcorder";
        final String mediaSourceValueMicrophone = "microphone";

        if (mUploadMessage != null) {
            // Already a file picker operation in progress.
            return;
        }

        mUploadMessage = callback;

        // Parse the accept type.
        String params[] = fileChooserParams.acceptTypes.split(";");
        String mimeType = params[0];
        boolean capture = fileChooserParams.capture;

        //Ensure it is not still set from a previous upload.
        mCameraFilePath = null;

        if (mimeType.equals(imageMimeType)) {
            if (capture) {
                // Specified 'image/*' and requested capture. Launch the camera.
                startActivity(createCameraIntent());
                return;
            } else {
                // Specified just 'image/*', and no capture. Show a traditional picker filtered
                // on accept type by sending an intent for both the Camera and image/* OPENABLE.
                Intent chooser = createChooserIntent(createCameraIntent());
                chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(imageMimeType));
                startActivity(chooser);
                return;
            }
        } else if (mimeType.equals(videoMimeType)) {
            if (capture) {
                // Specified 'video/*' and requested capture. Launch the camcorder.
                startActivity(createCamcorderIntent());
                return;
           } else {
                // Specified just 'video/*', and no capture. Show a traditional file picker,
                // filtered on accept type by sending an intent for both camcorder
                // and video/* OPENABLE.
                Intent chooser = createChooserIntent(createCamcorderIntent());
                chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(videoMimeType));
                startActivity(chooser);
                return;
            }
        } else if (mimeType.equals(audioMimeType)) {
            if (capture) {
                // Specified 'audio/*' and requested capture. Launch the sound recorder.
                startActivity(createSoundRecorderIntent());
                return;
            } else {
                // Specified just 'audio/*', and no capture. Show a traditional file picker,
                // filtered on accept type by sending an intent for both the sound
                // recorder and audio/* OPENABLE.
                Intent chooser = createChooserIntent(createSoundRecorderIntent());
                chooser.putExtra(Intent.EXTRA_INTENT, createOpenableIntent(audioMimeType));
                startActivity(chooser);
                return;
            }
        }

        // No special handling based on the accept type was necessary, so trigger the default
        // file upload chooser.
        startActivity(createDefaultOpenableIntent());
    }

    private void startActivity(Intent intent) {
        try {
            mController.getActivity().startActivityForResult(intent, Controller.FILE_SELECTED);
        } catch (ActivityNotFoundException e) {
            // No installed app was able to handle the intent that
            // we sent, so fallback to the default file upload control.
            try {
                mCaughtActivityNotFoundException = true;
                mController.getActivity().startActivityForResult(createDefaultOpenableIntent(),
                        Controller.FILE_SELECTED);
            } catch (ActivityNotFoundException e2) {
                // Nothing can return us a file, so file upload is effectively disabled.
                Toast.makeText(mController.getActivity(), R.string.uploads_disabled,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private Intent createDefaultOpenableIntent() {
        // Create and return a chooser with the default OPENABLE
        // actions including the camera, camcorder and sound
        // recorder where available.
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");

        Intent chooser = createChooserIntent(createCameraIntent(), createCamcorderIntent(),
                createSoundRecorderIntent());
        chooser.putExtra(Intent.EXTRA_INTENT, i);
        return chooser;
    }

    private Intent createChooserIntent(Intent... intents) {
        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents);
        chooser.putExtra(Intent.EXTRA_TITLE,
                mController.getActivity().getResources()
                        .getString(R.string.choose_upload));
        return chooser;
    }

    private Intent createOpenableIntent(String type) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        return i;
    }

    private Intent createCameraIntent() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File externalDataDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        File cameraDataDir = new File(externalDataDir.getAbsolutePath() +
                File.separator + "browser-photos");
        cameraDataDir.mkdirs();
        mCameraFilePath = cameraDataDir.getAbsolutePath() + File.separator +
                System.currentTimeMillis() + ".jpg";
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mCameraFilePath)));
        return cameraIntent;
    }

    private Intent createCamcorderIntent() {
        return new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    }

    private Intent createSoundRecorderIntent() {
        return new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
    }

}
