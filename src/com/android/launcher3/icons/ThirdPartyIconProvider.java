package com.android.launcher3.icons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.customization.IconDatabase;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.pack.IconResolver;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.util.ComponentKey;

import javax.inject.Inject;

import static com.android.launcher3.icons.BaseIconFactory.CONFIG_HINT_NO_WRAP;

@LauncherAppSingleton
public class ThirdPartyIconProvider extends LauncherIconProvider {

    @Inject
    public ThirdPartyIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager) {
        super(context, themeManager);
    }

    @Override
    public void updateSystemState() {
        super.updateSystemState();
        mSystemState = mSystemState.withAdditionalValues(
                IconDatabase.getGlobal(mContext),
                "icon-pack-shape-v3"
        );
    }

    @Override
    public PersistedItemState getStateForApp(ApplicationInfo info) {
        PersistedItemState state = super.getStateForApp(info);
        return info == null ? state : state.withAdditionalValues(
                IconPackManager.get(mContext).getStateForPackage(info.packageName,
                        UserHandle.getUserHandleForUid(info.uid)));
    }

    @SuppressLint("WrongConstant")
    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        ComponentKey key = new ComponentKey(
                info.getComponentName(), UserHandle.getUserHandleForUid(info.applicationInfo.uid));

        IconResolver.DefaultDrawableProvider fallback =
                () -> super.getIcon(info, iconDpi);
        IconResolver resolver = IconPackManager.get(mContext).resolve(key);
        Drawable icon = resolver == null ? null : resolver.getIcon(iconDpi, fallback);

        if (icon == null) {
            return fallback.get();
        }
        // Pack artwork is already designed by the pack. Adaptive pack icons must opt out
        // too: Android 17 otherwise flattens their layers and reapplies the launcher's mask.
        icon.setChangingConfigurations(icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        return icon;
    }
}
