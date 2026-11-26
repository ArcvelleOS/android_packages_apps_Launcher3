/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.qsb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

public class QsbOuterDrawable extends Drawable {
    private final Paint mPaint;
    private final RectF mRect;
    private final Context mContext;
    private int mOpacity = 70;
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mFrosted;
    private int mFrostOpacity = 40;

    public QsbOuterDrawable(Context context) {
        mContext = context;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRect = new RectF();
        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(Math.max(1, Utilities.dpToPx(1f)));
        mRimPaint.setColor(Color.argb(35, 255, 255, 255));
        updateOpacity();
    }

    public void updateOpacity() {
        mOpacity = Utilities.getQsbOuterOpacity(mContext);
        invalidateSelf();
    }

    /**
     * Returns the alpha (0-255) of the glass search box for the given hotseat opacity. It is
     * denser than the dock background so the search box stays readable as an inset.
     */
    public static int getFrostedAlpha(int opacity) {
        float percent = opacity + (100 - opacity) * 0.35f;
        return Math.round(255f * Math.min(100f, percent) / 100f);
    }

    /**
     * Draws only the search box as a translucent glass pill, matching the hotseat background,
     * instead of the opaque outer ring and solid search box.
     */
    public void setFrosted(boolean frosted, int opacity) {
        if (mFrosted != frosted || mFrostOpacity != opacity) {
            mFrosted = frosted;
            mFrostOpacity = opacity;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        // Get the themed color for outer shape
        int themedColor = Themes.getAttrColor(mContext, R.attr.folderBackgroundColor);

        float radius = mContext.getResources().getDimensionPixelSize(
                R.dimen.qsb_widget_height);
        if (!mFrosted) {
            // Apply opacity to outer shape only
            int alpha = (int) (mOpacity * 255 / 100);
            int outerColorWithAlpha = (themedColor & 0x00FFFFFF) | (alpha << 24);

            mPaint.setColor(outerColorWithAlpha);

            // Draw outer shape
            mRect.set(bounds);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
        }

        // Match bg_google_qsb_outer insets for inner search box
        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.qsb_icon_padding);
        int rightPadding = mContext.getResources().getDimensionPixelSize(R.dimen.qsb_inner_inset_end);

        Rect innerBounds = new Rect(
                bounds.left + padding,
                bounds.top + padding,
                bounds.right - rightPadding,
                bounds.bottom - padding
        );

        int fillColor = mContext.getColor(R.color.materialColorSurfaceBright);
        if (mFrosted) {
            // Follow the hotseat background opacity so the search box reads as the same glass.
            fillColor = (fillColor & 0x00FFFFFF) | (getFrostedAlpha(mFrostOpacity) << 24);
        }
        mPaint.setColor(fillColor);

        RectF innerRect = new RectF(innerBounds);
        canvas.drawRoundRect(innerRect, radius, radius, mPaint);
        if (mFrosted) {
            float inset = mRimPaint.getStrokeWidth() / 2f;
            innerRect.inset(inset, inset);
            canvas.drawRoundRect(innerRect, radius, radius, mRimPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        // Not used
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Not used
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
