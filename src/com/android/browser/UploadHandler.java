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
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;

import java.io.File;
import java.util.Vector;

/**
 * Handle the file upload callbacks from WebView here
 */
public class UploadHandler {

    /*
     * The Object used to inform the WebView of the file to upload.
     */
    private ValueCallback<Uri> mUploadMessage;
    private String mCameraFilePath;

    private Controller mController;

    public UploadHandler(Controller controller) {
        mController = controller;
    }

    String getFilePath() {
        return mCameraFilePath;
    }

    void onResult(int resultCode, Intent intent) {
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

        mUploadMessage.onReceiveValue(result);
    }

    void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {

        final String imageMimeType = "image/*";
        final String videoMimeType = "video/*";
        final String audioMimeType = "audio/*";
        final String mediaSourceKey = "capture";
        final String mediaSourceValueCamera = "camera";
        final String mediaSourceValueFileSystem = "filesystem";
        final String mediaSourceValueCamcorder = "camcorder";
        final String mediaSourceValueMicrophone = "microphone";

        // media source can be 'filesystem' or 'camera' or 'camcorder' or 'microphone'.
        String mediaSource = "";

        // We add the camera intent if there was no accept type (or '*/*' or 'image/*').
        boolean addCameraIntent = true;
        // We add the camcorder intent if there was no accept type (or '*/*' or 'video/*').
        boolean addCamcorderIntent = true;

        if (mUploadMessage != null) {
            // Already a file picker operation in progress.
            return;
        }

        mUploadMessage = uploadMsg;

        // Parse the accept type.
        String params[] = acceptType.split(";");
        String mimeType = params[0];

        for (String p : params) {
            String[] keyValue = p.split("=");
            if (keyValue.length == 2) {
                // Process key=value parameters.
                if (mediaSourceKey.equals(keyValue[0])) {
                    mediaSource = keyValue[1];
                }
            }
        }

        // This intent will display the standard OPENABLE file picker.
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);

        // Create an intent to add to the standard file picker that will
        // capture an image from the camera. We'll combine this intent with
        // the standard OPENABLE picker unless the web developer specifically
        // requested the camera or gallery be opened by passing a parameter
        // in the accept type.
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File externalDataDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        File cameraDataDir = new File(externalDataDir.getAbsolutePath() +
                File.separator + "browser-photos");
        cameraDataDir.mkdirs();
        mCameraFilePath = cameraDataDir.getAbsolutePath() + File.separator +
                System.currentTimeMillis() + ".jpg";
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mCameraFilePath)));

        Intent camcorderIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        Intent soundRecIntent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);

        if (mimeType.equals(imageMimeType)) {
            i.setType(imageMimeType);
            addCamcorderIntent = false;
            if (mediaSource.equals(mediaSourceValueCamera)) {
                // Specified 'image/*' and requested the camera, so go ahead and launch the camera
                // directly.
                startActivity(cameraIntent);
                return;
            } else if (mediaSource.equals(mediaSourceValueFileSystem)) {
                // Specified filesytem as the source, so don't want to consider the camera.
                addCameraIntent = false;
            }
        } else if (mimeType.equals(videoMimeType)) {
            i.setType(videoMimeType);
            addCameraIntent = false;
            // The camcorder saves it's own file and returns it to us in the intent, so
            // we don't need to generate one here.
            mCameraFilePath = null;

            if (mediaSource.equals(mediaSourceValueCamcorder)) {
                // Specified 'video/*' and requested the camcorder, so go ahead and launch the
                // camcorder directly.
                startActivity(camcorderIntent);
                return;
            } else if (mediaSource.equals(mediaSourceValueFileSystem)) {
                // Specified filesystem as the source, so don't want to consider the camcorder.
                addCamcorderIntent = false;
            }
        } else if (mimeType.equals(audioMimeType)) {
            i.setType(audioMimeType);
            addCameraIntent = false;
            addCamcorderIntent = false;
            if (mediaSource.equals(mediaSourceValueMicrophone)) {
                // Specified 'audio/*' and requested microphone, so go ahead and launch the sound
                // recorder.
                startActivity(soundRecIntent);
                return;
            }
            // On a default system, there is no single option to open an audio "gallery". Both the
            // sound recorder and music browser respond to the OPENABLE/audio/* intent unlike the
            // image/* and video/* OPENABLE intents where the image / video gallery are the only
            // respondants (and so the user is not prompted by default).
        } else {
            i.setType("*/*");
        }

        // Combine the chooser and the extra choices (like camera or camcorder)
        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_INTENT, i);

        Vector<Intent> extraInitialIntents = new Vector<Intent>(0);

        if (addCameraIntent) {
            extraInitialIntents.add(cameraIntent);
        }

        if (addCamcorderIntent) {
            extraInitialIntents.add(camcorderIntent);
        }

        if (extraInitialIntents.size() > 0) {
            Intent[] extraIntents = new Intent[extraInitialIntents.size()];
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                    extraInitialIntents.toArray(extraIntents));
        }

        chooser.putExtra(Intent.EXTRA_TITLE,
                mController.getActivity().getResources()
                        .getString(R.string.choose_upload));
        startActivity(chooser);
    }

    private void startActivity(Intent intent) {
        mController.getActivity().startActivityForResult(intent,
                Controller.FILE_SELECTED);
    }

}
