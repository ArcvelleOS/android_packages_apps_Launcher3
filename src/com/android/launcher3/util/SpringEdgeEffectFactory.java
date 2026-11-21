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
import androidx.dynamicanimation.animation.FloatValueHolder;
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
        private float mTranslation;

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

            mSpringAnimation = new SpringAnimation(new FloatValueHolder());
            mSpringAnimation.setSpring(new SpringForce(0f)
                    .setStiffness(stiffness)
                    .setDampingRatio(dampingRatio));
            mSpringAnimation.addUpdateListener((animation, value, velocity) -> setTranslation(value));
            mSpringAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled) {
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
            int size = mIsVertical ? mRecyclerView.getHeight() : mRecyclerView.getWidth();
            if (size <= 0) {
                finish();
                return 0f;
            }
            if (mSpringAnimation.isRunning()) {
                // Let an inward swipe scroll freely while the visual spring settles.
                if (deltaDistance <= 0f) {
                    return 0f;
                }
                mSpringAnimation.cancel();
                mPullDistance = Math.max(0f,
                        Math.min(mMaxStretchFraction, mSign * mTranslation / size));
            }
            // Only report the distance consumed by this pull, excluding spring motion.
            float previous = mPullDistance;
            mPullDistance = Math.max(0f,
                    Math.min(mMaxStretchFraction, mPullDistance + deltaDistance));
            setTranslation(mSign * mPullDistance * size);
            if (mPullDistance > 0f) listenForDetach();
            else stopListeningForDetach();
            return mPullDistance - previous;
        }

        @Override
        public float getDistance() {
            // RecyclerView claims the next touch if it sees stretch left to consume.
            return mPullDistance;
        }

        private void setTranslation(float translation) {
            // Each edge changes only its own contribution to the view's translation.
            float change = translation - mTranslation;
            mTranslation = translation;
            if (mIsVertical) {
                mRecyclerView.setTranslationY(mRecyclerView.getTranslationY() + change);
            } else {
                mRecyclerView.setTranslationX(mRecyclerView.getTranslationX() + change);
            }
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
            // RecyclerView releases every edge, including edges this gesture never pulled.
            if (mPullDistance == 0f) return;
            mRecyclerView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            startSpring(0f);
        }

        @Override
        public void onAbsorb(int velocity) {
            mSpringAnimation.cancel();
            mRecyclerView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            startSpring(mSign * velocity * mVelocityScale);
        }

        private void startSpring(float velocity) {
            mPullDistance = 0f;
            int size = mIsVertical ? mRecyclerView.getHeight() : mRecyclerView.getWidth();
            if (size <= 0) {
                finish();
                return;
            }
            float maxStretch = size * mMaxStretchFraction;
            setTranslation(Math.max(-maxStretch, Math.min(maxStretch, mTranslation)));
            mSpringAnimation.setMinValue(-maxStretch).setMaxValue(maxStretch);
            mSpringAnimation.setStartValue(mTranslation);
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
