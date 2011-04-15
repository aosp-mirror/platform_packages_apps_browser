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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Parcel;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class CrashRecoveryHandler {

    private static final String LOGTAG = "BrowserCrashRecovery";
    private static final String STATE_FILE = "browser_state.parcel";
    private static final int BUFFER_SIZE = 4096;

    private Controller mController;

    public CrashRecoveryHandler(Controller controller) {
        mController = controller;
    }

    public void backupState() {
        final Bundle state = new Bundle();
        mController.onSaveInstanceState(state);
        final Context context = mController.getActivity();
        new Thread() {
            @Override
            public void run() {
                Parcel p = Parcel.obtain();
                try {
                    state.writeToParcel(p, 0);
                    FileOutputStream fout = context.openFileOutput(STATE_FILE,
                            Context.MODE_PRIVATE);
                    fout.write(p.marshall());
                    fout.close();
                } catch (Exception e) {
                    Log.i(LOGTAG, "Failed to save persistent state", e);
                } finally {
                    p.recycle();
                }
            }
        }.start();
    }

    public void clearState() {
        Context context = mController.getActivity();
        context.deleteFile(STATE_FILE);
    }

    public void promptToRecover(final Bundle state, final Intent intent) {
        new AlertDialog.Builder(mController.getActivity())
                .setTitle(R.string.recover_title)
                .setMessage(R.string.recover_prompt)
                .setPositiveButton(R.string.recover_yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mController.doStart(state, intent);
                    }
                })
                .setNegativeButton(R.string.recover_no, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearState();
                        mController.doStart(null, intent);
                    }
                })
                .show();
    }

    public void startRecovery(Intent intent) {
        Parcel parcel = Parcel.obtain();
        try {
            Context context = mController.getActivity();
            FileInputStream fin = context.openFileInput(STATE_FILE);
            ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fin.read(buffer)) > 0) {
                dataStream.write(buffer, 0, read);
            }
            byte[] data = dataStream.toByteArray();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            Bundle state = parcel.readBundle();
            promptToRecover(state, intent);
        } catch (FileNotFoundException e) {
            // No state to recover
            mController.doStart(null, intent);
        } catch (Exception e) {
            Log.w(LOGTAG, "Failed to recover state!", e);
            mController.doStart(null, intent);
        } finally {
            parcel.recycle();
        }
    }
}
