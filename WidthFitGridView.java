package com.ralph.code;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.List;

public class WidthFitGridView extends ViewGroup {

    public interface OnItemClickListener {
        void onItemClicked(int position, View v, ViewGroup parent);
    }

    private OnItemClickListener mOnItemClickListener;

    private OnClickListener clickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mOnItemClickListener != null) {
                int position = getIndexFromChildView(v);
                mOnItemClickListener.onItemClicked(position, v,
                        WidthFitGridView.this);
            }
        }
    };

    private int mItemGap = 21;

    private int mLineVerticalGap = 15;

    private List<LineItem> mLineInfoList = new ArrayList<LineItem>();

    public WidthFitGridView(Context context) {
        this(context, null);
    }

    public WidthFitGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        //setBackgroundColor(Color.RED);
        // mItemGap = ScreenUtil.dp2px(7);
        // mLineVerticalGap = ScreenUtil.dp2px(5);
        setPadding(mItemGap / 2, mLineVerticalGap / 2, 0, 0);
    }

    private int getIndexFromChildView(View child) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (child == getChildAt(i)) {
                return i;
            }
        }
        return -1;
    }

    private ListAdapter mAdapter;

    private MyObserver myObserver;

    public void setAdapter(ListAdapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(myObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            myObserver = new MyObserver();
            mAdapter.registerDataSetObserver(myObserver);
        }
        detachAllViewsFromParent();
        fillChildren();
    }

    private void fillChildren() {
        int childCount = mAdapter == null ? 0 : mAdapter.getCount();
        for (int i = 0; i < childCount; i++) {
            View convertView = getChildAt(i);
            if (convertView != null && convertView.getParent() != null) {
                detachViewFromParent(convertView);
            }
            View childView = mAdapter.getView(i, convertView, this);
            childView.setOnClickListener(clickListener);
            addViewInLayout(childView, i, generateDefaultLayoutParams(), true);
        }
        if (getChildCount() > childCount) {
            detachViewsFromParent(childCount, getChildCount() - childCount);
        }
        requestLayout();
        invalidate();
    }

    @Override
    public void addView(View child, int index) {
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int totalWidth = getDefaultSize(getSuggestedMinimumWidth(),
                widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int childHeight = getPaddingTop();

        int childMeasureSpec = MeasureSpec.makeMeasureSpec(
                LayoutParams.WRAP_CONTENT, MeasureSpec.AT_MOST);
        measureChildren(childMeasureSpec, childMeasureSpec);
        mLineInfoList.clear();
        int childCount = getChildCount();

        int width = totalWidth - getPaddingLeft() - getPaddingRight();
        int lineCount = 0;
        int currentLineLeft = getPaddingLeft();
        LineItem mLineInfo = null;
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            int childWidth = childView.getMeasuredWidth();
            int temp = 0;
            do {
                if (lineCount == 0) {
                    mLineInfo = new LineItem();
                    mLineInfoList.add(mLineInfo);
                    mLineInfo.startIndex = i;
                    temp = currentLineLeft + childWidth;
                } else {
                    temp = currentLineLeft + mItemGap + childWidth;
                }
                if (temp <= width) {
                    currentLineLeft = temp;
                    lineCount++;
                    mLineInfo.childCount = lineCount;
                    mLineInfo.lineHeight = Math.max(mLineInfo.lineHeight,
                            childView.getMeasuredHeight());
                    if(i == childCount - 1){
                        childHeight += mLineInfo.lineHeight;
                    }
                } else if (lineCount == 0) {
                    mLineInfo.childCount = 1;
                    mLineInfo.lineHeight = childView.getMeasuredHeight();
                    childHeight += mLineInfo.lineHeight;
                    lineCount = 0;
                    break;
                } else {
                    currentLineLeft = getPaddingLeft();
                    lineCount = 0;
                    childHeight += mLineInfo.lineHeight;
                }
            } while (lineCount == 0);
        }

        if (mLineInfoList.size() > 0) {
            childHeight += (mLineInfoList.size() - 1) * mItemGap;
        }
        childHeight += getPaddingBottom();
        if (Build.VERSION.SDK_INT > 15) {
            childHeight = Math.max(childHeight, getMinimumHeight());
        }

        if (heightMode == MeasureSpec.AT_MOST
                || heightMode == MeasureSpec.UNSPECIFIED) {
            heightSize = childHeight;
        }

        setMeasuredDimension(width, heightSize);

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) {
            return;
        }
        int top = getPaddingTop();
        for (LineItem li : mLineInfoList) {
            int end = li.startIndex + li.childCount;
            int left = getPaddingLeft();
            int centerY = top + li.lineHeight / 2;
            for (int index = li.startIndex; index < end; index++) {
                int childHeight = getChildAt(index).getMeasuredHeight();
                getChildAt(index).layout(left, centerY - childHeight / 2,
                        left + getChildAt(index).getMeasuredWidth(),
                        centerY + childHeight / 2);
                left += (getChildAt(index).getMeasuredWidth() + mItemGap);
            }
            top += (li.lineHeight + mLineVerticalGap);
        }
    }

    class LineItem {
        int childCount;
        int startIndex;
        int lineHeight;
        int lineTop;
    }

    class MyObserver extends DataSetObserver {

        @Override
        public void onChanged() {
            super.onChanged();
            fillChildren();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
        }
    }

}
