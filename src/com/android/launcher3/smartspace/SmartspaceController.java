package com.android.launcher3.smartspace;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceManager;
import android.app.smartspace.SmartspaceSession;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Lunaris cards supplied exclusively by the ROM's configured Smartspace provider. */
final class SmartspaceController {
    private static final String TAG = "LauncherSmartspace";
    private final Context mContext;
    private final Consumer<List<SmartspaceCard>> mListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<SmartspaceTarget> mTargets = new ArrayList<>();
    private final Runnable mPublish = this::publish;
    private SmartspaceSession mSession;
    private SmartspaceSession.OnTargetsAvailableListener mSessionListener;
    private boolean mActive;
    private boolean mReceiverRegistered;

    SmartspaceController(Context context, Consumer<List<SmartspaceCard>> listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { publish(); }
    };

    void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        if (active) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            mReceiverRegistered = true;
            connectSession();
            publish();
        } else {
            mHandler.removeCallbacksAndMessages(null);
            if (mReceiverRegistered) mContext.unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
            disconnectSession();
            mTargets.clear();
        }
    }

    private void connectSession() {
        boolean enabled = false;
        for (SmartspaceEvent event : SmartspaceEvent.values()) enabled |= event.isEnabled(mContext);
        if (!enabled) return;

        SmartspaceManager manager = mContext.getSystemService(SmartspaceManager.class);
        if (manager == null) return;
        try {
            SmartspaceSession session = manager.createSmartspaceSession(
                    new SmartspaceConfig.Builder(mContext, "home")
                            .setSmartspaceTargetCount(20).build());
            mSession = session;
            mSessionListener = targets -> {
                if (!mActive || mSession != session) return;
                mTargets.clear();
                mTargets.addAll(targets);
                publish();
            };
            session.addOnTargetsAvailableListener(MAIN_EXECUTOR, mSessionListener);
            session.notifySmartspaceEvent(new SmartspaceTargetEvent.Builder(
                    SmartspaceTargetEvent.EVENT_UI_SURFACE_SHOWN).build());
            session.requestSmartspaceUpdate();
        } catch (RuntimeException e) {
            Log.w(TAG, "System Smartspace provider is unavailable", e);
            disconnectSession();
        }
    }

    private void disconnectSession() {
        SmartspaceSession session = mSession;
        mSession = null;
        if (session == null) return;
        try {
            if (mSessionListener != null) {
                session.removeOnTargetsAvailableListener(mSessionListener);
            }
            session.notifySmartspaceEvent(new SmartspaceTargetEvent.Builder(
                    SmartspaceTargetEvent.EVENT_UI_SURFACE_HIDDEN).build());
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot notify Smartspace service", e);
        } finally {
            mSessionListener = null;
            try {
                session.close();
            } catch (RuntimeException e) {
                Log.w(TAG, "Cannot close Smartspace session", e);
            }
        }
    }

    void notifyEvent(SmartspaceCard card, int type) {
        if (mSession == null || card == null || card.target == null) return;
        try {
            SmartspaceTargetEvent.Builder event = new SmartspaceTargetEvent.Builder(type)
                    .setSmartspaceTarget(card.target);
            if (card.actionId != null) event.setSmartspaceActionId(card.actionId);
            mSession.notifySmartspaceEvent(event.build());
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot send Smartspace event", e);
        }
    }

    private void publish() {
        if (!mActive) return;
        long now = System.currentTimeMillis();
        long nextUpdate = Long.MAX_VALUE;
        List<SmartspaceCard> cards = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (SmartspaceTarget target : mTargets) {
            try {
                SmartspaceCard card = SmartspaceCard.fromTarget(mContext, target);
                if (card != null && ids.add(card.id)) cards.add(card);
                if (target.getCreationTimeMillis() > now) {
                    nextUpdate = Math.min(nextUpdate, target.getCreationTimeMillis());
                }
                if (target.getExpiryTimeMillis() > now) {
                    nextUpdate = Math.min(nextUpdate, target.getExpiryTimeMillis());
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Cannot read Smartspace target", e);
            }
        }
        mListener.accept(cards);
        mHandler.removeCallbacks(mPublish);
        if (nextUpdate != Long.MAX_VALUE) {
            mHandler.postDelayed(mPublish, Math.max(1, nextUpdate - now));
        }
    }
}
