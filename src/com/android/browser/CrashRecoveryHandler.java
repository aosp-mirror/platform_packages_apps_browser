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
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class CrashRecoveryHandler {

    private static final String LOGTAG = "BrowserCrashRecovery";
    private static final String STATE_FILE = "browser_state.parcel";
    private static final String RECOVERY_PREFERENCES = "browser_recovery_prefs";
    private static final String KEY_LAST_RECOVERED = "last_recovered";
    private static final int BUFFER_SIZE = 4096;
    private static final long BACKUP_DELAY = 500; // 500ms between writes
    /* This is the duration for which we will prompt to restore
     * instead of automatically restoring. The first time the browser crashes,
     * we will automatically restore. If we then crash again within XX minutes,
     * we will prompt instead of automatically restoring.
     */
    private static final long PROMPT_INTERVAL = 30 * 60 * 1000; // 30 minutes

    private static CrashRecoveryHandler sInstance;

    private Controller mController;
    private Handler mForegroundHandler;
    private Handler mBackgroundHandler;

    public static CrashRecoveryHandler initialize(Controller controller) {
        if (sInstance == null) {
            sInstance = new CrashRecoveryHandler(controller);
        } else {
            sInstance.mController = controller;
        }
        return sInstance;
    }

    public static CrashRecoveryHandler getInstance() {
        return sInstance;
    }

    private CrashRecoveryHandler(Controller controller) {
        mController = controller;
        mForegroundHandler = new Handler();
        HandlerThread thread = new HandlerThread(LOGTAG,
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mBackgroundHandler = new Handler(thread.getLooper());
    }

    public void backupState() {
        mForegroundHandler.postDelayed(mCreateState, BACKUP_DELAY);
    }

    private Runnable mCreateState = new Runnable() {

        @Override
        public void run() {
            try {
                final Bundle state = new Bundle();
                mController.onSaveInstanceState(state, false);
                Context context = mController.getActivity()
                        .getApplicationContext();
                mBackgroundHandler.post(new WriteState(context, state));
                // Remove any queued up saves
                mForegroundHandler.removeCallbacks(mCreateState);
            } catch (Throwable t) {
                Log.w(LOGTAG, "Failed to save state", t);
                return;
            }
        }

    };

    static class WriteState implements Runnable {
        private Context mContext;
        private Bundle mState;

        WriteState(Context context, Bundle state) {
            mContext = context;
            mState = state;
        }

        @Override
        public void run() {
            if (mState.isEmpty()) {
                clearState(mContext);
                return;
            }
            Parcel p = Parcel.obtain();
            try {
                mState.writeToParcel(p, 0);
                File state = new File(mContext.getCacheDir(), STATE_FILE);
                FileOutputStream fout = new FileOutputStream(state);
                fout.write(p.marshall());
                fout.close();
            } catch (Throwable e) {
                Log.i(LOGTAG, "Failed to save persistent state", e);
            } finally {
                p.recycle();
            }
        }

    }

    public static void clearState(Context context) {
        File state = new File(context.getCacheDir(), STATE_FILE);
        if (state.exists()) {
            state.delete();
        }
    }

    public void promptToRecover(final Bundle state, final Intent intent) {
        new AlertDialog.Builder(mController.getActivity())
                .setTitle(R.string.recover_title)
                .setMessage(R.string.recover_prompt)
                .setIcon(R.mipmap.ic_launcher_browser)
                .setPositiveButton(R.string.recover_yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        updateLastRecovered();
                        mController.doStart(state, intent);
                    }
                })
                .setNegativeButton(R.string.recover_no, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        clearState(mController.getActivity());
                        mController.doStart(null, intent);
                    }
                })
                .show();
    }

    private boolean shouldPrompt() {
        // STOPSHIP TODO: Remove once b/4971724 is fixed
        if (true) return false;
        Context context = mController.getActivity();
        SharedPreferences prefs = context.getSharedPreferences(
                RECOVERY_PREFERENCES, Context.MODE_PRIVATE);
        long lastRecovered = prefs.getLong(KEY_LAST_RECOVERED,
                System.currentTimeMillis());
        long timeSinceLastRecover = System.currentTimeMillis() - lastRecovered;
        if (timeSinceLastRecover > PROMPT_INTERVAL) {
            return false;
        }
        return true;
    }

    private void updateLastRecovered() {
        Context context = mController.getActivity();
        SharedPreferences prefs = context.getSharedPreferences(
                RECOVERY_PREFERENCES, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong(KEY_LAST_RECOVERED, System.currentTimeMillis())
            .commit();
    }

    public void startRecovery(Intent intent) {
        Bundle state = null;
        Parcel parcel = Parcel.obtain();
        try {
            Context context = mController.getActivity();
            File stateFile = new File(context.getCacheDir(), STATE_FILE);
            FileInputStream fin = new FileInputStream(stateFile);
            ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fin.read(buffer)) > 0) {
                dataStream.write(buffer, 0, read);
            }
            byte[] data = dataStream.toByteArray();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            state = parcel.readBundle();
            if (shouldPrompt()) {
                promptToRecover(state, intent);
                return;
            } else {
                updateLastRecovered();
            }
        } catch (FileNotFoundException e) {
            // No state to recover
            state = null;
        } catch (Exception e) {
            Log.w(LOGTAG, "Failed to recover state!", e);
            state = null;
        } finally {
            parcel.recycle();
        }
        mController.doStart(state, intent);
    }
}
