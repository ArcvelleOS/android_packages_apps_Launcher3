/*
 * Copyright (C) 2020 Shift GmbH
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

package com.android.launcher3.icons.pack;

import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.icons.IconThemeController;

import java.util.ArrayList;
import java.util.List;

public class IconPackHeaderPreference extends RadioHeaderPreference {
    private static final String TAG = "IconPackPreview";
    private static final int PREVIEW_ICON_NUM = 8;

    private final Context context;
    private final ThemeManager mThemeManager;
    private final ThemeManager.ThemeChangeListener mThemeListener =
            () -> MAIN_EXECUTOR.execute(() -> onRadioElementSelected(null));
    private ImageView[] icons = null;
    private int mPreviewGeneration;

    public IconPackHeaderPreference(Context context) {
        this(context, null);
    }

    public IconPackHeaderPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context,
                androidx.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public IconPackHeaderPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
        mThemeManager = ThemeManager.INSTANCE.get(context);

        setLayoutResource(R.layout.preference_widget_icons_preview);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mThemeManager.addChangeListener(mThemeListener);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        final ImageView[] imageViews = {
            (ImageView) holder.findViewById(R.id.pref_icon_a),
            (ImageView) holder.findViewById(R.id.pref_icon_b),
            (ImageView) holder.findViewById(R.id.pref_icon_c),
            (ImageView) holder.findViewById(R.id.pref_icon_d),
            (ImageView) holder.findViewById(R.id.pref_icon_e),
            (ImageView) holder.findViewById(R.id.pref_icon_f),
            (ImageView) holder.findViewById(R.id.pref_icon_g),
            (ImageView) holder.findViewById(R.id.pref_icon_h)
        };
        this.icons = imageViews;
        onRadioElementSelected(null);
    }

    @Override
    public void onDetached() {
        mThemeManager.removeChangeListener(mThemeListener);
        this.icons = null;
        super.onDetached();
    }

    @Override
    public void onRadioElementSelected(String key) {
        if (icons == null || icons.length == 0) {
            return;
        }

        final ImageView[] previewViews = icons;
        final int generation = ++mPreviewGeneration;
        final IconProvider iconProvider = LauncherAppState.getInstance(context).getIconProvider();
        final PackageManager pm = context.getPackageManager();
        final InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        final int iconDpi = idp.fillResIconDpi;
        final int iconSize = idp.iconBitmapSize;
        final boolean customShapes = Flags.enableLauncherIconShapes();
        final IconThemeController theme = mThemeManager.getThemeController();
        final IconShape iconShape = mThemeManager.getIconShapeData().getValue();
        final int iconFlags = theme == null ? 0 : FLAG_THEMED;
        THREAD_POOL_EXECUTOR.execute(() -> {
            List<ResolveInfo> apps = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            apps.sort(new ResolveInfo.DisplayNameComparator(pm));
            List<Drawable> previews = new ArrayList<>();
            try (BaseIconFactory factory =
                    new BaseIconFactory(context, iconDpi, iconSize, customShapes, theme)) {
                for (int i = 0; i < Math.min(PREVIEW_ICON_NUM, apps.size()); i++) {
                    try {
                        Drawable icon = iconProvider.getIcon(apps.get(i).activityInfo, iconDpi);
                        BitmapInfo info = factory.createBadgedIconBitmap(icon);
                        IconShape shape = customShapes && iconFlags != 0 && info.isFullBleed()
                                ? iconShape : null;
                        previews.add(info.newIcon(context, iconFlags, shape));
                    } catch (RuntimeException e) {
                        // An app can disappear while its preview is being loaded.
                        Log.w(TAG, "Unable to load an icon preview", e);
                        previews.add(null);
                    }
                }
            }
            MAIN_EXECUTOR.execute(() -> {
                if (icons != previewViews || generation != mPreviewGeneration) return;
                for (int i = 0; i < previewViews.length; i++) {
                    previewViews[i].setImageDrawable(i < previews.size() ? previews.get(i) : null);
                }
            });
        });
    }
}
