/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starmark.lib.widget.map;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import java.util.ArrayList;

/**
 * <p/>
 * This view isn't all that useful on its own; rather it serves as an example of how to correctly
 * implement these types of gestures to perform zooming and scrolling with interesting content
 * types.
 * <p/>
 * The view is interactive in that it can be zoomed and panned using
 * typical <a href="http://developer.android.com/design/patterns/gestures.html">gestures</a> such
 * as double-touch, drag, pinch-open, and pinch-close. This is done using the
 * {@link ScaleGestureDetector}, {@link GestureDetector}, and {@link OverScroller} classes. Note
 * that the platform-provided view scrolling behavior (e.g. {@link View#scrollBy(int, int)} is NOT
 * used.
 * <p/>
 * The view also demonstrates the correct use of
 * <a href="http://developer.android.com/design/style/touch-feedback.html">touch feedback</a> to
 * indicate to users that they've reached the content edges after a pan or fling gesture. This
 * is done using the {@link EdgeEffectCompat} class.
 * <p/>
 * Finally, this class demonstrates the basics of creating a custom view, including support for
 * custom attributes (see the constructors), a simple implementation for
 * {@link #onMeasure(int, int)}, an implementation for {@link #onSaveInstanceState()} and a fairly
 * straightforward {@link Canvas}-based rendering implementation in
 * {@link #onDraw(android.graphics.Canvas)}.
 * <p/>
 * Note that this view doesn't automatically support directional navigation or other accessibility
 * methods. Activities using this view should generally provide alternate navigation controls.
 * Activities using this view should also present an alternate, text-based representation of this
 * view's content for vision-impaired users.
 */
public class IndoorMapView extends View {
    private static final String TAG = "IndoorMapView";

    /**
     * Initial fling velocity for pan operations, in screen widths (or heights) per second.
     *
     * @see #panLeft()
     * @see #panRight()
     * @see #panUp()
     * @see #panDown()
     */
    private static final float PAN_VELOCITY_FACTOR = 2f;

    /**
     * The scaling factor for a single zoom 'step'.
     *
     * @see #zoomIn()
     * @see #zoomOut()
     */
    private static final float ZOOM_AMOUNT = 0.25f;

    // Viewport extremes. See mCurrentViewport for a discussion of the viewport.
    private static final float AXIS_X_MIN = -1f;
    private static final float AXIS_X_MAX = 1f;
    private static final float AXIS_Y_MIN = -1f;
    private static final float AXIS_Y_MAX = 1f;

    /**
     * The current viewport. This rectangle represents the currently visible chart domain
     * and range. The currently visible chart X values are from this rectangle's left to its right.
     * The currently visible chart Y values are from this rectangle's top to its bottom.
     * <p/>
     * Note that this rectangle's top is actually the smaller Y value, and its bottom is the larger
     * Y value. Since the chart is drawn onscreen in such a way that chart Y values increase
     * towards the top of the screen (decreasing pixel Y positions), this rectangle's "top" is drawn
     * above this rectangle's "bottom" value.
     *
     * @see #mContentRect
     */
    private RectF mCurrentViewport = new RectF(AXIS_X_MIN, AXIS_Y_MIN, AXIS_X_MAX, AXIS_Y_MAX);

    /**
     * The current destination rectangle (in pixel coordinates) into which the chart data should
     * be drawn. Chart labels are drawn outside this area.
     *
     * @see #mCurrentViewport
     */
    private Rect mContentRect = new Rect();

    // State objects and values related to gesture tracking.
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mGestureDetector;
    private OverScroller mScroller;
    private Zoomer mZoomer;
    private PointF mZoomFocalPoint = new PointF();
    private RectF mScrollerStartViewport = new RectF(); // Used only for zooms and flings.

    // Edge effect / overscroll tracking objects.
    private EdgeEffectCompat mEdgeEffectTop;
    private EdgeEffectCompat mEdgeEffectBottom;
    private EdgeEffectCompat mEdgeEffectLeft;
    private EdgeEffectCompat mEdgeEffectRight;

    private boolean mEdgeEffectTopActive;
    private boolean mEdgeEffectBottomActive;
    private boolean mEdgeEffectLeftActive;
    private boolean mEdgeEffectRightActive;

    private Point mSurfaceSizeBuffer = new Point();

    private ArrayList<Integer> mMapImgResources = new ArrayList<>();
    private Drawable mCurrentMapDrawable;


    public IndoorMapView(Context context) {
        this(context, null, 0);
    }

    public IndoorMapView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IndoorMapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.IndoorMapView, defStyle, defStyle);

        try {
//            mLabelTextColor = a.getColor(
//                    R.styleable.IndoorMapView_labelTextColor, mLabelTextColor);
//            mLabelTextSize = a.getDimension(
//                    R.styleable.IndoorMapView_labelTextSize, mLabelTextSize);
//            mLabelSeparation = a.getDimensionPixelSize(
//                    R.styleable.IndoorMapView_labelSeparation, mLabelSeparation);
//
//            mGridThickness = a.getDimension(
//                    R.styleable.IndoorMapView_gridThickness, mGridThickness);
//            mGridColor = a.getColor(
//                    R.styleable.IndoorMapView_gridColor, mGridColor);
//
//            mAxisThickness = a.getDimension(
//                    R.styleable.IndoorMapView_axisThickness, mAxisThickness);
//            mAxisColor = a.getColor(
//                    R.styleable.IndoorMapView_axisColor, mAxisColor);
//
//            mDataThickness = a.getDimension(
//                    R.styleable.IndoorMapView_dataThickness, mDataThickness);
//            mDataColor = a.getColor(
//                    R.styleable.IndoorMapView_dataColor, mDataColor);
            int map_array_id = a.getResourceId(R.styleable.IndoorMapView_maps, -1);
            if(map_array_id != -1) {
                TypedArray maps = getResources().obtainTypedArray(map_array_id);
                int length = maps.length();

                for (int i = 0; i < length; i++) {
                    mMapImgResources.add(maps.getResourceId(i, -1));
                }
                if (mMapImgResources.size() > 0) {
                    int resid = mMapImgResources.get(0);
                    if (resid > 0) {
                        mCurrentMapDrawable = getResources().getDrawable(resid);
                        mCurrentMapDrawable.setBounds(
                                0,
                                0,
                                mCurrentMapDrawable.getIntrinsicWidth(),
                                mCurrentMapDrawable.getIntrinsicHeight());
                    }
                }
                maps.recycle();
            }
        } finally {

            a.recycle();
        }

        initPaints();

        // Sets up interactions
        mScaleGestureDetector = new ScaleGestureDetector(context, mScaleGestureListener);
        mGestureDetector = new GestureDetectorCompat(context, mGestureListener);

        mScroller = new OverScroller(context);
        mZoomer = new Zoomer(context);

        // Sets up edge effects
        mEdgeEffectLeft = new EdgeEffectCompat(context);
        mEdgeEffectTop = new EdgeEffectCompat(context);
        mEdgeEffectRight = new EdgeEffectCompat(context);
        mEdgeEffectBottom = new EdgeEffectCompat(context);
    }

    /**
     * (Re)initializes {@link Paint} objects based on current attribute values.
     */
    private void initPaints() {
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
//        mContentRect.set(
//                getPaddingLeft(),
//                getPaddingTop(),
//                getWidth() - getPaddingRight(),
//                getHeight() - getPaddingBottom());
        mContentRect.set(
                0, 0, mCurrentMapDrawable.getIntrinsicWidth(), mCurrentMapDrawable.getIntrinsicHeight()
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                Math.max(getSuggestedMinimumWidth(),
                        resolveSize(getPaddingLeft() + getPaddingRight(),
                                widthMeasureSpec)),
                Math.max(getSuggestedMinimumHeight(),
                        resolveSize(getPaddingTop() + getPaddingBottom(),
                                heightMeasureSpec)));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and objects related to drawing
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Clips the next few drawing operations to the content area
        int clipRestoreCount = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        drawMap(canvas);
        drawEdgeEffectsUnclipped(canvas);

        // Removes clipping rectangle
        canvas.restoreToCount(clipRestoreCount);
    }

    private void drawMap(Canvas canvas) {
        int restoreToCount = canvas.save();
        canvas.drawColor(Color.RED);
        if(mCurrentMapDrawable != null) {
            Rect bound = calcBounds();
            float hw = bound.width() / 2f;
            mCurrentMapDrawable.setBounds(bound);
            mCurrentMapDrawable.draw(canvas);
        }
        canvas.restoreToCount(restoreToCount);
    }

    Rect mRectBuffer = new Rect();
    private Rect calcBounds() {
        mRectBuffer = new Rect();
        float w = mCurrentMapDrawable.getIntrinsicWidth();
        float h = mCurrentMapDrawable.getIntrinsicHeight();

        float viewpx = 1 / (mCurrentViewport.width() / 2f);
        float viewpy = 1 / (mCurrentViewport.height() / 2f);
        float crx = (float)getWidth() / w;
        float cry = (float)getHeight() / h;
        float minRatio = Math.min(crx, cry);

        float bw = mContentRect.width() * minRatio;
        float bh = mContentRect.height() * minRatio;

        mRectBuffer.set(
                0,
                0,
                (int)(bw * viewpx),
                (int)(bh * viewpy)
        );

        Log.d(TAG, "viewpx,viewpy:" + viewpx + "," + viewpy);
        Log.d(TAG, "crx,cry:" + crx + "," + cry);
        Log.d(TAG, "bw:" + bw + ",bh:" + bh);
        Log.d(TAG, mCurrentViewport.toShortString());
        Log.d(TAG, mRectBuffer.toShortString());
        return mRectBuffer;
    }

    /**
     * Draws the overscroll "glow" at the four edges of the chart region, if necessary. The edges
     * of the chart region are stored in {@link #mContentRect}.
     *
     * @see EdgeEffectCompat
     */
    private void drawEdgeEffectsUnclipped(Canvas canvas) {
        // The methods below rotate and translate the canvas as needed before drawing the glow,
        // since EdgeEffectCompat always draws a top-glow at 0,0.

        boolean needsInvalidate = false;

        if (!mEdgeEffectTop.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(getLeft(), getTop());
            mEdgeEffectTop.setSize(getWidth(), getHeight());
            if (mEdgeEffectTop.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectBottom.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(2 * getLeft() - getWidth(), getHeight());
            canvas.rotate(180, getWidth(), 0);
            mEdgeEffectBottom.setSize(getWidth(), getHeight());
            if (mEdgeEffectBottom.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectLeft.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(getLeft(), getHeight());
            canvas.rotate(-90, 0, 0);
            mEdgeEffectLeft.setSize(getHeight(), getWidth());
            if (mEdgeEffectLeft.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectRight.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(getWidth(), getTop());
            canvas.rotate(90, 0, 0);
            mEdgeEffectRight.setSize(getHeight(), getWidth());
            if (mEdgeEffectRight.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and objects related to gesture handling
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Finds the chart point (i.e. within the chart's domain and range) represented by the
     * given pixel coordinates, if that pixel is within the chart region described by
     * {@link #mContentRect}. If the point is found, the "dest" argument is set to the point and
     * this function returns true. Otherwise, this function returns false and "dest" is unchanged.
     */
    private boolean hitTest(float x, float y, PointF dest) {
        if (!mContentRect.contains((int) x, (int) y)) {
            return false;
        }

        dest.set(
                mCurrentViewport.left
                        + mCurrentViewport.width()
                        * (x - mContentRect.left) / mContentRect.width(),
                mCurrentViewport.top
                        + mCurrentViewport.height()
                        * (y - mContentRect.bottom) / -mContentRect.height());
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = mScaleGestureDetector.onTouchEvent(event);
        retVal = mGestureDetector.onTouchEvent(event) || retVal;
        return retVal || super.onTouchEvent(event);
    }

    /**
     * The scale listener, used for handling multi-finger scale gestures.
     */
    private final ScaleGestureDetector.OnScaleGestureListener mScaleGestureListener
            = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        /**
         * This is the active focal point in terms of the viewport. Could be a local
         * variable but kept here to minimize per-frame allocations.
         */
        private PointF viewportFocus = new PointF();
        private float lastSpanX;
        private float lastSpanY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            lastSpanX = ScaleGestureDetectorCompat.getCurrentSpan(scaleGestureDetector);
            lastSpanY = ScaleGestureDetectorCompat.getCurrentSpan(scaleGestureDetector);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            float spanX = ScaleGestureDetectorCompat.getCurrentSpan(scaleGestureDetector);
            float spanY = ScaleGestureDetectorCompat.getCurrentSpan(scaleGestureDetector);

            float newWidth = lastSpanX / spanX * mCurrentViewport.width();
            float newHeight = lastSpanY / spanY * mCurrentViewport.height();

            float focusX = scaleGestureDetector.getFocusX();
            float focusY = scaleGestureDetector.getFocusY();
            hitTest(focusX, focusY, viewportFocus);

            mCurrentViewport.set(
                    viewportFocus.x
                            - newWidth * (focusX - mContentRect.left)
                            / mContentRect.width(),
                    viewportFocus.y
                            - newHeight * (mContentRect.bottom - focusY)
                            / mContentRect.height(),
                    0,
                    0);
            mCurrentViewport.right = mCurrentViewport.left + newWidth;
            mCurrentViewport.bottom = mCurrentViewport.top + newHeight;
            constrainViewport();
            ViewCompat.postInvalidateOnAnimation(IndoorMapView.this);

            lastSpanX = spanX;
            lastSpanY = spanY;
            return true;
        }
    };

    /**
     * Ensures that current viewport is inside the viewport extremes defined by {@link #AXIS_X_MIN},
     * {@link #AXIS_X_MAX}, {@link #AXIS_Y_MIN} and {@link #AXIS_Y_MAX}.
     */
    private void constrainViewport() {
        mCurrentViewport.left = Math.max(AXIS_X_MIN, mCurrentViewport.left);
        mCurrentViewport.top = Math.max(AXIS_Y_MIN, mCurrentViewport.top);
        mCurrentViewport.bottom = Math.max(Math.nextUp(mCurrentViewport.top),
                Math.min(AXIS_Y_MAX, mCurrentViewport.bottom));
        mCurrentViewport.right = Math.max(Math.nextUp(mCurrentViewport.left),
                Math.min(AXIS_X_MAX, mCurrentViewport.right));
    }

    /**
     * The gesture listener, used for handling simple gestures such as double touches, scrolls,
     * and flings.
     */
    private final GestureDetector.SimpleOnGestureListener mGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            releaseEdgeEffects();
            mScrollerStartViewport.set(mCurrentViewport);
            mScroller.forceFinished(true);
            ViewCompat.postInvalidateOnAnimation(IndoorMapView.this);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mZoomer.forceFinished(true);
            if (hitTest(e.getX(), e.getY(), mZoomFocalPoint)) {
                mZoomer.startZoom(ZOOM_AMOUNT);
            }
            ViewCompat.postInvalidateOnAnimation(IndoorMapView.this);
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Scrolling uses math based on the viewport (as opposed to math using pixels).
            /**
             * Pixel offset is the offset in screen pixels, while viewport offset is the
             * offset within the current viewport. For additional information on surface sizes
             * and pixel offsets, see the docs for {@link computeScrollSurfaceSize()}. For
             * additional information about the viewport, see the comments for
             * {@link mCurrentViewport}.
             */
            float viewportOffsetX = distanceX * mCurrentViewport.width() / mContentRect.width();
            float viewportOffsetY = -distanceY * mCurrentViewport.height() / mContentRect.height();
            computeScrollSurfaceSize(mSurfaceSizeBuffer);
            int scrolledX = (int) (mSurfaceSizeBuffer.x
                    * (mCurrentViewport.left + viewportOffsetX - AXIS_X_MIN)
                    / (AXIS_X_MAX - AXIS_X_MIN));
            int scrolledY = (int) (mSurfaceSizeBuffer.y
                    * (AXIS_Y_MAX - mCurrentViewport.bottom - viewportOffsetY)
                    / (AXIS_Y_MAX - AXIS_Y_MIN));
            boolean canScrollX = mCurrentViewport.left > AXIS_X_MIN
                    || mCurrentViewport.right < AXIS_X_MAX;
            boolean canScrollY = mCurrentViewport.top > AXIS_Y_MIN
                    || mCurrentViewport.bottom < AXIS_Y_MAX;
            setViewportBottomLeft(
                    mCurrentViewport.left + viewportOffsetX,
                    mCurrentViewport.bottom + viewportOffsetY);

            if (canScrollX && scrolledX < 0) {
                mEdgeEffectLeft.onPull(scrolledX / (float) mContentRect.width());
                mEdgeEffectLeftActive = true;
            }
            if (canScrollY && scrolledY < 0) {
                mEdgeEffectTop.onPull(scrolledY / (float) mContentRect.height());
                mEdgeEffectTopActive = true;
            }
            if (canScrollX && scrolledX > mSurfaceSizeBuffer.x - mContentRect.width()) {
                mEdgeEffectRight.onPull((scrolledX - mSurfaceSizeBuffer.x + mContentRect.width())
                        / (float) mContentRect.width());
                mEdgeEffectRightActive = true;
            }
            if (canScrollY && scrolledY > mSurfaceSizeBuffer.y - mContentRect.height()) {
                mEdgeEffectBottom.onPull((scrolledY - mSurfaceSizeBuffer.y + mContentRect.height())
                        / (float) mContentRect.height());
                mEdgeEffectBottomActive = true;
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            fling((int) -velocityX, (int) -velocityY);
            return true;
        }
    };

    private void releaseEdgeEffects() {
        mEdgeEffectLeftActive
                = mEdgeEffectTopActive
                = mEdgeEffectRightActive
                = mEdgeEffectBottomActive
                = false;
        mEdgeEffectLeft.onRelease();
        mEdgeEffectTop.onRelease();
        mEdgeEffectRight.onRelease();
        mEdgeEffectBottom.onRelease();
    }

    private void fling(int velocityX, int velocityY) {
        releaseEdgeEffects();
        // Flings use math in pixels (as opposed to math based on the viewport).
        computeScrollSurfaceSize(mSurfaceSizeBuffer);
        mScrollerStartViewport.set(mCurrentViewport);
        int startX = (int) (mSurfaceSizeBuffer.x * (mScrollerStartViewport.left - AXIS_X_MIN) / (
                AXIS_X_MAX - AXIS_X_MIN));
        int startY = (int) (mSurfaceSizeBuffer.y * (AXIS_Y_MAX - mScrollerStartViewport.bottom) / (
                AXIS_Y_MAX - AXIS_Y_MIN));
        mScroller.forceFinished(true);
        mScroller.fling(
                startX,
                startY,
                velocityX,
                velocityY,
                0, mSurfaceSizeBuffer.x - mContentRect.width(),
                0, mSurfaceSizeBuffer.y - mContentRect.height(),
                mContentRect.width() / 2,
                mContentRect.height() / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Computes the current scrollable surface size, in pixels. For example, if the entire chart
     * area is visible, this is simply the current size of {@link #mContentRect}. If the chart
     * is zoomed in 200% in both directions, the returned size will be twice as large horizontally
     * and vertically.
     */
    private void computeScrollSurfaceSize(Point out) {
        out.set(
                (int) (mContentRect.width() * (AXIS_X_MAX - AXIS_X_MIN)
                        / mCurrentViewport.width()),
                (int) (mContentRect.height() * (AXIS_Y_MAX - AXIS_Y_MIN)
                        / mCurrentViewport.height()));
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        boolean needsInvalidate = false;

        if (mScroller.computeScrollOffset()) {
            // The scroller isn't finished, meaning a fling or programmatic pan operation is
            // currently active.

            computeScrollSurfaceSize(mSurfaceSizeBuffer);
            int currX = mScroller.getCurrX();
            int currY = mScroller.getCurrY();

            boolean canScrollX = (mCurrentViewport.left > AXIS_X_MIN
                    || mCurrentViewport.right < AXIS_X_MAX);
            boolean canScrollY = (mCurrentViewport.top > AXIS_Y_MIN
                    || mCurrentViewport.bottom < AXIS_Y_MAX);

            if (canScrollX
                    && currX < 0
                    && mEdgeEffectLeft.isFinished()
                    && !mEdgeEffectLeftActive) {
                mEdgeEffectLeft.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectLeftActive = true;
                needsInvalidate = true;
            } else if (canScrollX
                    && currX > (mSurfaceSizeBuffer.x - mContentRect.width())
                    && mEdgeEffectRight.isFinished()
                    && !mEdgeEffectRightActive) {
                mEdgeEffectRight.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectRightActive = true;
                needsInvalidate = true;
            }

            if (canScrollY
                    && currY < 0
                    && mEdgeEffectTop.isFinished()
                    && !mEdgeEffectTopActive) {
                mEdgeEffectTop.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectTopActive = true;
                needsInvalidate = true;
            } else if (canScrollY
                    && currY > (mSurfaceSizeBuffer.y - mContentRect.height())
                    && mEdgeEffectBottom.isFinished()
                    && !mEdgeEffectBottomActive) {
                mEdgeEffectBottom.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectBottomActive = true;
                needsInvalidate = true;
            }

            float currXRange = AXIS_X_MIN + (AXIS_X_MAX - AXIS_X_MIN)
                    * currX / mSurfaceSizeBuffer.x;
            float currYRange = AXIS_Y_MAX - (AXIS_Y_MAX - AXIS_Y_MIN)
                    * currY / mSurfaceSizeBuffer.y;
            setViewportBottomLeft(currXRange, currYRange);
        }

        if (mZoomer.computeZoom()) {
            // Performs the zoom since a zoom is in progress (either programmatically or via
            // double-touch).
            float newWidth = (1f - mZoomer.getCurrZoom()) * mScrollerStartViewport.width();
            float newHeight = (1f - mZoomer.getCurrZoom()) * mScrollerStartViewport.height();
            float pointWithinViewportX = (mZoomFocalPoint.x - mScrollerStartViewport.left)
                    / mScrollerStartViewport.width();
            float pointWithinViewportY = (mZoomFocalPoint.y - mScrollerStartViewport.top)
                    / mScrollerStartViewport.height();

            mCurrentViewport.set(
                    mZoomFocalPoint.x - newWidth * pointWithinViewportX,
                    mZoomFocalPoint.y - newHeight * pointWithinViewportY,
                    mZoomFocalPoint.x + newWidth * (1 - pointWithinViewportX),
                    mZoomFocalPoint.y + newHeight * (1 - pointWithinViewportY));
            constrainViewport();
            needsInvalidate = true;
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Sets the current viewport (defined by {@link #mCurrentViewport}) to the given
     * X and Y positions. Note that the Y value represents the topmost pixel position, and thus
     * the bottom of the {@link #mCurrentViewport} rectangle. For more details on why top and
     * bottom are flipped, see {@link #mCurrentViewport}.
     */
    private void setViewportBottomLeft(float x, float y) {
        /**
         * Constrains within the scroll range. The scroll range is simply the viewport extremes
         * (AXIS_X_MAX, etc.) minus the viewport size. For example, if the extrema were 0 and 10,
         * and the viewport size was 2, the scroll range would be 0 to 8.
         */

        float curWidth = mCurrentViewport.width();
        float curHeight = mCurrentViewport.height();
        x = Math.max(AXIS_X_MIN, Math.min(x, AXIS_X_MAX - curWidth));
        y = Math.max(AXIS_Y_MIN + curHeight, Math.min(y, AXIS_Y_MAX));

        mCurrentViewport.set(x, y - curHeight, x + curWidth, y);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods for programmatically changing the viewport
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the current viewport (visible extremes for the chart domain and range.)
     */
    public RectF getCurrentViewport() {
        return new RectF(mCurrentViewport);
    }

    /**
     * Sets the chart's current viewport.
     *
     * @see #getCurrentViewport()
     */
    public void setCurrentViewport(RectF viewport) {
        mCurrentViewport = viewport;
        constrainViewport();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly zooms the chart in one step.
     */
    public void zoomIn() {
        mScrollerStartViewport.set(mCurrentViewport);
        mZoomer.forceFinished(true);
        mZoomer.startZoom(ZOOM_AMOUNT);
        mZoomFocalPoint.set(
                (mCurrentViewport.right + mCurrentViewport.left) / 2,
                (mCurrentViewport.bottom + mCurrentViewport.top) / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly zooms the chart out one step.
     */
    public void zoomOut() {
        mScrollerStartViewport.set(mCurrentViewport);
        mZoomer.forceFinished(true);
        mZoomer.startZoom(-ZOOM_AMOUNT);
        mZoomFocalPoint.set(
                (mCurrentViewport.right + mCurrentViewport.left) / 2,
                (mCurrentViewport.bottom + mCurrentViewport.top) / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly pans the chart left one step.
     */
    public void panLeft() {
        fling((int) (-PAN_VELOCITY_FACTOR * getWidth()), 0);
    }

    /**
     * Smoothly pans the chart right one step.
     */
    public void panRight() {
        fling((int) (PAN_VELOCITY_FACTOR * getWidth()), 0);
    }

    /**
     * Smoothly pans the chart up one step.
     */
    public void panUp() {
        fling(0, (int) (-PAN_VELOCITY_FACTOR * getHeight()));
    }

    /**
     * Smoothly pans the chart down one step.
     */
    public void panDown() {
        fling(0, (int) (PAN_VELOCITY_FACTOR * getHeight()));
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and classes related to view state persistence.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.viewport = mCurrentViewport;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mCurrentViewport = ss.viewport;
    }

    /**
     * Persistent state that is saved by InteractiveLineGraphView.
     */
    public static class SavedState extends BaseSavedState {
        private RectF viewport;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(viewport.left);
            out.writeFloat(viewport.top);
            out.writeFloat(viewport.right);
            out.writeFloat(viewport.bottom);
        }

        @Override
        public String toString() {
            return "IndoorMapView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " viewport=" + viewport.toString() + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        });

        SavedState(Parcel in) {
            super(in);
            viewport = new RectF(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        }
    }

}


//Unitity function
//    /**
//     * Rounds the given number to the given number of significant digits. Based on an answer on
//     * <a href="http://stackoverflow.com/questions/202302">Stack Overflow</a>.
//     */
//    private static float roundToOneSignificantFigure(double num) {
//        final float d = (float) Math.ceil((float) Math.log10(num < 0 ? -num : num));
//        final int power = 1 - (int) d;
//        final float magnitude = (float) Math.pow(10, power);
//        final long shifted = Math.round(num * magnitude);
//        return shifted / magnitude;
//    }
//
//private static final int POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000};
//
//    /**
//     * Formats a float value to the given number of decimals. Returns the length of the string.
//     * The string begins at out.length - [return value].
//     */
//    private static int formatFloat(final char[] out, float val, int digits) {
//        boolean negative = false;
//        if (val == 0) {
//            out[out.length - 1] = '0';
//            return 1;
//        }
//        if (val < 0) {
//            negative = true;
//            val = -val;
//        }
//        if (digits > POW10.length) {
//            digits = POW10.length - 1;
//        }
//        val *= POW10[digits];
//        long lval = Math.round(val);
//        int index = out.length - 1;
//        int charCount = 0;
//        while (lval != 0 || charCount < (digits + 1)) {
//            int digit = (int) (lval % 10);
//            lval = lval / 10;
//            out[index--] = (char) (digit + '0');
//            charCount++;
//            if (charCount == digits) {
//                out[index--] = '.';
//                charCount++;
//            }
//        }
//        if (negative) {
//            out[index--] = '-';
//            charCount++;
//        }
//        return charCount;
//    }