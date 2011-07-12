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

package com.android.browser.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Transformation;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.Scroller;

import com.android.internal.R;

public class Gallery extends ViewGroup implements
        GestureDetector.OnGestureListener {

    private static final String TAG = "Gallery";

    private static final boolean localLOGV = false;

    private static final int INVALID_POSITION = -1;

    /**
     * Duration in milliseconds from the start of a scroll during which we're
     * unsure whether the user is scrolling or flinging.
     */
    private static final int SCROLL_TO_FLING_UNCERTAINTY_TIMEOUT = 250;
    private static final int INVALID_POINTER = -1;

    private boolean mInLayout;
    private int mWidthMeasureSpec;
    private int mHeightMeasureSpec;
    private boolean mBlockLayoutRequests;

    private Rect mTouchFrame;

    private RecycleBin mRecycler;

    private boolean mHorizontal;
    private int mFirstPosition;
    private int mItemCount;
    private boolean mDataChanged;

    protected BaseAdapter mAdapter;

    private int mSelectedPosition;
    private int mOldSelectedPosition;

    private int mSpacing = 0;
    private int mAnimationDuration = 400;
    private float mUnselectedAlpha;
    private int mLeftMost;
    private int mRightMost;
    private int mGravity;

    private GestureDetector mGestureDetector;

    private int mDownTouchPosition;
    private View mDownTouchView;
    private FlingRunnable mFlingRunnable = new FlingRunnable();

    private OnItemSelectedListener mOnItemSelectedListener;
    private SelectionNotifier mSelectionNotifier;

    /**
     * Sets mSuppressSelectionChanged = false. This is used to set it to false
     * in the future. It will also trigger a selection changed.
     */
    private Runnable mDisableSuppressSelectionChangedRunnable = new Runnable() {
        public void run() {
            mSuppressSelectionChanged = false;
            selectionChanged();
        }
    };

    private boolean mShouldStopFling;
    private View mSelectedChild;
    private boolean mShouldCallbackDuringFling = true;
    private boolean mShouldCallbackOnUnselectedItemClick = true;
    private boolean mSuppressSelectionChanged;
    private boolean mReceivedInvokeKeyDown;

    /**
     * If true, this onScroll is the first for this user's drag (remember, a
     * drag sends many onScrolls).
     */
    private boolean mIsFirstScroll;

    private boolean mIsBeingDragged;

    private int mActivePointerId = INVALID_POINTER;

    private int mTouchSlop;

    private float mLastMotionCoord;

    public Gallery(Context context) {
        this(context, null);
    }

    public Gallery(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.galleryStyle);
    }

    public Gallery(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mRecycler = new RecycleBin();
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(true);
        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.Gallery, defStyle, 0);
        int index = a.getInt(com.android.internal.R.styleable.Gallery_gravity,
                -1);
        if (index >= 0) {
            setGravity(index);
        }
        int animationDuration = a.getInt(
                com.android.internal.R.styleable.Gallery_animationDuration, -1);
        if (animationDuration > 0) {
            setAnimationDuration(animationDuration);
        }
        float unselectedAlpha = a.getFloat(
                com.android.internal.R.styleable.Gallery_unselectedAlpha, 0.5f);
        setUnselectedAlpha(unselectedAlpha);
        mHorizontal = true;
        a.recycle();
        // We draw the selected item last (because otherwise the item to the
        // right overlaps it)
        mGroupFlags |= FLAG_USE_CHILD_DRAWING_ORDER;
        mGroupFlags |= FLAG_SUPPORT_STATIC_TRANSFORMATIONS;
        final ViewConfiguration configuration = ViewConfiguration.get(mContext);
        mTouchSlop = configuration.getScaledTouchSlop();
        setFocusable(true);
        setWillNotDraw(false);
    }

    /**
     * Interface definition for a callback to be invoked when an item in this
     * view has been selected.
     */
    public interface OnItemSelectedListener {
        void onItemSelected(ViewGroup parent, View view, int position, long id);

    }

    /**
     * Register a callback to be invoked when an item in this AdapterView has
     * been selected.
     *
     * @param listener
     *            The callback that will run
     */
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        mOnItemSelectedListener = listener;
    }

    public final OnItemSelectedListener getOnItemSelectedListener() {
        return mOnItemSelectedListener;
    }

    public void setOrientation(int orientation) {
        mHorizontal = (orientation == LinearLayout.HORIZONTAL);
        requestLayout();
    }

    public void setAdapter(BaseAdapter adapter) {
        mAdapter = adapter;
        if (mAdapter != null) {
            mAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    super.onChanged();
                    mDataChanged = true;
                    handleDataChanged();
                }

                @Override
                public void onInvalidated() {
                    super.onInvalidated();
                }
            });
        }
        handleDataChanged();
    }

    void handleDataChanged() {
        if (mAdapter != null) {
            resetList();
            mItemCount = mAdapter.getCount();
            // checkFocus();
            int position = mItemCount > 0 ? 0 : INVALID_POSITION;
            if (mSelectedPosition >= 0) {
                position = Math.min(mItemCount - 1, mSelectedPosition);
            }
            setSelectedPositionInt(position);
            if (mItemCount == 0) {
                // Nothing selected
                checkSelectionChanged();
            }
        } else {
            // checkFocus();
            mOldSelectedPosition = INVALID_POSITION;
            setSelectedPositionInt(INVALID_POSITION);
            resetList();
            // Nothing selected
            checkSelectionChanged();
        }
    }

    /**
     * Clear out all children from the list
     */
    void resetList() {
        mDataChanged = false;
        removeAllViewsInLayout();
        invalidate();
    }

    public void setCallbackDuringFling(boolean shouldCallback) {
        mShouldCallbackDuringFling = shouldCallback;
    }

    public void setCallbackOnUnselectedItemClick(boolean shouldCallback) {
        mShouldCallbackOnUnselectedItemClick = shouldCallback;
    }

    public void setAnimationDuration(int animationDurationMillis) {
        mAnimationDuration = animationDurationMillis;
    }

    public void setUnselectedAlpha(float unselectedAlpha) {
        mUnselectedAlpha = unselectedAlpha;
    }

    @Override
    protected boolean getChildStaticTransformation(View child, Transformation t) {
        return false;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(
            ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new Gallery.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize;
        int heightSize;
        if (mDataChanged) {
            handleDataChanged();
        }
        int preferredHeight = 0;
        int preferredWidth = 0;
        boolean needsMeasuring = true;
        int selectedPosition = getSelectedItemPosition();
        if (selectedPosition >= 0 && mAdapter != null
                && selectedPosition < mAdapter.getCount()) {
            // Try looking in the recycler. (Maybe we were measured once
            // already)
            View view = mRecycler.get(selectedPosition);
            if (view == null) {
                // Make a new one
                view = mAdapter.getView(selectedPosition, null, this);
            }
            if (view != null) {
                // Put in recycler for re-measuring and/or layout
                mRecycler.put(selectedPosition, view);
            }
            if (view != null) {
                if (view.getLayoutParams() == null) {
                    mBlockLayoutRequests = true;
                    view.setLayoutParams(generateDefaultLayoutParams());
                    mBlockLayoutRequests = false;
                }
                measureChild(view, widthMeasureSpec, heightMeasureSpec);
                preferredHeight = getChildHeight(view);
                preferredWidth = getChildWidth(view);
                needsMeasuring = false;
            }
        }
        if (needsMeasuring) {
            // No views -- just use padding
            preferredHeight = 0;
            if (widthMode == MeasureSpec.UNSPECIFIED) {
                preferredWidth = 0;
            }
        }
        preferredHeight = Math
                .max(preferredHeight, getSuggestedMinimumHeight());
        preferredWidth = Math.max(preferredWidth, getSuggestedMinimumWidth());
        heightSize = resolveSizeAndState(preferredHeight, heightMeasureSpec, 0);
        widthSize = resolveSizeAndState(preferredWidth, widthMeasureSpec, 0);
        setMeasuredDimension(widthSize, heightSize);
        mHeightMeasureSpec = heightMeasureSpec;
        mWidthMeasureSpec = widthMeasureSpec;
    }

    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout = true;
        layout(0, false);
        mInLayout = false;
    }

    int getChildHeight(View child) {
        return child.getMeasuredHeight();
    }

    int getChildWidth(View child) {
        return child.getMeasuredWidth();
    }

    /**
     * Tracks a motion scroll. In reality, this is used to do just about any
     * movement to items (touch scroll, arrow-key scroll, set an item as
     * selected).
     *
     * @param deltaX
     *            Change in X from the previous event.
     */
    void trackMotionScroll(int deltaX) {
        if (getChildCount() == 0) {
            return;
        }
        boolean toLeft = deltaX < 0;
        int limitedDeltaX = getLimitedMotionScrollAmount(toLeft, deltaX);
        if (limitedDeltaX != deltaX) {
            // The above call returned a limited amount, so stop any
            // scrolls/flings
            mFlingRunnable.endFling(false);
            onFinishedMovement();
        }
        offsetChildrenLeftAndRight(limitedDeltaX);
        detachOffScreenChildren(toLeft);
        if (toLeft) {
            // If moved left, there will be empty space on the right
            fillToGalleryRight();
        } else {
            // Similarly, empty space on the left
            fillToGalleryLeft();
        }
        setSelectionToCenterChild();
        invalidate();
    }

    int getLimitedMotionScrollAmount(boolean motionToLeft, int deltaX) {
        int extremeItemPosition = motionToLeft ? mItemCount - 1 : 0;
        View extremeChild = getChildAt(extremeItemPosition - mFirstPosition);
        if (extremeChild == null) {
            return deltaX;
        }
        int extremeChildCenter = getCenterOfView(extremeChild);
        int galleryCenter = getCenterOfGallery();
        if (motionToLeft) {
            if (extremeChildCenter <= galleryCenter) {
                return 0;
            }
        } else {
            if (extremeChildCenter >= galleryCenter) {
                return 0;
            }
        }
        int centerDifference = galleryCenter - extremeChildCenter;
        return motionToLeft ? Math.max(centerDifference, deltaX) : Math.min(
                centerDifference, deltaX);
    }

    /**
     * Offset the horizontal location of all children of this view by the
     * specified number of pixels.
     *
     * @param offset
     *            the number of pixels to offset
     */
    private void offsetChildrenLeftAndRight(int offset) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            if (mHorizontal) {
                getChildAt(i).offsetLeftAndRight(offset);
            } else {
                getChildAt(i).offsetTopAndBottom(offset);
            }
        }
    }

    /**
     * @return The center of this Gallery.
     */
    private int getCenterOfGallery() {
        return (mHorizontal ? (getWidth() - mPaddingLeft - mPaddingRight) / 2
                + mPaddingLeft : (getHeight() - mPaddingTop - mPaddingBottom)
                / 2 + mPaddingTop);
    }

    /**
     * @return The center of the given view.
     */
    private int getCenterOfView(View view) {
        return (mHorizontal ? view.getLeft() + view.getWidth() / 2 : view
                .getTop() + view.getHeight() / 2);
    }

    /**
     * Detaches children that are off the screen (i.e.: Gallery bounds).
     *
     * @param toLeft
     *            Whether to detach children to the left of the Gallery, or to
     *            the right.
     */
    private void detachOffScreenChildren(boolean toLeft) {
        int numChildren = getChildCount();
        int firstPosition = mFirstPosition;
        int start = 0;
        int count = 0;
        if (toLeft) {
            final int galleryLeft = (mHorizontal ? mPaddingLeft : mPaddingTop);
            for (int i = 0; i < numChildren; i++) {
                final View child = getChildAt(i);
                if ((mHorizontal && (child.getRight() >= galleryLeft))
                        || (!mHorizontal && (child.getBottom() >= galleryLeft))) {
                    break;
                } else {
                    count++;
                    mRecycler.put(firstPosition + i, child);
                }
            }
        } else {
            final int galleryRight = (mHorizontal ? getWidth() - mPaddingRight
                    : getHeight() - mPaddingBottom);
            for (int i = numChildren - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                if ((mHorizontal && (child.getLeft() <= galleryRight))
                        || (!mHorizontal && (child.getTop() <= galleryRight))) {
                    break;
                } else {
                    start = i;
                    count++;
                    mRecycler.put(firstPosition + i, child);
                }
            }
        }
        detachViewsFromParent(start, count);
        if (toLeft) {
            mFirstPosition += count;
        }
    }

    private void scrollIntoSlots() {
        if (getChildCount() == 0 || mSelectedChild == null)
            return;
        int selectedCenter = getCenterOfView(mSelectedChild);
        int targetCenter = getCenterOfGallery();
        int scrollAmount = targetCenter - selectedCenter;
        if (scrollAmount != 0) {
            mFlingRunnable.startUsingDistance(scrollAmount);
        } else {
            onFinishedMovement();
        }
    }

    private void onFinishedMovement() {
        if (mSuppressSelectionChanged) {
            mSuppressSelectionChanged = false;
            // We haven't sent callbacks during the fling, so do it now
            selectionChanged();
        }
        invalidate();
    }

    protected void setSelectionToCenterChild() {
        if (mSelectedChild == null)
            return;
        int galleryCenter = getCenterOfGallery();
        int lastDistance = Integer.MAX_VALUE;
        int newSelectedChildIndex = 0;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            int distance = Math.abs(getCenterOfView(child) - galleryCenter);
            if (distance > lastDistance) {
                // we're moving away from the center, done
                break;
            } else {
                newSelectedChildIndex = i;
                lastDistance = distance;
            }
        }
        int newPos = mFirstPosition + newSelectedChildIndex;
        if (newPos != mSelectedPosition) {
            setSelectedPositionInt(newPos);
            checkSelectionChanged();
        }
    }

    /**
     * Creates and positions all views for this Gallery.
     * <p>
     * We layout rarely, most of the time {@link #trackMotionScroll(int)} takes
     * care of repositioning, adding, and removing children.
     *
     * @param delta
     *            Change in the selected position. +1 means the selection is
     *            moving to the right, so views are scrolling to the left. -1
     *            means the selection is moving to the left.
     */
    void layout(int delta, boolean animate) {
        int childrenLeft = 0;
        int childrenWidth = (mHorizontal ? mRight - mLeft : mBottom - mTop);
        if (mDataChanged) {
            handleDataChanged();
        }
        if (mItemCount == 0) {
            mOldSelectedPosition = INVALID_POSITION;
            setSelectedPositionInt(INVALID_POSITION);
            resetList();
            return;
        }
        if (mSelectedPosition >= 0) {
            setSelectedPositionInt(mSelectedPosition);
        }
        recycleAllViews();
        detachAllViewsFromParent();
        mRightMost = 0;
        mLeftMost = 0;
        mFirstPosition = mSelectedPosition;
        View sel = makeAndAddView(mSelectedPosition, 0, 0, true);
        // Put the selected child in the center
        int selectedOffset = childrenLeft + (childrenWidth / 2)
                - (mHorizontal ? (sel.getWidth() / 2) : (sel.getHeight() / 2));
        if (mHorizontal) {
            sel.offsetLeftAndRight(selectedOffset);
        } else {
            sel.offsetTopAndBottom(selectedOffset);
        }
        fillToGalleryRight();
        fillToGalleryLeft();
        mRecycler.clear();
        invalidate();
        checkSelectionChanged();
        mDataChanged = false;
        updateSelectedItemMetadata();
    }

    void recycleAllViews() {
        final int childCount = getChildCount();
        final RecycleBin recycleBin = mRecycler;
        final int position = mFirstPosition;
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            int index = position + i;
            recycleBin.put(index, v);
        }
    }

    private void fillToGalleryLeft() {
        int itemSpacing = mSpacing;
        int galleryLeft = mHorizontal ? mPaddingLeft : mPaddingTop;
        View prevIterationView = getChildAt(0);
        int curPosition;
        int curRightEdge;
        if (prevIterationView != null) {
            curPosition = mFirstPosition - 1;
            curRightEdge = (mHorizontal ? prevIterationView.getLeft()
                    : prevIterationView.getTop()) - itemSpacing;
        } else {
            // No children available!
            curPosition = 0;
            curRightEdge = (mHorizontal ? mRight - mLeft - mPaddingRight
                    : mBottom - mBottom - mPaddingBottom);
            mShouldStopFling = true;
        }
        while (curRightEdge > galleryLeft && curPosition >= 0) {
            prevIterationView = makeAndAddView(curPosition, curPosition
                    - mSelectedPosition, curRightEdge, false);
            // Remember some state
            mFirstPosition = curPosition;
            // Set state for next iteration
            curRightEdge = (mHorizontal ? prevIterationView.getLeft()
                    - itemSpacing : prevIterationView.getTop() - itemSpacing);
            curPosition--;
        }
    }

    private void fillToGalleryRight() {
        int itemSpacing = mSpacing;
        int galleryRight = (mHorizontal ? mRight - mLeft - mPaddingRight
                : mBottom - mTop - mPaddingBottom);
        int numChildren = getChildCount();
        int numItems = mItemCount;
        View prevIterationView = getChildAt(numChildren - 1);
        int curPosition;
        int curLeftEdge;
        if (prevIterationView != null) {
            curPosition = mFirstPosition + numChildren;
            curLeftEdge = mHorizontal ? prevIterationView.getRight()
                    + itemSpacing : prevIterationView.getBottom() + itemSpacing;
        } else {
            mFirstPosition = curPosition = mItemCount - 1;
            curLeftEdge = mHorizontal ? mPaddingLeft : mPaddingTop;
            mShouldStopFling = true;
        }
        while (curLeftEdge < galleryRight && curPosition < numItems) {
            prevIterationView = makeAndAddView(curPosition, curPosition
                    - mSelectedPosition, curLeftEdge, true);

            // Set state for next iteration
            curLeftEdge = mHorizontal ? prevIterationView.getRight()
                    + itemSpacing : prevIterationView.getBottom() + itemSpacing;
            curPosition++;
        }
    }

    /**
     * Obtain a view, either by pulling an existing view from the recycler or by
     * getting a new one from the adapter. If we are animating, make sure there
     * is enough information in the view's layout parameters to animate from the
     * old to new positions.
     *
     * @param position
     *            Position in the gallery for the view to obtain
     * @param offset
     *            Offset from the selected position
     * @param x
     *            X-coordintate indicating where this view should be placed.
     *            This will either be the left or right edge of the view,
     *            depending on the fromLeft paramter
     * @param fromLeft
     *            Are we posiitoning views based on the left edge? (i.e.,
     *            building from left to right)?
     * @return A view that has been added to the gallery
     */
    private View makeAndAddView(int position, int offset, int x,
            boolean fromLeft) {
        View child;
        if (!mDataChanged) {
            child = mRecycler.get(position);
            if (child != null) {
                // Can reuse an existing view
                int childLeft = mHorizontal ? child.getLeft() : child.getTop();

                // Remember left and right edges of where views have been placed
                mRightMost = Math.max(mRightMost,
                        childLeft
                                + (mHorizontal ? child.getMeasuredWidth()
                                        : child.getMeasuredHeight()));
                mLeftMost = Math.min(mLeftMost, childLeft);

                // Position the view
                setUpChild(position, child, offset, x, fromLeft);

                return child;
            }
        }
        // Nothing found in the recycler -- ask the adapter for a view
        child = mAdapter.getView(position, null, this);
        // Position the view
        setUpChild(position, child, offset, x, fromLeft);
        return child;
    }

    /**
     * Helper for makeAndAddView to set the position of a view and fill out its
     * layout paramters.
     *
     * @param child
     *            The view to position
     * @param offset
     *            Offset from the selected position
     * @param x
     *            X-coordintate indicating where this view should be placed.
     *            This will either be the left or right edge of the view,
     *            depending on the fromLeft paramter
     * @param fromLeft
     *            Are we positioning views based on the left edge? (i.e.,
     *            building from left to right)?
     */
    private void setUpChild(int position, View child, int offset, int x,
            boolean fromLeft) {
        Gallery.LayoutParams lp = (Gallery.LayoutParams) child
                .getLayoutParams();
        if (lp == null) {
            lp = (Gallery.LayoutParams) generateDefaultLayoutParams();
        }
        addViewInLayout(child, fromLeft ? -1 : 0, lp);
        child.setSelected(offset == 0);
        int childHeightSpec = ViewGroup.getChildMeasureSpec(mHeightMeasureSpec,
                0, lp.height);
        int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                0, lp.width);
        child.measure(childWidthSpec, childHeightSpec);
        int childLeft;
        int childRight;
        // Position vertically based on gravity setting
        int childTop = calculateTop(child, true);
        int childBottom = childTop
                + (mHorizontal ? child.getMeasuredHeight() : child
                        .getMeasuredWidth());
        int width = mHorizontal ? child.getMeasuredWidth() : child
                .getMeasuredHeight();
        if (fromLeft) {
            childLeft = x;
            childRight = childLeft + width;
        } else {
            childLeft = x - width;
            childRight = x;
        }
        if (mHorizontal) {
            child.layout(childLeft, childTop, childRight, childBottom);
        } else {
            child.layout(childTop, childLeft, childBottom, childRight);
        }
    }

    /**
     * Figure out vertical placement based on mGravity
     *
     * @param child
     *            Child to place
     * @return Where the top of the child should be
     */
    private int calculateTop(View child, boolean duringLayout) {
        int myHeight = mHorizontal ? (duringLayout ? getMeasuredHeight()
                : getHeight()) : (duringLayout ? getMeasuredWidth()
                : getWidth());
        int childHeight = mHorizontal ? (duringLayout ? child
                .getMeasuredHeight() : child.getHeight())
                : (duringLayout ? child.getMeasuredWidth() : child.getWidth());
        int childTop = 0;
        switch (mGravity) {
        case Gravity.TOP:
        case Gravity.LEFT:
            childTop = 0;
            break;
        case Gravity.CENTER_VERTICAL:
        case Gravity.CENTER_HORIZONTAL:
            int availableSpace = myHeight - childHeight;
            childTop = availableSpace / 2;
            break;
        case Gravity.BOTTOM:
        case Gravity.RIGHT:
            childTop = myHeight - childHeight;
            break;
        }
        return childTop;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * Shortcut the most recurring case: the user is in the dragging state
         * and he is moving his finger. We want to intercept this motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }
        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_MOVE: {
            /*
             * mIsBeingDragged == false, otherwise the shortcut would have
             * caught it. Check whether the user has moved far enough from his
             * original down touch.
             */
            final int activePointerId = mActivePointerId;
            if (activePointerId == INVALID_POINTER) {
                // If we don't have a valid id, the touch down wasn't on
                // content.
                break;
            }
            final int pointerIndex = ev.findPointerIndex(activePointerId);
            final float coord = mHorizontal ? ev.getX(pointerIndex) : ev
                    .getY(pointerIndex);
            final int diff = (int) Math.abs(coord - mLastMotionCoord);
            if (diff > mTouchSlop) {
                mIsBeingDragged = true;
                mLastMotionCoord = coord;
                if (mParent != null)
                    mParent.requestDisallowInterceptTouchEvent(true);
            }
            break;
        }
        case MotionEvent.ACTION_DOWN: {
            final float coord = mHorizontal ? ev.getX() : ev.getY();
            /*
             * Remember location of down touch. ACTION_DOWN always refers to
             * pointer index 0.
             */
            mLastMotionCoord = coord;
            mActivePointerId = ev.getPointerId(0);
            /*
             * If being flinged and user touches the screen, initiate drag;
             * otherwise don't. mScroller.isFinished should be false when being
             * flinged.
             */
            mIsBeingDragged = !mFlingRunnable.mScroller.isFinished();
            mGestureDetector.onTouchEvent(ev);
            break;
        }
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
            /* Release the drag */
            mIsBeingDragged = false;
            mActivePointerId = INVALID_POINTER;
            break;
        case MotionEvent.ACTION_POINTER_DOWN: {
            final int index = ev.getActionIndex();
            mLastMotionCoord = mHorizontal ? ev.getX(index) : ev.getY(index);
            mActivePointerId = ev.getPointerId(index);
            break;
        }
        case MotionEvent.ACTION_POINTER_UP:
            mLastMotionCoord = ev.getX(ev.findPointerIndex(mActivePointerId));
            break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Give everything to the gesture detector
        boolean retValue = mGestureDetector.onTouchEvent(event);
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
            // Helper method for lifted finger
            onUp();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            onCancel();
        }
        return retValue;
    }

    public boolean onSingleTapUp(MotionEvent e) {
        if (mDownTouchPosition >= 0) {
            // An item tap should make it selected, so scroll to this child.
            scrollToChild(mDownTouchPosition - mFirstPosition);
            if (mShouldCallbackOnUnselectedItemClick
                    || mDownTouchPosition == mSelectedPosition) {
                performItemClick(mDownTouchView, mDownTouchPosition,
                        mAdapter.getItemId(mDownTouchPosition));
            }
            return true;
        }
        return false;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        if (!mShouldCallbackDuringFling) {
            removeCallbacks(mDisableSuppressSelectionChangedRunnable);
            if (!mSuppressSelectionChanged)
                mSuppressSelectionChanged = true;
        }
        mFlingRunnable.startUsingVelocity(mHorizontal ? (int) -velocityX
                : (int) -velocityY);
        return true;
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
        if (localLOGV)
            Log.v(TAG, String.valueOf(e2.getX() - e1.getX()));
        mParent.requestDisallowInterceptTouchEvent(true);
        if (!mShouldCallbackDuringFling) {
            if (mIsFirstScroll) {
                if (!mSuppressSelectionChanged)
                    mSuppressSelectionChanged = true;
                postDelayed(mDisableSuppressSelectionChangedRunnable,
                        SCROLL_TO_FLING_UNCERTAINTY_TIMEOUT);
            }
        } else {
            if (mSuppressSelectionChanged)
                mSuppressSelectionChanged = false;
        }
        trackMotionScroll(mHorizontal ? -1 * (int) distanceX : -1
                * (int) distanceY);

        mIsFirstScroll = false;
        return true;
    }

    public boolean onDown(MotionEvent e) {
        mFlingRunnable.stop(false);
        mDownTouchPosition = pointToPosition((int) e.getX(), (int) e.getY());
        if (mDownTouchPosition >= 0) {
            mDownTouchView = getChildAt(mDownTouchPosition - mFirstPosition);
            mDownTouchView.setPressed(true);
        }
        // Reset the multiple-scroll tracking state
        mIsFirstScroll = true;
        // Must return true to get matching events for this down event.
        return true;
    }

    /**
     * Called when a touch event's action is MotionEvent.ACTION_UP.
     */
    void onUp() {
        if (mFlingRunnable.mScroller.isFinished()) {
            scrollIntoSlots();
        }
        dispatchUnpress();
    }

    /**
     * Called when a touch event's action is MotionEvent.ACTION_CANCEL.
     */
    void onCancel() {
        onUp();
    }

    public void onLongPress(MotionEvent e) {
    }

    public void onShowPress(MotionEvent e) {
    }

    private void dispatchPress(View child) {
        if (child != null) {
            child.setPressed(true);
        }
        setPressed(true);
    }

    private void dispatchUnpress() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            getChildAt(i).setPressed(false);
        }
        setPressed(false);
    }

    @Override
    public void dispatchSetSelected(boolean selected) {
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        if (mSelectedChild != null) {
            mSelectedChild.setPressed(pressed);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return event.dispatch(this, null, null);
    }

    /**
     * Handles left, right, and clicking
     *
     * @see android.view.View#onKeyDown
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {

        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (movePrevious()) {
                playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
            }
            return true;

        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if (moveNext()) {
                playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
            }
            return true;

        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER:
            mReceivedInvokeKeyDown = true;
            // fallthrough to default handling
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER: {

            if (mReceivedInvokeKeyDown) {
                if (mItemCount > 0) {

                    dispatchPress(mSelectedChild);
                    postDelayed(new Runnable() {
                        public void run() {
                            dispatchUnpress();
                        }
                    }, ViewConfiguration.getPressedStateDuration());

                    int selectedIndex = mSelectedPosition - mFirstPosition;
                    performItemClick(getChildAt(selectedIndex),
                            mSelectedPosition,
                            mAdapter.getItemId(mSelectedPosition));
                }
            }

            // Clear the flag
            mReceivedInvokeKeyDown = false;

            return true;
        }
        }

        return super.onKeyUp(keyCode, event);
    }

    private void performItemClick(View childAt, int mSelectedPosition2,
            long itemId) {
    }

    boolean movePrevious() {
        if (mItemCount > 0 && mSelectedPosition > 0) {
            scrollToChild(mSelectedPosition - mFirstPosition - 1);
            return true;
        } else {
            return false;
        }
    }

    boolean moveNext() {
        if (mItemCount > 0 && mSelectedPosition < mItemCount - 1) {
            scrollToChild(mSelectedPosition - mFirstPosition + 1);
            return true;
        } else {
            return false;
        }
    }

    private boolean scrollToChild(int childPosition) {
        View child = getChildAt(childPosition);
        if (child != null) {
            int distance = getCenterOfGallery() - getCenterOfView(child);
            mFlingRunnable.startUsingDistance(distance);
            return true;
        }
        return false;
    }

    protected void setSelectedPositionInt(int position) {
        mSelectedPosition = position;
        updateSelectedItemMetadata();
    }

    void checkSelectionChanged() {
        if (mSelectedPosition != mOldSelectedPosition) {
            selectionChanged();
            mOldSelectedPosition = mSelectedPosition;
        }
    }

    private class SelectionNotifier implements Runnable {
        public void run() {
            if (mDataChanged) {
                // Data has changed between when this SelectionNotifier
                // was posted and now. We need to wait until the AdapterView
                // has been synched to the new data.
                if (mAdapter != null) {
                    post(this);
                }
            } else {
                fireOnSelected();
            }
        }
    }

    void selectionChanged() {
        if (mSuppressSelectionChanged)
            return;
        if (mOnItemSelectedListener != null) {
            if (mInLayout || mBlockLayoutRequests) {
                // If we are in a layout traversal, defer notification
                if (mSelectionNotifier == null) {
                    mSelectionNotifier = new SelectionNotifier();
                }
                post(mSelectionNotifier);
            } else {
                fireOnSelected();
            }
        }

        // we fire selection events here not in View
        // if (mSelectedPosition != ListView.INVALID_POSITION && isShown() &&
        // !isInTouchMode()) {
        // sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        // }
    }

    private void fireOnSelected() {
        if (mOnItemSelectedListener == null)
            return;

        int selection = this.getSelectedItemPosition();
        if (selection >= 0) {
            View v = getSelectedView();
            mOnItemSelectedListener.onItemSelected(this, v, selection,
                    mAdapter.getItemId(selection));
        }
    }

    public int getSelectedItemPosition() {
        return mSelectedPosition;
    }

    public View getSelectedView() {
        if (mItemCount > 0 && mSelectedPosition >= 0) {
            return getChildAt(mSelectedPosition - mFirstPosition);
        } else {
            return null;
        }
    }

    private void updateSelectedItemMetadata() {
        View oldSelectedChild = mSelectedChild;
        View child = mSelectedChild = getChildAt(mSelectedPosition
                - mFirstPosition);
        if (child == null) {
            return;
        }
        child.setSelected(true);
        child.setFocusable(true);

        if (hasFocus()) {
            child.requestFocus();
        }
        // We unfocus the old child down here so the above hasFocus check
        // returns true
        if (oldSelectedChild != null && oldSelectedChild != child) {
            // Make sure its drawable state doesn't contain 'selected'
            oldSelectedChild.setSelected(false);
            // Make sure it is not focusable anymore, since otherwise arrow keys
            // can make this one be focused
            oldSelectedChild.setFocusable(false);
        }
    }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            mGravity = gravity;
            requestLayout();
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        int selectedIndex = mSelectedPosition - mFirstPosition;
        // Just to be safe
        if (selectedIndex < 0)
            return i;
        if (i == childCount - 1) {
            // Draw the selected child last
            return selectedIndex;
        } else if (i >= selectedIndex) {
            // Move the children to the right of the selected child earlier one
            return i + 1;
        } else {
            // Keep the children to the left of the selected child the same
            return i;
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction,
            Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        /*
         * The gallery shows focus by focusing the selected item. So, give focus
         * to our selected item instead. We steal keys from our selected item
         * elsewhere.
         */
        if (gainFocus && mSelectedChild != null) {
            mSelectedChild.requestFocus(direction);
            mSelectedChild.setSelected(true);
        }
    }

    void setNextSelectedPositionInt(int position) {
        mSelectedPosition = position;
    }

    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return mFirstPosition + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    private class FlingRunnable implements Runnable {
        private Scroller mScroller;

        /**
         * X value reported by mScroller on the previous fling
         */
        private int mLastFlingX;

        public FlingRunnable() {
            mScroller = new Scroller(getContext());
        }

        private void startCommon() {
            // Remove any pending flings
            removeCallbacks(this);
        }

        public void startUsingVelocity(int initialVelocity) {
            if (initialVelocity == 0)
                return;
            startCommon();
            int initialX = initialVelocity < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingX = initialX;
            mScroller.fling(initialX, 0, initialVelocity, 0, 0,
                    Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            post(this);
        }

        public void startUsingDistance(int distance) {
            if (distance == 0)
                return;
            startCommon();
            mLastFlingX = 0;
            mScroller.startScroll(0, 0, -distance, 0, mAnimationDuration);
            post(this);
        }

        public void stop(boolean scrollIntoSlots) {
            removeCallbacks(this);
            endFling(scrollIntoSlots);
        }

        private void endFling(boolean scrollIntoSlots) {
            mScroller.forceFinished(true);
            if (scrollIntoSlots)
                scrollIntoSlots();
        }

        public void run() {
            if (mItemCount == 0) {
                endFling(true);
                return;
            }
            mShouldStopFling = false;
            final Scroller scroller = mScroller;
            boolean more = scroller.computeScrollOffset();
            final int x = scroller.getCurrX();
            // Flip sign to convert finger direction to list items direction
            // (e.g. finger moving down means list is moving towards the top)
            int delta = mLastFlingX - x;
            // Pretend that each frame of a fling scroll is a touch scroll
            if (delta > 0) {
                // Moving towards the left. Use first view as mDownTouchPosition
                mDownTouchPosition = mFirstPosition;
                // Don't fling more than 1 screen
                delta = mHorizontal ? Math.min(getWidth() - mPaddingLeft
                        - mPaddingRight - 1, delta) : Math.min(getHeight()
                        - mPaddingTop - mPaddingBottom - 1, delta);
            } else {
                // Moving towards the right. Use last view as mDownTouchPosition
                int offsetToLast = getChildCount() - 1;
                mDownTouchPosition = mFirstPosition + offsetToLast;
                // Don't fling more than 1 screen
                delta = mHorizontal ? Math.max(-(getWidth() - mPaddingRight
                        - mPaddingLeft - 1), delta) : Math.max(-(getHeight()
                        - mPaddingBottom - mPaddingTop - 1), delta);
            }
            trackMotionScroll(delta);
            if (more && !mShouldStopFling) {
                mLastFlingX = x;
                post(this);
            } else {
                endFling(true);
            }
        }
    }

    /**
     * Gallery extends LayoutParams to provide a place to hold current
     * Transformation information along with previous position/transformation
     * info.
     *
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    class RecycleBin {
        private final SparseArray<View> mScrapHeap = new SparseArray<View>();

        public void put(int position, View v) {
            mScrapHeap.put(position, v);
        }

        View get(int position) {
            // System.out.print("Looking for " + position);
            View result = mScrapHeap.get(position);
            if (result != null) {
                // System.out.println(" HIT");
                mScrapHeap.delete(position);
            } else {
                // System.out.println(" MISS");
            }
            return result;
        }

        void clear() {
            final SparseArray<View> scrapHeap = mScrapHeap;
            final int count = scrapHeap.size();
            for (int i = 0; i < count; i++) {
                final View view = scrapHeap.valueAt(i);
                if (view != null) {
                    removeDetachedView(view, true);
                }
            }
            scrapHeap.clear();
        }
    }

}
