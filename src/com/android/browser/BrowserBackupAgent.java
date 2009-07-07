/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.io.IOException;

import android.app.BackupAgent;
import android.backup.BackupDataInput;
import android.backup.BackupDataOutput;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.Browser;
import android.provider.Browser.BookmarkColumns;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.zip.CRC32;

/**
 * Settings backup agent for the Android browser.  Currently the only thing
 * stored is the set of bookmarks.  It's okay if I/O exceptions are thrown
 * out of the agent; the calling code handles it and the backup operation
 * simply fails.
 */
public class BrowserBackupAgent extends BackupAgent {
    static final String BOOKMARK_KEY = "_bookmarks_";

    /**
     * In order to determine whether the bookmark set has changed since the
     * last time we did a backup, we store the following bits of info in the
     * state file after a backup:
     *
     * 1. the size of the flattened bookmark file
     * 2. the CRC32 of that file
     *
     * After we flatten the bookmarks file here in onBackup, we compare its
     * metrics with the values from the saved state.  If they match, it means
     * the bookmarks didn't really change and we don't need to send the data.
     * (If they don't match, of course, then they've changed and we do indeed
     * send the new flattened file to be backed up.)
     */
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        long savedFileSize = -1;
        long savedCrc = -1;

        // Extract the previous bookmark file size & CRC from the saved state
        DataInputStream in = new DataInputStream(
                new FileInputStream(oldState.getFileDescriptor()));
        try {
            savedFileSize = in.readLong();
            savedCrc = in.readLong();
        } catch (EOFException e) {
            // It means we had no previous state; that's fine
        }

        // TODO: BUILD THE FLATTENED BOOKMARK FILE FROM THE DB (into tmpfile)
        File tmpfile = getFilesDir().createTempFile("bkp", null);
        CRC32 crc = new CRC32();
        try {
            Cursor cursor = getContentResolver().query(Browser.BOOKMARKS_URI,
                    new String[] { BookmarkColumns.URL, BookmarkColumns.VISITS,
                    BookmarkColumns.DATE, BookmarkColumns.CREATED,
                    BookmarkColumns.TITLE },
                    BookmarkColumns.BOOKMARK + " == 1 ", null, null);
            int count = cursor.getCount();
            FileOutputStream out = new FileOutputStream(tmpfile);
            for (int i = 0; i < count; i++) {
                StringBuilder sb = new StringBuilder();
                // URL
                sb.append("'");
                sb.append(cursor.getString(0));
                sb.append("','");
                // VISITS
                sb.append(cursor.getInt(1));
                sb.append("','");
                // DATE
                sb.append(cursor.getLong(2));
                sb.append("','");
                // CREATED
                sb.append(cursor.getLong(3));
                sb.append("','");
                // TITLE
                sb.append(cursor.getString(4));
                sb.append("'");
                out.write(sb.toString().getBytes());
            }
            out.close();
            /*
                    android.util.Log.d("s", "backing up data" +
                            getContentResolver().openFileDescriptor(Browser.BOOKMARKS_URI, "r").toString());
             */
            // NOTE: feed the flattened data through the crc engine on the fly
            // to save re-reading it later just to checksum it

            // Once the file is built, compare its metrics with the saved ones
            if ((crc.getValue() != savedCrc) || (tmpfile.length() != savedFileSize)) {
                // Different checksum or different size, so we need to back it up
                copyFileToBackup(BOOKMARK_KEY, tmpfile, data);
            }

            // Last, record the metrics of the bookmark file that we just stored
            writeBackupState(tmpfile.length(), crc.getValue(), newState);
        } finally {
            // Make sure to tidy up when we're done
            tmpfile.delete();
        }
    }

    /**
     * Restore from backup -- reads in the flattened bookmark file as supplied from
     * the backup service, parses that out, and rebuilds the bookmarks table in the
     * browser database from it.
     */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
            ParcelFileDescriptor newState) throws IOException {
        long crc = -1;
        File tmpfile = getFilesDir().createTempFile("rst", null);
        try {
            while (data.readNextHeader()) {
                if (BOOKMARK_KEY.equals(data.getKey())) {
                    // Read the flattened bookmark data into a temp file
                    crc = copyBackupToFile(data, tmpfile, data.getDataSize());

                    // TODO: READ THE FLAT BOOKMARKS FILE 'tmpfile' AND REBUILD THE DB TABLE
                }

                // Last, write the state we just restored from so we can discern
                // changes whenever we get invoked for backup in the future
                writeBackupState(tmpfile.length(), crc, newState);
            }
        } finally {
            // Whatever happens, delete the temp file
            tmpfile.delete();
        }
    }

    /*
     * Utility functions
     */

    // Write the file to backup as a single record under the given key
    private void copyFileToBackup(String key, File file, BackupDataOutput data)
            throws IOException {
        final int CHUNK = 8192;
        byte[] buf = new byte[CHUNK];

        int toCopy = (int) file.length();
        data.writeEntityHeader(key, toCopy);

        FileInputStream in = new FileInputStream(file);
        int nRead;
        while (toCopy > 0) {
            nRead = in.read(buf, 0, CHUNK);
            data.writeEntityData(buf, nRead);
            toCopy -= nRead;
        }
        in.close();
    }

    // Read the given file from backup to a file, calculating a CRC32 along the way
    private long copyBackupToFile(BackupDataInput data, File file, int toRead)
            throws IOException {
        final int CHUNK = 8192;
        byte[] buf = new byte[CHUNK];
        CRC32 crc = new CRC32();

        while (toRead > 0) {
            int numRead = data.readEntityData(buf, 0, CHUNK);
            crc.update(buf, 0, numRead);
            toRead -= numRead;
        }

        return crc.getValue();
    }

    // Write the given metrics to the new state file
    private void writeBackupState(long fileSize, long crc, ParcelFileDescriptor stateFile)
            throws IOException {
        DataOutputStream out = new DataOutputStream(
                new FileOutputStream(stateFile.getFileDescriptor()));
        out.writeLong(fileSize);
        out.writeLong(crc);
    }
}
