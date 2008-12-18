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
  private static ImagesLoad mImagesLoader;
  private FilePickerAdapter mAdapter;

  public GearsFilePickerDialog(Activity activity,
                               Handler handler,
                               String arguments) {
    super (activity, handler, arguments);
    mAdapter = new FilePickerAdapter(activity);
  }

  public void setup() {
    inflate(R.layout.gears_dialog_filepicker, R.id.panel_content);
    setupButtons(0,
                 R.string.filepicker_button_allow,
                 R.string.filepicker_button_deny);
    setupDialog();
    GridView view = (GridView) findViewById(R.id.files_list);
    view.setAdapter(mAdapter);
    view.setOnTouchListener(this);

    mImagesLoader = new ImagesLoad(mAdapter);
    mImagesLoader.setAdapterView(view);
    Thread thread = new Thread(mImagesLoader);
    thread.start();
  }

  public void setupDialog(TextView message, ImageView icon) {
    message.setText(R.string.filepicker_message);
    message.setTextSize(24);
    icon.setImageResource(R.drawable.gears_icon_48x48);
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
        File thumbnail = new File(thumbnailPath);
        if (thumbnail.exists()) {
          finalImage = BitmapFactory.decodeFile(thumbnailPath);
          if (finalImage != null) {
            return finalImage;
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
        if (saveImage(thumbnailPath, finalImage)) {
          if (mDebug) {
            Log.v(TAG, "Saved thumbnail for file " + path);
          }
        } else {
          Log.e(TAG, "Could NOT Save thumbnail for file " + path);
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

      String sdCardPath = Environment.getExternalStorageDirectory().getPath();
      mRootElement = new FilePickerElement(sdCardPath, "SD Card", this);
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
            elem.toggleSelection();
          }
          notifyDataSetChanged();
        }
      };
      imageView.setOnClickListener(listener);
      cell.setLayoutParams(new GridView.LayoutParams(96, 96));

      imageView.setTag(position);

      if (elem.isSelected()) {
        cell.setBackgroundColor(Color.LTGRAY);
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
