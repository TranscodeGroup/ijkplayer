package tv.danmaku.ijk.media.example.widget.media;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GestureHelper {
    private static final String TAG = "GestureHelper";

    public interface OnScaleChangedListener {
        void onScaleChanged(float scale);
    }

    interface OnSingleTapListener {
        boolean onSingleTap(MotionEvent ev);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(min, value), max);
    }

    private final ScaleGestureDetector mScaleGestureDetector;
    private final GestureDetector mGestureDetector;
    private final ViewGroup mParent;
    private @Nullable View mRenderView;
    private OnScaleChangedListener mOnScaleChangedListener;
    private GestureDetector.OnDoubleTapListener mOnDoubleTapListener;
    private final RectF mTempRectF = new RectF();
    private final Matrix mMatrix = new Matrix();
    private float mScale = 1.0f;
    private float mBeginScale = 1.0f;
    private boolean mZoomEnabled = true;
    private float mMinZoom = 1.0f;
    private float mMaxZoom = 8.0f;

    /*package*/ GestureHelper(ViewGroup parent, final OnSingleTapListener onSingleTapListener) {
        Context context = parent.getContext();
        mParent = parent;
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mBeginScale = mScale;
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mRenderView == null || !mZoomEnabled) {
                    return false;
                }
                float finalScale = clamp(mBeginScale * detector.getScaleFactor(), mMinZoom, mMaxZoom);
                float deltaScale = finalScale / mScale;

                if (mScale != finalScale) {
                    mScale = finalScale;
                    if (mOnScaleChangedListener != null) {
                        mOnScaleChangedListener.onScaleChanged(finalScale);
                    }
                }
                mMatrix.postScale(deltaScale, deltaScale, detector.getFocusX(), detector.getFocusY());
                applyMatrix(mRenderView);
                return false;
            }
        });
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (mRenderView != null && (mZoomEnabled || mOnDoubleTapListener != null)) {
                    return true;
                } else {
                    onSingleTapListener.onSingleTap(null);
                    return false;
                }
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mOnDoubleTapListener != null && mOnDoubleTapListener.onSingleTapConfirmed(e)) {
                    return true;
                }
                return onSingleTapListener.onSingleTap(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return mOnDoubleTapListener != null && mOnDoubleTapListener.onDoubleTap(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return mOnDoubleTapListener != null && mOnDoubleTapListener.onDoubleTapEvent(e);
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (mRenderView == null || !mZoomEnabled || e2.getPointerCount() != 1) {
                    return false;
                }
                mMatrix.postTranslate(-distanceX, -distanceY);
                applyMatrix(mRenderView);
                return true;
            }
        });
    }

    public void reset() {
        mMatrix.reset();
        mScale = 1.0f;
        if (mRenderView != null) {
            mRenderView.setAnimationMatrix(mMatrix);
        }
    }

    public void setZoomEnabled(boolean enabled) {
        mZoomEnabled = enabled;
    }

    public boolean isZoomEnabled() {
        return mZoomEnabled;
    }

    public void setMinZoom(float minZoom) {
        mMinZoom = minZoom;
    }

    public void setMaxZoom(float maxZoom) {
        mMaxZoom = maxZoom;
    }

    public void setOnScaleChangedListener(OnScaleChangedListener listener) {
        mOnScaleChangedListener = listener;
    }

    public void setOnDoubleTapListener(GestureDetector.OnDoubleTapListener listener) {
        mOnDoubleTapListener = listener;
    }

    /*package*/ boolean onTouch(MotionEvent event) {
        if (mRenderView == null) {
            return false;
        }
        boolean handled = mGestureDetector.onTouchEvent(event);
        boolean scaleHandled = false;
        if (mZoomEnabled) {
            scaleHandled = mScaleGestureDetector.onTouchEvent(event);
        }
        return scaleHandled || handled;
    }

    /*package*/ void setRenderView(final View renderView) {
        mRenderView = renderView;
        if (renderView != null) {
            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    renderView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    applyMatrix(renderView);
                }
            };
            renderView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }

    private void applyMatrix(@NonNull View view) {
        getMappedRectF(view, mTempRectF);
        float widthDiff = mParent.getWidth() - mTempRectF.width();
        float heightDiff = mParent.getHeight() - mTempRectF.height();
        float adjustedLeft =
                widthDiff > 0
                // when view is smaller than parent, make sure view is centered
                ? widthDiff / 2
                // when view is bigger than parent, make sure view is not outside parent
                : clamp(mTempRectF.left, widthDiff, 0);
        float adjustedTop =
                heightDiff > 0
                ? heightDiff / 2
                : clamp(mTempRectF.top, heightDiff, 0);
        mMatrix.postTranslate(adjustedLeft - mTempRectF.left, adjustedTop - mTempRectF.top);
        view.setAnimationMatrix(mMatrix);
    }

    /** @see View#getHitRect(Rect) */
    private void getMappedRectF(View view, RectF outRect) {
        outRect.set(0, 0, view.getWidth(), view.getHeight());
        mMatrix.mapRect(outRect);
        outRect.offset(view.getLeft(), view.getTop());
    }
}
