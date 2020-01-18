package com.example.autosizeedittext;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;

import java.lang.reflect.Field;

import static android.R.attr.minWidth;


public class AutoResizeEditText extends AppCompatEditText {

    private static float DEFAULT_MIN_TEXT_SIZE = 16f;
    private static int DEFAULT_MIN_WIDTH = 800;

    private final SparseIntArray textCachedSizes = new SparseIntArray();
    private SizeTester sizeTester;
    int startWidth;
    private float maxTextSize;
    private float minTextSize;
    private int maxWidth;
    private TextPaint paint;
    private float scaleFactor = 1.f;
    private boolean shouldResize;
    private boolean isDefaultTypeface = true;


    public AutoResizeEditText(final Context context) {
        this(context, null, 0);
    }

    public AutoResizeEditText(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoResizeEditText(final Context context, final AttributeSet attrs,
                                    final int defStyle) {
        super(context, attrs, defStyle);



        sizeTester = new SizeTester() {
            final RectF textRect = new RectF();

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public int onTestSize(final int suggestedSize,
                                  final RectF availableSPace) {
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

        setFocusable(true);
        setFocusableInTouchMode(true);
        setTextIsSelectable(true);
        setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        addSelfRemovableTextWatcher();
        setDrawingCacheEnabled(true);


        int newWidth = (int) (startWidth * scaleFactor);
        if (newWidth > minWidth && newWidth < ((View) getParent()).getWidth()) {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = newWidth;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            setLayoutParams(params);
        }
    }

    @Override
    protected void onTextChanged(final CharSequence text, final int start, final int before, final int after) {
        super.onTextChanged(text, start, before, after);
        adjustTextSize();
    }

    @Override
    protected void onSizeChanged(final int width, final int height, final int oldwidth, final int oldheight) {
        textCachedSizes.clear();
        super.onSizeChanged(width, height, oldwidth, oldheight);
        if (width != oldwidth || height != oldheight) {
            adjustTextSize();
        }
    }

    /**
     * Resizes text on layout changes
     */
    private void adjustTextSize() {
        final int startSize = (int) minTextSize;
        int heightLimit;
        if (isDefaultTypeface) {
            heightLimit = getMeasuredHeight()
                    - getCompoundPaddingBottom() - getCompoundPaddingTop();
            maxWidth = getMeasuredWidth() - getCompoundPaddingLeft()
                    - getCompoundPaddingRight();
        } else {
            heightLimit = ((int) (getMeasuredHeight() * scaleFactor))
                    - getCompoundPaddingBottom() - getCompoundPaddingTop();
            maxWidth = getMeasuredWidth() - getCompoundPaddingLeft()
                    - getCompoundPaddingRight();
        }

        if (maxWidth <= 0) {
            return;
        }

        super.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                efficientTextSizeSearch(startSize, (int) maxTextSize,
                        sizeTester, new RectF(0, 0, maxWidth, heightLimit)));

    }

    /**
     * Gets cached text size from list of previously stored sizes
     */
    private int efficientTextSizeSearch(final int start, final int end,
                                        final SizeTester sizeTester, final RectF availableSpace) {
        String text;
        if (!TextUtils.isEmpty(getHint())) {
            text = getHint().toString();
        } else {
            text = getText().toString();
        }

        final int key = text.length();
        int size = textCachedSizes.get(key);
        if (size != 0) {
            return size;
        }
        size = binarySearch(start, end, sizeTester, availableSpace);
        textCachedSizes.put(key, size);
        return size;
    }

    /**
     * Calculates best text size for current EditText size
     */
    private int binarySearch(final int start, final int end, final SizeTester sizeTester, final RectF availableSpace) {
        int lastBest = start;
        int low = start;
        int high = end - 1;
        int middle;
        while (low <= high) {
            middle = low + high >>> 1;
            final int midValCmp = sizeTester.onTestSize(middle, availableSpace);
            if (midValCmp < 0) {
                lastBest = low;
                low = middle + 1;
            } else if (midValCmp > 0) {
                high = middle - 1;
                lastBest = high;
            } else {
                return middle;
            }
        }
        return lastBest;
    }

    /**
     * This method sets TextView#Editor#mInsertionControllerEnabled field to false
     * to return false from the Editor#hasInsertionController() method to PREVENT showing
     * of the insertionController from EditText
     * The Editor#hasInsertionController() method is called in  Editor#onTouchUpEvent(MotionEvent event) method.
     */
    private void setInsertionDisabled() {
        try {
            Field editorField = TextView.class.getDeclaredField("mEditor");
            editorField.setAccessible(true);
            Object editorObject = editorField.get(this);

            Class editorClass = Class.forName("android.widget.Editor");
            Field mInsertionControllerEnabledField = editorClass.getDeclaredField("mInsertionControllerEnabled");
            mInsertionControllerEnabledField.setAccessible(true);
            mInsertionControllerEnabledField.set(editorObject, false);
        } catch (Exception ignored) {
            // ignore exception here
        }
    }

    /**
     * Adds TextWatcher if EditText has hint
     */
    private void addSelfRemovableTextWatcher() {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //empty
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                removeTextChangedListener(this);
                setHint(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
                //empty
            }
        });
    }



    public boolean shouldResize() {
        return shouldResize;
    }

    public void shouldResize(boolean shouldResize) {
        this.shouldResize = shouldResize;
    }

    private interface SizeTester {
        /**
         * AutoResizeEditText
         *
         * @param suggestedSize  Size of text to be tested
         * @param availableSpace available space in which text must fit
         * @return an integer < 0 if after applying {@code suggestedSize} to
         * text, it takes less space than {@code availableSpace}, > 0
         * otherwise
         */
        int onTestSize(int suggestedSize, RectF availableSpace);
    }

    /**
     * OnMoveListener
     */
    public interface OnMoveListener {
        void onStartMoving();

        void onFinishMoving(AutoResizeEditText autofitEditText, MotionEvent event);
    }

    /**
     * OnEditTextActivateListener
     */
    public interface OnEditTextActivateListener {
        void onEditTextActivated(AutoResizeEditText autofitEditText);
    }

}