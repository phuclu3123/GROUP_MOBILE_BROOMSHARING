package vn.edu.ueh.phuclu.broomsharing.ui.admin;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

// Giữ ảnh vừa khung; cho phép chụm, kéo và chạm hai lần để đọc chi tiết minh chứng.
public class EvidenceImageView extends AppCompatImageView {
    private final Matrix transform = new Matrix();
    private final ScaleGestureDetector scaling;
    private final GestureDetector gestures;
    private float zoom = 1f, lastX, lastY;

    public EvidenceImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaling =
                new ScaleGestureDetector(
                        context,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {
                                float next =
                                        Math.max(
                                                1f, Math.min(6f, zoom * detector.getScaleFactor()));
                                transform.postScale(
                                        next / zoom,
                                        next / zoom,
                                        detector.getFocusX(),
                                        detector.getFocusY());
                                zoom = next;
                                constrain();
                                return true;
                            }
                        });
        gestures =
                new GestureDetector(
                        context,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onDown(MotionEvent event) {
                                return true;
                            }

                            @Override
                            public boolean onDoubleTap(MotionEvent event) {
                                if (zoom > 1f) resetZoom();
                                else {
                                    transform.postScale(2.5f, 2.5f, event.getX(), event.getY());
                                    zoom = 2.5f;
                                    constrain();
                                }
                                return true;
                            }

                            @Override
                            public boolean onSingleTapConfirmed(MotionEvent event) {
                                performClick();
                                return true;
                            }
                        });
    }

    // Tính lại tỉ lệ theo khung hiện tại; căn giữa mà không làm biến dạng ảnh.
    public void resetZoom() {
        Drawable image = getDrawable();
        if (image == null || getWidth() == 0 || getHeight() == 0) return;
        float factor =
                Math.min(
                        (float) getWidth() / image.getIntrinsicWidth(),
                        (float) getHeight() / image.getIntrinsicHeight());
        transform.setScale(factor, factor);
        transform.postTranslate(
                (getWidth() - image.getIntrinsicWidth() * factor) / 2,
                (getHeight() - image.getIntrinsicHeight() * factor) / 2);
        zoom = 1;
        setImageMatrix(transform);
    }

    // Không cho kéo ảnh ra khỏi khung; chiều nhỏ hơn khung luôn được căn giữa.
    private void constrain() {
        Drawable image = getDrawable();
        if (image == null) return;
        RectF bounds = new RectF(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
        transform.mapRect(bounds);
        float dx =
                bounds.width() <= getWidth()
                        ? (getWidth() - bounds.width()) / 2 - bounds.left
                        : bounds.left > 0
                                ? -bounds.left
                                : bounds.right < getWidth() ? getWidth() - bounds.right : 0;
        float dy =
                bounds.height() <= getHeight()
                        ? (getHeight() - bounds.height()) / 2 - bounds.top
                        : bounds.top > 0
                                ? -bounds.top
                                : bounds.bottom < getHeight() ? getHeight() - bounds.bottom : 0;
        transform.postTranslate(dx, dy);
        setImageMatrix(transform);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetZoom();
    }

    // Tách thao tác chụm nhiều ngón khỏi kéo một ngón để ảnh không giật khi bỏ ngón.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaling.onTouchEvent(event);
        gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                && event.getPointerCount() == 1
                && !scaling.isInProgress()) {
            if (zoom > 1f) {
                transform.postTranslate(event.getX() - lastX, event.getY() - lastY);
                constrain();
            }
            lastX = event.getX();
            lastY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            lastX = event.getX(remaining);
            lastY = event.getY(remaining);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
