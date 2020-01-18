package com.example.autosizeedittext;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

public class CustomEditText extends EditText {
    public CustomEditText(Context context) {
        super(context);
        initialize();
    }

    public CustomEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public CustomEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CustomEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    private interface SizeTester {
        /**
         *
         * @param suggestedSize
         *            Size of text to be tested
         * @param availableSpace
         *            available space in which text must fit
         * @return an integer < 0 if after applying {@code suggestedSize} to
         *         text, it takes less space than {@code availableSpace}, > 0
         *         otherwise
         */
        public int onTestSize(int suggestedSize, RectF availableSpace);
    }

    private RectF mTextRect = new RectF();

    private RectF mAvailableSpaceRect;

    private SparseIntArray mTextCachedSizes;

    private TextPaint mPaint;

    private float mMaxTextSize ;

    private float mSpacingMult = 1.0f;

    private float mSpacingAdd = 0.0f;

    private float mMinTextSize = 16;

    private int mWidthLimit;

    private static final int NO_LINE_LIMIT = -1;
    private int mMaxLines;

    private boolean mEnableSizeCache = true;
    private boolean mInitializedDimens;

    private void initialize() {
        mPaint = new TextPaint(getPaint());
        mMaxTextSize = getTextSize();
        mAvailableSpaceRect = new RectF();
        mTextCachedSizes = new SparseIntArray();
        if (mMaxLines == 0) {
            // no value was assigned during construction
            mMaxLines = NO_LINE_LIMIT;
        }
    }

    @Override
    public void setTextSize(float size) {
        mMaxTextSize = size;
        mTextCachedSizes.clear();
        adjustTextSize(getText().toString());
    }

    @Override
    public void setMaxLines(int maxlines) {
        super.setMaxLines(maxlines);
        mMaxLines = maxlines;
        adjustTextSize(getText().toString());
    }

    public int getMaxLines() {
        return mMaxLines;
    }

    @Override
    public void setSingleLine() {
        super.setSingleLine();
        mMaxLines = 1;
        adjustTextSize(getText().toString());
    }

    @Override
    public void setSingleLine(boolean singleLine) {
        super.setSingleLine(singleLine);
        if (singleLine) {
            mMaxLines = 1;
        } else {
            mMaxLines = NO_LINE_LIMIT;
        }
        adjustTextSize(getText().toString());
    }

    @Override
    public void setLines(int lines) {
        super.setLines(lines);
        mMaxLines = lines;
        adjustTextSize(getText().toString());
    }

    @Override
    public void setTextSize(int unit, float size) {
        Context c = getContext();
        Resources r;

        if (c == null)
            r = Resources.getSystem();
        else
            r = c.getResources();
        mMaxTextSize = TypedValue.applyDimension(unit, size,
                r.getDisplayMetrics());
        mTextCachedSizes.clear();
        adjustTextSize(getText().toString());
    }

    @Override
    public void setLineSpacing(float add, float mult) {
        super.setLineSpacing(add, mult);
        mSpacingMult = mult;
        mSpacingAdd = add;
    }

    public void setMinTextSize(float minTextSize) {
        mMinTextSize = minTextSize;
        adjustTextSize(getText().toString());
    }

    private void adjustTextSize(String s) {
        if (!mInitializedDimens) {
            return;
        }
        int startSize = (int) mMinTextSize;
        int heightLimit = getMeasuredHeight() - getCompoundPaddingBottom()
                - getCompoundPaddingTop();
        mWidthLimit = getMeasuredWidth() - getCompoundPaddingLeft()
                - getCompoundPaddingRight();
        mAvailableSpaceRect.right = mWidthLimit;
        mAvailableSpaceRect.bottom = heightLimit;
        super.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                efficientTextSizeSearch(s,startSize, (int) mMaxTextSize,
                        sizeTester, mAvailableSpaceRect));
    }

    public SizeTester sizeTester = new SizeTester() {
        final RectF textRect = new RectF();

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public int onTestSize(final int suggestedSize,
        final RectF availableSPace) {

            TextPaint paint =new TextPaint();

            paint.setTextSize(suggestedSize);

            String text;
            if (!TextUtils.isEmpty(getHint())) {
                text = getHint().toString();
            } else {
                text = getText().toString();
            }

            textRect.bottom = paint.getFontSpacing();
            textRect.right = paint.measureText(text);
            textRect.offsetTo(0, 0);

            if (availableSPace.contains(textRect)) {
                return -1;
            } else {
                return 1;
            }
        }
    };

    private int efficientTextSizeSearch(CharSequence s,int start, int end,
                                        SizeTester sizeTester, RectF availableSpace) {
        if (!mEnableSizeCache) {
            return binarySearch(s,start, end, sizeTester, availableSpace);
        }
        int key = getText().toString().length();
        int size = mTextCachedSizes.get(key);
        if (size != 0) {
            return size;
        }
        size = binarySearch(s,start, end, sizeTester, availableSpace);
        mTextCachedSizes.put(key, size);
        return size;
    }

    private static int binarySearch(CharSequence s,int start, int end, SizeTester sizeTester,
                                    RectF availableSpace) {
        int lastBest = start;
        int lo = start;
        int hi = end - 1;
        int mid = 0;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            int midValCmp = sizeTester.onTestSize(mid, availableSpace);
            if (midValCmp < 0) {
                lastBest = lo;
                lo = mid + 1;
            } else if (midValCmp > 0) {
                hi = mid - 1;
                lastBest = hi;
            } else {
                return mid;
            }
        }
        return lastBest;

    }

    @Override
    protected void onTextChanged(final CharSequence text, final int start,
                                 final int before, final int after) {
        super.onTextChanged(text, start, before, after);
        adjustTextSize(getText().toString());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldwidth,
                                 int oldheight) {
        mInitializedDimens = true;
        mTextCachedSizes.clear();
        super.onSizeChanged(width, height, oldwidth, oldheight);
        if (width != oldwidth || height != oldheight) {
            adjustTextSize(getText().toString());
        }
    }
}
