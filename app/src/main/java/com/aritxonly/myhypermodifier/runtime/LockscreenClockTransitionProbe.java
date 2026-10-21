package com.aritxonly.myhypermodifier;

import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.io.PrintWriter;
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
    private static volatile String installState = "not-started", lastEvent = "none";
    private static volatile int hookCount, sourceCount;
    private static final AtomicInteger EVENTS = new AtomicInteger();
    private static volatile boolean lastEnabled;
    private static final AtomicBoolean SETTINGS_OBSERVER = new AtomicBoolean();
    private static Field doubleRow, timeGroup, hour, minute, colon;

    private LockscreenClockTransitionProbe() {}

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (Build.VERSION.SDK_INT < 36 || !INSTALLED.compareAndSet(false, true)) return;
        installState = "resolving";
        Log.i(TAG, "INSTALL resolving");
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
            Class<?> remote = Class.forName("com.android.systemui.keyguard.KeyguardService$1", false, loader);
            Class<?> finished = Class.forName("android.window.IRemoteTransitionFinishedCallback", false, loader);
            Class<?> transitionInfo = Class.forName("android.window.TransitionInfo", false, loader);
            // Resolve the whole contract first, before registering any observer.
            Method[] events = {
                    injector.getDeclaredMethod("keyguardGoingAway"),
                    remote.getDeclaredMethod("startAnimation", android.os.IBinder.class,
                            transitionInfo, android.view.SurfaceControl.Transaction.class, finished),
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
                String event = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                handles.add(module.hook(method).setId("clock-transition-probe-" + event)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            // Settings are refreshed by the existing settings listener. No
                            // reload, task lookup, IPC or disk access in these callbacks.
                            if (ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled) {
                                EVENTS.incrementAndGet(); lastEvent = event;
                                observe(event);
                            }
                            return chain.proceed();
                        }));
                module.deoptimize(method);
            }
            module.deoptimize(inflate); module.deoptimize(detach);
            hookCount = handles.size();
            installState = "installed";
            Log.i(TAG, "INSTALL ready hooks=" + hookCount);
            if (SETTINGS_OBSERVER.compareAndSet(false, true)) ModuleSettings.onChanged(() -> {
                boolean active = ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled;
                if (lastEnabled == active) return;
                lastEnabled = active;
                // Called on the existing settings worker, only when this value changes.
                Log.i(TAG, "CONFIG active=" + active + " install=" + installState);
            });
            HookDiagnostics.available("com.android.systemui", FEATURE);
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            hookCount = 0;
            installState = "failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
            Log.e(TAG, "INSTALL " + installState);
            INSTALLED.set(false);
            throw error;
        }
    }

    // Called by an existing SystemUI dump Hook. No new Binder endpoint and no
    // View traversal from the dumpsys thread; the last main-thread result is used.
    static void dump(PrintWriter out) {
        out.println("  MyHyperModifier clockTransition: state=" + installState
                + ", hooks=" + hookCount + ", moduleEnabled=" + ModuleSettings.moduleHooksEnabled
                + ", probeEnabled=" + ModuleSettings.clockTransitionProbeEnabled
                + ", events=" + EVENTS.get() + ", lines=" + LINES.get()
                + ", lastEvent=" + lastEvent + ", lastSources=" + sourceCount);
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
            sourceCount = found;
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
