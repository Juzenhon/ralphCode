
package com.test.www;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.widget.ImageView;

public class ClipImageView extends ImageView {

    private RectF mClipRect = new RectF();

    /** ������Ƭģʽ */
    private static final int MODE_DRAG = 1;

    /** �Ŵ���С��Ƭģʽ */
    private static final int MODE_ZOOM = 2;

    /** ��¼��������Ƭģʽ���ǷŴ���С��Ƭģʽ */
    private int mode = 0;// ��ʼ״̬

    /** ���ڼ�¼��ʼʱ�������λ�� */
    private PointF startPoint = new PointF();

    /** ���ڼ�¼����ͼƬ�ƶ�������λ�� */
    private Matrix matrix = new Matrix();

    /** ������ָ�Ŀ�ʼ���� */
    private float startDis;

    /** ������ָ���м�� */
    private PointF midPoint = new PointF();

    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RectF mSrcRect = new RectF();

    private RectF mCurrentRect = new RectF();

    private RectF mScaleSrcRect = new RectF();

    private float maxScale = 2;

    private float currentSmallScale;

    private float currentMaxScale;

    public ClipImageView(Context context) {
        this(context, null);
    }

    public ClipImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5);
        mPaint.setColor(Color.BLUE);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mClipRect.set(left, top + 150, right, bottom - 150);
        mSrcRect.set(getDrawable().getBounds());
        mCurrentRect.set(getDrawable().getBounds());
        midPoint.set(getWidth() / 2, getHeight() / 2);
    }

    public Bitmap clipBitmap() {
        buildDrawingCache();
        Bitmap bp = getDrawingCache();
        int left = (int)(mCurrentRect.left > mClipRect.left ? mCurrentRect.left : mClipRect.left);
        int right = (int)(mCurrentRect.right < mClipRect.right ? mCurrentRect.right
                : mClipRect.right);
        Bitmap bitmap = Bitmap.createBitmap(bp, left, (int)mClipRect.top, right - left,
                (int)mClipRect.height());
        destroyDrawingCache();
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(mClipRect, mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                mode = MODE_DRAG;
                // ��¼ImageView��ǰ���ƶ�λ��
                startPoint.set(event.getX(), event.getY());
                break;

            // ��ָ����Ļ���ƶ������¼��ᱻ���ϴ���
            case MotionEvent.ACTION_MOVE:
                // ����ͼƬ
                if (mode == MODE_DRAG) {

                    float dx = event.getX() - startPoint.x; // �õ�x����ƶ�����
                    float dy = event.getY() - startPoint.y; // �õ�x����ƶ�����
                    if (dx * dx + dy * dy > 3) {
                        startPoint.set(event.getX(), event.getY());

                        if (mCurrentRect.left <= mClipRect.left
                                && mCurrentRect.right >= mClipRect.right) {

                            if (mCurrentRect.left + dx > mClipRect.left) {
                                dx = mClipRect.left - mCurrentRect.left;
                            }

                            if (mCurrentRect.right + dx < mClipRect.right) {
                                dx = mClipRect.right - mCurrentRect.right;
                            }
                        } else if (mCurrentRect.left >= mClipRect.left
                                && mCurrentRect.right <= mClipRect.right) {
                            if (mCurrentRect.left + dx < mClipRect.left) {
                                dx = mClipRect.left - mCurrentRect.left;
                            }

                            if (mCurrentRect.right + dx > mClipRect.right) {
                                dx = mClipRect.right - mCurrentRect.right;
                            }
                        } else if (mCurrentRect.left > mClipRect.left
                                && mCurrentRect.right > mClipRect.right) {
                            if (dx > 0) {
                                dx = 0;
                            } else {
                                if (mCurrentRect.right + dx < mClipRect.right) {
                                    dx = mClipRect.right - mCurrentRect.right;
                                }
                            }
                        } else if (mCurrentRect.left < mClipRect.left
                                && mCurrentRect.right < mClipRect.right) {
                            if (dx < 0) {
                                dx = 0;
                            } else {
                                if (mCurrentRect.left + dx > mClipRect.left) {
                                    dx = mClipRect.left - mCurrentRect.left;
                                }
                            }
                        }

                        if (mCurrentRect.top + dy > mClipRect.top) {
                            dy = mClipRect.top - mCurrentRect.top;
                        }
                        if (mCurrentRect.bottom + dy < mClipRect.bottom) {
                            dy = mClipRect.bottom - mCurrentRect.bottom;
                        }

                        mCurrentRect.offset(dx, dy);
                        matrix.setRectToRect(mSrcRect, mCurrentRect, ScaleToFit.CENTER);
                        setImageMatrix(matrix);
                    }
                } else if (mode == MODE_ZOOM) {// �Ŵ���СͼƬ

                    float endDis = distance(event);// ��������
                    if (endDis > 10f) { // ������ָ��£��һ���ʱ�����ش���10
                        float scale = endDis / startDis;// �õ����ű���

                        if (scale > currentMaxScale) {
                            scale = currentMaxScale;
                        }
                        if (scale < currentSmallScale) {
                            scale = currentSmallScale;
                        }

                        float left = mScaleSrcRect.left - (midPoint.x - mScaleSrcRect.left)
                                * (scale - 1);
                        float right = mScaleSrcRect.right + (mScaleSrcRect.right - midPoint.x)
                                * (scale - 1);

                        float top = mScaleSrcRect.top - (midPoint.y - mScaleSrcRect.top)
                                * (scale - 1);
                        float bottom = mScaleSrcRect.bottom + (mScaleSrcRect.bottom - midPoint.y)
                                * (scale - 1);

                        if (top > mClipRect.top) {
                            bottom = bottom - top + mClipRect.top;
                            top = mClipRect.top;
                        }

                        if (bottom < mClipRect.bottom) {
                            top = top + mClipRect.bottom - bottom;
                            bottom = mClipRect.bottom;
                        }

                        mCurrentRect.set(left, top, right, bottom);
                        matrix.setRectToRect(mSrcRect, mCurrentRect, ScaleToFit.CENTER);

                        setImageMatrix(matrix);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:// ��ָ�뿪��Ļ
            case MotionEvent.ACTION_POINTER_UP:// �������뿪��Ļ��������Ļ�ϻ��д���(��ָ)
                mode = 0;
                break;
            case MotionEvent.ACTION_POINTER_DOWN: // ����Ļ���Ѿ��д���(��ָ)������һ������ѹ����Ļ
                mode = MODE_ZOOM;
                /** ����������ָ��ľ��� */
                startDis = distance(event);
                mScaleSrcRect.set(mCurrentRect);
                currentSmallScale = mClipRect.height() / mScaleSrcRect.height();
                currentMaxScale = maxScale * mSrcRect.height() / mScaleSrcRect.height();
                /** ����������ָ����м�� */
                // if (startDis > 10f) { // ������ָ��£��һ���ʱ�����ش���10
                // midPoint = mid(event);
                // }
                break;
        }
        return true;

    }

    /** ����������ָ��ľ��� */

    private float distance(MotionEvent event) {

        float dx = event.getX(1) - event.getX(0);

        float dy = event.getY(1) - event.getY(0);

        /** ʹ�ù��ɶ���������֮��ľ��� */

        return FloatMath.sqrt(dx * dx + dy * dy);

    }

}
