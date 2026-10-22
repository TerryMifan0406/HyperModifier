package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Restore native Focus recipes on shade/keyguard/AOD; custom-image effects remain stock. */
final class FocusNotificationGlassTintHooks {
    private static final String PACKAGE = "com.android.systemui";
    private static final String PREFIX = PACKAGE + ".statusbar.notification.style.vieweffect.";
    private static final String FEATURE = "超级岛通知深色材质";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean OBSERVED = new AtomicBoolean();
    private static final AtomicInteger CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final Map<View, State> TARGETS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static volatile boolean hasTargets;
    private static Method setter;

    static final class State {
        final float[] stock = new float[ShadeCardGlassPolicy.COUNT];
        final float[] tuned = new float[ShadeCardGlassPolicy.COUNT];
        final Object[] args = new Object[1];
        final AtomicBoolean pending = new AtomicBoolean();
        int kind;
        boolean recorded;
        int applied = Integer.MIN_VALUE;

        Object[] observe(float[] original, int percent) {
            System.arraycopy(original, 0, stock, 0, stock.length);
            recorded = true;
            applied = percent;
            args[0] = percent < 0 ? original : FocusNotificationGlassPolicy.apply(original, kind, percent, tuned);
            return args;
        }

        Object[] replay(int percent) {
            args[0] = percent < 0 ? stock : FocusNotificationGlassPolicy.apply(stock, kind, percent, tuned);
            return args;
        }
    }

    static void install(XposedModule module, ClassLoader loader) throws Throwable {
        if (ModuleSettings.disableShadeGlassHooks || !INSTALLED.compareAndSet(false, true)) return;
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            Class<?> row = Class.forName(PACKAGE + ".statusbar.notification.row.ExpandableNotificationRow", false, loader);
            Method injector = row.getMethod("getInjector");
            Method background = injector.getReturnType().getMethod("getBackgroundNormal");
            setter = View.class.getMethod("setMiGlass", float[].class);
            String[] effects = {"FocusNotificationGlassEffect", "FocusNotificationGlassOnKeyguardEffect",
                    "FocusNotificationGlassOnKeyguardLightWallPaperEffect", "FocusNotificationGlassFullAodEffect"};
            int[] kinds = {FocusNotificationGlassPolicy.NORMAL, FocusNotificationGlassPolicy.KEYGUARD,
                    FocusNotificationGlassPolicy.KEYGUARD, FocusNotificationGlassPolicy.AOD};
            Method[] applies = new Method[effects.length];
            Method[] clears = new Method[effects.length];
            // Resolve all metadata before installing. No class/member lookup in material callbacks.
            for (int i = 0; i < effects.length; i++) {
                Class<?> effect = Class.forName(PREFIX + effects[i], false, loader);
                applies[i] = effect.getDeclaredMethod("apply", Object.class, Context.class);
                clears[i] = effect.getDeclaredMethod("clear", Object.class);
            }
            Method customApply = Class.forName(PREFIX + "FocusNotificationGlassCustomBgEffect", false, loader)
                    .getDeclaredMethod("apply", Object.class, Context.class);
            for (int i = 0; i < effects.length; i++) {
                int kind = kinds[i];
                handles.add(module.hook(applies[i]).setId("focus-dark-glass-apply-" + effects[i])
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            if (ModuleSettings.disableShadeGlassHooks) return chain.proceed();
                            View view = (View) background.invoke(injector.invoke(chain.getArg(0)));
                            if (view == null) return chain.proceed();
                            synchronized (TARGETS) {
                                State state = TARGETS.get(view);
                                if (state == null) {
                                    state = new State();
                                    TARGETS.put(view, state);
                                }
                                state.kind = kind;
                                hasTargets = true;
                            }
                            if (config() >= 0) ShadeCardGlassHooks.forgetGenericCard(view);
                            try {
                                return chain.proceed();
                            } catch (Throwable error) {
                                forget(view);
                                throw error;
                            }
                        }));
                handles.add(module.hook(clears[i]).setId("focus-dark-glass-clear-" + effects[i])
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            if (hasTargets) {
                                forget((View) background.invoke(injector.invoke(chain.getArg(0))));
                            }
                            return chain.proceed();
                        }));
            }
            // Row reuse into a custom image must invalidate queued refreshes as well.
            handles.add(module.hook(customApply).setId("focus-dark-glass-custom-image")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (hasTargets) {
                            forget((View) background.invoke(injector.invoke(chain.getArg(0))));
                        }
                        return chain.proceed();
                    }));
            ModuleSettings.onChanged(FocusNotificationGlassTintHooks::refresh);
            HookDiagnostics.available(PACKAGE, FEATURE);
        } catch (Throwable error) {
            for (int i = handles.size() - 1; i >= 0; i--) {
                try { handles.get(i).unhook(); }
                catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            }
            INSTALLED.set(false);
            throw error;
        }
    }

    /** Invoked by the existing shared setter hook before generic card classification. */
    static Object[] rewrite(View view, float[] original) {
        if (!hasTargets) return null;
        State state;
        synchronized (TARGETS) { state = TARGETS.get(view); }
        if (state == null) return null;
        if (Boolean.TRUE.equals(REPLAYING.get())) {
            state.args[0] = original;
            return config() >= 0 ? state.args : null;
        }
        if (!ShadeCardGlassPolicy.isActiveRecipe(original)) {
            forget(view);
            return null;
        }
        int percent = config();
        Object[] args = state.observe(original, percent);
        if (percent >= 0 && !OBSERVED.get() && OBSERVED.compareAndSet(false, true)) {
            HookDiagnostics.observed(PACKAGE, "超级岛通知深色材质已命中", "kind=" + state.kind + ", opacity=" + percent);
        }
        return percent >= 0 ? args : null;
    }

    private static void forget(View view) {
        synchronized (TARGETS) {
            TARGETS.remove(view);
            hasTargets = !TARGETS.isEmpty();
        }
    }

    static boolean tracked(View view) {
        if (!hasTargets) return false;
        synchronized (TARGETS) { return TARGETS.containsKey(view); }
    }

    private static int config() {
        if (ModuleSettings.disableMediaIslandHooks || !ModuleSettings.moduleHooksEnabled || ModuleSettings.disableShadeGlassHooks
                || !ModuleSettings.dynamicIslandNotificationOpacityEnabled) return -1;
        return Math.max(0, Math.min(100, ModuleSettings.dynamicIslandNotificationOpacity));
    }

    private static void refresh() {
        if (ModuleSettings.disableShadeGlassHooks) return;
        int percent = config();
        if (CONFIG.getAndSet(percent) == percent || !hasTargets) return;
        ArrayList<View> views;
        synchronized (TARGETS) { views = new ArrayList<>(TARGETS.keySet()); }
        for (View view : views) {
            if (view == null) continue;
            State state;
            synchronized (TARGETS) { state = TARGETS.get(view); }
            if (state == null || !state.pending.compareAndSet(false, true)) continue;
            State current = state;
            if (!view.post(() -> {
                current.pending.set(false);
                if (ModuleSettings.disableShadeGlassHooks) return;
                synchronized (TARGETS) { if (TARGETS.get(view) != current) return; }
                int latest = config();
                if (!current.recorded || current.applied == latest) return;
                try {
                    if (latest >= 0) ShadeCardGlassHooks.forgetGenericCard(view);
                    else ShadeCardGlassHooks.restoreGenericCard(view, current.stock);
                    REPLAYING.set(true);
                    setter.invoke(view, current.replay(latest));
                    current.applied = latest;
                } catch (ReflectiveOperationException error) {
                    Log.w("MyHyperModifier", "Focus material refresh failed", error);
                } finally { REPLAYING.remove(); }
            })) current.pending.set(false);
        }
    }
}
