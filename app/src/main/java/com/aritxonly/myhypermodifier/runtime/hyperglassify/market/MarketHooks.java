package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Bridges Xiaomi Market's Activity lifecycle to the app-owned Compose navigation overlay. */
final class MarketHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY =
            "com.xiaomi.market.business_ui.main.MarketTabActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private MarketHooks() {
    }

    private static Method lifecycleMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try { return cursor.getDeclaredMethod(name); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = mainActivity.getDeclaredMethod("onCreate", Bundle.class);
            Method onDestroy = mainActivity.getDeclaredMethod("onDestroy");

            module.hook(onCreate)
                    .setId("market-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            MarketFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            Method onResume = lifecycleMethod(mainActivity, "onResume");
            module.hook(onResume)
                    .setId("market-floating-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (mainActivity.isInstance(chain.getThisObject())) {
                            MarketFloatingNavigation.attach((Activity) chain.getThisObject());
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("market-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            MarketFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatchTouchEvent)
                    .setId("market-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (target instanceof Activity && event instanceof MotionEvent) {
                            MarketFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Market navigation hooks", throwable);
            HookDiagnostics.failure("com.xiaomi.market", "小米应用商店悬浮导航", throwable);
        }
    }
}
