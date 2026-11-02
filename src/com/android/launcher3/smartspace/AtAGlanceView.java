package com.android.launcher3.smartspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.UserManager;
import android.widget.FrameLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StateManager.StateListener;

/** Lunaris Smartspace hosted by A17's non-persistent workspace model item. */
public class AtAGlanceView extends FrameLayout implements
        SharedPreferences.OnSharedPreferenceChangeListener, StateListener<LauncherState> {
    private final Launcher mLauncher;
    private final SmartspaceView mSmartspace;
    private boolean mResumed;
    private boolean mVisible;
    private boolean mNormalState;

    public AtAGlanceView(Launcher launcher) {
        super(launcher);
        mLauncher = launcher;
        mSmartspace = new SmartspaceView(launcher);
        addView(mSmartspace, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateListening();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mResumed = mLauncher.hasBeenResumed();
        mNormalState = mLauncher.isInState(LauncherState.NORMAL)
                && !mLauncher.getStateManager().isInTransition();
        LauncherPrefs.getPrefs(getContext()).registerOnSharedPreferenceChangeListener(this);
        mLauncher.getStateManager().addStateListener(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        getContext().registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        updateListening();
    }

    @Override
    protected void onDetachedFromWindow() {
        LauncherPrefs.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        mLauncher.getStateManager().removeStateListener(this);
        getContext().unregisterReceiver(mScreenReceiver);
        mSmartspace.setActive(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        mVisible = visible;
        // View's constructor may dispatch visibility before our child is initialized.
        if (mSmartspace != null) updateListening();
    }

    @Override
    public void onStateTransitionStart(LauncherState state) {
        mNormalState = false;
        updateListening();
    }

    @Override
    public void onStateTransitionComplete(LauncherState state) {
        mNormalState = state == LauncherState.NORMAL;
        updateListening();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || LauncherPrefs.SHOW_SMARTSPACE.getSharedPrefKey().equals(key)) {
            updateListening();
        }
        if (key == null || key.startsWith("pref_smartspace_")) mSmartspace.refreshSettings();
    }

    public void setResumed(boolean resumed) {
        mResumed = resumed;
        updateListening();
    }

    private void updateListening() {
        PowerManager power = getContext().getSystemService(PowerManager.class);
        UserManager users = getContext().getSystemService(UserManager.class);
        mSmartspace.setActive(isAttachedToWindow() && mVisible && mResumed && mNormalState
                && power != null && power.isInteractive() && users != null && users.isUserUnlocked()
                && LauncherPrefs.SHOW_SMARTSPACE.get(getContext()));
    }
}
