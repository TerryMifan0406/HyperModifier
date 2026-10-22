package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Final optical pass over native Glass, after this module's surface-specific recipes. */
final class UnifiedGlassHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static final Map<View, UnifiedGlassState> TARGETS = new WeakHashMap<>();
    private static final GlassRecipeRefreshState CONFIG = new GlassRecipeRefreshState();
    private static Method setter;

    static boolean isReplaying() { return Boolean.TRUE.equals(REPLAYING.get()); }
    private static boolean enabled() {
        return moduleHooksEnabled && unifiedGlassEnabled && !disableShadeGlassHooks;
    }

    static void install(XposedModule module, String packageName) {
        if (disableShadeGlassHooks || !INSTALLED.compareAndSet(false, true)) return;
        try {
            setter = View.class.getMethod("setMiGlass", float[].class);
            module.hook(setter).setId("unified-glass-optics")
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (disableShadeGlassHooks || isReplaying()) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        if (MediaIslandGate.protectedView(view)) return chain.proceed();
                        if (!isLoaded()) markLoaded(view.getContext());
                        float[] original = (float[]) chain.getArg(0);
                        if (!ShadeCardGlassPolicy.isActiveRecipe(original)) {
                            synchronized (TARGETS) { TARGETS.remove(view); }
                            return chain.proceed();
                        }
                        UnifiedGlassState state;
                        synchronized (TARGETS) {
                            state = TARGETS.get(view);
                            if (state == null) {
                                state = new UnifiedGlassState();
                                TARGETS.put(view, state);
                            }
                        }
                        boolean apply = state.observe(original, enabled(), unifiedGlassParameters);
                        Object result = apply ? chain.proceed(state.args) : chain.proceed();
                        state.recordApplied(apply);
                        return result;
                    });
            onChanged(UnifiedGlassHooks::refresh);
            HookDiagnostics.available(packageName, "统一玻璃材质");
        } catch (Throwable error) {
            INSTALLED.set(false);
            HookDiagnostics.failure(packageName, "统一玻璃材质", error);
        }
    }

    private static void refresh() {
        if (disableShadeGlassHooks || !CONFIG.changed(enabled(), unifiedGlassParameters)) return;
        ArrayList<View> views;
        synchronized (TARGETS) { views = new ArrayList<>(TARGETS.keySet()); }
        for (View view : views) {
            if (view == null) continue;
            UnifiedGlassState state;
            synchronized (TARGETS) { state = TARGETS.get(view); }
            if (state == null || !state.pending.compareAndSet(false, true)) continue;
            UnifiedGlassState current = state;
            if (!view.post(() -> {
                current.pending.set(false);
                if (disableShadeGlassHooks) return;
                synchronized (TARGETS) { if (TARGETS.get(view) != current) return; }
                boolean active = enabled();
                float[] config = unifiedGlassParameters;
                if (!current.prepareRefresh(active, config)) return;
                try {
                    // Surface hooks must not record our optical override as their stock recipe.
                    REPLAYING.set(true);
                    setter.invoke(view, current.args);
                    current.recordRefresh();
                } catch (ReflectiveOperationException error) {
                    Log.w("MyHyperModifier", "Unified Glass refresh failed", error);
                } finally { REPLAYING.remove(); }
            })) current.pending.set(false);
        }
    }
}
