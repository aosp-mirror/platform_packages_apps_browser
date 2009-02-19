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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.IOException;
import java.lang.ClassCastException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Base dialog class for gears
 */
class GearsBaseDialog {

  private static final String TAG = "GearsNativeDialog";
  protected Handler mHandler;
  protected Activity mActivity;
  protected String mDialogArguments;

  private Bitmap mIcon;
  private final int MAX_ICON_SIZE = 64;
  protected int mChoosenIconSize;

  // Dialog closing types
  public static final int CANCEL = 0;
  public static final int ALWAYS_DENY = 1;
  public static final int ALLOW = 2;
  public static final int DENY = 3;
  public static final int NEW_ICON = 4;
  public static final int UPDATE_ICON = 5;
  public static final int REQUEST_ICON = 6;
  public static final int PAUSE_REQUEST_ICON = 7;
  public static final int CLEAR_REQUEST_ICON = 8;

  protected final String LOCAL_DATA_STRING = "localData";
  protected final String LOCAL_STORAGE_STRING = "localStorage";
  protected final String LOCATION_DATA_STRING = "locationData";

  protected String mGearsVersion = "UNDEFINED";
  protected boolean mDebug = false;

  public GearsBaseDialog(Activity activity, Handler handler, String arguments) {
    mActivity = activity;
    mHandler = handler;
    mDialogArguments = arguments;
  }

  Resources getResources() {
    return mActivity.getResources();
  }

  Object getSystemService(String name) {
    return mActivity.getSystemService(name);
  }

  View findViewById(int id) {
    return mActivity.findViewById(id);
  }

  private String getString(int id) {
    return mActivity.getString(id);
  }

  public void setDebug(boolean debug) {
    mDebug = debug;
  }

  public void setGearsVersion(String version) {
    mGearsVersion = version;
  }

  public String closeDialog(int closingType) {
    return null;
  }

  /*
   * Utility methods for setting up the dialogs elements
   */

  /**
   * Inflate a given layout in a view (which has to be
   * a ViewGroup, e.g. LinearLayout).
   * This is used to share the basic dialog outline among
   * the different dialog types.
   */
  void inflate(int layout, int viewID) {
    LayoutInflater inflater = (LayoutInflater) getSystemService(
        Context.LAYOUT_INFLATER_SERVICE);
    View view = findViewById(viewID);
    if (view != null) {
      try {
        ViewGroup viewGroup = (ViewGroup) view;
        inflater.inflate(layout, viewGroup);
      } catch (ClassCastException e) {
        String msg = "exception, the view (" + view + ")";
        msg += " is not a ViewGroup";
        Log.e(TAG, msg, e);
      } catch (InflateException e) {
        Log.e(TAG, "exception while inflating the layout", e);
      }
    } else {
      String msg = "problem, trying to inflate a non-existent view";
      msg += " (" + viewID + ")";
      Log.e(TAG, msg);
    }
  }

  /**
   * Button setup.
   * Set the button's text and its listener. If the text resource's id
   * is 0, makes the button invisible.
   */
  void setupButton(int buttonRscID,
                   int rscString,
                   View.OnClickListener listener,
                   boolean isLink,
                   boolean requestFocus) {
    View view = findViewById(buttonRscID);
    if (view == null) {
      return;
    }

    Button button = (Button) view;

    if (rscString == 0) {
      button.setVisibility(View.GONE);
    } else {
      CharSequence text = getString(rscString);
      button.setText(text);
      button.setOnClickListener(listener);
      if (isLink) {
        displayAsLink(button);
      }
      if (requestFocus) {
        button.requestFocus();
      }
    }
  }

  /**
   * Button setup: as the above method, except that 'isLink' and
   * 'requestFocus' default to false.
   */
  void setupButton(int buttonRsc, int rsc,
                   View.OnClickListener listener) {
    setupButton(buttonRsc, rsc, listener, false, false);
  }

  /**
   * Utility method to setup the three dialog buttons.
   */
  void setupButtons(int alwaysDenyRsc, int allowRsc, int denyRsc) {
    setupButton(R.id.button_alwaysdeny, alwaysDenyRsc,
                new Button.OnClickListener() {
                  public void onClick(View v) {
                    mHandler.sendEmptyMessage(ALWAYS_DENY);
                  }
                });

    setupButton(R.id.button_allow, allowRsc,
                new Button.OnClickListener() {
                  public void onClick(View v) {
                    mHandler.sendEmptyMessage(ALLOW);
                  }
                });

    setupButton(R.id.button_deny, denyRsc,
                new Button.OnClickListener() {
                  public void onClick(View v) {
                    mHandler.sendEmptyMessage(DENY);
                  }
                });
  }

  /**
   * Display a button as an HTML link. Remove the background, set the
   * text color to R.color.dialog_link and draw an underline
   */
  void displayAsLink(Button button) {
    if (button == null) {
      return;
    }

    CharSequence text = button.getText();
    button.setBackgroundDrawable(null);
    int color = getResources().getColor(R.color.dialog_link);
    button.setTextColor(color);
    SpannableString str = new SpannableString(text);
    str.setSpan(new UnderlineSpan(), 0, str.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    button.setText(str);
    button.setFocusable(false);
  }

  /**
   * Utility method to set elements' text indicated in
   * the dialogs' arguments.
   */
  void setLabel(JSONObject json, String name, int rsc) {
    try {
      if (json.has(name)) {
        String text = json.getString(name);
        View view = findViewById(rsc);
        if (view != null && text != null) {
          TextView textView = (TextView) view;
          textView.setText(text);
          textView.setVisibility(View.VISIBLE);
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "json exception", e);
    }
  }

  /**
   * Utility method to hide a view.
   */
  void hideView(View v, int rsc) {
    if (rsc == 0) {
      return;
    }
    View view;
    if (v == null) {
      view = findViewById(rsc);
    } else {
      view = v.findViewById(rsc);
    }
    if (view != null) {
      view.setVisibility(View.GONE);
    }
  }

  /**
   * Utility method to show a view.
   */
  void showView(View v, int rsc) {
    if (rsc == 0) {
      return;
    }
    View view;
    if (v == null) {
      view = findViewById(rsc);
    } else {
      view = v.findViewById(rsc);
    }
    if (view != null) {
      view.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Utility method to set a text.
   */
  void setText(View v, int rsc, CharSequence text) {
    if (rsc == 0) {
      return;
    }
    View view = v.findViewById(rsc);
    if (view != null) {
      TextView textView = (TextView) view;
      textView.setText(text);
      textView.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Utility method to set a text.
   */
  void setText(View v, int rsc, int txtRsc) {
    if (rsc == 0) {
      return;
    }
    View view = v.findViewById(rsc);
    if (view != null) {
      TextView textView = (TextView) view;
      if (txtRsc == 0) {
        textView.setVisibility(View.GONE);
      } else {
        CharSequence text = getString(txtRsc);
        textView.setText(text);
        textView.setVisibility(View.VISIBLE);
      }
    }
  }

  /**
   * Utility class to download an icon in the background.
   * Once done ask the UI thread to update the icon.
   */
  class IconDownload implements Runnable {
    private String mUrlString;

    IconDownload(String url) {
      mUrlString = url;
    }

    public void run() {
      if (mUrlString == null) {
        return;
      }
      try {
        URL url = new URL(mUrlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.connect();
        int length = connection.getContentLength();
        InputStream is = connection.getInputStream();
        Bitmap customIcon = BitmapFactory.decodeStream(is);
        if (customIcon != null) {
          mIcon = customIcon;
          mHandler.sendEmptyMessage(UPDATE_ICON);
        }
      } catch (ClassCastException e) {
        Log.e(TAG, "Class cast exception (" + mUrlString + ")", e);
      } catch (MalformedURLException e) {
        Log.e(TAG, "Malformed url (" + mUrlString + ") ", e);
      } catch (IOException e) {
        Log.e(TAG, "Exception downloading icon (" + mUrlString + ") ", e);
      }
    }
  }

  /**
   * Utility method to update the icon.
   * Called on the UI thread.
   */
  public void updateIcon() {
    if (mIcon == null) {
      return;
    }
    View view = findViewById(R.id.origin_icon);
    if (view != null) {
      ImageView imageView = (ImageView) view;
      imageView.setMaxHeight(MAX_ICON_SIZE);
      imageView.setMaxWidth(MAX_ICON_SIZE);
      imageView.setScaleType(ImageView.ScaleType.FIT_XY);
      imageView.setImageBitmap(mIcon);
      imageView.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Utility method to download an icon from a url and set
   * it to the GUI element R.id.origin_icon.
   * It is used both in the shortcut dialog and the
   * permission dialog.
   * The actual download is done in the background via
   * IconDownload; once the icon is downlowded the UI is updated
   * via updateIcon().
   * The icon size is included in the layout with the choosen
   * size, although not displayed, to limit text reflow once
   * the icon is received.
   */
  void downloadIcon(String url) {
    if (url == null) {
      return;
    }
    View view = findViewById(R.id.origin_icon);
    if (view != null) {
      view.setMinimumWidth(mChoosenIconSize);
      view.setMinimumHeight(mChoosenIconSize);
      view.setVisibility(View.INVISIBLE);
    }
    Thread thread = new Thread(new IconDownload(url));
    thread.start();
  }

  /**
   * Utility method that get the dialogMessage
   * and icon and ask the setupDialog(message,icon)
   * method to set the values.
   */
  public void setupDialog() {
    TextView dialogMessage = null;
    ImageView icon = null;

    View view = findViewById(R.id.dialog_message);
    if (view != null) {
      dialogMessage = (TextView) view;
    }

    View iconView = findViewById(R.id.icon);
    if (iconView != null) {
      icon = (ImageView) iconView;
    }

    if ((dialogMessage != null) && (icon != null)) {
      setupDialog(dialogMessage, icon);
      dialogMessage.setVisibility(View.VISIBLE);
    }
  }

  /*
   * Set the message and icon of the dialog
   */
  public void setupDialog(TextView message, ImageView icon) {
    message.setText(R.string.unrecognized_dialog_message);
    icon.setImageResource(R.drawable.ic_dialog_menu_generic);
    message.setVisibility(View.VISIBLE);
  }

  /**
   * Setup the dialog
   * By default, just display a simple message.
   */
  public void setup() {
    setupButtons(0, 0, R.string.default_button);
    setupDialog();
  }

  /**
   * Method called when the back button is pressed,
   * allowing the dialog to intercept the default behaviour.
   */
  public boolean handleBackButton() {
    return false;
  }

  /**
   * Returns the resource string of the notification displayed
   * after the dialog. By default, does not return one.
   */
  public int notification() {
    return 0;
  }

  /**
   * If a secondary dialog (e.g. a confirmation dialog) is created,
   * GearsNativeDialog will call this method.
   */
  public Dialog onCreateDialog(int id) {
    // This should be redefined by subclasses as needed.
    return null;
  }

}
