

package com.android.browser;

import android.graphics.Bitmap;
import android.graphics.utils.BoundaryPatch;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.webkit.WebView;

/*package*/ class MeshTracker extends WebView.DragTracker {

    private static class Mesh {
        private int mRows;
        private int mCols;
        private BoundaryPatch mPatch = new BoundaryPatch();
        private float[] mCubics = new float[24];
        private float[] mOrig = new float[24];
        private float mStretchX, mStretchY;

        Mesh(int rows, int cols) {
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
        private void doit0(float dx, float dy) {
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

        private void doit1(float dx, float dy) {
            int index;

            if (dx < 0) {
                index = 4;
            } else {
                index = 10;
            }
            mCubics[index*2 + 0] = mOrig[index*2 + 0] + dx;
            mCubics[index*2 + 2] = mOrig[index*2 + 2] + dx;

            if (dy < 0) {
                index = 7;
            } else {
                index = 1;
            }
            mCubics[index*2 + 1] = mOrig[index*2 + 1] + dy;
            mCubics[index*2 + 3] = mOrig[index*2 + 3] + dy;
        }

        public void setStretch(float dx, float dy) {
            mStretchX = dx;
            mStretchY = dy;
            doit1(dx, dy);
            rebuildPatch();
        }

        public void draw(Canvas canvas) {
            mPatch.draw(canvas);
        }
    }

    private Mesh mMesh;
    private Bitmap mBitmap;

    public MeshTracker() {}

    @Override public void onStartDrag(float x, float y) {
        mMesh = new Mesh(16, 16);
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
        canvas.drawColor(0xFF000000);
        Paint paint = new Paint();
        paint.setAlpha(0x80);
        canvas.drawBitmap(mBitmap, 0, 0, paint);
        mMesh.draw(canvas);
    }
}

