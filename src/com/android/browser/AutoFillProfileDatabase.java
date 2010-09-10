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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class AutoFillProfileDatabase {

    static final String LOGTAG = "AutoFillProfileDatabase";

    static final String DATABASE_NAME = "autofill.db";
    static final int DATABASE_VERSION = 1;
    static final String PROFILES_TABLE_NAME = "profiles";
    private AutoFillProfileDatabaseHelper mOpenHelper;
    private static AutoFillProfileDatabase sInstance;

    public static final class Profiles implements BaseColumns {
        private Profiles() { }

        static final String FULL_NAME = "fullname";
        static final String EMAIL_ADDRESS = "email";
    }

    private static class AutoFillProfileDatabaseHelper extends SQLiteOpenHelper {
        AutoFillProfileDatabaseHelper(Context context) {
             super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + PROFILES_TABLE_NAME + " ("
                    + Profiles._ID + " INTEGER PRIMARY KEY,"
                    + Profiles.FULL_NAME + " TEXT,"
                    + Profiles.EMAIL_ADDRESS + " TEXT"
                    + " );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(LOGTAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
            onCreate(db);
        }
    }

    private AutoFillProfileDatabase(Context context) {
        mOpenHelper = new AutoFillProfileDatabaseHelper(context);
    }

    public static AutoFillProfileDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AutoFillProfileDatabase(context);
        }
        return sInstance;
    }

    private SQLiteDatabase getDatabase(boolean writable) {
        return writable ? mOpenHelper.getWritableDatabase() : mOpenHelper.getReadableDatabase();
    }

    public void addOrUpdateProfile(final int id, final String fullName, final String email) {
        final String SQL = "INSERT OR REPLACE INTO " + PROFILES_TABLE_NAME + " ("
                + Profiles._ID + ","
                + Profiles.FULL_NAME + ","
                + Profiles.EMAIL_ADDRESS
                + ") VALUES (?,?,?);";
        final Object[] PARAMS = {id, fullName, email};
        getDatabase(true).execSQL(SQL, PARAMS);
    }

    public Cursor getProfile(int id) {
        final String[] COLS = {Profiles.FULL_NAME, Profiles.EMAIL_ADDRESS };
        final String[] SEL_ARGS = { Integer.toString(id) };
        return getDatabase(false).query(PROFILES_TABLE_NAME, COLS, Profiles._ID + "=?", SEL_ARGS,
                null, null, null, "1");
    }

    public void close() {
        mOpenHelper.close();
    }
}
