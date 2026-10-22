package com.aritxonly.myhypermodifier;

import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** HyperOS icon containers account for child width/padding rather than LayoutParams margins. */
final class StatusBarNetworkMarginsHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String MOBILE_VIEW = SYSTEM_UI
            + ".statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView";
    private static final String SPEED_VIEW = SYSTEM_UI + ".statusbar.views.NetworkSpeedView";
    private static final String WIFI_VIEW = SYSTEM_UI + ".statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final float[] LAST_LEFT = {Float.NaN, Float.NaN, Float.NaN};
    private static final float[] LAST_RIGHT = {Float.NaN, Float.NaN, Float.NaN};
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Map<ViewGroup, State> VIEWS = new WeakHashMap<>();

    private StatusBarNetworkMarginsHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        Set<Method> hooked = new HashSet<>();
        installView(module, loader, MOBILE_VIEW, hooked);
        installView(module, loader, SPEED_VIEW, hooked);
        installView(module, loader, WIFI_VIEW, hooked);
    }

    private static void installView(XposedModule module, ClassLoader loader, String name,
                                    Set<Method> hooked) {
        String feature = name.equals(MOBILE_VIEW) ? "移动信号左右边距"
                : name.equals(WIFI_VIEW) ? "WLAN 左右边距" : "实时网速左右边距";
        try {
            Class<?> owner = Class.forName(name, false, loader);
            Method measure = null;
            while (owner != null) {
                try {
                    measure = owner.getDeclaredMethod("onMeasure", int.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) { owner = owner.getSuperclass(); }
            }
            if (measure == null) throw new NoSuchMethodException(name + ".onMeasure");
            if (hooked.add(measure)) {
                module.hook(measure)
                        .setId("status-bar-network-margins-" + hooked.size())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (chain.getThisObject() instanceof ViewGroup root) {
                                String actual = root.getClass().getName();
                                if (MOBILE_VIEW.equals(actual) || SPEED_VIEW.equals(actual) || WIFI_VIEW.equals(actual)) {
                                    ModuleSettings.ensureLoaded();
                                    apply(root, SPEED_VIEW.equals(actual) ? 1 : WIFI_VIEW.equals(actual) ? 2 : 0);
                                }
                            }
                            return chain.proceed();
                        });
            }
            HookDiagnostics.available(SYSTEM_UI, feature);
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, feature, error);
        }
    }

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode;
    }

    private static float left(int kind) {
        return !enabled() ? 0f : kind == 1 ? ModuleSettings.statusBarNetworkSpeedMarginLeft
                : kind == 2 ? ModuleSettings.statusBarWifiMarginLeft : ModuleSettings.statusBarMobileMarginLeft;
    }

    private static float right(int kind) {
        return !enabled() ? 0f : kind == 1 ? ModuleSettings.statusBarNetworkSpeedMarginRight
                : kind == 2 ? ModuleSettings.statusBarWifiMarginRight : ModuleSettings.statusBarMobileMarginRight;
    }

    private static void apply(ViewGroup root, int kind) {
        float left = left(kind), right = right(kind);
        State state = VIEWS.get(root);
        if (state == null) {
            state = new State(root, kind);
            VIEWS.put(root, state);
        }
        state.policy.resolveInPlace(root.getPaddingLeft(), root.getPaddingRight(), left, right,
                root.getResources().getDisplayMetrics().density);
        int newLeft = state.policy.left(), newRight = state.policy.right();
        // A negative slot must not crop the text or WLAN edge extending into the gap.
        if (kind != 0) {
            boolean negative = newLeft < 0 || newRight < 0;
            boolean children = !negative && state.clipChildren;
            boolean clipPadding = !negative && state.clipToPadding;
            if (root.getClipChildren() != children) root.setClipChildren(children);
            if (root.getClipToPadding() != clipPadding) root.setClipToPadding(clipPadding);
        }
        if (root.getPaddingLeft() == newLeft && root.getPaddingRight() == newRight) return;
        if (root.isPaddingRelative()) {
            boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            root.setPaddingRelative(rtl ? newRight : newLeft, root.getPaddingTop(),
                    rtl ? newLeft : newRight, root.getPaddingBottom());
        } else root.setPadding(newLeft, root.getPaddingTop(), newRight, root.getPaddingBottom());
        if (kind == 1) ReflectiveAccess.setFieldValue(root, "mEmptyWidth", -1);
    }

    static void refresh() {
        if (!PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            PENDING.set(false);
            int changed = 0;
            for (int kind = 0; kind < 3; kind++) {
                float left = left(kind), right = right(kind);
                if (Float.compare(left, LAST_LEFT[kind]) != 0 || Float.compare(right, LAST_RIGHT[kind]) != 0) {
                    LAST_LEFT[kind] = left;
                    LAST_RIGHT[kind] = right;
                    changed |= 1 << kind;
                }
            }
            if (changed == 0) return;
            for (var entry : VIEWS.entrySet()) {
                ViewGroup root = entry.getKey();
                int kind = entry.getValue().kind;
                if (root == null || (changed & (1 << kind)) == 0) continue;
                apply(root, kind);
                root.requestLayout();
            }
        });
    }

    private static final class State {
        final int kind;
        final StatusBarMarginPolicy policy;
        final boolean clipChildren;
        final boolean clipToPadding;

        State(ViewGroup root, int kind) {
            this.kind = kind;
            policy = new StatusBarMarginPolicy(root.getPaddingLeft(), root.getPaddingRight());
            clipChildren = root.getClipChildren();
            clipToPadding = root.getClipToPadding();
        }
    }
}
