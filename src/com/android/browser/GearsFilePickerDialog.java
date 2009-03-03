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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
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
import java.io.RandomAccessFile;
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
  private static SystemThumbnails mSystemThumbnails;
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

    showView(null, R.id.selection);
    setSelectionText();

    mImagesLoader = new ImagesLoad(mAdapter);
    mImagesLoader.setAdapterView(view);
    Thread imagesLoaderThread = new Thread(mImagesLoader);
    imagesLoaderThread.setPriority(Thread.MIN_PRIORITY);
    imagesLoaderThread.start();

    mSystemThumbnails = new SystemThumbnails();
    Thread systemThumbnailsThread = new Thread(mSystemThumbnails);
    systemThumbnailsThread.setPriority(Thread.MIN_PRIORITY);
    systemThumbnailsThread.start();
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
    icon.setImageResource(R.drawable.ic_dialog_menu_generic);
  }

  public boolean onTouch(View v, MotionEvent event) {
    mImagesLoader.pauseIconRequest();
    return false;
  }

  /**
   * Utility class encapsulating thumbnails information
   * for a file (file image id and magic number)
   */
  class SystemThumbnailInfo {
    private long mID;
    private long mMagicNumber;
    SystemThumbnailInfo(long anID, long magicNumber) {
      mID = anID;
      mMagicNumber = magicNumber;
    }
    public long getID() {
      return mID;
    }
    public long getMagicNumber() {
      return mMagicNumber;
    }
  }

  /**
   * Utility class to pre-fetch the thumbnails information
   */
  class SystemThumbnails implements Runnable {
    private Map<String, SystemThumbnailInfo> mThumbnails;

    SystemThumbnails() {
      mThumbnails = Collections.synchronizedMap(new HashMap());
    }

    public void run() {
      Uri query = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
      Cursor cursor = mActivity.managedQuery(query,
          new String[] { "_id", "mini_thumb_magic", "_data" },
          null, null, null);

      if (cursor != null) {
        int count = cursor.getCount();
        for (int i = 0; i < count; i++) {
          cursor.moveToPosition(i);
          SystemThumbnailInfo info = new SystemThumbnailInfo(cursor.getLong(0),
                                                             cursor.getLong(1));
          mThumbnails.put(cursor.getString(2), info);
        }
      }
    }

    public SystemThumbnailInfo getThumb(String path) {
      SystemThumbnailInfo ret = mThumbnails.get(path);
      if (ret == null) {
        Uri query = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = mActivity.managedQuery(query,
          new String[] { "_id", "mini_thumb_magic", "_data" },
              "_data = ?", new String[] { path }, null);
        if (cursor != null && cursor.moveToFirst()) {
          long longid = cursor.getLong(0);
          long miniThumbMagic = cursor.getLong(1);
          ret = new SystemThumbnailInfo(longid, miniThumbMagic);
          mThumbnails.put(path, ret);
        }
      }
      return ret;
    }
  }

  /**
   * Utility class to load and generate thumbnails
   * for image files
   */
  class ImagesLoad implements Runnable {
    private Vector mImagesPath;
    private BaseAdapter mAdapter;
    private AdapterView mAdapterView;
    private Vector<FilePickerElement> mElements;
    private Handler mLoaderHandler;
    // We use the same value as in Camera.app's ImageManager.java
    private static final int BYTES_PER_MINI_THUMB = 10000;
    private final byte[] mMiniThumbData = new byte[BYTES_PER_MINI_THUMB];
    private final int MINI_THUMB_DATA_FILE_VERSION = 3;
    private final int THUMBNAIL_SIZE = 128;
    private Map<Uri, RandomAccessFile> mThumbFiles;

    ImagesLoad(BaseAdapter adapter) {
      mAdapter = adapter;
      mThumbFiles = Collections.synchronizedMap(new HashMap());
    }

    public void signalChanges() {
      Message message = mHandler.obtainMessage(GearsBaseDialog.NEW_ICON,
                                               mAdapter);
      mHandler.sendMessage(message);
    }

    private String getMiniThumbFileFromUri(Uri uri) {
      if (uri == null) {
        return null;
      }
      String directoryName =
          Environment.getExternalStorageDirectory().toString() +
          "/dcim/.thumbnails";
      String path = directoryName + "/.thumbdata" +
          MINI_THUMB_DATA_FILE_VERSION + "-" + uri.hashCode();
      return path;
    }

    private Bitmap getMiniThumbFor(Uri uri, long longid, long magic) {
      RandomAccessFile thumbFile = mThumbFiles.get(uri);
      try {
        if (thumbFile == null) {
          String path = getMiniThumbFileFromUri(uri);
          File f = new File(path);
          if (f.exists()) {
            thumbFile = new RandomAccessFile(f, "rw");
            mThumbFiles.put(uri, thumbFile);
          }
        }
      } catch (IOException ex) {
      }
      if (thumbFile == null) {
        return null;
      }
      byte[] data = getMiniThumbFromFile(thumbFile, longid,
                                         mMiniThumbData, magic);
      if (data != null) {
        return BitmapFactory.decodeByteArray(data, 0, data.length);
      }
      return null;
    }

    private byte [] getMiniThumbFromFile(RandomAccessFile r,
                                         long id,
                                         byte [] data,
                                         long magicCheck) {
      if (r == null)
        return null;
      long pos = id * BYTES_PER_MINI_THUMB;
      RandomAccessFile f = r;
      synchronized (f) {
        try {
          f.seek(pos);
          if (f.readByte() == 1) {
            long magic = f.readLong();
            if (magic != magicCheck) {
              return null;
            }
            int length = f.readInt();
            f.read(data, 0, length);
            return data;
          } else {
            return null;
          }
        } catch (IOException ex) {
          long fileLength;
          try {
            fileLength = f.length();
          } catch (IOException ex1) {
            fileLength = -1;
          }
          return null;
        }
      }
    }

    /*
     * Returns a thumbnail saved by the Camera application
     * We pre-cached the information (image id and magic number)
     * when starting the filepicker.
     */
    public Bitmap getSystemThumbnail(FilePickerElement elem) {
      if (elem.askedForSystemThumbnail() == false) {
        elem.setAskedForSystemThumbnail(true);
        String path = elem.getPath();
        SystemThumbnailInfo thumbInfo = mSystemThumbnails.getThumb(path);
        if (thumbInfo != null) {
          Uri query = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
          Bitmap bmp = getMiniThumbFor(query, thumbInfo.getID(),
                                       thumbInfo.getMagicNumber());
          if (bmp != null) {
            return bmp;
          }
        }
      }
      return null;
    }

    /*
     * Generate a thumbnail for a given element
     */
    public Bitmap generateImage(FilePickerElement elem) {
      String path = elem.getPath();
      Bitmap finalImage = null;
      try {

        // First we try to get the thumbnail from the system
        // (created by the Camera application)

        finalImage = getSystemThumbnail(elem);
        if (finalImage != null) {
          return finalImage;
        }

        // No thumbnail was found, so we have to create one
        //
        // First we get the image information and
        // determine the sampleSize

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        int width = options.outWidth;
        int height = options.outHeight;
        int sampleSize = 1;
        if (width > THUMBNAIL_SIZE || height > THUMBNAIL_SIZE) {
          sampleSize = 2;
          while ((width / sampleSize > 2*THUMBNAIL_SIZE)
                 || (height / sampleSize > 2*THUMBNAIL_SIZE)) {
            sampleSize += 2;
          }
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        Bitmap originalImage = BitmapFactory.decodeFile(path, options);
        if (originalImage == null) {
          return null;
        }

        // Let's rescale the image to a THUMBNAIL_SIZE

        width = originalImage.getWidth();
        height = originalImage.getHeight();

        if (width > height) {
          width = (int) (width * (THUMBNAIL_SIZE / (double) height));
          height = THUMBNAIL_SIZE;
        } else {
          height = (int) (height * (THUMBNAIL_SIZE / (double) width));
          width = THUMBNAIL_SIZE;
        }
        originalImage = Bitmap.createScaledBitmap(originalImage,
                                                  width, height, true);

        // We can now crop the image to a THUMBNAIL_SIZE rectangle

        width = originalImage.getWidth();
        height = originalImage.getHeight();
        int d = 0;
        if (width > height) {
          d = (width - height) / 2;
          finalImage = Bitmap.createBitmap(originalImage, d, 0,
                                           THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        } else {
          d = (height - width) / 2;
          finalImage = Bitmap.createBitmap(originalImage, 0, d,
                                           THUMBNAIL_SIZE, THUMBNAIL_SIZE);
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

    public void clearIconRequests() {
      Message message = Message.obtain(mLoaderHandler,
                                       GearsBaseDialog.CLEAR_REQUEST_ICON);
      mLoaderHandler.sendMessageAtFrontOfQueue(message);
    }

    public void postIconRequest(FilePickerElement item,
                                int position,
                                boolean front) {
      if (item == null) {
        return;
      }
      if (item.isImage() && (item.getThumbnail() == null))  {
        Message message = mLoaderHandler.obtainMessage(
            GearsBaseDialog.REQUEST_ICON, position, 0, item);
        if (front) {
          mLoaderHandler.sendMessageAtFrontOfQueue(message);
        } else {
          mLoaderHandler.sendMessage(message);
        }
      }
    }

    public boolean generateIcon(FilePickerElement elem) {
      if (elem.isImage()) {
        if (elem.getThumbnail() == null) {
          Bitmap image = generateImage(elem);
          if (image != null) {
            elem.setThumbnail(image);
            return true;
          }
        }
      }
      return false;
    }

    public void setAdapterView(AdapterView view) {
      mAdapterView = view;
    }

    public void run() {
      Looper.prepare();
      mLoaderHandler = new Handler() {
        public void handleMessage(Message msg) {
          if (msg.what == GearsBaseDialog.CLEAR_REQUEST_ICON) {
            mLoaderHandler.removeMessages(
                GearsBaseDialog.PAUSE_REQUEST_ICON);
            mLoaderHandler.removeMessages(
                GearsBaseDialog.REQUEST_ICON);
          } else if (msg.what == GearsBaseDialog.PAUSE_REQUEST_ICON) {
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
            FilePickerElement elem = (FilePickerElement) msg.obj;
            if (generateIcon(elem)) {
              signalChanges();
            }
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              Log.e(TAG, "InterruptedException ", e);
            }
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
    private boolean mAskedForSystemThumbnail;

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
      mAskedForSystemThumbnail = false;
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
      mAskedForSystemThumbnail = false;

      setIcons();
    }

    public void setAskedForSystemThumbnail(boolean value) {
      mAskedForSystemThumbnail = value;
    }

    public boolean askedForSystemThumbnail() {
      return mAskedForSystemThumbnail;
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
                com.android.internal.R.drawable.ic_menu_back);
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
      mImagesLoader.clearIconRequests();
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
        FilePickerElement elem = (FilePickerElement) children.get(position);
        return elem;
      }
      return null;
    }

    /*
     * Depending on the type, we return either
     * the icon (mIcon) or the back icon (mBackIcon).
     * If we can load a system thumbnail we do this
     * synchronously and return it, else we ask the
     * mImagesLoader to generate a thumbnail for us.
     */
    public Bitmap getIcon(int position) {
      if (mIsParent) {
        return mBackIcon;
      }
      if (isImage()) {
        if (mThumbnail != null) {
          return mThumbnail;
        } else {
          Bitmap image = mImagesLoader.getSystemThumbnail(this);
          if (image != null) {
            mThumbnail = image;
            return mThumbnail;
          }
          mImagesLoader.postIconRequest(this, position, true);
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
            v.setBackgroundColor(android.R.color.background_dark);
          }
          return false;
        }
      });

      cell.setTag(position);

      if (elem.isSelected()) {
        int color = getResources().getColor(R.color.icon_selection);
        cell.setBackgroundColor(color);
      } else {
        cell.setBackgroundColor(android.R.color.background_dark);
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
