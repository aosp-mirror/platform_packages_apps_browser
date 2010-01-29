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

import android.graphics.Bitmap;
import android.graphics.utils.BoundaryPatch;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.webkit.WebView;

/*package*/ class MeshTracker extends WebView.DragTracker {

    private static class Mesh {
        private int mWhich;
        private int mRows;
        private int mCols;
        private BoundaryPatch mPatch = new BoundaryPatch();
        private float[] mCubics = new float[24];
        private float[] mOrig = new float[24];
        private float mStretchX, mStretchY;

        Mesh(int which, int rows, int cols) {
            mWhich = which;
            mRows = rows;
            mCols = cols;
        }

        private void rebuildPatch() {
            mPatch.setCubicBoundary(mCubics, 0, mRows, mCols);
        }

        private void setSize(float w, float h) {
            float[] pts = mCubics;
            float x1 = w*0.3333f;
            float y1 = h*0.3333f;
            float x2 = w*0.6667f;
            float y2 = h*0.6667f;
            pts[0*2+0] = 0;  pts[0*2+1] = 0;
            pts[1*2+0] = x1; pts[1*2+1] = 0;
            pts[2*2+0] = x2; pts[2*2+1] = 0;

            pts[3*2+0] = w; pts[3*2+1] = 0;
            pts[4*2+0] = w; pts[4*2+1] = y1;
            pts[5*2+0] = w; pts[5*2+1] = y2;

            pts[6*2+0] = w; pts[6*2+1] = h;
            pts[7*2+0] = x2; pts[7*2+1] = h;
            pts[8*2+0] = x1; pts[8*2+1] = h;

            pts[9*2+0] = 0;  pts[9*2+1] = h;
            pts[10*2+0] = 0; pts[10*2+1] = y2;
            pts[11*2+0] = 0; pts[11*2+1] = y1;

            System.arraycopy(pts, 0, mOrig, 0, 24);

            // recall our stretcher
            setStretch(mStretchX, mStretchY);
        }

        public void setBitmap(Bitmap bm) {
            mPatch.setTexture(bm);
            setSize(bm.getWidth(), bm.getHeight());
        }

        // first experimental behavior
        private void doit1(float dx, float dy) {
            final float scale = 0.75f;  // temper how far we actually move
            dx *= scale;
            dy *= scale;

            int index;
            if (dx < 0) {
                index = 10;
            } else {
                index = 4;
            }
            mCubics[index*2 + 0] = mOrig[index*2 + 0] + dx;
            mCubics[index*2 + 2] = mOrig[index*2 + 2] + dx;

            if (dy < 0) {
                index = 1;
            } else {
                index = 7;
            }
            mCubics[index*2 + 1] = mOrig[index*2 + 1] + dy;
            mCubics[index*2 + 3] = mOrig[index*2 + 3] + dy;
        }

        private void doit2(float dx, float dy) {
            final float scale = 0.35f;  // temper how far we actually move
            dx *= scale;
            dy *= scale;
            final float cornerScale = 0.25f;

            int index;
            if (dx < 0) {
                index = 4;
            } else {
                index = 10;
            }
            mCubics[index*2 + 0] = mOrig[index*2 + 0] + dx;
            mCubics[index*2 + 2] = mOrig[index*2 + 2] + dx;
            // corners
            index -= 1;
            mCubics[index*2 + 0] = mOrig[index*2 + 0] + dx * cornerScale;
            index = (index + 3) % 12; // next corner
            mCubics[index*2 + 0] = mOrig[index*2 + 0] + dx * cornerScale;

            if (dy < 0) {
                index = 7;
            } else {
                index = 1;
            }
            mCubics[index*2 + 1] = mOrig[index*2 + 1] + dy;
            mCubics[index*2 + 3] = mOrig[index*2 + 3] + dy;
            // corners
            index -= 1;
            mCubics[index*2 + 1] = mOrig[index*2 + 1] + dy * cornerScale;
            index = (index + 3) % 12; // next corner
            mCubics[index*2 + 1] = mOrig[index*2 + 1] + dy * cornerScale;
        }

        public void setStretch(float dx, float dy) {
            mStretchX = dx;
            mStretchY = dy;
            switch (mWhich) {
                case 1:
                    doit1(dx, dy);
                    break;
                case 2:
                    doit2(dx, dy);
                    break;
            }
            rebuildPatch();
        }

        public void draw(Canvas canvas) {
            mPatch.draw(canvas);
        }
    }

    private Mesh mMesh;
    private Bitmap mBitmap;
    private int mWhich;
    private Paint mBGPaint;

    public MeshTracker(int which) {
        mWhich = which;
    }

    public void setBGPaint(Paint paint) {
        mBGPaint = paint;
    }

    @Override public void onStartDrag(float x, float y) {
        mMesh = new Mesh(mWhich, 16, 16);
    }

    @Override public void onBitmapChange(Bitmap bm) {
        mBitmap = bm;
        mMesh.setBitmap(bm);
    }

    @Override public boolean onStretchChange(float sx, float sy) {
        mMesh.setStretch(-sx, -sy);
        return true;
    }

    @Override public void onStopDrag() {
        mMesh = null;
    }

    @Override public void onDraw(Canvas canvas) {
        if (mWhich == 2) {
            if (mBGPaint != null) {
                canvas.drawPaint(mBGPaint);
            } else {
                canvas.drawColor(0xFF000000);
            }
        }
        mMesh.draw(canvas);
    }
}

