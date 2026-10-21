package com.aritxonly.myhypermodifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.text.FieldPosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** One shared, non-waking ticker, active only for visible status-bar and notification-header clocks. Main-thread state. */
final class StatusBarClockSecondsHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean REFRESH_PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<TextView, Binding> CLOCKS = new WeakHashMap<>();
    private static Field mode, controller, is24, demo, amPmStyle;
    private static Method updateTime;
    private static boolean queued, observing, screenOn, lastEnabled, lastHeaderEnabled, lastHideLeadingZero;
    private static Context application;
    private static final Runnable TICK = () -> {
        queued = false;
        boolean active = false;
        for (Binding binding : CLOCKS.values()) active |= binding.render();
        if (active) schedule();
    };
    private static final BroadcastReceiver SCREEN = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            if (screenOn) for (Binding binding : CLOCKS.values()) binding.configure();
            reconcile();
        }
    };

    private StatusBarClockSecondsHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> clock = Class.forName(
                    SYSTEM_UI + ".statusbar.views.MiuiClock", false, loader);
            mode = clock.getField("mClockMode");
            controller = clock.getField("mMiuiStatusBarClockController");
            amPmStyle = clock.getField("mAmPmStyle");
            is24 = controller.getType().getField("mIs24");
            demo = controller.getType().getField("mDemoMode");
            updateTime = clock.getDeclaredMethod("updateTime");
            module.hook(updateTime).setId("status-bar-clock-seconds-update")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Binding binding = CLOCKS.get(chain.getThisObject());
                        if (binding != null && binding.secondsEnabled()) {
                            binding.configure();
                            if (binding.render()) schedule();
                        }
                        if (binding != null) binding.hideLeadingZero();
                        return result;
                    });
            module.hook(clock.getDeclaredMethod("onAttachedToWindow"))
                    .setId("status-bar-clock-seconds-attach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        TextView view = (TextView) chain.getThisObject();
                        // Resolve IDs at attach only; date-only and lockscreen clocks stay stock.
                        int clockId = view.getResources().getIdentifier("clock", "id", SYSTEM_UI);
                        int bigId = view.getResources().getIdentifier("big_time", "id", SYSTEM_UI);
                        int horizontalId = view.getResources().getIdentifier("horizontal_time", "id", SYSTEM_UI);
                        boolean header = view.getParent() != null && view.getParent().getClass()
                                .getName().equals(SYSTEM_UI + ".qs.MiuiNotificationHeaderView")
                                && ((bigId != 0 && view.getId() == bigId)
                                || (horizontalId != 0 && view.getId() == horizontalId));
                        if (((clockId != 0 && view.getId() == clockId) || header)
                                && !CLOCKS.containsKey(view)) {
                            Binding binding = new Binding(view, header);
                            CLOCKS.put(view, binding);
                            view.getViewTreeObserver().addOnGlobalLayoutListener(binding);
                            ModuleSettings.ensureLoaded();
                            binding.hideLeadingZero();
                            reconcile();
                        }
                        return result;
                    });
            module.hook(clock.getDeclaredMethod("onDetachedFromWindow"))
                    .setId("status-bar-clock-seconds-detach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        TextView view = (TextView) chain.getThisObject();
                        Binding binding = CLOCKS.remove(view);
                        if (binding != null && view.getViewTreeObserver().isAlive()) {
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(binding);
                        }
                        Object result = chain.proceed();
                        reconcile();
                        return result;
                    });
            HookDiagnostics.available(SYSTEM_UI, "状态栏显秒");
            HookDiagnostics.available(SYSTEM_UI, "状态栏与通知中心时钟前导零/显秒");
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "状态栏显秒", error);
        }
    }

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.statusBarShowSeconds;
    }

    private static boolean headerEnabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.notificationClockShowSeconds;
    }

    private static boolean hideLeadingZeroEnabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.statusBarHideClockLeadingZero;
    }

    /** Called by the existing settings loader; unrelated changes do not replay any clock. */
    static void refresh() {
        if (!INSTALLED.get() || !REFRESH_PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            REFRESH_PENDING.set(false);
            boolean now = enabled();
            boolean header = headerEnabled();
            boolean hide = hideLeadingZeroEnabled();
            if (now == lastEnabled && header == lastHeaderEnabled && hide == lastHideLeadingZero) return;
            boolean statusChanged = now != lastEnabled;
            boolean headerChanged = header != lastHeaderEnabled;
            boolean hideChanged = hide != lastHideLeadingZero;
            lastEnabled = now;
            lastHeaderEnabled = header;
            lastHideLeadingZero = hide;
            for (Binding binding : CLOCKS.values()) {
                if (!hideChanged && !(binding.header ? headerChanged : statusChanged)) continue;
                TextView view = binding.view.get();
                if (view != null) {
                    // Rebuild from the current system time for both enable and restore.
                    binding.refreshStock(view);
                }
            }
            reconcile();
        });
    }

    private static void reconcile() {
        MAIN.removeCallbacks(TICK);
        queued = false;
        if ((!enabled() && !headerEnabled()) || CLOCKS.isEmpty()) {
            stopObservation();
            return;
        }
        if (!observing) {
            TextView view = CLOCKS.keySet().iterator().next();
            Context context = view.getContext().getApplicationContext();
            if (context == null) return;
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                context.registerReceiver(SCREEN, filter, Context.RECEIVER_NOT_EXPORTED);
                application = context;
                observing = true;
                PowerManager power = context.getSystemService(PowerManager.class);
                screenOn = power != null && power.isInteractive();
            } catch (RuntimeException ignored) {
                stopObservation();
                return;
            }
        }
        boolean active = false;
        for (Binding binding : CLOCKS.values()) {
            if (binding.secondsEnabled() && binding.format == null) binding.configure();
            active |= binding.render();
        }
        if (active) schedule();
    }

    private static void stopObservation() {
        if (observing) {
            try { application.unregisterReceiver(SCREEN); }
            catch (RuntimeException ignored) { /* SystemUI may be tearing down. */ }
        }
        observing = false;
        application = null;
        screenOn = false;
    }

    private static void schedule() {
        if (queued) return;
        queued = true;
        MAIN.postAtTime(TICK, SystemClock.uptimeMillis()
                + StatusBarClockSecondsPolicy.delayToNextSecond(System.currentTimeMillis()));
    }

    private static final class Binding implements ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<TextView> view;
        final boolean header;
        final int dateTime24Id, dateTime12Id;
        final Date date = new Date();
        final StringBuffer buffer = new StringBuffer(24);
        final FieldPosition position = new FieldPosition(0);
        SimpleDateFormat format;
        Locale locale;
        TimeZone zone;
        String pattern;
        boolean eligible, wasShown;

        Binding(TextView view, boolean header) {
            this.view = new WeakReference<>(view);
            this.header = header;
            dateTime24Id = header ? view.getResources().getIdentifier(
                    "status_bar_clock_date_time_format", "string", SYSTEM_UI) : 0;
            dateTime12Id = header ? view.getResources().getIdentifier(
                    "status_bar_clock_date_time_format_12", "string", SYSTEM_UI) : 0;
        }

        boolean secondsEnabled() { return header ? headerEnabled() : enabled(); }

        void configure() {
            TextView target = view.get();
            if (target == null) return;
            try {
                int clockMode = mode.getInt(target);
                eligible = clockMode == 0 || (header && clockMode == 2);
                if (!eligible) return;
                Object owner = controller.get(target);
                String desired = StatusBarClockSecondsPolicy.pattern(
                        is24.getBoolean(owner), amPmStyle.getInt(target) == 0,
                        hideLeadingZeroEnabled());
                if (clockMode == 2) {
                    int id = is24.getBoolean(owner) ? dateTime24Id : dateTime12Id;
                    if (id == 0) { eligible = false; return; }
                    desired = StatusBarClockSecondsPolicy.withSeconds(
                            target.getResources().getString(id), hideLeadingZeroEnabled());
                }
                Locale nextLocale = target.getResources().getConfiguration().getLocales().get(0);
                TimeZone nextZone = TimeZone.getDefault();
                if (format == null || !desired.equals(pattern) || !nextLocale.equals(locale)
                        || !nextZone.equals(zone)) {
                    pattern = desired;
                    locale = nextLocale;
                    zone = nextZone;
                    format = new SimpleDateFormat(pattern, locale);
                    format.setTimeZone(zone);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                eligible = false;
            }
        }

        boolean render() {
            TextView target = view.get();
            if (!secondsEnabled() || !screenOn || target == null || !eligible
                    || format == null || !target.isAttachedToWindow() || !target.isShown()
                    || target.getWindowVisibility() != View.VISIBLE) return false;
            boolean demoMode;
            try { demoMode = demo.getBoolean(controller.get(target)); }
            catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
            if (!StatusBarClockSecondsPolicy.shouldTick(secondsEnabled(), screenOn,
                    target.isAttachedToWindow(), target.isShown()
                            && target.getWindowVisibility() == View.VISIBLE, demoMode)) return false;
            date.setTime(System.currentTimeMillis());
            buffer.setLength(0);
            String text = format.format(date, buffer, position).toString();
            if (!TextUtils.equals(target.getText(), text)) target.setText(text);
            return true;
        }

        void hideLeadingZero() {
            if (!hideLeadingZeroEnabled()) return;
            TextView target = view.get();
            if (target == null) return;
            CharSequence text = target.getText();
            int zero = StatusBarClockSecondsPolicy.leadingHourZero(text);
            if (zero < 0) return;
            try {
                Object owner = controller.get(target);
                int clockMode = mode.getInt(target);
                if ((clockMode != 0 && !(header && clockMode == 2)) || !is24.getBoolean(owner)
                        || demo.getBoolean(owner)) return;
                target.setText(zero == 0 ? text.subSequence(1, text.length())
                        : TextUtils.concat(text.subSequence(0, zero), text.subSequence(zero + 1, text.length())));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Unknown clock variants retain the system text.
            }
        }

        void refreshStock(TextView target) {
            try { updateTime.invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* Keep stock hooks. */ }
        }

        @Override public void onGlobalLayout() {
            if (!secondsEnabled()) return;
            TextView target = view.get();
            boolean shown = target != null && target.isShown()
                    && target.getWindowVisibility() == View.VISIBLE;
            if (shown == wasShown) return;
            wasShown = shown;
            reconcile();
        }
    }
}
