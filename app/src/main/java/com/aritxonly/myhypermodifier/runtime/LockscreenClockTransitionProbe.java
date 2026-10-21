package com.aritxonly.myhypermodifier;

import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
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
import java.util.concurrent.atomic.AtomicLong;

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
    private static volatile boolean lastEnabled, lastMaterialEnabled;
    private static final AtomicBoolean SETTINGS_OBSERVER = new AtomicBoolean();
    private static Field doubleRow, editMode, timeGroup, hour, minute, colon;
    private static ClockGlyphSnapshot.Reader glyphReader;
    private static final AtomicLong CAPTURE_GENERATION = new AtomicLong();
    private static ClockGlyphSnapshot prepared; // main thread only; discarded on exit/cancel/off
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable EXPIRE_CAPTURE = () -> release("timeout");
    private static volatile String lastCapture = "none", captureState = "idle";
    private static final ProbeEventHistory HISTORY = new ProbeEventHistory(16);

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
            glyphReader = new ClockGlyphSnapshot.Reader(Class.forName("com.miui.clock.allInOne.TimeView", false, loader));
            editMode = field(clock, "mEditMode");
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
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            ClockMaterialPreview.sourceDetached((View) chain.getThisObject());
                            CLOCKS.remove((View) chain.getThisObject()); release("source-detached");
                        }
                        return chain.proceed();
                    }));
            for (Method method : events) {
                String name = method.getName();
                String event = method.getDeclaringClass().getSimpleName() + "." + name;
                boolean prepare = method.getDeclaringClass() == injector && name.equals("keyguardGoingAway");
                boolean remoteStart = method.getDeclaringClass() == remote && name.equals("startAnimation");
                boolean exitFinished = name.equals("onKeyguardExitFinished");
                boolean finish = name.equals("onKeyguardExitFinished") || name.equals("cancelKeyguardExitAnimation")
                        || name.equals("handleCancelKeyguardExitAnimation");
                handles.add(module.hook(method).setId("clock-transition-probe-" + event)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            // Settings are refreshed by the existing settings listener. No
                            // reload, task lookup, IPC or disk access in these callbacks.
                            if (ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled) {
                                EVENTS.incrementAndGet(); lastEvent = event;
                                if (prepare && (LINES.get() < MAX_LINES || ClockMaterialPreview.needsPreparation())) capture();
                                if (remoteStart) ClockMaterialPreview.remoteStart(chain.getArg(1));
                                observe(event);
                            }
                            long generation = CAPTURE_GENERATION.get();
                            Object materialToken = finish ? ClockMaterialPreview.token() : null;
                            boolean completed = false;
                            try {
                                Object result = chain.proceed(); completed = true;
                                return result;
                            } finally {
                                if (finish) {
                                    if (completed && exitFinished) ClockMaterialPreview.finished(materialToken);
                                    else ClockMaterialPreview.cancel(materialToken);
                                    releaseOnMain(name, generation);
                                }
                            }
                        }));
                module.deoptimize(method);
            }
            module.deoptimize(inflate); module.deoptimize(detach);
            hookCount = handles.size();
            installState = "installed";
            Log.i(TAG, "INSTALL ready hooks=" + hookCount);
            if (SETTINGS_OBSERVER.compareAndSet(false, true)) ModuleSettings.onChanged(() -> {
                boolean active = ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled;
                boolean material = ClockMaterialPreview.enabled();
                if (lastMaterialEnabled != material) {
                    lastMaterialEnabled = material;
                    ClockMaterialPreview.settingsChanged(loader);
                }
                if (lastEnabled == active) return;
                lastEnabled = active;
                // Called on the existing settings worker, only when this value changes.
                Log.i(TAG, "CONFIG active=" + active + " install=" + installState);
                if (!active) MAIN.post(() -> {
                    if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.clockTransitionProbeEnabled)
                        release("disabled");
                });
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
        out.println("    captureState=" + captureState + " lastCapture=" + lastCapture);
        for (String entry : HISTORY.snapshot()) out.println("    " + entry);
        ClockMaterialPreview.dump(out);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }

    private static void observe(String event) {
        if (LINES.get() >= MAX_LINES || LINES.getAndIncrement() >= MAX_LINES) return;
        long now = SystemClock.uptimeMillis();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            report(now + " " + event + " off-main; view snapshot skipped");
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
                message.append(" clock{class=").append(clock.getClass().getSimpleName())
                        .append(",horizontal=").append(horizontal).append(",shown=").append(clock.isShown())
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
            message.append(" capture=").append(captureState)
                    .append(" preparedGlyphs=").append(prepared == null ? 0 : prepared.glyphs.size());
            report(message.toString());
        } catch (ReflectiveOperationException | RuntimeException error) {
            // Observation failure never changes original method arguments/results.
            report(now + " " + event + " snapshot unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static void report(String message) {
        HISTORY.add(message); Log.i(TAG, message);
    }

    private static void capture() {
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        release("new-session");
        try {
            boolean geometry = LINES.get() < MAX_LINES;
            List<ClockGlyphSnapshot.Glyph> glyphs = geometry ? ClockGlyphSnapshot.newBuffer() : null;
            List<View> previewSources = ClockMaterialPreview.needsPreparation() ? new ArrayList<>() : null;
            StringBuilder roles = geometry ? new StringBuilder("sources") : null;
            for (View clock : CLOCKS.keySet()) {
                if (!clock.isShown() || !clock.isAttachedToWindow() || clock.getDisplay() == null
                        || clock.getDisplay().getDisplayId() != 0 || doubleRow.getBoolean(clock)
                        || editMode.getBoolean(clock)) continue;
                if (previewSources != null) previewSources.add(clock);
                if (geometry) {
                    String role = ClockGlyphCapturePolicy.role(clock.getClass().getName());
                    roles.append(' ').append(role);
                    glyphReader.append(glyphs, role, "hour", (View) hour.get(clock));
                    glyphReader.append(glyphs, role, "minute", (View) minute.get(clock));
                    glyphReader.append(glyphs, role, "colon", (View) colon.get(clock));
                }
            }
            if (geometry) {
                ClockGlyphSnapshot snapshot = ClockGlyphSnapshot.of(glyphs, roles.toString());
                // Unknown roles and glass are recorded, never declared replay-ready.
                lastCapture = snapshot.summary;
                prepared = snapshot.glyphs.isEmpty() ? null : snapshot;
                captureState = prepared == null ? "empty" : "captured-geometry-only";
                if (prepared != null) MAIN.postDelayed(EXPIRE_CAPTURE, 5000L);
            } else captureState = "preview-only:geometry-budget-exhausted";
            if (previewSources != null) ClockMaterialPreview.prepare(previewSources, hour, minute, colon);
        } catch (ReflectiveOperationException | RuntimeException error) {
            lastCapture = error.getClass().getSimpleName(); release("capture-failed");
        }
    }

    private static void releaseOnMain(String reason, long generation) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (CAPTURE_GENERATION.get() == generation) release(reason);
        } else MAIN.post(() -> {
            if (CAPTURE_GENERATION.get() == generation) release(reason);
        });
    }

    private static void release(String reason) {
        MAIN.removeCallbacks(EXPIRE_CAPTURE);
        CAPTURE_GENERATION.incrementAndGet();
        prepared = null; captureState = "released:" + reason;
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
