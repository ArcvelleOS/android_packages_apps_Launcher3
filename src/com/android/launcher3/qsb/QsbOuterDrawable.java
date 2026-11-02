/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.qsb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

/** Draws the translucent outer shell and solid search surface of the hotseat QSB. */
public class QsbOuterDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Context mContext;
    private int mOpacity;

    public QsbOuterDrawable(Context context) {
        mContext = context;
        updateOpacity();
    }

    public void updateOpacity() {
        mOpacity = Math.max(0, Math.min(100, Utilities.getQsbOuterOpacity(mContext)));
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        int themedColor = Themes.getAttrColor(mContext, R.attr.folderBackgroundColor);
        int alpha = mOpacity * 255 / 100;
        mPaint.setColor((themedColor & 0x00FFFFFF) | (alpha << 24));

        float radius = mContext.getResources().getDimensionPixelSize(R.dimen.qsb_widget_height);
        mRect.set(bounds);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);

        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.qsb_icon_padding);
        int rightPadding =
                mContext.getResources().getDimensionPixelSize(R.dimen.qsb_inner_inset_end);
        Rect innerBounds = new Rect(
                bounds.left + padding,
                bounds.top + padding,
                bounds.right - rightPadding,
                bounds.bottom - padding);
        mPaint.setColor(mContext.getColor(R.color.materialColorSurfaceBright));
        canvas.drawRoundRect(new RectF(innerBounds), radius, radius, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        // The drawable's opacity is controlled by the launcher preference.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // The drawable uses the active launcher theme colors.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
