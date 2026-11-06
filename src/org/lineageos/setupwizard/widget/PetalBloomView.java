/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.core.graphics.PathParser;

import org.lineageos.setupwizard.R;

/**
 * The website's hero bloom, growing out of the bottom edge. It stands still once open and
 * only redraws while the entrance runs.
 */
public class PetalBloomView extends View {

    private static final String PETAL = "M0,0C-46,-120 -128,-250 -96,-392C-76,-480 -28,-536 "
            + "0,-566C28,-536 76,-480 96,-392C128,-250 46,-120 0,0Z";
    private static final float PETAL_LENGTH = 566f;

    // Back to front: { petals, degrees apart, scale }, as on the site.
    private static final float[][] LAYERS = {{13, 14, 1.72f}, {11, 16, 1.28f}, {9, 19, 0.88f}};
    private static final float[] STOPS = {0f, 0.6f, 1f};
    private static final float[] STOP_ALPHA = {1f, 0.66f, 0f};

    private static final int ENTRANCE_MS = 1700;

    private final Path mPetal = PathParser.createPathFromPathData(PETAL);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFade = new Paint();
    private final int[][] mColors;
    private final int mCanvasColor;
    private final Shader[] mShaders = new Shader[LAYERS.length];

    // How far the fan has spread, and how much of the view's height the tallest petal reaches.
    private final float mSpread;
    private final float mReach;

    private float mProgress = 1f;
    private ValueAnimator mEntrance;
    private boolean mSkipEntrance;

    public PetalBloomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PetalBloomView);
        mSpread = a.getFloat(R.styleable.PetalBloomView_petalSpread, 1f);
        // Large text needs the room, so the bloom stays clear of it.
        mReach = a.getFloat(R.styleable.PetalBloomView_petalReach, 0.7f)
                / (float) Math.sqrt(Math.max(1f, getResources().getConfiguration().fontScale));
        a.recycle();

        mColors = new int[][] {
                colors(context, R.array.petal_bloom_back),
                colors(context, R.array.petal_bloom_middle),
                colors(context, R.array.petal_bloom_front)};
        mCanvasColor = context.getColor(R.color.petal_setup_surface);
        mFill.setStyle(Paint.Style.FILL);
        mLine.setStyle(Paint.Style.STROKE);
        mLine.setColor(context.getColor(R.color.petal_bloom_line));
        mLine.setStrokeWidth(getResources().getDisplayMetrics().density);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Stay fully open, for a page that's only being redrawn on its way out. */
    public void skipEntrance() {
        mSkipEntrance = true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSkipEntrance || !ValueAnimator.areAnimatorsEnabled()) return;
        mEntrance = ValueAnimator.ofFloat(0f, 1f);
        mEntrance.setDuration(ENTRANCE_MS);
        mEntrance.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        mEntrance.addUpdateListener(animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        mProgress = 0f;
        mEntrance.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mEntrance != null) {
            mEntrance.cancel();
            mEntrance = null;
        }
        mProgress = 1f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        for (int i = 0; i < LAYERS.length; i++) {
            int[] c = mColors[i];
            int[] faded = new int[c.length];
            for (int s = 0; s < c.length; s++) {
                faded[s] = withAlpha(c[s], STOP_ALPHA[s]);
            }
            mShaders[i] = new LinearGradient(0f, 0f, 0f, -PETAL_LENGTH, faded, STOPS,
                    Shader.TileMode.CLAMP);
        }
        mFade.setShader(new LinearGradient(0f, h * 0.78f, 0f, h, Color.TRANSPARENT,
                withAlpha(mCanvasColor, 0.92f), Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        final float tallest = PETAL_LENGTH * LAYERS[0][2];
        final float base = h * mReach / tallest;
        final float strokeBase = getResources().getDisplayMetrics().density;

        canvas.save();
        canvas.translate(w / 2f, h * 1.02f);
        for (int layer = 0; layer < LAYERS.length; layer++) {
            float[] spec = LAYERS[layer];
            // Each inner layer opens slightly after the one behind it.
            float t = clamp((mProgress - layer * 0.08f) / (1f - layer * 0.08f));
            float scale = base * spec[2] * (0.82f + 0.18f * t);
            int count = (int) spec[0];
            mFill.setShader(mShaders[layer]);
            mFill.setAlpha((int) (178 * t));
            mLine.setAlpha((int) (92 * t));
            mLine.setStrokeWidth(strokeBase / scale);
            for (int i = 0; i < count; i++) {
                float angle = (i - (count - 1) / 2f) * spec[1] * mSpread * t;
                canvas.save();
                canvas.rotate(angle);
                canvas.scale(scale, scale);
                canvas.drawPath(mPetal, mFill);
                canvas.drawPath(mPetal, mLine);
                canvas.restore();
            }
        }
        canvas.restore();
        canvas.drawRect(0f, h * 0.78f, w, h, mFade);
    }

    private static int[] colors(Context context, int arrayRes) {
        TypedArray array = context.getResources().obtainTypedArray(arrayRes);
        int[] colors = new int[array.length()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = array.getColor(i, 0);
        }
        array.recycle();
        return colors;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb((int) (255 * alpha), Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
