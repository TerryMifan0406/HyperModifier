package com.aritxonly.myhypermodifier;

import android.app.Activity;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Installation-only lookup. Inherited Activity hooks filter unrelated instances immediately. */
final class NavigationHostLifecycleHooks {
    static void install(XposedModule module, Class<?> mainClass,
                        Consumer<Activity> resume, Consumer<Activity> pause) throws Exception {
        module.hook(method(mainClass, "onResume"))
                .setId("gpu-navigation-resume-" + mainClass.getName())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (mainClass.isInstance(chain.getThisObject())) resume.accept((Activity) chain.getThisObject());
                    return result;
                });
        module.hook(method(mainClass, "onPause"))
                .setId("gpu-navigation-pause-" + mainClass.getName())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (mainClass.isInstance(chain.getThisObject())) pause.accept((Activity) chain.getThisObject());
                    return chain.proceed();
                });
    }
    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredMethod(name); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
