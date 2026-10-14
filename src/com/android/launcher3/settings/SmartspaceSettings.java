package com.android.launcher3.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.smartspace.SmartspaceEvent;

/** Lunaris provider-event filters within Miscellaneous. */
final class SmartspaceSettings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final PreferenceFragmentCompat mFragment;
    private final PreferenceGroup mEvents;

    SmartspaceSettings(PreferenceFragmentCompat fragment) {
        mFragment = fragment;
        mEvents = fragment.findPreference("smartspace_events");
        if (mEvents == null) return;
        Preference info = new Preference(context());
        info.setKey("smartspace_events_info");
        info.setSummary(R.string.smartspace_events_summary);
        info.setSelectable(false);
        info.setIconSpaceReserved(false);
        info.setOrder(-1);
        mEvents.addPreference(info);
        for (SmartspaceEvent event : SmartspaceEvent.values()) {
            SwitchPreferenceCompat preference = new SwitchPreferenceCompat(context());
            preference.setKey(event.preference.getSharedPrefKey());
            preference.setTitle(event.title);
            preference.setDefaultValue(true);
            preference.setPersistent(true);
            preference.setIconSpaceReserved(false);
            preference.setOrder(event.ordinal());
            mEvents.addPreference(preference);
        }
        refresh();
    }

    private Context context() {
        return mFragment.getPreferenceManager().getContext();
    }

    void refresh() {
        if (mEvents != null) mEvents.setEnabled(LauncherPrefs.SHOW_SMARTSPACE.get(context()));
    }

    void start() {
        LauncherPrefs.getPrefs(context()).registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    void stop() {
        LauncherPrefs.getPrefs(context()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || LauncherPrefs.SHOW_SMARTSPACE.getSharedPrefKey().equals(key)) refresh();
    }
}
