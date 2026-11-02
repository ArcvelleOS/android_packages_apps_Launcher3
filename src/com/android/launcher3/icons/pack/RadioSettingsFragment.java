/*
 * Copyright (C) 2020 Shift GmbH
 * Copyright (C) 2026 crDroid Android Project
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

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.List;

public abstract class RadioSettingsFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceClickListener {
    private SelectorWithWidgetPreference selectedPreference = null;
    private RadioHeaderPreference headerPref = null;

    protected abstract List<SelectorWithWidgetPreference> getPreferences(Context context);

    protected PreferenceGroup getRadioPreferenceGroup(Context context, PreferenceScreen screen) {
        return screen;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final PreferenceManager prefManager = getPreferenceManager();
        final Context context = prefManager.getContext();
        final PreferenceScreen screen = prefManager.createPreferenceScreen(context);

        headerPref = getHeader(context);
        if (headerPref != null) {
            screen.addPreference(headerPref);
        }

        addAdditionalPreferences(context, screen);
        loadPreferences(context, screen);
        setPreferenceScreen(screen);
    }

    protected void addAdditionalPreferences(Context context, PreferenceScreen screen) {}

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference instanceof SelectorWithWidgetPreference) {
            if (preference == selectedPreference && selectedPreference.isChecked()) {
                return true;
            }

            if (selectedPreference != null) {
                selectedPreference.setChecked(false);
            }
            selectedPreference = (SelectorWithWidgetPreference) preference;
            selectedPreference.setChecked(true);
            onSelected(preference.getKey());
            return true;
        } else {
            return false;
        }
    }

    protected RadioHeaderPreference getHeader(Context context) {
        return null;
    }

    protected void onSelected(String key) {
        if (headerPref != null) {
            headerPref.onRadioElementSelected(key);
        }
    }

    protected void reloadPreferences() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        // Remove radio preferences from their display group (backwards so we don't mess up
        // indices). Subclasses can group the dynamic rows inside a category.
        final Context context = getPreferenceManager().getContext();
        final PreferenceGroup group = getRadioPreferenceGroup(context, screen);
        final int numPreferences = group.getPreferenceCount();
        for (int i = numPreferences - 1; i >= 0; i--) {
            final Preference p = group.getPreference(i);
            if (p instanceof SelectorWithWidgetPreference) {
                group.removePreference(p);
            }
        }

        // Add radio preferences
        final PreferenceManager prefManager = getPreferenceManager();
        loadPreferences(context, screen);
    }

    private void loadPreferences(Context context, PreferenceScreen screen) {
        selectedPreference = null;
        final List<SelectorWithWidgetPreference> prefs = getPreferences(context);
        final PreferenceGroup group = getRadioPreferenceGroup(context, screen);
        for (final SelectorWithWidgetPreference p : prefs) {
            // The persisted selection supplied by getPreferences is the source of truth.
            if (p.isChecked()) {
                if (selectedPreference == null) {
                    selectedPreference = p;
                } else {
                    p.setChecked(false);
                }
            }
            p.setOnPreferenceClickListener(this);
            group.addPreference(p);
        }

        if (selectedPreference == null && !prefs.isEmpty()) {
            // The selected pack was removed. Persist the default selection as well as its UI.
            selectedPreference = prefs.get(0);
            selectedPreference.setChecked(true);
            onSelected(selectedPreference.getKey());
        } else if (headerPref != null && selectedPreference != null) {
            headerPref.onRadioElementSelected(selectedPreference.getKey());
        }
    }
}
