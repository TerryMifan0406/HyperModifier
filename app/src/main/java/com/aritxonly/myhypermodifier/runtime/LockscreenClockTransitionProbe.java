package com.aritxonly.myhypermodifier;

import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Stage-one observer only. Never changes clock geometry, visibility or unlock completion. */
final class LockscreenClockTransitionProbe {
    private static final String FEATURE = "锁屏桌面时钟过渡诊断";
    private static final String TAG = "MHMClockTransition";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    // Values do not refer back to their View keys. Access only on the main thread.
    private static final WeakHashMap<View, Boolean> CLOCKS = new WeakHashMap<>();
    private static final AtomicInteger LINES = new AtomicInteger();
    private static final int MAX_LINES = 64;
    private static Field doubleRow, timeGroup, hour, minute, colon;

    private LockscreenClockTransitionProbe() {}

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (Build.VERSION.SDK_INT < 36 || !INSTALLED.compareAndSet(false, true)) return;
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            Class<?> clock = Class.forName("com.miui.clock.allInOne.AllInOneBase", false, loader);
            Class<?> mediator = Class.forName("com.android.systemui.keyguard.KeyguardViewMediator", false, loader);
            Class<?> params = Class.forName("com.android.wm.shell.shared.compat.SurfaceTransition$Params", false, loader);
            Class<?> injector = Class.forName("com.android.keyguard.injector.KeyguardViewMediatorInjector", false, loader);
            doubleRow = field(clock, "mDoubleRow"); timeGroup = field(clock, "mTimeGroup");
            hour = field(clock, "mHourView"); minute = field(clock, "mMinuteView"); colon = field(clock, "mColonView");
            Method inflate = clock.getDeclaredMethod("onFinishInflate");
            Method detach = clock.getDeclaredMethod("onDetachedFromWindow");
            // Resolve the whole contract first, before registering any observer.
            Method[] events = {
                    mediator.getDeclaredMethod("startKeyguardExitAnimation", params),
                    mediator.getDeclaredMethod("handleStartKeyguardExitAnimationInner", params),
                    mediator.getDeclaredMethod("onKeyguardExitFinished", String.class),
                    mediator.getDeclaredMethod("cancelKeyguardExitAnimation"),
                    mediator.getDeclaredMethod("handleCancelKeyguardExitAnimation"),
                    injector.getDeclaredMethod("keyguardUnlockHide", long.class, long.class, String.class),
            };
            handles.add(module.hook(inflate).setId("clock-transition-probe-inflate")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            if (CLOCKS.size() >= 8) CLOCKS.remove(CLOCKS.keySet().iterator().next());
                            CLOCKS.put((View) chain.getThisObject(), Boolean.TRUE);
                        }
                        return result;
                    }));
            handles.add(module.hook(detach).setId("clock-transition-probe-detach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (Looper.myLooper() == Looper.getMainLooper()) CLOCKS.remove((View) chain.getThisObject());
                        return chain.proceed();
                    }));
            for (Method method : events) {
                String event = method.getName();
                handles.add(module.hook(method).setId("clock-transition-probe-" + event)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            // Settings are refreshed by the existing settings listener. No
                            // reload, task lookup, IPC or disk access in these callbacks.
                            if (ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled)
                                observe(event);
                            return chain.proceed();
                        }));
                module.deoptimize(method);
            }
            module.deoptimize(inflate); module.deoptimize(detach);
            HookDiagnostics.available("com.android.systemui", FEATURE);
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            INSTALLED.set(false);
            throw error;
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }

    private static void observe(String event) {
        if (LINES.get() >= MAX_LINES || LINES.getAndIncrement() >= MAX_LINES) return;
        long now = SystemClock.uptimeMillis();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.i(TAG, now + " " + event + " off-main; view snapshot skipped");
            return;
        }
        try {
            StringBuilder message = new StringBuilder().append(now).append(' ').append(event);
            int found = 0;
            for (View clock : CLOCKS.keySet()) {
                if (!clock.isAttachedToWindow() || clock.getDisplay() == null || clock.getDisplay().getDisplayId() != 0)
                    continue;
                found++;
                boolean horizontal = !doubleRow.getBoolean(clock);
                message.append(" clock{horizontal=").append(horizontal).append(",shown=").append(clock.isShown())
                        .append(",alpha=").append(clock.getAlpha());
                if (horizontal) {
                    appendBounds(message, "group", (View) timeGroup.get(clock));
                    appendBounds(message, "hour", (View) hour.get(clock));
                    appendBounds(message, "minute", (View) minute.get(clock));
                    appendBounds(message, "colon", (View) colon.get(clock));
                }
                message.append('}');
            }
            message.append(" sources=").append(found);
            if (LINES.get() == MAX_LINES) message.append(" log budget exhausted; restart SystemUI to collect again");
            Log.i(TAG, message.toString());
        } catch (ReflectiveOperationException | RuntimeException error) {
            // Observation failure never changes original method arguments/results.
            Log.w(TAG, now + " " + event + " snapshot unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static void appendBounds(StringBuilder out, String label, View view) {
        if (view == null) { out.append(' ').append(label).append("=null"); return; }
        Rect bounds = new Rect();
        boolean visible = view.getGlobalVisibleRect(bounds);
        out.append(' ').append(label).append("=[").append(bounds.left).append(',').append(bounds.top)
                .append(',').append(bounds.right).append(',').append(bounds.bottom).append("] visible=")
                .append(visible).append(" alpha=").append(view.getAlpha());
    }
}
