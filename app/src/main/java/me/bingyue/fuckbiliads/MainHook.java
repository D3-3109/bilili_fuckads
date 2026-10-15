package me.bingyue.fuckbiliads;

import android.util.Log;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

/** 适用于 tv.danmaku.bili 8.84.0（8840200）的商业开屏拦截。 */
public final class MainHook extends XposedModule {
    private static final String TAG = "FuckBiliAdsModern";
    private static final String TARGET = "tv.danmaku.bili";
    private static final String LEGACY_MODEL = "tv.danmaku.bili.ui.splash.ad.model.Splash";
    private static final String MODERN_MODEL = "tv.danmaku.bili.splash.ad.model.SplashOrder";
    private String processName;
    private boolean installed;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        log(Log.INFO, TAG, "loaded process=" + processName + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName()) || !TARGET.equals(processName) || installed) {
            return;
        }
        ClassLoader loader = param.getClassLoader();
        int count = 0;
        count += install(loader, LEGACY_MODEL, LEGACY_MODEL, Kind.LEGACY_VALIDITY);
        count += install(loader, "tv.danmaku.bili.ui.splash.ad.k", LEGACY_MODEL, Kind.SELECTOR);
        count += install(loader, "tv.danmaku.bili.splash.ad.core.SplashOrderSelectComponentKt",
                MODERN_MODEL, Kind.SELECTOR);
        // 覆盖直接使用实时广告结果的展示路径。
        count += install(loader, "tv.danmaku.bili.ui.splash.ad.page.q0", LEGACY_MODEL, Kind.FACTORY);
        count += install(loader, "tv.danmaku.bili.splash.ad.page.q", MODERN_MODEL, Kind.FACTORY);
        if ((getModuleApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            installHotDiagnostics(loader);
        }
        installed = true;
        log(Log.INFO, TAG, "ready hooks=" + count);
    }

    private enum Kind { LEGACY_VALIDITY, SELECTOR, FACTORY }

    private int install(ClassLoader loader, String className, String modelName, Kind kind) {
        int count = 0;
        Class<?> targetClass = requireClass(loader, className);
        for (Method method : targetClass.getDeclaredMethods()) {
            if (!matches(method, modelName, kind)) {
                continue;
            }
            String label = className + "#" + method.getName() + "/" + method.getParameterCount();
            Set<String> reportedContexts = ConcurrentHashMap.newKeySet();
            hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept(chain -> {
                String context = "source=none";
                for (Object arg : chain.getArgs()) {
                    if (arg instanceof Enum<?> && arg.getClass().getName().endsWith(".SplashSource")) {
                        context = "source=" + ((Enum<?>) arg).name();
                    }
                }
                if (kind == Kind.FACTORY) {
                    context += " material=" + (chain.getArg(0) != null);
                }
                if (reportedContexts.add(context)) {
                    log(Log.INFO, TAG, "blocked " + label + " " + context);
                }
                return kind == Kind.LEGACY_VALIDITY ? Boolean.FALSE : null;
            });
            count++;
            log(Log.INFO, TAG, "installed " + label);
        }
        if (count == 0) {
            throw new IllegalStateException("找不到必需的拦截方法：" + className);
        }
        return count;
    }

    private void installHotDiagnostics(ClassLoader loader) {
        Method check = requireMethod(requireClass(loader,
                "tv.danmaku.bili.splash.ad.core.a"), "a", Activity.class);
        Method getState = requireMethod(requireClass(loader,
                "tv.danmaku.bili.splash.ad.core.b"), "a");
        Class<?> stateClass = requireClass(loader, "r06.a");
        Method getInterval = requireMethod(stateClass, "g");
        Method getLastTime = requireMethod(stateClass, "h");
        hook(check).setPriority(PRIORITY_HIGHEST).setExceptionMode(ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    Object state = getState.invoke(null);
                    long intervalMs = ((Number) getInterval.invoke(state)).longValue() * 1000;
                    long lastTime = ((Number) getLastTime.invoke(state)).longValue();
                    long elapsedMs = SystemClock.elapsedRealtime() - lastTime;
                    log(Log.INFO, TAG, "hot-check intervalMs=" + intervalMs
                            + " elapsedMs=" + elapsedMs + " initialized=" + (lastTime != 0));
                    return chain.proceed();
                });
        log(Log.INFO, TAG, "installed diagnostic hot-check");
    }

    private static Class<?> requireClass(ClassLoader loader, String className) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("必需的目标类缺失：" + className, error);
        }
    }

    private static Method requireMethod(Class<?> targetClass, String name, Class<?>... parameters) {
        try {
            return targetClass.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("必需的目标方法缺失：" + targetClass.getName() + "#" + name, error);
        }
    }

    private static boolean matches(Method method, String modelName, Kind kind) {
        if (kind == Kind.LEGACY_VALIDITY) {
            return method.getName().equals("isValid") && method.getParameterCount() == 0
                    && method.getReturnType() == boolean.class && !Modifier.isStatic(method.getModifiers());
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (kind == Kind.SELECTOR) {
            return method.getReturnType().getName().equals(modelName);
        }
        return method.getReturnType().getName().equals("androidx.fragment.app.Fragment")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0].getName().equals(modelName);
    }
}
