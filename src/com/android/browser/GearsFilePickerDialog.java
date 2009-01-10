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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Gears FilePicker dialog
 */
class GearsFilePickerDialog extends GearsBaseDialog
  implements View.OnTouchListener {

  private static final String TAG = "Gears FilePicker";
  private static Bitmap mDirectoryIcon;
  private static Bitmap mDefaultIcon;
  private static Bitmap mImageIcon;
  private static Bitmap mBackIcon;

  private static String MULTIPLE_FILES = "MULTIPLE_FILES";
  private static String SINGLE_FILE = "SINGLE_FILE";

  private static ImagesLoad mImagesLoader;
  private FilePickerAdapter mAdapter;
  private String mSelectionMode;
  private boolean mMultipleSelection;
  private String mCurrentPath;

  // Disable saving thumbnails until this is refactored to fit into
  // existing schemes.
  private static final boolean enableSavedThumbnails = false;

  public GearsFilePickerDialog(Activity activity,
                               Handler handler,
                               String arguments) {
    super (activity, handler, arguments);
    mAdapter = new FilePickerAdapter(activity);
    parseArguments();
  }

  public void parseArguments() {
    mSelectionMode = MULTIPLE_FILES;
    try {
      JSONObject json = new JSONObject(mDialogArguments);

      if (json.has("mode")) {
        mSelectionMode = json.getString("mode");
      }
    } catch (JSONException e) {
      Log.e(TAG, "exc: " + e);
    }
    if (mSelectionMode.equalsIgnoreCase(SINGLE_FILE)) {
      mMultipleSelection = false;
    } else {
      mMultipleSelection = true;
    }
  }

  public void setup() {
    inflate(R.layout.gears_dialog_filepicker, R.id.panel_content);
    setupButtons(0,
                 R.string.filepicker_button_allow,
                 R.string.filepicker_button_deny);
    setupDialog();

    TextView textViewPath = (TextView) findViewById(R.id.path_name);
    if (textViewPath != null) {
      textViewPath.setText(R.string.filepicker_path);
    }

    GridView view = (GridView) findViewById(R.id.files_list);
    view.setAdapter(mAdapter);
    view.setOnTouchListener(this);

    setSelectionText();

    mImagesLoader = new ImagesLoad(mAdapter);
    mImagesLoader.setAdapterView(view);
    Thread thread = new Thread(mImagesLoader);
    thread.start();
  }

  public void setSelectionText() {
    Vector elements = mAdapter.selectedElements();
    if (elements == null)
      return;
    TextView info = (TextView) findViewById(R.id.selection);
    int nbElements = elements.size();
    if (nbElements == 0) {
      info.setText(R.string.filepicker_no_files_selected);
    } else if (nbElements == 1) {
      info.setText(R.string.filepicker_one_file_selected);
    } else {
      info.setText(nbElements + " " +
                   mActivity.getString(
                       R.string.filepicker_some_files_selected));
    }
  }

  public void setCurrentPath(String path) {
    if (path != null) {
      mCurrentPath = path;
      TextView textViewPath = (TextView) findViewById(R.id.current_path);
      if (textViewPath != null) {
        textViewPath.setText(path);
      }
    }
  }

  public void setupDialog(TextView message, ImageView icon) {
    message.setText(R.string.filepicker_message);
    message.setTextSize(24);
    icon.setImageResource(R.drawable.gears_icon_32x32);
  }

  public boolean onTouch(View v, MotionEvent event) {
    mImagesLoader.pauseIconRequest();
    return false;
  }

  /**
   * Utility class to load and generate thumbnails
   * for image files
   */
  class ImagesLoad implements Runnable {
    private Map mImagesMap;
    private Vector mImagesPath;
    private BaseAdapter mAdapter;
    private AdapterView mAdapterView;
    private Vector<FilePickerElement> mElements;
    private Handler mLoaderHandler;

    ImagesLoad(BaseAdapter adapter) {
      mAdapter = adapter;
    }

    public void signalChanges() {
      Message message = mHandler.obtainMessage(GearsBaseDialog.NEW_ICON,
                                               mAdapter);
      mHandler.sendMessage(message);
    }

    /**
     * TODO: use the same thumbnails as the photo app
     * (bug: http://b/issue?id=1497927)
     */
    public String getThumbnailPath(String path) {
      File f = new File(path);
      String myPath = f.getParent() + "/.thumbnails";
      File d = new File(myPath);
      if (!d.exists()) {
        d.mkdirs();
      }
      return myPath + "/" + f.getName();
    }

    public boolean saveImage(String path, Bitmap image) {
      boolean ret = false;
      try {
        FileOutputStream outStream = new FileOutputStream(path);
        ret = image.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
      } catch (IOException e) {
        Log.e(TAG, "IOException ", e);
      }
      return ret;
    }

    public Bitmap generateImage(FilePickerElement elem) {
      String path = elem.getPath();
      Bitmap finalImage = null;
      try {
        String thumbnailPath = getThumbnailPath(path);
        if (enableSavedThumbnails) {
          File thumbnail = new File(thumbnailPath);
          if (thumbnail.exists()) {
            finalImage = BitmapFactory.decodeFile(thumbnailPath);
            if (finalImage != null) {
              return finalImage;
            }
          }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        int width = options.outWidth;
        int height = options.outHeight;
        int size = 128;
        int sampleSize = 1;
        if (width > size || height > size) {
          sampleSize = 2;
          while ((width / sampleSize > size)
                 || (height / sampleSize > size)) {
            sampleSize += 2;
          }
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        Bitmap originalImage = BitmapFactory.decodeFile(path, options);
        if (originalImage == null) {
          return null;
        }
        finalImage = Bitmap.createScaledBitmap(originalImage, size, size, true);
        if (enableSavedThumbnails) {
          if (saveImage(thumbnailPath, finalImage)) {
            if (mDebug) {
              Log.v(TAG, "Saved thumbnail for file " + path);
            }
          } else {
            Log.e(TAG, "Could NOT Save thumbnail for file " + path);
          }
        }
        originalImage.recycle();
      } catch (java.lang.OutOfMemoryError e) {
        Log.e(TAG, "Intercepted OOM ", e);
      }
      return finalImage;
    }

    public void pauseIconRequest() {
      Message message = Message.obtain(mLoaderHandler,
                                       GearsBaseDialog.PAUSE_REQUEST_ICON);
      mLoaderHandler.sendMessageAtFrontOfQueue(message);
    }
    public void postIconRequest(FilePickerElement item, int position) {
      if (item == null) {
        return;
      }
      Message message = mLoaderHandler.obtainMessage(
          GearsBaseDialog.REQUEST_ICON, position, 0, item);
      mLoaderHandler.sendMessage(message);
    }

    public void generateIcon(FilePickerElement elem) {
      if (elem.isImage()) {
        if (elem.getThumbnail() == null) {
          Bitmap image = generateImage(elem);
          if (image != null) {
            elem.setThumbnail(image);
          }
        }
      }
    }

    public void setAdapterView(AdapterView view) {
      mAdapterView = view;
    }

    public void run() {
      Looper.prepare();
      mLoaderHandler = new Handler() {
        public void handleMessage(Message msg) {
          int visibleElements = 10;
          if (msg.what == GearsBaseDialog.PAUSE_REQUEST_ICON) {
            try {
              // We are busy (likely) scrolling the view,
              // so we just pause the loading.
              Thread.sleep(1000);
              mLoaderHandler.removeMessages(
                  GearsBaseDialog.PAUSE_REQUEST_ICON);
            } catch (InterruptedException e) {
              Log.e(TAG, "InterruptedException ", e);
            }
          } else if (msg.what == GearsBaseDialog.REQUEST_ICON) {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              Log.e(TAG, "InterruptedException ", e);
            }
            FilePickerElement elem = (FilePickerElement) msg.obj;
            int firstVisiblePosition = mAdapterView.getFirstVisiblePosition();
            // If the elements are not visible, we slow down the update
            // TODO: replace this by a low-priority thread
            if ((msg.arg1 < firstVisiblePosition - visibleElements)
                && msg.arg1 > firstVisiblePosition + visibleElements) {
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
              }
            }
            generateIcon(elem);
            signalChanges();
          }
        }
      };
      Looper.loop();
    }
  }

  /**
   * Utility class representing an element displayed in the
   * file picker, associated with an icon and/or thumbnail
   */
  class FilePickerElement {
    private File mPath;
    private String mName;
    private Bitmap mIcon;
    private boolean mIsSelected;
    private Vector mChildren;
    private FilePickerElement mParent;
    private boolean mIsParent;
    private BaseAdapter mAdapter;
    private String mExtension;
    private Bitmap mThumbnail;
    private boolean mIsImage;

    public FilePickerElement(String name, BaseAdapter adapter) {
      this(name, adapter, null);
    }

    public FilePickerElement(String path, String name, BaseAdapter adapter) {
      this(path, name, adapter, null);
    }

    public FilePickerElement(String name,
                             BaseAdapter adapter,
                             FilePickerElement parent) {
      mName = name;
      mAdapter = adapter;
      mParent = parent;
      mIsSelected = false;
      mChildren = null;
    }

    public FilePickerElement(String path,
                             String name,
                             BaseAdapter adapter,
                             FilePickerElement parent) {
      mPath = new File(path);
      mName = name;
      mIsSelected = false;
      mChildren = null;
      mParent = parent;
      mAdapter = adapter;
      mExtension = null;

      setIcons();
    }

    public void setIcons() {
      if (mPath.isDirectory()) {
        if (mDirectoryIcon == null) {
          mDirectoryIcon = BitmapFactory.decodeResource(
              getResources(), R.drawable.gears_folder);
        }
        mIcon = mDirectoryIcon;

      } else {
        if (isImage()) {
          if (mImageIcon == null) {
            mImageIcon = BitmapFactory.decodeResource(
                getResources(), R.drawable.gears_file_image);
          }
          mIcon = mImageIcon;
        } else if (isAudio()) {
          mIcon = BitmapFactory.decodeResource(
              getResources(), R.drawable.gears_file_audio);
        } else if (isVideo()) {
          mIcon = BitmapFactory.decodeResource(
              getResources(), R.drawable.gears_file_video);
        } else {
          if (mDefaultIcon == null) {
            mDefaultIcon = BitmapFactory.decodeResource(
                getResources(), R.drawable.gears_file_unknown);
          }
          mIcon = mDefaultIcon;
        }
      }
      if (mBackIcon == null) {
        mBackIcon = BitmapFactory.decodeResource(getResources(),
                                                 R.drawable.gears_back);
      }
    }

    public boolean isImage() {
      if (mIsImage) return mIsImage;
      String extension = getExtension();
      if (extension != null) {
        if (extension.equalsIgnoreCase("jpg") ||
            extension.equalsIgnoreCase("jpeg") ||
            extension.equalsIgnoreCase("png") ||
            extension.equalsIgnoreCase("gif")) {
          mIsImage = true;
          return true;
        }
      }
      return false;
    }

    public boolean isAudio() {
      String extension = getExtension();
      if (extension != null) {
        if (extension.equalsIgnoreCase("mp3") ||
            extension.equalsIgnoreCase("wav") ||
            extension.equalsIgnoreCase("aac")) {
          return true;
        }
      }
      return false;
    }

    public boolean isVideo() {
      String extension = getExtension();
      if (extension != null) {
        if (extension.equalsIgnoreCase("mpg") ||
            extension.equalsIgnoreCase("mpeg") ||
            extension.equalsIgnoreCase("mpe") ||
            extension.equalsIgnoreCase("divx") ||
            extension.equalsIgnoreCase("3gpp") ||
            extension.equalsIgnoreCase("avi")) {
          return true;
        }
      }
      return false;
    }

    public void setParent(boolean isParent) {
      mIsParent = isParent;
    }

    public boolean isDirectory() {
      return mPath.isDirectory();
    }

    public String getExtension() {
      if (isDirectory()) {
        return null;
      }
      if (mExtension == null) {
        String path = getPath();
        int index = path.lastIndexOf(".");
        if ((index != -1) && (index != path.length() - 1)){
          // if we find a dot that is not the last character
          mExtension = path.substring(index+1);
          return mExtension;
        }
      }
      return mExtension;
    }

    public void refresh() {
      mChildren = null;
      Vector children = getChildren();
      for (int i = 0; i < children.size(); i++) {
        FilePickerElement elem = (FilePickerElement) children.get(i);
        mImagesLoader.postIconRequest(elem, i);
      }
    }

    public Vector getChildren() {
      if (isDirectory()) {
        if (mChildren == null) {
          mChildren = new Vector();
          File[] files = mPath.listFiles();
          if (mParent != null) {
            mChildren.add(mParent);
            mParent.setParent(true);
          }
          for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            String fpath = files[i].getPath();
            if (!name.startsWith(".")) { // hide dotfiles
              FilePickerElement elem = new FilePickerElement(fpath, name,
                                                             mAdapter, this);
              elem.setParent(false);
              mChildren.add(elem);
            }
          }
        }
      }
      return mChildren;
    }

    public FilePickerElement getChild(int position) {
      Vector children = getChildren();
      if (children != null) {
        return (FilePickerElement) children.get(position);
      }
      return null;
    }

    public Bitmap getIcon(int position) {
      if (mIsParent) {
        return mBackIcon;
      }
      if (isImage()) {
        if (mThumbnail != null) {
          return mThumbnail;
        } else {
          mImagesLoader.postIconRequest(this, position);
        }
      }
      return mIcon;
    }

    public Bitmap getThumbnail() {
      return mThumbnail;
    }

    public void setThumbnail(Bitmap icon) {
      mThumbnail = icon;
    }

    public String getName() {
      return mName;
    }

    public String getPath() {
      return mPath.getPath();
    }

    public void toggleSelection() {
      mIsSelected = !mIsSelected;
    }

    public boolean isSelected() {
      return mIsSelected;
    }

  }

  /**
   * Adapter for the GridView
   */
  class FilePickerAdapter extends BaseAdapter {
    private Context mContext;
    private Map mImagesMap;
    private Map mImagesSelected;

    private Vector mImages;
    private Vector<FilePickerElement> mFiles;

    private FilePickerElement mRootElement;
    private FilePickerElement mCurrentElement;

    public FilePickerAdapter(Context context) {
      mContext = context;
      mImages = new Vector();
      mFiles = new Vector();

      mImagesMap = Collections.synchronizedMap(new HashMap());
      mImagesSelected = new HashMap();

      Uri requests[] = { MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                         MediaStore.Images.Media.EXTERNAL_CONTENT_URI };

      String startingPath = Environment.getExternalStorageDirectory().getPath();
      mRootElement = new FilePickerElement(startingPath, "SD Card", this);
      mCurrentElement = mRootElement;
    }

    public void addImage(String path) {
      mImages.add(path);
      Bitmap image = BitmapFactory.decodeResource(
          getResources(), R.drawable.gears_file_unknown);
      mImagesMap.put(path, image);
      mImagesSelected.put(path, Boolean.FALSE);
    }

    public int getCount() {
      Vector elems = mCurrentElement.getChildren();
      setCurrentPath(mCurrentElement.getPath());
      return elems.size();
    }

    public Object getItem(int position) {
      return position;
    }

    public long getItemId(int position) {
      return position;
    }

    public Vector selectedElements() {
      if (mCurrentElement == null) {
        return null;
      }
      Vector children = mCurrentElement.getChildren();
      Vector ret = new Vector();
      for (int i = 0; i < children.size(); i++) {
        FilePickerElement elem = (FilePickerElement) children.get(i);
        if (elem.isSelected()) {
          ret.add(elem);
        }
      }
      return ret;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      View cell = convertView;
      if (cell == null) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        cell = inflater.inflate(R.layout.gears_dialog_filepicker_cell, null);
      }
      ImageView imageView = (ImageView) cell.findViewById(R.id.icon);
      TextView textView = (TextView) cell.findViewById(R.id.name);
      FilePickerElement elem = mCurrentElement.getChild(position);
      if (elem == null) {
        String message = "Could not get elem " + position;
        message += " for " + mCurrentElement.getPath();
        Log.e(TAG, message);
        return null;
      }
      String path = elem.getPath();
      textView.setText(elem.getName());

      View.OnClickListener listener = new View.OnClickListener() {
        public void onClick(View view) {
          int pos = (Integer) view.getTag();
          FilePickerElement elem = mCurrentElement.getChild(pos);
          if (elem.isDirectory()) {
            mCurrentElement = elem;
            mCurrentElement.refresh();
          } else {
            if (mMultipleSelection) {
              elem.toggleSelection();
            } else {
              Vector elems = selectedElements();
              if (elems != null) {
                if (elems.size() == 0) {
                  elem.toggleSelection();
                } else if ((elems.size() == 1)
                           && elem.isSelected()) {
                  elem.toggleSelection();
                }
              }
            }
          }
          setSelectionText();
          notifyDataSetChanged();
        }
      };
      cell.setLayoutParams(new GridView.LayoutParams(96, 96));
      cell.setOnClickListener(listener);
      cell.setOnTouchListener(new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int color = getResources().getColor(R.color.icon_selection);
            v.setBackgroundColor(color);
          } else {
            v.setBackgroundColor(Color.WHITE);
          }
          return false;
        }
      });

      cell.setTag(position);

      if (elem.isSelected()) {
        int color = getResources().getColor(R.color.icon_selection);
        cell.setBackgroundColor(color);
      } else {
        cell.setBackgroundColor(Color.WHITE);
      }
      Bitmap bmp = elem.getIcon(position);
      if (bmp != null) {
        imageView.setImageBitmap(bmp);
      }

      return cell;
    }
  }

  private String selectedFiles() {
    Vector selection = mAdapter.selectedElements();
    JSONArray jsonSelection = new JSONArray();
    if (selection != null) {
      for (int i = 0; i < selection.size(); i++) {
        FilePickerElement elem = (FilePickerElement) selection.get(i);
        jsonSelection.put(elem.getPath());
      }
    }
    return jsonSelection.toString();
  }

  public String closeDialog(int closingType) {
    return selectedFiles();
  }
}
