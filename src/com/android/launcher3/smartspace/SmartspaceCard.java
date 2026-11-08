package com.android.launcher3.smartspace;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemInfo;
import android.app.smartspace.uitemplatedata.TapAction;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Process;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.RemoteViews;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ActivityOptionsWrapper;

import java.net.URISyntaxException;

final class SmartspaceCard {
    private static final String TAG = "SmartspaceCard";
    private static final String EXTRA_SMARTSPACE_INTENT =
            "com.google.android.apps.gsa.smartspace.extra.SMARTSPACE_INTENT";
    private static final String GSA_WEATHER_ACTIVITY =
            "com.google.android.apps.search.weather.WeatherExportedActivity";
    private static final String PACKAGE_KEEP = "com.google.android.keep";
    private static final String PACKAGE_TASKS = "com.google.android.apps.tasks";

    final String id;
    final SmartspaceEvent event;
    final CharSequence title;
    final CharSequence subtitle;
    final CharSequence details;
    final Icon icon;
    final View.OnClickListener action;
    final SmartspaceTarget target;
    final String actionId;
    final long expiresAt;
    final RemoteViews remoteViews;

    private SmartspaceCard(String id, SmartspaceEvent event, CharSequence title,
            CharSequence subtitle, CharSequence details, Icon icon,
            View.OnClickListener action, long expiresAt, SmartspaceTarget target,
            String actionId) {
        this.id = id;
        this.event = event;
        this.title = title;
        this.subtitle = subtitle;
        this.details = details;
        this.icon = icon;
        this.action = action;
        this.expiresAt = expiresAt;
        this.target = target;
        this.actionId = actionId;
        this.remoteViews = target == null ? null : target.getRemoteViews();
    }

    static SmartspaceCard fromTarget(Context context, SmartspaceTarget target) {
        SmartspaceEvent event = SmartspaceEvent.forFeature(target.getFeatureType());
        if (!event.isEnabled(context) || !Process.myUserHandle().equals(target.getUserHandle())
                || target.getFeatureType() == SmartspaceTarget.FEATURE_STEP_DATE) return null;
        long now = System.currentTimeMillis();
        if (target.getCreationTimeMillis() > now
                || (target.getExpiryTimeMillis() > 0 && target.getExpiryTimeMillis() <= now)) {
            return null;
        }
        SmartspaceAction header = target.getHeaderAction();
        SmartspaceAction base = target.getBaseAction();
        SmartspaceAction display = header != null && hasDisplayContent(header) ? header : base;
        CharSequence title = display == null ? null : display.getTitle();
        CharSequence subtitle = display == null ? null : display.getSubtitle();
        CharSequence details = null;
        Icon icon = display == null ? null : display.getIcon();
        if (TextUtils.isEmpty(title) && base != null) title = base.getTitle();
        if (TextUtils.isEmpty(subtitle) && base != null) subtitle = base.getSubtitle();
        if (icon == null && base != null) icon = base.getIcon();
        SmartspaceAction click = hasAction(header) ? header : base;
        Intent intent = click == null ? null : click.getIntent();
        PendingIntent pending = click == null ? null : click.getPendingIntent();
        String actionId = click == null ? null : click.getId();
        BaseTemplateData template = target.getTemplateData();
        if (template != null) {
            SubItemInfo primary = allowedItem(context, template.getPrimaryItem(), event);
            SubItemInfo secondary = allowedItem(context, template.getSubtitleItem(), event);
            if (primary != null && primary.getText() != null) {
                CharSequence templateTitle = primary.getText().getText();
                if (!TextUtils.isEmpty(templateTitle)) title = templateTitle;
            }
            if (secondary != null && secondary.getText() != null) {
                CharSequence templateSubtitle = secondary.getText().getText();
                if (!TextUtils.isEmpty(templateSubtitle)) subtitle = templateSubtitle;
            }
            SubItemInfo subtitleSupplemental = allowedItem(context,
                    template.getSubtitleSupplementalItem(), event);
            SubItemInfo supplementalLine = allowedItem(context,
                    template.getSupplementalLineItem(), event);
            SubItemInfo supplementalAlarm = allowedItem(context,
                    template.getSupplementalAlarmItem(), event);
            Icon templateIcon = getTemplateIcon(primary, secondary, subtitleSupplemental,
                    supplementalLine, supplementalAlarm);
            if (templateIcon != null) {
                icon = templateIcon;
            }
            CharSequence templateDetails = joinDetails(subtitleSupplemental, supplementalLine,
                    supplementalAlarm);
            if (!TextUtils.isEmpty(templateDetails)) {
                details = templateDetails;
            }
            TapAction tap = getTemplateTapAction(primary, secondary, subtitleSupplemental,
                    supplementalLine, supplementalAlarm);
            if (tap != null && (tap.getUserHandle() == null
                    || Process.myUserHandle().equals(tap.getUserHandle()))) {
                intent = tap.getIntent();
                pending = tap.getPendingIntent();
                actionId = tap.getId() == null ? null : tap.getId().toString();
            }
        }
        SmartspaceContent content = SmartspaceContent.read(context, target, event);
        if (icon == null || (content.icon != null
                && (event == SmartspaceEvent.PACKAGES || event == SmartspaceEvent.DOORBELL))) {
            icon = content.icon;
        }
        if (intent == null && pending == null) {
            if (content.tap != null) {
                intent = content.tap.getIntent();
                pending = content.tap.getPendingIntent();
                actionId = content.tap.getId() == null ? null : content.tap.getId().toString();
            } else if (content.action != null) {
                intent = content.action.getIntent();
                pending = content.action.getPendingIntent();
                actionId = content.action.getId();
            }
        }
        Intent sanitizedIntent = sanitizeSmartspaceIntent(context, target, event, intent);
        if (sanitizedIntent != null) {
            intent = sanitizedIntent;
            pending = null;
        } else if (isTrampolineIntent(intent)) {
            intent = null;
        }
        content.remove(title);
        content.remove(subtitle);
        if (TextUtils.isEmpty(title) && !TextUtils.isEmpty(subtitle)) {
            title = subtitle;
            subtitle = null;
        }
        if (TextUtils.isEmpty(title)) title = content.takeFirst();
        if (TextUtils.isEmpty(title) && (icon != null || target.getRemoteViews() != null)) {
            title = context.getText(event.title);
        }
        if (TextUtils.isEmpty(subtitle)) subtitle = content.takeFirst();
        content.add(details);
        content.remove(title);
        content.remove(subtitle);
        details = TextUtils.join(" · ", content.lines);
        if (TextUtils.isEmpty(title)) return null;
        return new SmartspaceCard(target.getSmartspaceTargetId(), event, title, subtitle, details,
                icon, action(intent, pending), target.getExpiryTimeMillis(), target, actionId);
    }

    private static Icon getTemplateIcon(SubItemInfo... items) {
        for (SubItemInfo item : items) {
            if (item != null && item.getIcon() != null) {
                android.app.smartspace.uitemplatedata.Icon templateIcon = item.getIcon();
                Icon icon = templateIcon.getIcon();
                if (icon != null) return icon;
            }
        }
        return null;
    }

    private static CharSequence joinDetails(SubItemInfo... items) {
        StringBuilder details = new StringBuilder();
        for (SubItemInfo item : items) {
            if (item == null || item.getText() == null
                    || TextUtils.isEmpty(item.getText().getText())) continue;
            if (details.length() > 0) details.append(" · ");
            details.append(item.getText().getText());
        }
        return details.length() == 0 ? null : details.toString();
    }

    private static boolean hasDisplayContent(SmartspaceAction action) {
        return !TextUtils.isEmpty(action.getTitle()) || !TextUtils.isEmpty(action.getSubtitle())
                || action.getIcon() != null;
    }

    private static TapAction getTemplateTapAction(SubItemInfo... items) {
        for (SubItemInfo item : items) {
            if (item == null || item.getTapAction() == null) continue;
            TapAction tap = item.getTapAction();
            if ((tap.getIntent() != null || tap.getPendingIntent() != null)
                    && (tap.getUserHandle() == null
                    || Process.myUserHandle().equals(tap.getUserHandle()))) {
                return tap;
            }
        }
        return null;
    }

    static SubItemInfo allowedItem(Context context, SubItemInfo item,
            SmartspaceEvent targetEvent) {
        if (item == null || item.getLoggingInfo() == null) return item;
        // Keep the provider's accessory battery/charging rows with its paired-device card.
        if (targetEvent == SmartspaceEvent.BLUETOOTH) return item;
        int feature = item.getLoggingInfo().getFeatureType();
        return feature == SmartspaceTarget.FEATURE_UNDEFINED
                || feature == SmartspaceTarget.FEATURE_STEP_DATE
                || SmartspaceEvent.forFeature(feature).isEnabled(context) ? item : null;
    }

    private static boolean hasAction(SmartspaceAction action) {
        return action != null && (action.getPendingIntent() != null || action.getIntent() != null);
    }

    static View.OnClickListener action(Intent intent, PendingIntent pending) {
        if (intent == null && pending == null) return null;
        return view -> {
            try {
                if (pending != null) {
                    ActivityOptions options = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pending.send(view.getContext(), 0, null, null, null, null, options.toBundle());
                } else {
                    Intent launchIntent = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (isIntentResolvable(view.getContext(), launchIntent)) {
                        Launcher launcher = Launcher.getLauncher(view.getContext());
                        ActivityOptionsWrapper options = launcher.getActivityLaunchOptions(view, null);
                        launchIntent.setSourceBounds(Utilities.getViewBounds(view));
                        view.getContext().startActivity(launchIntent,
                                options != null ? options.toBundle() : null);
                    } else {
                        Toast.makeText(view.getContext(), R.string.smartspace_action_unavailable,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (PendingIntent.CanceledException | RuntimeException e) {
                Toast.makeText(view.getContext(), R.string.smartspace_action_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        };
    }

    private static boolean isTrampolineIntent(Intent intent) {
        if (intent == null) return false;
        ComponentName component = intent.getComponent();
        if (component != null) {
            String className = component.getClassName();
            if (className != null && (className.contains("SmartspaceTrampolineActivity")
                    || className.contains("ExportedSmartspaceTrampolineActivity"))) {
                return true;
            }
        }
        return false;
    }

    private static Intent unwrapSmartspaceIntent(Intent intent) {
        if (intent == null) return null;
        Intent inner = null;
        if (intent.getExtras() != null) {
            String uri = intent.getStringExtra(EXTRA_SMARTSPACE_INTENT);
            if (TextUtils.isEmpty(uri)) {
                uri = intent.getStringExtra("smartspace_intent");
            }
            if (TextUtils.isEmpty(uri)) {
                uri = intent.getStringExtra("intent");
            }
            if (!TextUtils.isEmpty(uri)) {
                try {
                    inner = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                } catch (URISyntaxException e) {
                    Log.w(TAG, "Failed to parse smartspace intent URI: " + uri, e);
                }
            }
            if (inner == null) {
                try {
                    Object extraIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (extraIntent instanceof Intent) {
                        inner = (Intent) extraIntent;
                    } else {
                        Object extra = intent.getParcelableExtra("intent");
                        if (extra instanceof Intent) {
                            inner = (Intent) extra;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return inner;
    }

    private static boolean isIntentResolvable(Context context, Intent intent) {
        if (intent == null || isTrampolineIntent(intent)) return false;
        PackageManager pm = context.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, 0);
        if (ri == null || ri.activityInfo == null) return false;
        if (!ri.activityInfo.exported && !ri.activityInfo.packageName.equals(context.getPackageName())) {
            return false;
        }
        if (!TextUtils.isEmpty(ri.activityInfo.permission)) {
            if (context.checkSelfPermission(ri.activityInfo.permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static String getTargetPackage(Intent intent) {
        if (intent == null) return null;
        String pkg = intent.getPackage();
        if (TextUtils.isEmpty(pkg) && intent.getComponent() != null) {
            pkg = intent.getComponent().getPackageName();
        }
        return pkg;
    }

    private static Intent getCalendarIntent() {
        return new Intent(Intent.ACTION_VIEW)
                .setData(ContentUris.appendId(
                        CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
                        System.currentTimeMillis()).build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    private static Intent sanitizeSmartspaceIntent(Context context, SmartspaceTarget target,
            SmartspaceEvent event, Intent intent) {
        PackageManager pm = context.getPackageManager();
        // 1. Google Weather: open dedicated Google Weather app if installed, else fallback to Google search weather
        if (event == SmartspaceEvent.WEATHER
                && target.getFeatureType() == SmartspaceTarget.FEATURE_WEATHER) {
            Intent weatherApp = pm.getLaunchIntentForPackage("com.google.android.apps.weather");
            if (weatherApp != null) {
                return weatherApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            Intent gsaWeather = new Intent()
                    .setComponent(new ComponentName(Utilities.GSA_PACKAGE,
                            GSA_WEATHER_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (isIntentResolvable(context, gsaWeather)) return gsaWeather;
        }

        String targetPackage = null;

        if (intent != null) {
            Intent candidate = intent;
            if (isTrampolineIntent(candidate) || (candidate.getExtras() != null
                    && (candidate.hasExtra(EXTRA_SMARTSPACE_INTENT)
                    || candidate.hasExtra("smartspace_intent")))) {
                Intent unwrapped = unwrapSmartspaceIntent(candidate);
                while (unwrapped != null && unwrapped != candidate) {
                    candidate = unwrapped;
                    unwrapped = unwrapSmartspaceIntent(candidate);
                }
            }
            targetPackage = getTargetPackage(candidate);
            if (isIntentResolvable(context, candidate)) {
                return candidate;
            }
            if (targetPackage == null) {
                targetPackage = getTargetPackage(intent);
            }
        }

        // Fallback from notification source package (e.g. Keep notes, messages, alerts)
        String notificationKey = target.getSourceNotificationKey();
        if (!TextUtils.isEmpty(notificationKey)) {
            String[] parts = notificationKey.split("\\|");
            String notifPkg = parts.length > 1 ? parts[1] : null;
            if (!TextUtils.isEmpty(notifPkg) && !Utilities.GSA_PACKAGE.equals(notifPkg)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(notifPkg);
                if (launchIntent != null) {
                    return launchIntent;
                }
                if (targetPackage == null) {
                    targetPackage = notifPkg;
                }
            }
        }

        // Fallback from target component name if not GSA
        ComponentName component = target.getComponentName();
        if (component != null && !Utilities.GSA_PACKAGE.equals(component.getPackageName())) {
            Intent compIntent = new Intent().setComponent(component);
            if (isIntentResolvable(context, compIntent)) {
                return compIntent;
            }
            Intent launchIntent = pm.getLaunchIntentForPackage(component.getPackageName());
            if (launchIntent != null) {
                return launchIntent;
            }
            if (targetPackage == null) {
                targetPackage = component.getPackageName();
            }
        }

        // Fallbacks based on Event / Feature type
        if (event == SmartspaceEvent.REMINDERS
                || target.getFeatureType() == SmartspaceTarget.FEATURE_REMINDER) {
            // Priority for reminders: Keep -> Tasks -> Calendar -> Assistant web
            Intent keepIntent = pm.getLaunchIntentForPackage(PACKAGE_KEEP);
            if (keepIntent != null) {
                return keepIntent;
            }
            Intent tasksIntent = pm.getLaunchIntentForPackage(PACKAGE_TASKS);
            if (tasksIntent != null) {
                return tasksIntent;
            }
            Intent calIntent = getCalendarIntent();
            if (isIntentResolvable(context, calIntent)) {
                return calIntent;
            }
            Intent webIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://assistant.google.com/reminders/mainview"));
            if (isIntentResolvable(context, webIntent)) {
                return webIntent;
            }
        } else if (event == SmartspaceEvent.CALENDAR
                || target.getFeatureType() == SmartspaceTarget.FEATURE_CALENDAR) {
            Intent calIntent = getCalendarIntent();
            if (isIntentResolvable(context, calIntent)) {
                return calIntent;
            }
        } else if (event == SmartspaceEvent.TIMERS
                || target.getFeatureType() == SmartspaceTarget.FEATURE_TIMER
                || target.getFeatureType() == SmartspaceTarget.FEATURE_STOPWATCH) {
            Intent timerIntent = new Intent(AlarmClock.ACTION_SHOW_TIMERS);
            if (isIntentResolvable(context, timerIntent)) {
                return timerIntent;
            }
        } else if (event == SmartspaceEvent.ALARMS
                || target.getFeatureType() == SmartspaceTarget.FEATURE_ALARM
                || target.getFeatureType() == SmartspaceTarget.FEATURE_UPCOMING_ALARM) {
            Intent alarmIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
            if (isIntentResolvable(context, alarmIntent)) {
                return alarmIntent;
            }
        }

        // If target package is known but not installed on device, direct to Play Store
        if (!TextUtils.isEmpty(targetPackage) && !Utilities.GSA_PACKAGE.equals(targetPackage)
                && !"android".equals(targetPackage)) {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + targetPackage));
            if (isIntentResolvable(context, marketIntent)) {
                return marketIntent;
            }
        }

        if (isIntentResolvable(context, intent)) {
            return intent;
        }

        return null;
    }
}
