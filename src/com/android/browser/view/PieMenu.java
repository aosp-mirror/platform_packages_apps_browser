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

package com.android.browser.view;

import com.android.browser.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PieMenu extends FrameLayout {

    private static final int RADIUS_GAP = 10;

    public interface PieController {
        /**
         * called before menu opens to customize menu
         * returns if pie state has been changed
         */
        public boolean onOpen();
    }
    private Point mCenter;
    private int mRadius;
    private int mRadiusInc;
    private int mSlop;

    private boolean mOpen;
    private Paint mPaint;
    private Paint mSelectedPaint;
    private PieController mController;

    private Map<View, List<View>> mMenu;
    private List<View> mStack;

    private boolean mDirty;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public PieMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * @param context
     * @param attrs
     */
    public PieMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @param context
     */
    public PieMenu(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        this.setTag(new MenuTag(0));
        mStack = new ArrayList<View>();
        mStack.add(this);
        Resources res = ctx.getResources();
        mRadius = (int) res.getDimension(R.dimen.qc_radius);
        mRadiusInc = (int) res.getDimension(R.dimen.qc_radius_inc);
        mSlop = (int) res.getDimension(R.dimen.qc_slop);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(res.getColor(R.color.qc_slice_normal));
        mSelectedPaint = new Paint();
        mSelectedPaint.setAntiAlias(true);
        mSelectedPaint.setColor(res.getColor(R.color.qc_slice_active));
        mOpen = false;
        mMenu = new HashMap<View, List<View>>();
        setWillNotDraw(false);
        setDrawingCacheEnabled(false);
        mCenter = new Point(0,0);
        mDirty = true;
    }

    public void setController(PieController ctl) {
        mController = ctl;
    }

    public void setRadius(int r) {
        mRadius = r;
        requestLayout();
    }

    public void setRadiusIncrement(int ri) {
        mRadiusInc = ri;
        requestLayout();
    }

    /**
     * add a menu item to another item as a submenu
     * @param item
     * @param parent
     */
    public void addItem(View item, View parent) {
        List<View> subs = mMenu.get(parent);
        if (subs == null) {
            subs = new ArrayList<View>();
            mMenu.put(parent, subs);
        }
        subs.add(item);
        MenuTag tag = new MenuTag(((MenuTag) parent.getTag()).level + 1);
        item.setTag(tag);
    }

    public void addItem(View view) {
        // add the item to the pie itself
        addItem(view, this);
    }

    public void removeItem(View view) {
        List<View> subs = mMenu.get(view);
        mMenu.remove(view);
        for (View p : mMenu.keySet()) {
            List<View> sl = mMenu.get(p);
            if (sl != null) {
                sl.remove(view);
            }
        }
    }

    public void clearItems(View parent) {
        List<View> subs = mMenu.remove(parent);
        if (subs != null) {
            for (View sub: subs) {
                clearItems(sub);
            }
        }
    }

    public void clearItems() {
        mMenu.clear();
    }


    public void show(boolean show) {
        mOpen = show;
        if (mOpen) {
            if (mController != null) {
                boolean changed = mController.onOpen();
            }
            mDirty = true;
        }
        if (!show) {
            // hide sub items
            mStack.clear();
            mStack.add(this);
        }
        invalidate();
    }

    private void setCenter(int x, int y) {
        if (x < mSlop) {
            mCenter.x = 0;
        } else {
            mCenter.x = getWidth();
        }
        mCenter.y = y;
    }

    private boolean onTheLeft() {
        return mCenter.x < mSlop;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int radius = mRadius;
            // start in the center for 0 level menu
            float anchor = (float) Math.PI / 2;
            PointF angles = new PointF();
            int state = canvas.save();
            if (onTheLeft()) {
                // left handed
                canvas.scale(-1, 1);
            }
            for (View parent : mStack) {
                List<View> subs = mMenu.get(parent);
                if (subs != null) {
                    setGeometry(anchor, subs.size(), angles);
                }
                anchor = drawSlices(canvas, subs, radius, angles.x, angles.y);
                radius += mRadiusInc + RADIUS_GAP;
            }
            canvas.restoreToCount(state);
            mDirty = false;
        }
    }

    /**
     * draw the set of slices
     * @param canvas
     * @param items
     * @param radius
     * @param start
     * @param sweep
     * @return the angle of the selected slice
     */
    private float drawSlices(Canvas canvas, List<View> items, int radius,
            float start, float sweep) {
        float angle = start + sweep / 2;
        // gap between slices in degrees
        float gap = 1f;
        float newanchor = 0f;
        for (View item : items) {
            if (mDirty) {
                item.measure(item.getLayoutParams().width,
                        item.getLayoutParams().height);
                int w = item.getMeasuredWidth();
                int h = item.getMeasuredHeight();
                int x = (int) (radius * Math.sin(angle));
                int y =  mCenter.y - (int) (radius * Math.cos(angle)) - h / 2;
                if (onTheLeft()) {
                    x = mCenter.x + x - w / 2;
                } else {
                    x = mCenter.x - x - w / 2;
                }
                item.layout(x, y, x + w, y + h);
            }
            float itemstart = angle - sweep / 2;
            int inner = radius - mRadiusInc / 2;
            int outer = radius + mRadiusInc / 2;
            Path slice = makeSlice(getDegrees(itemstart) - gap,
                    getDegrees(itemstart + sweep) + gap,
                    outer, inner, mCenter);
            MenuTag tag = (MenuTag) item.getTag();
            tag.start = itemstart;
            tag.sweep = sweep;
            tag.inner = inner;
            tag.outer = outer;

            Paint p = item.isPressed() ? mSelectedPaint : mPaint;
            canvas.drawPath(slice, p);
            int state = canvas.save();
            if (onTheLeft()) {
                canvas.scale(-1, 1);
            }
            canvas.translate(item.getX(), item.getY());
            item.draw(canvas);
            canvas.restoreToCount(state);
            if (mStack.contains(item)) {
                // item is anchor for sub menu
                newanchor = angle;
            }
            angle += sweep;
        }
        return newanchor;
    }

    /**
     * converts a
     * @param angle from 0..PI to Android degrees (clockwise starting at 3 o'clock)
     * @return skia angle
     */
    private float getDegrees(double angle) {
        return (float) (270 - 180 * angle / Math.PI);
    }

    private Path makeSlice(float startangle, float endangle, int outerradius,
            int innerradius, Point center) {
        RectF bb = new RectF(center.x - outerradius, center.y - outerradius,
                center.x + outerradius, center.y + outerradius);
        RectF bbi = new RectF(center.x - innerradius, center.y - innerradius,
                center.x + innerradius, center.y + innerradius);
        Path path = new Path();
        path.arcTo(bb, startangle, endangle - startangle, true);
        path.arcTo(bbi, endangle, startangle - endangle);
        path.close();
        return path;
    }

    /**
     * all angles are 0 .. MATH.PI where 0 points up, and rotate counterclockwise
     * set the startangle and slice sweep in result
     * @param anchorangle : angle at which the menu is anchored
     * @param nslices
     * @param result : x : start, y : sweep
     */
    private void setGeometry(float anchorangle, int nslices, PointF result) {
        float span = (float) Math.min(anchorangle, Math.PI - anchorangle);
        float sweep = 2 * span / (nslices + 1);
        result.x = anchorangle - span + sweep / 2;
        result.y = sweep;
    }

    // touch handling for pie

    View mCurrentView;
    Rect mHitRect = new Rect();

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        float x = evt.getX();
        float y = evt.getY();
        int action = evt.getActionMasked();
        int edges = evt.getEdgeFlags();
        if (MotionEvent.ACTION_DOWN == action) {
            if ((x > getWidth() - mSlop) || (x < mSlop)) {
                setCenter((int) x, (int) y);
                show(true);
                return true;
            }
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                View v = mCurrentView;
                deselect();
                if (v != null) {
                    v.performClick();
                }
                show(false);
                return true;
            }
        } else if (MotionEvent.ACTION_CANCEL == action) {
            if (mOpen) {
                show(false);
            }
            deselect();
            return false;
        } else if (MotionEvent.ACTION_MOVE == action) {
            PointF polar = getPolar(x, y);
            if (polar.y > mRadius + 2 * mRadiusInc) {
                show(false);
                deselect();
                return false;
            }
            View v = findView(polar);
            if (mCurrentView != v) {
                onEnter(v);
                invalidate();
            }
        }
        // always re-dispatch event
        return false;
    }

    /**
     * enter a slice for a view
     * updates model only
     * @param view
     */
    private void onEnter(View view) {
        // deselect
        if (mCurrentView != null) {
            if (getLevel(mCurrentView) >= getLevel(view)) {
                mCurrentView.setPressed(false);
            }
        }
        if (view != null) {
            // clear up stack
            MenuTag tag = (MenuTag) view.getTag();
            int i = mStack.size() - 1;
            while (i > 0) {
                View v = mStack.get(i);
                if (((MenuTag) v.getTag()).level >= tag.level) {
                    v.setPressed(false);
                    mStack.remove(i);
                } else {
                    break;
                }
                i--;
            }
            List<View> items = mMenu.get(view);
            if (items != null) {
                mStack.add(view);
                mDirty = true;
            }
            view.setPressed(true);
        }
        mCurrentView = view;
    }

    private void deselect() {
        if (mCurrentView != null) {
            mCurrentView.setPressed(false);
        }
        mCurrentView = null;
    }

    private int getLevel(View v) {
        if (v == null) return -1;
        return ((MenuTag) v.getTag()).level;
    }

    private PointF getPolar(float x, float y) {
        PointF res = new PointF();
        // get angle and radius from x/y
        res.x = (float) Math.PI / 2;
        x = mCenter.x - x;
        if (mCenter.x < mSlop) {
            x = -x;
        }
        y = mCenter.y - y;
        res.y = (float) Math.sqrt(x * x + y * y);
        if (y > 0) {
            res.x = (float) Math.asin(x / res.y);
        } else if (y < 0) {
            res.x = (float) (Math.PI - Math.asin(x / res.y ));
        }
        return res;
    }

    /**
     *
     * @param polar x: angle, y: dist
     * @return
     */
    private View findView(PointF polar) {
        // find the matching item:
        for (View parent : mStack) {
            List<View> subs = mMenu.get(parent);
            if (subs != null) {
                for (View item : subs) {
                    MenuTag tag = (MenuTag) item.getTag();
                    if ((tag.inner < polar.y)
                            && (tag.outer > polar.y)
                            && (tag.start < polar.x)
                            && (tag.start + tag.sweep > polar.x)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    class MenuTag {

        int level;
        float start;
        float sweep;
        int inner;
        int outer;

        public MenuTag(int l) {
            level = l;
        }

    }

}
