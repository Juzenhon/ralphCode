package com.test.www;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ListAdapter;
import android.widget.Scroller;
import android.widget.TextView;

public class WheelView extends ViewGroup {

	public interface OnWheelItemChangeListener {

		void onSelectedItemChanged(WheelView wheel, int lastpos, int newpos);

	}

	private static final String TAG = WheelView.class.getSimpleName();

	public static final int INVALID_POSITION = -1;

	private static final float DEFAULT_ITEM_HEIGHT = 40.5f;

	private int mItemHeight;

	private RecycleBin mRecycleBin = new RecycleBin();

	private FlingRunnable mFlingRunnable;

	private enum State {
		STATE_IDEL, STATE_TOUCH, STATE_FLING, STATE_SCROLL_SLOT
	}

	private State mState = State.STATE_IDEL;

	private boolean mHasLightBar = false;

	ListAdapter mAdapter;

	private WheelDataSetObserver mDataSetObserver;

	private OnWheelItemChangeListener mWheelItemChangeListener;

	private Paint mPaint = new Paint();

	private VelocityTracker mVelocityTracker;

	private int mDownY;

	private int mLastY;

	private int mFirstVisiblePosition;

	private int mCurrentItem = 0;

	private int mLastSelectedItem = mCurrentItem;

	private int mItemCount;

	private int mCenterY;

	private int mWidthMeasureSpec;

	private int mHeightMeasureSpec;

	private int mTouchSlop;

	private int mMaximumVelocity;

	private int mMinimumVelocity;

	private boolean mBlockLayoutRequests = false;

	private boolean mInLayout = false;

	private boolean mDataChanged = false;

	private boolean mCanScroll;

	public WheelView(Context context) {
		this(context, null);
	}

	public WheelView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setWillNotDraw(false);
		mFlingRunnable = new FlingRunnable();
		mPaint.setStrokeWidth(3);
		ViewConfiguration viewConfig = ViewConfiguration.get(getContext());
		mTouchSlop = viewConfig.getScaledTouchSlop();
		mMaximumVelocity = viewConfig.getScaledMaximumFlingVelocity();
		mMinimumVelocity = viewConfig.getScaledMinimumFlingVelocity();
		mItemHeight = (int) (getResources().getDisplayMetrics().density * DEFAULT_ITEM_HEIGHT);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);

		Log.d(TAG, "onMeasure widthMode = " + widthMode
				+ ",widthSize = " + widthSize);
		if (widthMode == MeasureSpec.UNSPECIFIED) {
			widthMeasureSpec = MeasureSpec.makeMeasureSpec(
					LayoutParams.MATCH_PARENT, MeasureSpec.AT_MOST);
		}
		if (heightMode == MeasureSpec.UNSPECIFIED) {
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(
					LayoutParams.MATCH_PARENT, MeasureSpec.AT_MOST);
		}
		if (heightMode == MeasureSpec.AT_MOST) {
			if (mAdapter != null && mItemCount > 0) {
				int h = mItemCount * mItemHeight;
				if (heightSize > h) {
					heightSize = h;
					heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize,
							MeasureSpec.EXACTLY);
				}
			}
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		mCenterY = getMeasuredHeight() / 2;
		mWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(),
				MeasureSpec.EXACTLY);
	}

	public void setOnWheelItemChangeListener(OnWheelItemChangeListener listener) {
		mWheelItemChangeListener = listener;
	}

	public void setLightBar(boolean hasLightbar) {
		mHasLightBar = hasLightbar;
		invalidate();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {

		if (mAdapter == null || mItemCount == 0) {
			resetList();
			return;
		}
		final boolean blockLayoutRequests = mBlockLayoutRequests;
		if (!blockLayoutRequests) {
			mBlockLayoutRequests = true;
		} else {
			mInLayout = false;
			return;
		}

		mInLayout = true;
		try {
			if (mDataChanged) {

				View centerView = obtainView(mCurrentItem, false);
				int ct = getCenterRectTop();
				int cb = getCenterRectBottom();

				int upcount = fillFromTop(l, 0, r, ct);
				mFirstVisiblePosition = mCurrentItem - upcount;
				measureChildAndLayout(centerView, ct, -1);
				fillFromBottom(l, cb, r, b);
				fillActiveViews();
				mDataChanged = false;
			}
		} finally {
			if (!blockLayoutRequests) {
				mBlockLayoutRequests = false;
			}
		}
		mInLayout = false;

	}

	private void fillActiveViews() {
		mRecycleBin.clearActives();
		int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			mRecycleBin.addActiveView(i, getChildAt(i));
		}
	}

	private void measureChildAndLayout(View child, int top, int index) {
		LayoutParams lp = child.getLayoutParams();
		if (lp == null) {
			lp = generateDefaultLayoutParams();
			// lp.height = mItemHeight;
			// lp.width = LayoutParams.MATCH_PARENT;
			int childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(
					mWidthMeasureSpec, getPaddingLeft() + getPaddingRight(),
					lp.width);
			child.measure(childWidthMeasureSpec,
					MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY));
		}
		addViewInLayout(child, index, lp, true);
		int padl = getPaddingLeft();
		child.layout(padl, top, child.getMeasuredWidth() + padl,
				top + child.getMeasuredHeight());
		transformView(child, top, top + child.getMeasuredHeight());
	}

	@Override
	public void requestLayout() {
		if (!mBlockLayoutRequests && !mInLayout) {
			super.requestLayout();
		}
	}

	public Object getSelectedItem() {
		if (mAdapter != null && mAdapter.getCount() > 0) {
			return mAdapter.getItem(mCurrentItem);
		}
		return null;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int ct = getCenterRectTop();
		int cb = getCenterRectBottom();
		mPaint.setColor(0xfffafaf9);
		canvas.drawRect(0, 0, getMeasuredWidth(), ct, mPaint);
		canvas.drawRect(0, cb, getMeasuredWidth(), getMeasuredHeight(), mPaint);
		mPaint.setColor(Color.WHITE);
		canvas.drawRect(0, ct, getMeasuredWidth(), cb, mPaint);

	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		// mPaint.setColor(getResources().getColor(R.color.guide_light_gray));
		mPaint.setColor(0xffb3b3b3);
		int centerY = getMeasuredHeight() / 2;
		canvas.drawLine(getPaddingLeft(), centerY - mItemHeight / 2,
				getMeasuredWidth() - getPaddingRight(), centerY - mItemHeight
						/ 2, mPaint);
		canvas.drawLine(getPaddingLeft(), centerY + mItemHeight / 2,
				getMeasuredWidth() - getPaddingRight(), centerY + mItemHeight
						/ 2, mPaint);
		if (mHasLightBar) {
			canvas.drawRect(getPaddingLeft(), getPaddingTop(),
					getPaddingLeft() + 27, getMeasuredHeight()
							- getPaddingBottom(), mPaint);
			mPaint.setColor(0xff007aff);
			canvas.drawRect(getPaddingLeft(), centerY - mItemHeight / 2,
					getPaddingLeft() + 27, centerY + mItemHeight / 2, mPaint);
		}

	}

	public void setAdapter(ListAdapter adapter) {

		if (mAdapter != null && mDataSetObserver != null) {
			mAdapter.unregisterDataSetObserver(mDataSetObserver);
		}

		resetList();
		mRecycleBin.clear();
		mAdapter = adapter;

		if (mAdapter != null) {
			mItemCount = mAdapter.getCount();
			mDataSetObserver = new WheelDataSetObserver();
			mAdapter.registerDataSetObserver(mDataSetObserver);
			if (mItemCount > 0) {
				mLastSelectedItem = mCurrentItem = 0;
				mFirstVisiblePosition = 0;
			}
		}
		mDataChanged = true;
		requestLayout();
	}

	public ListAdapter getAdapter() {
		return mAdapter;
	}

	public void setCurrentItem(int position) {
		setSelectedItem(position, false);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if (mAdapter != null && mDataSetObserver == null) {
			mDataSetObserver = new WheelDataSetObserver();
			mAdapter.registerDataSetObserver(mDataSetObserver);

			// Data may have changed while we were detached. Refresh.
			mDataChanged = true;
			mItemCount = mAdapter.getCount();
		}
	}

	@Override
	public void addView(View child) {
		throw new UnsupportedOperationException(
				"addView(View) is not supported in WheelView");
	}

	@Override
	public void addView(View child, int index) {
		throw new UnsupportedOperationException(
				"addView(View,int) is not supported in WheelView");
	}

	@Override
	public void addView(View child, int index, LayoutParams params) {
		throw new UnsupportedOperationException(
				"addView(View,int,LayoutParams) is not supported in WheelView");
	}

	@Override
	public void addView(View child, int width, int height) {
		throw new UnsupportedOperationException(
				"addView(View,int,int) is not supported in WheelView");
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);
	}

	private int fillFromTop(int l, int t, int r, int b) {

		if (b - t < 1) {
			return 0;
		}
		int bottom = b;
		int top;
		int count = (b - t + mItemHeight) / mItemHeight;
		if (mCurrentItem - count < 0) {
			count = mCurrentItem;
		}
		int position = mCurrentItem - 1;
		for (int i = 0; i < count; i++) {
			View child = obtainView(position, false);
			top = bottom - mItemHeight;

			measureChildAndLayout(child, top, 0);

			bottom -= child.getMeasuredHeight();

			position--;
		}
		return count;
	}

	private void fillFromBottom(int l, int t, int r, int b) {


		if (b - t < 1) {
			return;
		}
		int count = (b - t + mItemHeight) / mItemHeight;
		if (mCurrentItem + count >= mItemCount) {
			count = mItemCount - mCurrentItem - 1;
		}

		int position = mCurrentItem + 1;
		int top = t;
		for (int i = 0; i < count; i++) {
			View child = obtainView(position, false);

			measureChildAndLayout(child, top, -1);
			top += child.getMeasuredHeight();
			position++;
		}

	}

	private View obtainView(int position, boolean active) {
		View result = null;
		View convertView = null;
		if (active) {
			convertView = mRecycleBin.getActiveView(position);
		} else {
			convertView = mRecycleBin.getScrapView();
		}
		result = mAdapter.getView(position, convertView, this);
		return result;
	}

	private View changePosView(int index, int fromPos, int toPos) {
		View result = null;
		View convertView = getChildAt(index);
		if (convertView == null) {
			// result = obtainView(toPos, false);
		} else {
			result = mAdapter.getView(toPos, convertView, this);
			if (convertView != result) {
				int top = convertView.getTop();
				detachViewFromParent(convertView);
				measureChildAndLayout(result, top, index);
			}
		}
		return result;
	}

	private void offsetChildrenTopAndBottom(int delta) {

		final int childCount = getChildCount();

		for (int i = 0; i < childCount; i++) {
			getChildAt(i).offsetTopAndBottom(delta);
		}
	}

	private void resetList() {
		removeAllViewsInLayout();
		mFirstVisiblePosition = 0;
		mLastSelectedItem = mCurrentItem = INVALID_POSITION;
		mItemCount = 0;
		invalidate();
	}

	/**
	 * 回收这区间的view,
	 * 
	 * @param top
	 * @param bottom
	 * @param fromUp
	 *            是否上最上面的view开始回收
	 * @return 回收view的数量
	 */
	private int recycleView(int top, int bottom) {

		int childCount = getChildCount();
		int count = 0;
		if (childCount == 0) {
			return count;
		}

		for (int i = 0; i < getChildCount();) {

			View child = getChildAt(i);

			int childTop = child.getTop();
			int childBottom = child.getBottom();

			if (childTop >= top && childBottom <= bottom) {
				detachViewFromParent(child);
				mRecycleBin.addScrapView(child);
				count++;
			} else {
				i++;
			}
		}

		return count;
	}

	private int fillEmptyView(int startPos, int top, int bottom, boolean fromUp) {

		int count = 0;
		if (fromUp) {
			int start = top;
			int position = startPos;
			while (start < bottom && position < mItemCount) {

				View child = obtainView(position, false);

				measureChildAndLayout(child, start, -1);
				mRecycleBin.addActiveView(position, child);
				position++;
				start += mItemHeight;
				count++;
			}
		} else {
			int start = bottom;
			int position = startPos;
			while (start > top && position >= 0) {

				View child = obtainView(position, false);

				measureChildAndLayout(child, start - mItemHeight, 0);
				mRecycleBin.addActiveView(position, child);
				start -= mItemHeight;
				position--;
				count++;
			}
		}
		return count;
	}

	protected int getCenterRectTop() {
		return mCenterY - mItemHeight / 2;
	}

	protected int getCenterRectBottom() {
		return mCenterY + mItemHeight / 2;
	}

	private void trackMotionScroll(int delta) {

		int childCount = getChildCount();
		if (childCount == 0) {
			return;
		}
		if (delta > mCenterY) {
			delta = mCenterY;
		}
		if (delta < -mCenterY) {
			delta = -mCenterY;
		}
		mBlockLayoutRequests = true;
		if (mFirstVisiblePosition == 0 && delta > 0) {
			int firstTop = getChildAt(0).getTop();

			int ct = getCenterRectTop();
			if (firstTop < ct) {
				delta = firstTop + delta > ct ? ct - firstTop : delta;
			} else {
				mFlingRunnable.endFling();
				mBlockLayoutRequests = false;
				return;
			}
		}
		if (mFirstVisiblePosition + getChildCount() == mItemCount && delta < 0) {
			int lastBottom = getChildAt(getChildCount() - 1).getBottom();

			int cb = getCenterRectBottom();
			if (lastBottom > cb) {
				delta = lastBottom + delta < cb ? cb - lastBottom : delta;
			} else {
				mFlingRunnable.endFling();
				mBlockLayoutRequests = false;
				return;
			}
		}
		offsetChildrenTopAndBottom(delta);

		// scroll down
		if (delta > 0) {
			// recycleBottom();
			recycleView(getHeight() - getPaddingBottom(), Integer.MAX_VALUE);

			// fillEmptyUp();
			int count = fillEmptyView(mFirstVisiblePosition - 1,
					getPaddingTop(), getChildAt(0).getTop(), false);
			mFirstVisiblePosition -= count;

		} else {// scroll up
			// recycleUp();
			int items = recycleView(Integer.MIN_VALUE, getPaddingTop());
			mFirstVisiblePosition += items;

			// fillEmptyDown();
			int start = getChildAt(getChildCount() - 1).getBottom();

			fillEmptyView(mFirstVisiblePosition + getChildCount(), start,
					getMeasuredHeight() - getPaddingBottom(), true);
		}

		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			int childtop = child.getTop();
			int childBottom = child.getBottom();
			if (childtop < mCenterY && childBottom > mCenterY) {
				mCurrentItem = mFirstVisiblePosition + i;
			}
			transformView(child, childtop, childBottom);
		}
		mBlockLayoutRequests = false;
	}

	@SuppressLint("NewApi")
	private void transformView(View view, int top, int bottom) {
		float vc = (bottom + top) / 2.0f;
		float translateY = 0;
		float offset = mItemHeight * 0.18f;
		float ratio = vc / mCenterY;
		float alpha = 1;
		if (ratio < 1.0f) {
			alpha = 0.6f + 0.4f * ratio;
		} else {
			alpha = 0.6f + 0.4f * (2 - ratio);
		}
		translateY = offset * (1 - ratio);
        
		if (view instanceof TextView) {
			boolean isCenter = vc > getCenterRectTop()
					&& vc < getCenterRectBottom();
			((TextView) view).setTextColor(isCenter ? 0xff007aff : 0xff838383);
		}
		view.setAlpha(alpha);
		view.setScaleX(alpha);
		view.setScaleY(alpha);

		view.setTranslationY(translateY);
	}

	private void scrollIntoSlot() {

		View child = getChildAt(mCurrentItem - mFirstVisiblePosition);
		int top = child.getTop();
		int delta = getCenterRectTop() - top;
		mFlingRunnable.startScroll(top, delta);
		
	}

	private void initOrResetVelocityTracker() {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		} else {
			mVelocityTracker.clear();
		}
	}

	private void initVelocityTrackerIfNotExists() {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
	}

	private void recycleVelocityTracker() {
		if (mVelocityTracker != null) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
	}

	@Override
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		if (disallowIntercept) {
			recycleVelocityTracker();
		}
		super.requestDisallowInterceptTouchEvent(disallowIntercept);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (mState == State.STATE_SCROLL_SLOT) {
			return false;
		}

		// int x = (int) ev.getX();
		int y = (int) ev.getY();
		int action = ev.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			if (mState == State.STATE_FLING) {
				mFlingRunnable.endFling();
				mCanScroll = true;
			}
			mState = State.STATE_TOUCH;
			mDownY = y;
			initOrResetVelocityTracker();
			mVelocityTracker.addMovement(ev);
		}

		return mCanScroll;
	}

	private int mActivePointerId;

	private int mLastPointerId;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
	
		final int childCount = getChildCount();
		if (childCount == 0) {
			return false;
		}
		if (mState == State.STATE_SCROLL_SLOT) {
			return true;
		}

		int y;
		int action = event.getActionMasked();
		if (action != MotionEvent.ACTION_DOWN) {
			initVelocityTrackerIfNotExists();
			mVelocityTracker.addMovement(event);
		}
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			mLastPointerId = mActivePointerId = event.getPointerId(0);

			break;
		case MotionEvent.ACTION_MOVE:
			int pointerIndex = event.findPointerIndex(mActivePointerId);
			if (pointerIndex == -1) {
				pointerIndex = 0;
				mActivePointerId = event.getPointerId(pointerIndex);
			}
			y = (int) event.getY(pointerIndex);

			if (!mCanScroll && Math.abs(y - mDownY) > mTouchSlop) {
				mCanScroll = true;
				mLastY = y;
			}
			int delta = y - mLastY;
			if (mCanScroll && Math.abs(delta) > 3) {
				onScroll(delta);
				mLastY = y;
			}
			break;
		case MotionEvent.ACTION_POINTER_DOWN: {

			mLastPointerId = mActivePointerId;

			final int index = event.getActionIndex();
			final int id = event.getPointerId(index);
			final int y0 = (int) event.getY(index);
			mActivePointerId = id;
			mLastY = y0;
			break;
		}
		case MotionEvent.ACTION_POINTER_UP:

			final int index = event.getActionIndex();
			final int id = event.getPointerId(index);
			if (id == mActivePointerId) {
				mActivePointerId = mLastPointerId;
				int pIndex = event.findPointerIndex(mActivePointerId);
				if (pIndex == -1) {
					pIndex = 0;
					mActivePointerId = event.getPointerId(pIndex);
				}
				y = (int) event.getY(pIndex);
				mLastY = y;
			}

			break;
		case MotionEvent.ACTION_UP: {
			// case MotionEvent.ACTION_CANCEL:
			y = (int) event.getY();
			if (mCanScroll) {
				mState = State.STATE_FLING;
				mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				int velocityY = (int) mVelocityTracker
						.getYVelocity(mActivePointerId);
				if (velocityY > 0 && velocityY < mMinimumVelocity
						|| velocityY < 0 && velocityY > mMinimumVelocity) {
					scrollIntoSlot();
				} else {
					mFlingRunnable.startFling(velocityY);
				}
			} else {
				perfromItemClick(y);
			}
			recycleVelocityTracker();
			mCanScroll = false;
			break;
		}
		case MotionEvent.ACTION_CANCEL:
			recycleVelocityTracker();
			mCanScroll = false;
			scrollIntoSlot();
			break;
		default:
			break;
		}

		return true;
	}

	private void onScroll(int delta) {
		trackMotionScroll(delta);
	}

	private int getPositionfromY(int y) {
		int pos = INVALID_POSITION;
		int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = getChildAt(i);
			int childtop = child.getTop();
			int childBottom = child.getBottom();
			if (childtop < y && childBottom > y) {
				pos = mFirstVisiblePosition + i;
			}
		}

		return pos;
	}

	private void perfromItemClick(int y) {

		int clickPos = getPositionfromY(y);
		if (clickPos == INVALID_POSITION) {
			return;
		}
		setSelectedItem(clickPos, true);
	}

	public void setSelectedItem(int position, boolean anim) {

		if (mAdapter != null && mItemCount > position && position >= 0) {
			if (anim) {
				int count = position - mCurrentItem;
				if (count == 0) {
					return;
				} else {
					mFlingRunnable.startScroll(0, -count * mItemHeight);
				}

			} else {
				changeSelectItemWithoutAnim(position);
			}
		}
	}

	public int getCurrentItem() {
		return mCurrentItem;
	}

	private void changeSelectItemWithoutAnim(int newPos) {

		if (mState != State.STATE_IDEL || mCurrentItem == newPos) {
			return;
		}

		mCurrentItem = newPos;
		removeAllViewsInLayout();
		mDataChanged = true;
		mCurrentItem = newPos;
		invalidate();
		requestLayout();
		notifyItemChanged();
		mLastSelectedItem = mCurrentItem;
	}

	private void notifyItemChanged() {

		if (mWheelItemChangeListener != null) {
			mWheelItemChangeListener.onSelectedItemChanged(this,
					mLastSelectedItem, mCurrentItem);
		}
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT, mItemHeight);
	}

	class FlingRunnable implements Runnable {

		private Scroller mScroller;

		private int mLastY;

		Runnable slotRunnable = new Runnable() {

			@Override
			public void run() {
				boolean running = mScroller.computeScrollOffset();
				if (running) {
					int currY = mScroller.getCurrY();
					int dy = currY - mLastY;
					trackMotionScroll(dy);
					mLastY = currY;
					postDelayed(slotRunnable, 15);
				} else {
					mState = State.STATE_IDEL;
					notifyItemChanged();
					mLastSelectedItem = mCurrentItem;
				}
			}
		};

		public FlingRunnable() {
			mScroller = new Scroller(getContext(),
					new AccelerateDecelerateInterpolator());
		}

		public void startFling(int velocityY) {
			endFling();
			int initialY = velocityY < 0 ? Integer.MAX_VALUE : 0;
			mScroller.fling(0, initialY, 0, velocityY, 0, 0, Integer.MIN_VALUE,
					Integer.MAX_VALUE);
			mLastY = initialY;
			post(this);
		}

		public void startScroll(int start, int delta) {
			endFling();
			mState = State.STATE_SCROLL_SLOT;
			mScroller.startScroll(0, start, 0, delta, 150);
			mLastY = start;
			post(slotRunnable);
		}

		public void endFling() {
			removeCallbacks(this);
			mScroller.forceFinished(true);
		}

		@Override
		public void run() {
			if (mScroller.computeScrollOffset()) {
				int currY = mScroller.getCurrY();
				int dy = currY - mLastY;
				trackMotionScroll(dy);
				mLastY = currY;
				postDelayed(this, 40);
			} else {
				if (mState == State.STATE_FLING) {
					scrollIntoSlot();
				}
			}
		}

	}

	class WheelDataSetObserver extends DataSetObserver {
		@Override
		public void onChanged() {
			resetList();
			mDataChanged = true;
			mItemCount = mAdapter.getCount();
			mCurrentItem = mItemCount > 0 ? 0 : INVALID_POSITION;
			mLastSelectedItem = mCurrentItem;
			requestLayout();
		}

		@Override
		public void onInvalidated() {
			super.onInvalidated();

		}
	}

	class RecycleBin {

		ArrayList<View> mScrapViews = new ArrayList<View>();

		SparseArray<View> mActiveViews = new SparseArray<View>();

		RecycleBin() {
		}

		public void addActiveView(int position, View child) {
			mActiveViews.put(position, child);
		}

		public View getActiveView(int position) {
			return mActiveViews.get(position);
		}

		public void deleteActiveView(int position) {
			mActiveViews.delete(position);
		}

		public void addScrapView(View view) {
			for (int i = 0; i < mActiveViews.size(); i++) {
				int key = mActiveViews.keyAt(i);
				if (mActiveViews.get(key) == view) {
					mActiveViews.delete(key);
				}
			}
			mScrapViews.add(view);
		}

		public void clearActives() {
			mActiveViews.clear();
		}

		public void clear() {
			mScrapViews.clear();
			mActiveViews.clear();
		}

		public View getScrapView() {
			if (mScrapViews.size() > 0) {
				return mScrapViews.remove(0);
			}
			return null;
		}

	}

}
