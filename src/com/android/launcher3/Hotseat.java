/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.CrossWindowBlurListeners;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.internal.graphics.drawable.BackgroundBlurDrawable;
import com.android.launcher3.ShortcutAndWidgetContainer.TranslationProvider;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dragndrop.SystemDragItemInfo;
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtilsKt;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Consumer;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable {

    public static final int ALPHA_CHANNEL_TASKBAR_ALIGNMENT = 0;
    public static final int ALPHA_CHANNEL_PREVIEW_RENDERER = 1;
    public static final int ALPHA_CHANNEL_TASKBAR_STASH = 2;
    public static final int ALPHA_CHANNEL_ASSISTANT_VISIBILITY = 3;
    public static final int ALPHA_CHANNEL_CHANNELS_COUNT = 4;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ALPHA_CHANNEL_TASKBAR_ALIGNMENT, ALPHA_CHANNEL_PREVIEW_RENDERER,
            ALPHA_CHANNEL_TASKBAR_STASH, ALPHA_CHANNEL_ASSISTANT_VISIBILITY})
    public @interface HotseatQsbAlphaId {
    }

    public static final int ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT = 0;
    public static final int ICONS_TRANSLATION_X_CHANNELS_COUNT = 1;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT})
    public @interface IconsTranslationX {
    }

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;
    private static final float BACKGROUND_ICON_PADDING_DP = 10f;
    private static final float BACKGROUND_END_PADDING_DP = 14f;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;
    private final MultiValueAlpha mIconsAlphaChannels;
    private final @Nullable MultiValueAlpha mQsbAlphaChannels;

    private @Nullable MultiProperty mQsbTranslationX;

    private final MultiPropertyFactory mIconsTranslationXFactory;

    private final @Nullable View mQsb;

    private BackgroundBlurDrawable mBackgroundBlurDrawable;
    private final GradientDrawable mBackgroundTintDrawable = new GradientDrawable();
    private final Rect mPillRect = new Rect();
    private final Rect mLastBlurBounds = new Rect();
    private float mLastCornerRadius = -1f;
    private int mLastBlurRadius = -1;
    private final Rect mTempCellRect1 = new Rect();
    private final Rect mTempCellRect2 = new Rect();
    private final Rect mInsets = new Rect();
    private boolean mHasInsets;
    private boolean mShowHotseatBg;
    private boolean mHotseatBlur;
    private int mBlurDepth;
    private int mOpacity;
    private boolean mCrossWindowBlurEnabled;
    private final Consumer<Boolean> mCrossWindowBlurListener =
            this::onCrossWindowBlurEnabledChanged;
    private final LauncherPrefChangeListener mPrefChangeListener =
            key -> updateBackgroundPreferences();
    private final StateListener<LauncherState> mStateListener = new StateListener<>() {
        @Override
        public void onStateTransitionStart(LauncherState toState) {
            invalidate();
        }

        @Override
        public void onStateTransitionComplete(LauncherState finalState) {
            invalidate();
        }
    };

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mBackgroundTintDrawable.setCallback(this);
        mQsb = LauncherComponentProvider.get(context).getQsbWidgetFactory().createView(this);
        addView(mQsb);
        if (mQsb instanceof Reorderable qsbReorderable) {
            mQsbTranslationX = qsbReorderable.getTranslateDelegate()
                    .getTranslationX(MultiTranslateDelegate.INDEX_NAV_BAR_ANIM);
        }
        mQsbAlphaChannels = new MultiValueAlpha(mQsb, ALPHA_CHANNEL_CHANNELS_COUNT);
        mQsbAlphaChannels.setUpdateVisibility(true);

        mIconsAlphaChannels = new MultiValueAlpha(getShortcutsAndWidgets(),
                ALPHA_CHANNEL_CHANNELS_COUNT) {
            @Override
            protected void apply(float value) {
                super.apply(value);
                if (mShowHotseatBg) {
                    updateBlurState();
                    invalidate();
                }
            }
        };
        mIconsAlphaChannels.setUpdateVisibility(true);
        mIconsTranslationXFactory = new MultiPropertyFactory<>(getShortcutsAndWidgets(),
                VIEW_TRANSLATE_X, ICONS_TRANSLATION_X_CHANNELS_COUNT, Float::sum) {
            @Override
            protected void apply(float value) {
                super.apply(value);
                if (mShowHotseatBg) {
                    invalidate();
                }
            }
        };
        updateBackgroundPreferences();
    }

    /** Provides translation X for hotseat icons for the channel. */
    public MultiProperty getIconsTranslationX(@IconsTranslationX int channelId) {
        return mIconsTranslationXFactory.get(channelId);
    }

    /** Provides translation X for hotseat Qsb. */
    @Nullable
    public MultiProperty getQsbTranslationX() {
        return mQsbTranslationX;
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    boolean isHasVerticalHotseat() {
        return mHasVerticalHotseat;
    }

    /** Returns whether the hotseat is a valid drop target for the specified drag object. */
    public boolean isValidDropTarget(DropTarget.DragObject dragObject) {
        final ShortcutAndWidgetContainer shortcutAndWidgetContainer = getShortcutsAndWidgets();
        return shortcutAndWidgetContainer != null
                && shortcutAndWidgetContainer.getVisibility() == View.VISIBLE
                && isSupportedDrag(dragObject);
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        if (bubbleBarEnabled) {
            if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                getShortcutsAndWidgets().setTranslationProvider(
                        cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
                if (mQsb instanceof HorizontalInsettableView) {
                    HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                    final float insetFraction =
                            (float) dp.getWorkspaceProfile().getIconSizePx()
                                    / dp.getHotseatProfile().getQsbWidth();
                    // post this to the looper so that QSB has a chance to redraw itself, e.g.
                    // after device rotation
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                }
            } else {
                getShortcutsAndWidgets().setTranslationProvider(null);
                if (mQsb instanceof HorizontalInsettableView) {
                    ((HorizontalInsettableView) mQsb).setHorizontalInsets(0);
                }
            }
        }

        resetCellSize(dp);
        if (hasVerticalHotseat) {
            setGridSize(1, dp.getHotseatProfile().getNumShownIcons());
        } else {
            setGridSize(dp.getHotseatProfile().getNumShownIcons(), 1);
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     *
     * <p>When the bubble bar becomes visible, if needed, this method animates the hotseat icons
     * to reduce the spacing between them and make room for the bubble bar. The QSB width is
     * animated as well to align with the hotseat icons.
     *
     * <p>When the bubble bar goes away, any adjustments that were previously made are reversed.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean shouldAdjust = isBubbleBarVisible
                && dp.shouldAdjustHotseatOrQsbForBubbleBar(getContext());
        boolean shouldAdjustHotseat = shouldAdjust
                && dp.shouldAlignBubbleBarWithHotseat();
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        // update the translation provider for future layout passes of hotseat icons.
        if (shouldAdjustHotseat) {
            icons.setTranslationProvider(
                    cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
        } else {
            icons.setTranslationProvider(null);
        }
        AnimatorSet animatorSet = new AnimatorSet();
        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            if (child.getLayoutParams() instanceof CellLayoutLayoutParams lp) {
                float tx = shouldAdjustHotseat
                        ? dp.getHotseatAdjustedTranslation(getContext(), lp.getCellX()) : 0;
                if (child instanceof Reorderable) {
                    MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                    animatorSet.play(
                            mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
                } else {
                    animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
                }
            }
        }
        //TODO(b/381109832) refactor & simplify adjustment logic
        boolean shouldAdjustQsb =
                shouldAdjustHotseat || (shouldAdjust && dp.shouldAlignBubbleBarWithQSB());
        if (mQsb instanceof HorizontalInsettableView horizontalInsettableQsb) {
            final float currentInsetFraction = horizontalInsettableQsb.getHorizontalInsets();
            final float targetInsetFraction = shouldAdjustQsb
                    ? (float) dp.getWorkspaceProfile().getIconSizePx() / dp.getHotseatProfile()
                    .getQsbWidth()
                    : 0;
            ValueAnimator qsbAnimator =
                    ValueAnimator.ofFloat(currentInsetFraction, targetInsetFraction);
            qsbAnimator.addUpdateListener(animation -> {
                float insetFraction = (float) animation.getAnimatedValue();
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    protected int getTranslationXForCell(int cellX, int cellY) {
        TranslationProvider translationProvider = getShortcutsAndWidgets().getTranslationProvider();
        if (translationProvider == null) return 0;
        return (int) translationProvider.getTranslationX(cellX);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        mHasInsets = true;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            if (mQsb != null) mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.getHotseatProfile().getBarSizePx() + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.getHotseatProfile().getBarSizePx() + insets.right;
            }
        } else {
            if (mQsb != null) {
                mQsb.setVisibility(grid.isHotseatQsbEnabled() ? View.VISIBLE : View.GONE);
            }
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.getHotseatProfile().getBarSizePx();
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        if (mShowHotseatBg) {
            int iconSize = grid.getWorkspaceProfile().getIconSizePx();
            int backgroundPadding = Utilities.dpToPx(BACKGROUND_ICON_PADDING_DP);
            if (grid.isVerticalBarLayout()) {
                int cellWidth = lp.width - padding.left - padding.right;
                int overhang = (int) Math.ceil((iconSize - cellWidth) / 2f) + backgroundPadding;
                if (grid.isSeascape()) {
                    int extraWidth = Math.max(0, overhang - padding.right);
                    lp.width += extraWidth;
                    padding.right += extraWidth;
                } else {
                    int extraWidth = Math.max(0, overhang - padding.left);
                    lp.width += extraWidth;
                    padding.left += extraWidth;
                }
            } else {
                int cellHeight = lp.height - padding.top - padding.bottom;
                int overhang = (int) Math.ceil((iconSize - cellHeight) / 2f) + backgroundPadding;
                int backgroundTopInset = Math.max(0, overhang - padding.top);
                lp.height += backgroundTopInset;
                padding.top += backgroundTopInset;
            }
        }
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        setCellLayoutContainer(w);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawHotseatBackground(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCrossWindowBlurEnabled = CrossWindowBlurListeners.getInstance()
                .isCrossWindowBlurEnabled();
        CrossWindowBlurListeners.getInstance().addListener(
                getContext().getMainExecutor(), mCrossWindowBlurListener);
        LauncherPrefs.get(getContext()).addListener(mPrefChangeListener,
                LauncherPrefs.SHOW_HOTSEAT_BG,
                LauncherPrefs.HOTSEAT_BLUR,
                LauncherPrefs.BLUR_DEPTH,
                LauncherPrefs.HOTSEAT_OPACITY);
        Launcher launcher = mActivity instanceof Launcher l ? l : null;
        if (launcher != null && launcher.getStateManager() != null) {
            launcher.getStateManager().addStateListener(mStateListener);
        }
        updateBackgroundPreferences();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mBackgroundBlurDrawable != null) {
            mBackgroundBlurDrawable.setVisible(false, false);
            mBackgroundBlurDrawable.setCallback(null);
            mBackgroundBlurDrawable = null;
        }
        mLastBlurBounds.setEmpty();
        mLastCornerRadius = -1f;
        mLastBlurRadius = -1;
        Launcher launcher = mActivity instanceof Launcher l ? l : null;
        if (launcher != null && launcher.getStateManager() != null) {
            launcher.getStateManager().removeStateListener(mStateListener);
        }
        CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
        LauncherPrefs.get(getContext()).removeListener(mPrefChangeListener,
                LauncherPrefs.SHOW_HOTSEAT_BG,
                LauncherPrefs.HOTSEAT_BLUR,
                LauncherPrefs.BLUR_DEPTH,
                LauncherPrefs.HOTSEAT_OPACITY);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            updateBackgroundPreferences();
        } else {
            hideHotseatBlur();
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            postInvalidate();
        } else {
            hideHotseatBlur();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            postInvalidate();
        }
    }

    private void onCrossWindowBlurEnabledChanged(boolean enabled) {
        mCrossWindowBlurEnabled = enabled;
        updateBlurState();
        invalidate();
    }

    private void updateBackgroundPreferences() {
        Context context = getContext();
        boolean wasShowingBackground = mShowHotseatBg;
        LauncherPrefs prefs = LauncherPrefs.get(context);
        mShowHotseatBg = prefs.get(LauncherPrefs.SHOW_HOTSEAT_BG);
        mHotseatBlur = prefs.get(LauncherPrefs.HOTSEAT_BLUR);
        mBlurDepth = Math.max(0, prefs.get(LauncherPrefs.BLUR_DEPTH));
        mOpacity = Utilities.boundToRange(prefs.get(LauncherPrefs.HOTSEAT_OPACITY), 0, 100);
        int baseColor = Themes.getAttrColor(context, R.attr.folderBackgroundColor);
        mBackgroundTintDrawable.setColor(ColorUtils.setAlphaComponent(baseColor,
                Math.round(255f * mOpacity / 100f)));
        mBackgroundTintDrawable.setStroke(Math.max(1, Utilities.dpToPx(1f)),
                Color.argb(35, 255, 255, 255));
        if (wasShowingBackground != mShowHotseatBg && mHasInsets) {
            setInsets(mInsets);
        }
        updateBlurState();
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who)
                || who == mBackgroundBlurDrawable
                || who == mBackgroundTintDrawable;
    }

    private boolean canBlurHotseat() {
        return mShowHotseatBg && mHotseatBlur && mBlurDepth > 0 && mCrossWindowBlurEnabled
                && !Utilities.shouldReduceWorkspaceBlurUsage(getContext())
                && isAttachedToWindow() && isHardwareAccelerated() && isShown()
                && getWindowVisibility() == VISIBLE;
    }

    private void updateBlurState() {
        if (!canBlurHotseat()) {
            hideHotseatBlur();
        }
    }

    private void hideHotseatBlur() {
        if (mBackgroundBlurDrawable != null && mBackgroundBlurDrawable.isVisible()) {
            mBackgroundBlurDrawable.setVisible(false, false);
            mLastBlurBounds.setEmpty();
        }
    }

    private void drawHotseatBackground(Canvas canvas) {
        if (!mShowHotseatBg || getVisibility() != VISIBLE
                || mCountX <= 0 || mCountY <= 0 || mCellWidth <= 0 || mCellHeight <= 0
                || !updatePillBounds()) {
            if (canvas.isHardwareAccelerated()) {
                hideHotseatBlur();
            }
            return;
        }

        float iconAlpha = Utilities.boundToRange(getShortcutsAndWidgets().getAlpha(), 0f, 1f);
        if (Math.round(255f * iconAlpha) == 0) {
            if (canvas.isHardwareAccelerated()) {
                hideHotseatBlur();
            }
            return;
        }

        float cornerRadius = Math.min(mPillRect.width(), mPillRect.height()) / 2f;
        boolean drewBlur = false;
        if (canvas.isHardwareAccelerated() && canBlurHotseat()) {
            if (mBackgroundBlurDrawable == null && getViewRootImpl() != null) {
                mBackgroundBlurDrawable = getViewRootImpl().createBackgroundBlurDrawable();
                if (mBackgroundBlurDrawable != null) {
                    mBackgroundBlurDrawable.setCallback(this);
                }
            }
            if (mBackgroundBlurDrawable != null) {
                if (!mLastBlurBounds.equals(mPillRect)) {
                    mBackgroundBlurDrawable.setBounds(mPillRect);
                    mLastBlurBounds.set(mPillRect);
                }
                if (mLastCornerRadius != cornerRadius) {
                    mBackgroundBlurDrawable.setCornerRadius(cornerRadius);
                    mLastCornerRadius = cornerRadius;
                }
                if (mLastBlurRadius != mBlurDepth) {
                    mBackgroundBlurDrawable.setBlurRadius(mBlurDepth);
                    mLastBlurRadius = mBlurDepth;
                }
                mBackgroundBlurDrawable.setAlpha(Math.round(255f * iconAlpha));
                if (!mBackgroundBlurDrawable.isVisible()) {
                    mBackgroundBlurDrawable.setVisible(true, false);
                }
                mBackgroundBlurDrawable.draw(canvas);
                drewBlur = true;
            }
        } else if (canvas.isHardwareAccelerated()) {
            hideHotseatBlur();
        }

        if (mBackgroundTintDrawable.getCornerRadius() != cornerRadius) {
            mBackgroundTintDrawable.setCornerRadius(cornerRadius);
        }
        mBackgroundTintDrawable.setBounds(mPillRect);
        mBackgroundTintDrawable.setAlpha(Math.round(255f * iconAlpha));
        mBackgroundTintDrawable.draw(canvas);
    }

    private boolean updatePillBounds() {
        if (mBorderSpace == null || mCellWidth <= 0 || mCellHeight <= 0) {
            return false;
        }
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        int minCellX = Integer.MAX_VALUE;
        int minCellY = Integer.MAX_VALUE;
        int maxCellX = Integer.MIN_VALUE;
        int maxCellY = Integer.MIN_VALUE;
        int iconCount = 0;
        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            if (child.getVisibility() == GONE
                    || !(child.getLayoutParams() instanceof CellLayoutLayoutParams lp)) {
                continue;
            }
            boolean useTmpCoords = lp.useTmpCoords && child.getVisibility() == VISIBLE;
            int cellX = useTmpCoords ? lp.getTmpCellX() : lp.getCellX();
            int cellY = useTmpCoords ? lp.getTmpCellY() : lp.getCellY();
            minCellX = Math.min(minCellX, cellX);
            minCellY = Math.min(minCellY, cellY);
            maxCellX = Math.max(maxCellX, cellX + lp.cellHSpan - 1);
            maxCellY = Math.max(maxCellY, cellY + lp.cellVSpan - 1);
            iconCount++;
        }
        if (iconCount == 0) {
            return false;
        }

        cellToRect(minCellX, minCellY, maxCellX - minCellX + 1, maxCellY - minCellY + 1,
                mPillRect);
        mPillRect.offset(Math.round(icons.getTranslationX()), Math.round(icons.getTranslationY()));
        int paddingX = Utilities.dpToPx(mHasVerticalHotseat
                ? BACKGROUND_ICON_PADDING_DP : BACKGROUND_END_PADDING_DP);
        int paddingY = Utilities.dpToPx(mHasVerticalHotseat
                ? BACKGROUND_END_PADDING_DP : BACKGROUND_ICON_PADDING_DP);
        mPillRect.inset(-paddingX, -paddingY);
        return mPillRect.intersect(0, 0, getWidth(), getHeight());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        // Always let touch follow through to Workspace.
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mQsb != null && mQsb.getVisibility() != View.GONE) {
            DeviceProfile dp = mActivity.getDeviceProfile();
            mQsb.measure(
                    makeMeasureSpec(dp.getHotseatQsbWidth(), MeasureSpec.EXACTLY),
                    makeMeasureSpec(dp.getHotseatProfile().getQsbHeight(), MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mShowHotseatBg) {
            updateBlurState();
            invalidate();
        }

        if (mQsb == null || mQsb.getVisibility() == View.GONE) return;

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int left;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (dp.getHotseatProfile().isQsbInline()) {
            int qsbSpace = dp.getHotseatProfile().getBorderSpace();
            left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                    : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
        } else {
            left = (r - l - qsbMeasuredWidth) / 2;
        }
        int right = left + qsbMeasuredWidth;

        int height = b - t;
        int qsbHeight = dp.getHotseatProfile().getQsbHeight();
        int bottomOffset;
        int subtraction;
        if (dp.getHotseatProfile().isQsbInline()) {
            bottomOffset = dp.getHotseatBarBottomPadding();
            subtraction = (qsbHeight - dp.getHotseatProfile().getCellHeightPx()) / 2;
        } else if (dp.getDeviceProperties().getTaskbarConfiguration().isTaskbarPresent()) {
            int offsetY = (dp.getHotseatProfile().getBarSizePx() - qsbHeight)
                    + dp.getHotseatProfile().getQsbShadowHeight();
            int bottom = height - offsetY;
            mQsb.layout(left, bottom - qsbHeight, right, bottom);
            return;
        } else {
            bottomOffset = dp.getHotseatProfile().getBarBottomSpacePx();
            subtraction = dp.getHotseatProfile().getQsbShadowHeight();
        }
        int bottom = height - (bottomOffset - subtraction);
        mQsb.layout(left, bottom - qsbHeight, right, bottom);
    }

    /**
     * Sets the alpha value of the specified alpha channel of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getIconsAlpha(channelId).setValue(alpha);
        invalidate();
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        MultiProperty prop = getQsbAlpha(channelId);
        if (prop != null) {
            prop.setValue(alpha);
        }
    }

    /** Returns the alpha channel for ShortcutAndWidgetContainer */
    public MultiProperty getIconsAlpha(@HotseatQsbAlphaId int channelId) {
        return mIconsAlphaChannels.get(channelId);
    }

    /** Returns the alpha channel for Qsb, or null if QSB is not present */
    @Nullable
    public MultiProperty getQsbAlpha(@HotseatQsbAlphaId int channelId) {
        return mQsbAlphaChannels != null ? mQsbAlphaChannels.get(channelId) : null;
    }

    /**
     * Returns the QSB inside hotseat, or null if QSB is not enabled.
     */
    @Nullable
    public View getQsb() {
        return mQsb;
    }

    @Nullable
    @Override
    public View mapOverItems(ItemOperator op) {
        if (mQsb != null && mQsb.getVisibility() != View.GONE
                && mQsb.getTag() instanceof ItemInfo info
                && op.evaluate(info, mQsb)) {
            return mQsb;
        }
        return super.mapOverItems(op);
    }

    /** Dumps the Hotseat internal state */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "Hotseat:");
        mIconsAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mIconsAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH");
        if (mQsbAlphaChannels != null) {
            mQsbAlphaChannels.dump(
                    prefix + "\t",
                    writer,
                    "mQsbAlphaChannels",
                    "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                    "ALPHA_CHANNEL_PREVIEW_RENDERER",
                    "ALPHA_CHANNEL_TASKBAR_STASH"
            );
        }
    }

    // TODO(b/479881252): Determine whether it still makes sense to disallow all instances of
    //  `SystemDragItemInfo` once `SystemDragController` is supported in all `ActivityContext`s. The
    //  current assumption is that `SystemDragItemInfo` indicates files dragged from another app.
    private boolean isSupportedDrag(DropTarget.DragObject d) {
        return !(HomeScreenFilesUtilsKt.isFileSystemItem(d.dragInfo)
                || d.dragInfo instanceof LauncherAppWidgetInfo
                || d.dragInfo instanceof PendingAddWidgetInfo
                || d.dragInfo instanceof SystemDragItemInfo);
    }

}
