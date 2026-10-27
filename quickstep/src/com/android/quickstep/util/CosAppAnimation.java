/*
 * Copyright (C) 2026 The PenguinOS Project
 * Copyright (C) 2026 Lunaris AOSP
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.quickstep.util;

import android.content.Context;
import android.os.UserManager;
import android.view.animation.Interpolator;

import com.android.launcher3.LauncherPrefs;

/** Optional ColorOS-inspired motion, using an analytic unit-mass spring without ROM helpers. */
public final class CosAppAnimation {
    public static final int LAUNCH_DURATION = 320;
    public static final long REVEAL_DURATION = 500;
    public static final float HOME_SCALE = 0.9f;
    public static final Interpolator OPEN = spring(1900f, 1.85f, LAUNCH_DURATION);
    public static final Interpolator REVEAL = spring(324f, 1f, REVEAL_DURATION);

    private CosAppAnimation() { }

    public static boolean isEnabled(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        return users != null && users.isUserUnlocked()
                && LauncherPrefs.COS_APP_ANIMATIONS.get(context);
    }

    private static Interpolator spring(float stiffness, float damping, long duration) {
        final double frequency = Math.sqrt(stiffness);
        final double seconds = duration / 1000.0;
        final double end = position(frequency, damping, seconds);
        return progress -> (float) (position(frequency, damping,
                Math.max(0f, Math.min(1f, progress)) * seconds) / end);
    }

    private static double position(double frequency, double damping, double time) {
        if (damping == 1) {
            return 1 - (1 + frequency * time) * Math.exp(-frequency * time);
        }
        double root = Math.sqrt(damping * damping - 1);
        double slow = -frequency * (damping - root);
        double fast = -frequency * (damping + root);
        return 1 - (fast * Math.exp(slow * time) - slow * Math.exp(fast * time))
                / (fast - slow);
    }

    public static float bounceDamping(float overshoot, float distance) {
        if (overshoot <= 0 || distance <= overshoot) return 1f;
        double log = Math.log(overshoot / distance);
        return Math.max(0.5f, Math.min(1f,
                (float) (-log / Math.sqrt(Math.PI * Math.PI + log * log))));
    }
}
