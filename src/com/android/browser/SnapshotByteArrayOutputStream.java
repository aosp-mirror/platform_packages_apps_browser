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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SnapshotByteArrayOutputStream extends OutputStream {

    // Maximum size, this needs to be small enough such that an entire row
    // can fit in CursorWindow's 2MB limit
    private static final int MAX_SIZE = 1700000;
    private ByteArrayOutputStream mStream;

    public SnapshotByteArrayOutputStream() {
        mStream = new ByteArrayOutputStream(MAX_SIZE);
    }

    @Override
    public synchronized void write(int oneByte) throws IOException {
        checkError(1);
        mStream.write(oneByte);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        checkError(count);
        mStream.write(buffer, offset, count);
    }

    private void checkError(int expandBy) throws IOException {
        if ((size() + expandBy) > MAX_SIZE) {
            throw new IOException("Exceeded max size!");
        }
    }

    public int size() {
        return mStream.size();
    }

    public byte[] toByteArray() {
        return mStream.toByteArray();
    }

}
