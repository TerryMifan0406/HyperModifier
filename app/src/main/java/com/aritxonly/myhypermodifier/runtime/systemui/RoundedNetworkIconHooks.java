package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Targets only SystemUI's mobile_signal / wifi_signal resource assignments, never animation frames. */
final class RoundedNetworkIconHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ImageView, Binding> VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<Resources, Cache> CACHES = new WeakHashMap<>();
    private static Class<?> iconClass;
    private static int mobileId, wifiId, mobileGroupId;
    private static int observedBindings, observedFallbacks, observedReplacements;
    private static boolean applying, lastEnabled;

    private RoundedNetworkIconHooks() {}

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.roundedNetworkIcons;
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        iconClass = Class.forName(SYSTEM_UI + ".statusbar.AlphaOptimizedImageView", false, loader);
        Method mobileConfig = Class.forName(SYSTEM_UI
                + ".statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView", false, loader)
                .getDeclaredMethod("onConfigurationChanged", Configuration.class);
        Method wifiConfig = Class.forName(SYSTEM_UI
                + ".statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView", false, loader)
                .getDeclaredMethod("onConfigurationChanged", Configuration.class);
        // These views inherit ImageView's setter. Filter by exact class and two cached IDs first.
        module.hook(ImageView.class.getDeclaredMethod("setImageResource", int.class))
                .setId("rounded-network-icons-resource")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    ImageView view = (ImageView) chain.getThisObject();
                    if (view.getClass() != iconClass || applying
                            || Looper.myLooper() != Looper.getMainLooper()) return chain.proceed();
                    if (mobileId == 0 || wifiId == 0) {
                        mobileId = view.getResources().getIdentifier("mobile_signal", "id", SYSTEM_UI);
                        wifiId = view.getResources().getIdentifier("wifi_signal", "id", SYSTEM_UI);
                        mobileGroupId = view.getResources().getIdentifier("mobile_group", "id", SYSTEM_UI);
                    }
                    int id = view.getId();
                    if ((id != mobileId && id != wifiId) || id == 0) return chain.proceed();
                    Object result = chain.proceed();
                    Binding binding = VIEWS.get(view);
                    if (binding == null) {
                        binding = new Binding();
                        VIEWS.put(view, binding);
                    }
                    binding.resource = (Integer) chain.getArg(0);
                    Drawable stock = view.getDrawable();
                    if (stock != binding.stock.get()) binding.stock = new WeakReference<>(stock);
                    binding.rounded.clear();
                    if (enabled()) {
                        int kind = id == mobileId ? RoundedNetworkIconPolicy.MOBILE : RoundedNetworkIconPolicy.WIFI;
                        if ((observedBindings & kind) == 0) {
                            observedBindings |= kind;
                            HookDiagnostics.observed(SYSTEM_UI, diagnosticName(kind) + "绑定", "已命中系统图标资源赋值");
                        }
                        apply(view, binding);
                    }
                    return result;
                });
        installConfigurationHook(module, mobileConfig, true);
        installConfigurationHook(module, wifiConfig, false);
        HookDiagnostics.available(SYSTEM_UI, "圆润信号和 WLAN 图标");
    }

    private static void installConfigurationHook(XposedModule module, Method method, boolean mobile) {
        module.hook(method).setId(mobile ? "rounded-mobile-configuration" : "rounded-wifi-configuration")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (!enabled()) return result;
                    int id = mobile ? mobileId : wifiId;
                    if (id == 0) return result;
                    View child = ((View) chain.getThisObject()).findViewById(id);
                    if (!(child instanceof ImageView view)) return result;
                    Binding binding = VIEWS.get(view);
                    if (binding == null) return result;
                    Drawable rounded = binding.rounded.get();
                    if (rounded == null || view.getDrawable() != rounded) return result;
                    applying = true;
                    try { view.setImageResource(binding.resource); }
                    finally { applying = false; }
                    binding.stock = new WeakReference<>(view.getDrawable());
                    binding.rounded.clear();
                    apply(view, binding);
                    return result;
                });
    }

    private static void apply(ImageView view, Binding binding) {
        Drawable current = view.getDrawable();
        Drawable rounded = binding.rounded.get();
        // Preserve unknown drawable assignments from themes/other modules instead of overwriting them.
        if (current != rounded && current != binding.stock.get()) {
            updateMobileOffset(view, binding, false);
            return;
        }
        if (!enabled()) {
            if (rounded != null && current == rounded) {
                applying = true;
                try { view.setImageResource(binding.resource); }
                finally { applying = false; }
                binding.stock = new WeakReference<>(view.getDrawable());
                binding.rounded.clear();
            }
            return;
        }
        if (current == rounded && rounded != null) {
            updateMobileOffset(view, binding, true);
            return;
        }
        Resources resources = view.getResources();
        Cache cache = CACHES.get(resources);
        if (cache == null) { cache = new Cache(); CACHES.put(resources, cache); }
        int generation = 31 * resources.getConfiguration().hashCode()
                + System.identityHashCode(view.getContext().getTheme());
        RoundedIconTuning tuning = RoundedIconTuning.DEFAULT;
        if (cache.generation != generation) {
            cache.mobileDefinitions.clear();
            cache.wifiDefinitions.clear();
            cache.generation = generation;
        }
        boolean mobile = view.getId() == mobileId;
        SparseArray<RoundedNetworkDrawable.Definition> definitions = mobile
                ? cache.mobileDefinitions : cache.wifiDefinitions;
        RoundedNetworkDrawable.Definition definition;
        int index = definitions.indexOfKey(binding.resource);
        if (index >= 0) definition = definitions.valueAt(index);
        else {
            int kind = 0;
            try { kind = RoundedNetworkIconPolicy.kind(resources.getResourceEntryName(binding.resource)); }
            catch (Resources.NotFoundException ignored) { /* Not a supported system resource. */ }
            definition = kind == 0 ? null : RoundedNetworkDrawable.read(resources,
                    view.getContext().getTheme(), binding.resource, kind, tuning);
            if (definitions.size() >= 64) definitions.clear();
            definitions.put(binding.resource, definition);
            int target = view.getId() == mobileId ? RoundedNetworkIconPolicy.MOBILE : RoundedNetworkIconPolicy.WIFI;
            if (definition == null && (observedFallbacks & target) == 0) {
                observedFallbacks |= target;
                String name;
                try { name = resources.getResourceName(binding.resource); }
                catch (Resources.NotFoundException ignored) { name = Integer.toHexString(binding.resource); }
                HookDiagnostics.unavailable(SYSTEM_UI, diagnosticName(target) + "资源回退",
                        name + (kind == 0 ? "：非支持的资源名" : "：矢量解析或几何转换未通过，保留原图"));
            }
        }
        if (definition == null || current == null) {
            updateMobileOffset(view, binding, false);
            return;
        }
        RoundedNetworkDrawable replacement = new RoundedNetworkDrawable(definition);
        replacement.copyAppearance(view, current);
        // ImageView then reapplies its own tint / blend mode to the replacement automatically.
        view.setImageDrawable(replacement);
        binding.rounded = new WeakReference<>(replacement);
        updateMobileOffset(view, binding, true);
        int target = view.getId() == mobileId ? RoundedNetworkIconPolicy.MOBILE : RoundedNetworkIconPolicy.WIFI;
        if ((observedReplacements & target) == 0) {
            observedReplacements |= target;
            HookDiagnostics.observed(SYSTEM_UI, diagnosticName(target) + "替换", "已加载系统矢量并设置圆润图形");
        }
    }

    private static void updateMobileOffset(ImageView view, Binding binding, boolean supported) {
        if (view.getId() != mobileId || mobileGroupId == 0) return;
        View group = null;
        ViewParent parent = view.getParent();
        while (parent instanceof View candidate) {
            if (candidate.getId() == mobileGroupId) { group = candidate; break; }
            parent = candidate.getParent();
        }
        View previous = binding.mobileGroup.get();
        if (previous != group) {
            RoundedMobileGroupOffset.setSupported(previous, false, enabled(), RoundedIconTuning.DEFAULT.mobileOffsetDp);
            binding.mobileGroup = new WeakReference<>(group);
        }
        RoundedMobileGroupOffset.setSupported(group, supported, enabled(), RoundedIconTuning.DEFAULT.mobileOffsetDp);
    }

    private static String diagnosticName(int kind) {
        return kind == RoundedNetworkIconPolicy.MOBILE ? "圆润移动信号：" : "圆润 WLAN：";
    }

    static void refresh() {
        if (!PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            PENDING.set(false);
            boolean active = enabled();
            if (active == lastEnabled) return;
            lastEnabled = active;
            for (var entry : VIEWS.entrySet()) {
                ImageView view = entry.getKey();
                if (view != null) apply(view, entry.getValue());
            }
            RoundedMobileGroupOffset.refresh(active, RoundedIconTuning.DEFAULT.mobileOffsetDp);
        });
    }

    private static final class Binding {
        int resource;
        WeakReference<View> mobileGroup = new WeakReference<>(null);
        WeakReference<Drawable> stock = new WeakReference<>(null);
        WeakReference<Drawable> rounded = new WeakReference<>(null);
    }
    private static final class Cache {
        int generation;
        final SparseArray<RoundedNetworkDrawable.Definition> mobileDefinitions = new SparseArray<>();
        final SparseArray<RoundedNetworkDrawable.Definition> wifiDefinitions = new SparseArray<>();
    }
}
