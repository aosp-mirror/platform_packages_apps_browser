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

import android.app.Service;
import android.util.Log;
import android.content.Intent;
import android.os.IBinder;

import android.content.Intent;
import android.content.ContentValues;
import android.content.ActivityNotFoundException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.lang.InterruptedException;

public class GearsDialogService extends Service {
  private static final String TAG = "GearsDialogService";
  private final String DIALOG_PACKAGE = "com.android.browser";
  private final String DIALOG_CLASS = DIALOG_PACKAGE + ".GearsDialog";

  public static Lock lock = new ReentrantLock();
  public static Condition dialogFinished = lock.newCondition();
  public static String results = null;

  @Override
  public IBinder onBind(Intent intent) {
    if (IGearsDialogService.class.getName().equals(intent.getAction())) {
      return mBinder;
    }
    return null;
  }

  private final IGearsDialogService.Stub mBinder = new IGearsDialogService.Stub() {
    public String showDialog(String htmlContent, String dialogArguments,
          boolean inSettings) {
      return GearsDialogService.this.showDialog(htmlContent, dialogArguments,
                 inSettings);
    }
  };

  public static void closeDialog(String res) {
    results = res;
  }

  public static void signalFinishedDialog() {
    lock.lock();
    dialogFinished.signal();
    lock.unlock();
  }

  /**
   * Show a 'true' modal dialog displaying html content, necessary
   * for Gears. The method starts the GearsDialog activity, passing
   * the necessary parameters to it, and then wait until the activity
   * is finished. When the dialog closes, it sets the variable results.
   */
  public String showDialog(String htmlContent, String dialogArguments,
      boolean inSettings) {
    try {
      Intent intent = new Intent();
      intent.setClassName(DIALOG_PACKAGE, DIALOG_CLASS);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.putExtra("htmlContent", htmlContent);
      intent.putExtra("dialogArguments", dialogArguments);
      intent.putExtra("inSettings", inSettings);
      lock.lock();
      startActivity(intent);
      dialogFinished.await();
    } catch (InterruptedException e) {
      Log.e(TAG, "exception e: " + e);
    } catch (ActivityNotFoundException e) {
      Log.e(TAG, "exception e: " + e);
    } finally {
      lock.unlock();
    }
    return results;
  }
}
