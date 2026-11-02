/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.qsb;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.MultiTranslateDelegate;

/** Pixel-style Google search pill used in the hotseat. */
public class QsbLayout extends FrameLayout implements Reorderable, HorizontalInsettableView {

  private static final String GSA_PACKAGE = "com.google.android.googlequicksearchbox";
  private static final String LENS_ACTIVITY =
      "com.google.android.apps.search.lens.LensExportedActivity";
  private static final String LENS_URI = "google://lens";

  private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
  private final ThemeManager.ThemeChangeListener mThemeChangeListener = this::updateIcons;
  private float mScaleForReorderBounce = 1f;
  private float mHorizontalInsets;
  private ThemeManager mThemeManager;
  private ImageView mGIcon;
  private ImageView mMicIcon;
  private ImageButton mLensIcon;
  private ImageView mAiModeButton;

  public QsbLayout(Context context) {
    this(context, null);
  }

  public QsbLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QsbLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    mThemeManager = ThemeManager.INSTANCE.get(getContext());
    mGIcon = findViewById(R.id.g_icon);
    mMicIcon = findViewById(R.id.mic_icon);
    mLensIcon = findViewById(R.id.lens_icon);
    mAiModeButton = findViewById(R.id.ai_mode_button);

    setOnClickListener(v -> launchSearch());
    mMicIcon.setOnClickListener(v -> launchVoiceSearch());
    mLensIcon.setOnClickListener(v -> launchLens());
    mAiModeButton.setOnClickListener(v -> launchAiSearch());
    updateIcons();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    mThemeManager.addChangeListener(mThemeChangeListener);
    updateIcons();
  }

  @Override
  protected void onDetachedFromWindow() {
    mThemeManager.removeChangeListener(mThemeChangeListener);
    super.onDetachedFromWindow();
  }

  private void updateIcons() {
    boolean googleSearchAvailable = isGoogleSearchAvailable();
    boolean themedIconsEnabled = mThemeManager != null && mThemeManager.isIconThemeEnabled();
    mGIcon.setImageResource(
        googleSearchAvailable
            ? themedIconsEnabled ? R.drawable.ic_super_g_themed : R.drawable.ic_super_g_color
            : R.drawable.ic_allapps_search);
    mMicIcon.setVisibility(googleSearchAvailable ? View.VISIBLE : View.GONE);
    mAiModeButton.setVisibility(googleSearchAvailable ? View.VISIBLE : View.GONE);
    boolean lensAvailable = googleSearchAvailable && isLensAvailable();
    mLensIcon.setVisibility(lensAvailable ? View.VISIBLE : View.GONE);

    if (googleSearchAvailable) {
      mMicIcon.setImageResource(
          themedIconsEnabled ? R.drawable.ic_mic_themed : R.drawable.ic_mic_color);
      mLensIcon.setImageResource(
          themedIconsEnabled ? R.drawable.ic_lens_themed : R.drawable.ic_lens_color);
      mAiModeButton.setImageResource(
          themedIconsEnabled ? R.drawable.ic_ai_mode_themed : R.drawable.ic_ai_mode_color);
    }
  }

  private boolean isGoogleSearchAvailable() {
    try {
      return getContext().getPackageManager().getApplicationInfo(GSA_PACKAGE, 0).enabled;
    } catch (PackageManager.NameNotFoundException ignored) {
      return false;
    }
  }

  private boolean isLensAvailable() {
    return resolve(new Intent().setComponent(new ComponentName(GSA_PACKAGE, LENS_ACTIVITY)))
        || resolve(new Intent(Intent.ACTION_VIEW, Uri.parse(LENS_URI)).setPackage(GSA_PACKAGE));
  }

  private void launchSearch() {
    Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(GSA_PACKAGE);
    if (launchSafely(search)) return;

    search = new Intent(Intent.ACTION_WEB_SEARCH).setPackage(GSA_PACKAGE);
    if (launchSafely(search)) return;

    if (launchSafely(new Intent(Intent.ACTION_WEB_SEARCH))) return;

    Intent launch = getContext().getPackageManager().getLaunchIntentForPackage(GSA_PACKAGE);
    if (launch != null && launchSafely(launch)) return;

    launchSafely(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
  }

  private void launchVoiceSearch() {
    if (!launchSafely(new Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GSA_PACKAGE))) {
      launchSearch();
    }
  }

  private void launchAiSearch() {
    String[] actions = {
      "com.google.android.PIXEL_SEARCH",
      Intent.ACTION_ASSIST,
      Intent.ACTION_VOICE_ASSIST,
      Intent.ACTION_WEB_SEARCH,
    };
    for (String action : actions) {
      if (launchSafely(new Intent(action).setPackage(GSA_PACKAGE))) return;
    }
    launchSearch();
  }

  private void launchLens() {
    Intent[] candidates = {
      new Intent(Intent.ACTION_MAIN)
          .setComponent(new ComponentName(GSA_PACKAGE, LENS_ACTIVITY))
          .putExtra("LensHomescreenShortcut", true)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
      new Intent(Intent.ACTION_VIEW, Uri.parse(LENS_URI)).setPackage(GSA_PACKAGE),
    };
    for (Intent candidate : candidates) {
      if (launchSafely(candidate)) return;
    }
    launchSearch();
  }

  private boolean resolve(Intent intent) {
    return intent.resolveActivity(getContext().getPackageManager()) != null;
  }

  private boolean launchSafely(Intent intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if ((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) == 0) {
      intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
    if (!resolve(intent)) return false;
    try {
      getContext().startActivity(intent);
      return true;
    } catch (ActivityNotFoundException | SecurityException ignored) {
      return false;
    }
  }

  @Override
  public MultiTranslateDelegate getTranslateDelegate() {
    return mTranslateDelegate;
  }

  @Override
  public void setReorderBounceScale(float scale) {
    mScaleForReorderBounce = scale;
    super.setScaleX(scale);
    super.setScaleY(scale);
  }

  @Override
  public float getReorderBounceScale() {
    return mScaleForReorderBounce;
  }

  @Override
  public void setHorizontalInsets(float insetPercentage) {
    mHorizontalInsets = insetPercentage;
    int insetPx = (int) (getWidth() * insetPercentage);
    setPadding(insetPx, getPaddingTop(), insetPx, getPaddingBottom());
  }

  @Override
  public float getHorizontalInsets() {
    return mHorizontalInsets;
  }
}
