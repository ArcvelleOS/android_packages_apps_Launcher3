/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.customization;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.views.AbstractSlideInView;
import com.android.launcher3.views.BaseDragLayer;

/** A themed sheet whose surface extends behind the navigation bar. */
public abstract class CustomizationBottomSheet extends AbstractSlideInView<BaseActivity>
        implements Insettable {
    private final Rect mInsets = new Rect();

    protected CustomizationBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setInsets(Rect insets) {
        if (mContent != null) {
            int contentBottomPadding = mContent.getPaddingBottom() - mInsets.bottom;
            mContent.setPadding(mContent.getPaddingLeft() - mInsets.left + insets.left,
                    mContent.getPaddingTop(),
                    mContent.getPaddingRight() - mInsets.right + insets.right,
                    contentBottomPadding + insets.bottom);
        }
        mInsets.set(insets);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Consume the bottom inset as content padding, while staying below the status bar.
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    getAvailableSheetHeight(heightMeasureSpec),
                    MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected final int getAvailableSheetHeight(int heightMeasureSpec) {
        return Math.max(0, MeasureSpec.getSize(heightMeasureSpec) - mInsets.top);
    }

    private void applyNavigationBarAppearance() {
        int surface = getContext().getColor(R.color.materialColorSurfaceContainer);
        mActivityContext.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_APP_INFO,
                ColorUtils.calculateLuminance(surface) > 0.5
                        ? SystemUiController.FLAG_LIGHT_NAV : SystemUiController.FLAG_DARK_NAV);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyNavigationBarAppearance();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // The icon picker is stacked above app info. Closing either sheet must preserve
        // the remaining sheet's navigation-bar appearance, including during close animations.
        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child != this && child instanceof CustomizationBottomSheet sheet
                    && child.isAttachedToWindow()) {
                sheet.applyNavigationBarAppearance();
                return;
            }
        }
        mActivityContext.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_APP_INFO, 0);
    }
}
