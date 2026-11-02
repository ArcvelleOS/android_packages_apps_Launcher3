/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.launcher3.util;

import android.content.Context;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.LauncherPrefs;

public class SpringEdgeEffectFactory extends RecyclerView.EdgeEffectFactory {

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(@NonNull RecyclerView recyclerView, int direction) {
        return new SpringEdgeEffect(recyclerView, direction);
    }

    public static EdgeEffect createSpringEdgeEffect(RecyclerView recyclerView, int direction) {
        return new SpringEdgeEffect(recyclerView, direction);
    }

    private static class SpringEdgeEffect extends EdgeEffect {

        private final RecyclerView mRecyclerView;
        private final SpringAnimation mSpringAnimation;
        private final boolean mIsVertical;
        private final float mMaxStretchFraction;
        private final float mVelocityScale;
        private final float mSign;
        private final View.OnAttachStateChangeListener mAttachListener =
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) { }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        finish();
                    }
                };
        private boolean mListeningForDetach;
        private float mPullDistance = 0f;

        SpringEdgeEffect(RecyclerView recyclerView, int direction) {
            super(recyclerView.getContext());
            mRecyclerView = recyclerView;
            mIsVertical = direction == DIRECTION_TOP || direction == DIRECTION_BOTTOM;
            mSign = (direction == DIRECTION_TOP || direction == DIRECTION_LEFT) ? 1f : -1f;

            Context context = recyclerView.getContext();
            float stiffness = Math.max(50, Math.min(1000,
                    LauncherPrefs.OVERSCROLL_STIFFNESS.get(context)));
            float dampingRatio = Math.max(10, Math.min(100,
                    LauncherPrefs.OVERSCROLL_DAMPING_RATIO.get(context))) / 100f;
            mMaxStretchFraction = Math.max(5, Math.min(40,
                    LauncherPrefs.OVERSCROLL_MAX_STRETCH.get(context))) / 100f;
            mVelocityScale = Math.max(10, Math.min(200,
                    LauncherPrefs.OVERSCROLL_VELOCITY_SCALE.get(context))) / 100f;

            DynamicAnimation.ViewProperty property =
                    mIsVertical ? DynamicAnimation.TRANSLATION_Y : DynamicAnimation.TRANSLATION_X;

            mSpringAnimation = new SpringAnimation(recyclerView, property, 0f);
            mSpringAnimation.setSpring(new SpringForce(0f)
                    .setStiffness(stiffness)
                    .setDampingRatio(dampingRatio));
            mSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled) {
                    mPullDistance = 0f;
                    setTranslation(0f);
                    stopListeningForDetach();
                }
            });
        }

        @Override
        public void onPull(float deltaDistance) {
            onPull(deltaDistance, 0.5f);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            onPullDistance(deltaDistance, displacement);
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            // A new pull can interrupt the return spring. Rebase the pull distance from
            // the current view translation before cancelling so the next pull continues
            // from the visible stretch instead of jumping from a stale distance.
            if (mSpringAnimation.isRunning()) {
                updatePullDistanceFromTranslation();
                mSpringAnimation.cancel();
            }
            // Snapshot after rebasing: spring motion is not distance consumed from this event.
            float previous = mPullDistance;
            mPullDistance = Math.max(0f,
                    Math.min(mMaxStretchFraction, mPullDistance + deltaDistance));
            float size = mIsVertical ? mRecyclerView.getHeight() : mRecyclerView.getWidth();
            setTranslation(mSign * mPullDistance * size);
            if (mPullDistance > 0f) listenForDetach();
            else stopListeningForDetach();
            return mPullDistance - previous;
        }

        private void updatePullDistanceFromTranslation() {
            float size = mIsVertical ? mRecyclerView.getHeight() : mRecyclerView.getWidth();
            if (size <= 0f) {
                mPullDistance = 0f;
                return;
            }

            float translation = mIsVertical
                    ? mRecyclerView.getTranslationY() : mRecyclerView.getTranslationX();
            mPullDistance = Math.max(0f,
                    Math.min(mMaxStretchFraction, mSign * translation / size));
        }

        @Override
        public float getDistance() {
            if (mSpringAnimation.isRunning()) updatePullDistanceFromTranslation();
            return mPullDistance;
        }

        private float getTranslation() {
            return mIsVertical ? mRecyclerView.getTranslationY() : mRecyclerView.getTranslationX();
        }

        private void setTranslation(float translation) {
            if (mIsVertical) mRecyclerView.setTranslationY(translation);
            else mRecyclerView.setTranslationX(translation);
        }

        @Override
        public void finish() {
            mPullDistance = 0f;
            mSpringAnimation.cancel();
            setTranslation(0f);
            stopListeningForDetach();
        }

        private void listenForDetach() {
            if (!mListeningForDetach) {
                mRecyclerView.addOnAttachStateChangeListener(mAttachListener);
                mListeningForDetach = true;
            }
        }

        private void stopListeningForDetach() {
            if (mListeningForDetach) {
                mRecyclerView.removeOnAttachStateChangeListener(mAttachListener);
                mListeningForDetach = false;
            }
        }

        @Override
        public void onRelease() {
            // RecyclerView releases all edges repeatedly during scrolling. Do not restart a
            // running spring or create a zero-distance spring for an unused edge.
            if (mSpringAnimation.isRunning() || getTranslation() == 0f) return;
            if (mPullDistance > 0f) {
                mRecyclerView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
            startSpring(0f);
        }

        @Override
        public void onAbsorb(int velocity) {
            mSpringAnimation.cancel();
            mRecyclerView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            startSpring(mSign * velocity * mVelocityScale);
        }

        private void startSpring(float velocity) {
            float size = mIsVertical ? mRecyclerView.getHeight() : mRecyclerView.getWidth();
            if (size <= 0f) {
                finish();
                return;
            }
            float maxStretch = size * mMaxStretchFraction;
            setTranslation(Math.max(-maxStretch, Math.min(maxStretch, getTranslation())));
            mSpringAnimation.setMinValue(-maxStretch).setMaxValue(maxStretch);
            mSpringAnimation.setStartVelocity(velocity);
            listenForDetach();
            mSpringAnimation.animateToFinalPosition(0f);
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            return mPullDistance == 0f && !mSpringAnimation.isRunning();
        }
    }
}
