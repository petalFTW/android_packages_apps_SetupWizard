/* Copyright (C) 2026 petalOS; SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.setupwizard.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.PathParser;

import org.lineageos.setupwizard.R;

/** The petalOS mark as a progress meter: its three petals fill as setup moves along. */
public final class PetalStepDrawable extends Drawable {

    // Left, centre, right: the order they fill. From assets/logo.svg.
    private static final String[] PETALS = {
        "M485,800C428,744 389,716 311,679C213,634 169,596 131,511C107,455 91,414 61,374"
                + "C48,357 53,348 75,343C174,320 285,374 363,464C433,545 469,678 485,800Z",
        "M500,772C480,655 432,539 395,467C349,375 372,294 402,238C433,181 480,132 492,122"
                + "C497,117 503,117 508,122C520,132 567,181 598,238C628,294 651,375 605,467"
                + "C568,539 520,655 500,772Z",
        "M515,800C572,744 611,716 689,679C787,634 831,596 869,511C893,455 909,414 939,374"
                + "C952,357 947,348 925,343C826,320 715,374 637,464C567,545 531,678 515,800Z",
    };
    private static final float BOX_X = 40f;
    private static final float BOX_Y = 100f;
    private static final float BOX_W = 920f;
    private static final float BOX_H = 720f;

    private final Path[] mPaths = new Path[PETALS.length];
    private final Paint mDone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTodo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mFilled;
    private final int mHeight;

    /** @param filled how many petals to fill */
    public PetalStepDrawable(Context context, int filled) {
        mFilled = filled;
        mHeight = context.getResources().getDimensionPixelSize(R.dimen.petal_step_mark_height);
        for (int i = 0; i < PETALS.length; i++) {
            mPaths[i] = PathParser.createPathFromPathData(PETALS[i]);
        }
        mDone.setShader(new LinearGradient(0f, BOX_Y, 0f, BOX_Y + BOX_H,
                context.getColor(R.color.petal_step_top),
                context.getColor(R.color.petal_step_bottom), Shader.TileMode.CLAMP));
        mTodo.setStyle(Paint.Style.STROKE);
        mTodo.setStrokeWidth(26f);
        mTodo.setColor(context.getColor(R.color.petal_step_todo));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float scale = Math.min(b.width() / BOX_W, b.height() / BOX_H);
        canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(scale, scale);
        canvas.translate(-BOX_X, -BOX_Y);
        for (int i = 0; i < mPaths.length; i++) {
            canvas.drawPath(mPaths[i], i < mFilled ? mDone : mTodo);
        }
        canvas.restore();
    }

    @Override
    public int getIntrinsicHeight() {
        return mHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(mHeight * BOX_W / BOX_H);
    }

    @Override
    public void setAlpha(int alpha) {
        mDone.setAlpha(alpha);
        mTodo.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mDone.setColorFilter(filter);
        mTodo.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
