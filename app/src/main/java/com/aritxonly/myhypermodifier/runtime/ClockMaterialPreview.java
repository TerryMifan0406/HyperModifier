package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent native TimeView material host. Never reparents or hides a source View. */
final class ClockMaterialPreview {
    // Same existing SystemUI internal window privilege; no new permission or endpoint.
    private static final int WINDOW_TYPE = 2017; // TYPE_STATUS_BAR_SUB_PANEL
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean METADATA_ATTEMPTED = new AtomicBoolean();
    private static volatile Factory factory;
    private static volatile Candidate candidate;
    private static volatile String state = "off", metadata = "not-requested";
    private static volatile int shownCount, nativeDrawCount;
    private static volatile String targetStatus = "not-seen", lastResult = "none";
    private static boolean attempted; // main thread: one experiment per explicit enable

    static Object token() { return candidate; }
    static boolean needsPreparation() { return enabled() && !attempted; }

    static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled
                && ModuleSettings.clockMaterialPreviewEnabled;
    }

    static void settingsChanged(ClassLoader loader) {
        if (enabled() && METADATA_ATTEMPTED.compareAndSet(false, true)) {
            try { factory = new Factory(loader); metadata = "ready"; }
            catch (ReflectiveOperationException | RuntimeException error) {
                metadata = "unavailable:" + failure(error);
            }
        }
        MAIN.post(() -> {
            if (!enabled()) { close("disabled"); attempted = false; }
        });
    }

    static void prepare(List<View> clocks, Field hour, Field minute, Field colon) {
        if (!enabled() || attempted) return;
        close("new-session"); attempted = true;
        Factory reader = factory;
        if (reader == null) { state = "skip:metadata-" + metadata; return; }
        try {
            List<CopiedPart> parts = new ArrayList<>();
            List<WeakReference<View>> sources = new ArrayList<>();
            int hours = 0, minutes = 0;
            Context context = null;
            for (View clock : clocks) {
                String role = ClockGlyphCapturePolicy.role(clock.getClass().getName());
                if (role.equals("HOUR_CLOCK")) hours++;
                else if (role.equals("MINUTE_CLOCK")) minutes++;
                else { state = "skip:unsupported-source"; return; }
                sources.add(new WeakReference<>(clock));
                context = clock.getContext().getApplicationContext();
            }
            if (hours != 1 || minutes != 1 || context == null) { state = "skip:source-pair"; return; }
            for (View clock : clocks) {
                reader.copy(parts, (View) hour.get(clock));
                reader.copy(parts, (View) minute.get(clock));
                reader.copy(parts, (View) colon.get(clock));
            }
            if (parts.isEmpty() || parts.size() > 4) { state = "skip:parts"; return; }
            Candidate value = new Candidate(context, clocks.get(0).getDisplay(), parts, sources);
            candidate = value; targetStatus = "not-seen"; lastResult = "sampling-radius=" + parts.get(0).blurRadius();
            state = "prepared:native-views=" + parts.size();
            MAIN.postDelayed(value.timeout, 1500L); // Bounded preparation watchdog, no polling.
        } catch (ReflectiveOperationException | RuntimeException error) {
            close("prepare-" + failure(error));
        }
    }

    // Binder callback: inspect immutable transition metadata only. Never access a View here.
    static void remoteStart(Object info) {
        Candidate value = candidate; Factory reader = factory;
        if (value == null || reader == null || !enabled()) return;
        if (value.shown) {
            MAIN.post(() -> { if (candidate == value) close("new-keyguard-transition"); }); return;
        }
        try {
            int flags = (Integer) reader.flags.invoke(info);
            if ((flags & 0x100) == 0) {
                MAIN.post(() -> { if (candidate == value) close("other-keyguard-transition"); }); return;
            }
            boolean home = false;
            List<?> changes = (List<?>) reader.changes.invoke(info);
            if (changes.size() > 64) return;
            for (Object change : changes) {
                Object task = reader.task.invoke(change);
                if (!(task instanceof android.app.ActivityManager.RunningTaskInfo running)) continue;
                int mode = (Integer) reader.mode.invoke(change);
                int activityType = (Integer) reader.activityType.invoke(task);
                int user = reader.user.getInt(task);
                String pkg = running.topActivity == null ? null : running.topActivity.getPackageName();
                home |= ClockMaterialPreviewPolicy.homeTarget(flags, mode, activityType, pkg,
                        user, Process.myUid() / 100000);
            }
            if (candidate == value) {
                value.home = home; targetStatus = "going-away home=" + home;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (candidate == value) value.home = false;
        }
    }

    static void finished(Object expected) {
        Candidate value = candidate;
        if (value == null || value != expected) return;
        MAIN.removeCallbacks(value.show);
        MAIN.postDelayed(value.show, 16L); // One next-frame attempt, never retry until source disappears.
    }

    static void cancel(Object expected) {
        if (candidate != expected) return;
        if (Looper.myLooper() == Looper.getMainLooper()) close("cancelled");
        else MAIN.post(() -> { if (candidate == expected) close("cancelled"); });
    }

    private static void show(Candidate value) {
        if (candidate != value || value.shown) return;
        boolean hidden = true;
        for (WeakReference<View> source : value.sources) {
            View view = source.get();
            if (view != null && view.isAttachedToWindow() && view.isShown()
                    && view.getWindowVisibility() == View.VISIBLE) {
                float alpha = 1;
                View parent = view;
                for (int i = 0; parent != null && i < 64; i++) {
                    alpha *= parent.getAlpha();
                    parent = parent.getParent() instanceof View next ? next : null;
                }
                if (parent != null || !Float.isFinite(alpha) || alpha > .01f) hidden = false;
            }
        }
        int rotation = value.display.getRotation();
        if (!ClockMaterialPreviewPolicy.show(enabled(), value.home, hidden,
                SystemClock.uptimeMillis() - value.created, rotation, value.parts.size())
                || rotation != value.rotation) {
            close("guard home=" + value.home + " hidden=" + hidden); return;
        }
        try {
            Context windowContext = value.context.createWindowContext(value.display, WINDOW_TYPE, null);
            List<Part> replicas = new ArrayList<>();
            Factory reader = factory;
            if (reader == null) { close("metadata-lost"); return; }
            for (CopiedPart part : value.parts) replicas.add(reader.rebuild(part, windowContext));
            value.root = new Host(windowContext, replicas);
            reader.bindSampling(value.root, replicas);
            value.manager = windowContext.getSystemService(WindowManager.class);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WINDOW_TYPE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setFitInsetsTypes(0);
            params.setTitle("MHM clock material experiment");
            value.manager.addView(value.root, params);
            reader.enableSampling(value.root, value.parts.get(0).blurRadius());
            value.shown = true; shownCount++;
            state = "shown:native-sampling-container-unverified";
            MAIN.removeCallbacks(value.timeout);
            MAIN.postDelayed(value.timeout, 500L);
        } catch (ReflectiveOperationException | RuntimeException error) { close("window-" + failure(error)); }
    }

    private static String failure(Throwable error) {
        String text = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static void close(String reason) {
        Candidate value = candidate; candidate = null;
        if (value != null) {
            MAIN.removeCallbacks(value.show); MAIN.removeCallbacks(value.timeout);
            if (value.root != null) {
                value.root.setVisibility(View.GONE);
                try { if (value.root.getParent() != null) value.manager.removeViewImmediate(value.root); }
                catch (RuntimeException error) {
                    // Make it invisible first; request normal removal if immediate removal was refused.
                    try { value.manager.removeView(value.root); } catch (RuntimeException ignored) {}
                    reason += ":remove-" + error.getClass().getSimpleName();
                }
                value.root.removeAllViews();
            }
        }
        state = "closed:" + reason;
    }

    static void dump(PrintWriter out) {
        out.println("    materialPreview: enabled=" + enabled() + " metadata=" + metadata + " state=" + state
                + " shown=" + shownCount + " nativeDrawn=" + nativeDrawCount + " target=" + targetStatus
                + " lastResult=" + lastResult);
    }

    private static final class Candidate {
        Host root;
        final Context context; // application context only; retained for at most this bounded experiment
        final Display display;
        final List<CopiedPart> parts;
        final List<WeakReference<View>> sources;
        final int rotation;
        final long created = SystemClock.uptimeMillis();
        WindowManager manager;
        volatile boolean home, shown;
        final Runnable show = () -> { if (candidate == this) ClockMaterialPreview.show(this); };
        final Runnable timeout = () -> { if (candidate == this) close("timeout"); };
        Candidate(Context context, Display display, List<CopiedPart> parts, List<WeakReference<View>> sources) {
            this.context = context; this.display = display; this.parts = parts;
            this.sources = sources; rotation = display.getRotation();
        }
    }

    private record CopiedPart(Object[] values, Paint paint, float[] glassData, Matrix screenMatrix,
                              Rect clip, int width, int height, float alpha, int blurRadius) {}
    private record Part(View replica, Matrix screenMatrix, Rect clip, int width, int height) {}

    private static final class Factory {
        final Constructor<?> constructor;
        final Field paint, glass, data, style, transparency;
        final Method chooseContainer, passWindow, backgroundMode, glassRadius, excludeBelow;
        final Field[] values;
        final Method applyGlass, flags, changes, task, mode, activityType;
        final Field user;
        Factory(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> time = Class.forName("com.miui.clock.allInOne.TimeView", false, loader);
            constructor = time.getConstructor(Context.class);
            style = field(time, "clockStyleInfo");
            transparency = field(Class.forName("com.miui.clock.module.ClockStyleInfo", false, loader), "glassTransparency");
            paint = field(time, "textPaint"); glass = field(time, "isMiGlassEffectEnable"); data = field(time, "glassData");
            String[] names = {"fontAlpha", "mColonAdvanceWidth", "mColonAlpha", "mColonSplitIndex", "mDrawTextX",
                    "mDrawTextY", "mFontStyle", "mText", "mTextColor", "textSizePx", "weight", "width", "height", "size"};
            values = new Field[names.length];
            for (int i = 0; i < names.length; i++) values[i] = field(time, names[i]);
            Class<?> blur = Class.forName("com.miui.clock.utils.MiuiBlurUtils", true, loader);
            for (String name : new String[]{"M_SET_MI_GLASS", "M_SET_MI_GLASS_CLIP", "M_SET_GLASS_EFFECT",
                    "M_SET_MI_VIEW_MATERIAL_TYPE", "M_SET_MI_VIEW_BLUR_MODE", "M_SET_MI_CUSTOM_SURFACE_COLOR_TYPE"})
                if (blur.getField(name).get(null) == null) throw new NoSuchMethodException(name);
            chooseContainer = capability(blur, "M_CHOOSE_BACKGROUND_BLUR_CONTAINER");
            passWindow = capability(blur, "M_SET_PASS_WINDOW_BLUR_ENABLED");
            backgroundMode = capability(blur, "M_SET_MI_BACKGROUND_BLUR_MODE");
            glassRadius = capability(blur, "M_SET_MI_GLASS_BLUR_RADIUS");
            excludeBelow = capability(blur, "M_DISABLE_MI_BACKGROUND_CONTAIN_BELOW");
            applyGlass = blur.getDeclaredMethod("setGlassEffectMethod", View.class, float[].class);
            Class<?> info = Class.forName("android.window.TransitionInfo", false, loader);
            Class<?> change = Class.forName("android.window.TransitionInfo$Change", false, loader);
            Class<?> taskInfo = Class.forName("android.app.TaskInfo", false, loader);
            flags = info.getMethod("getFlags"); changes = info.getMethod("getChanges");
            task = change.getMethod("getTaskInfo"); mode = change.getMethod("getMode");
            activityType = taskInfo.getMethod("getActivityType"); user = taskInfo.getField("userId");
        }
        void copy(List<CopiedPart> out, View source) throws ReflectiveOperationException {
            if (source == null || !source.isShown() || source.getAlpha() <= 0) return;
            Rect clip = new Rect();
            if (!source.getGlobalVisibleRect(clip) || clip.isEmpty()) return;
            if (!glass.getBoolean(source)) throw new IllegalArgumentException("not-glass");
            Object[] copied = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                copied[i] = values[i].get(source);
                if (copied[i] instanceof Float v && !Float.isFinite(v))
                    throw new IllegalArgumentException("nonfinite:" + values[i].getName());
                if (copied[i] instanceof String text && (text.isEmpty() || text.length() > 16))
                    throw new IllegalArgumentException("text-length");
            }
            Paint copiedPaint = new Paint((Paint) paint.get(source));
            float[] original = (float[]) data.get(source);
            if (original.length > 64) throw new IllegalArgumentException("glass-data");
            for (float v : original) if (!Float.isFinite(v)) throw new IllegalArgumentException("glass-data");
            float alpha = 1;
            View current = source;
            for (int i = 0; current != null && i < 64; i++) {
                alpha *= current.getAlpha();
                current = current.getParent() instanceof View parent ? parent : null;
            }
            if (current != null || !Float.isFinite(alpha)) throw new IllegalArgumentException("alpha");
            if (source.getWidth() <= 0 || source.getHeight() <= 0 || source.getWidth() > 8192
                    || source.getHeight() > 8192) throw new IllegalArgumentException("dimensions");
            Matrix matrix = new Matrix(); source.transformMatrixToGlobal(matrix);
            float[] transform = new float[9]; matrix.getValues(transform);
            for (float v : transform) if (!Float.isFinite(v)) throw new IllegalArgumentException("matrix");
            Object info = style.get(source);
            Object raw = info == null ? null : transparency.get(info);
            float amount = raw == null ? 0f : (Float) raw;
            if (!Float.isFinite(amount) || amount < 0f || amount > 1f)
                throw new IllegalArgumentException("glass-transparency");
            // Matches AllInOneBase.updateClockEffectsContainer, not a guessed kernel radius.
            int radius = (int) (10f + 40f * amount);
            if (!out.isEmpty() && out.get(0).blurRadius() != radius)
                throw new IllegalArgumentException("sampling-radius-mismatch");
            out.add(new CopiedPart(copied, copiedPaint, original.clone(), matrix, clip,
                    source.getWidth(), source.getHeight(), alpha, radius));
        }
        Part rebuild(CopiedPart source, Context context) throws ReflectiveOperationException {
            View replica = (View) constructor.newInstance(context);
            for (int i = 0; i < values.length; i++) values[i].set(replica, source.values()[i]);
            ((Paint) paint.get(replica)).set(source.paint());
            float[] target = (float[]) data.get(replica);
            if (target.length != source.glassData().length) throw new IllegalArgumentException("glass-data");
            System.arraycopy(source.glassData(), 0, target, 0, target.length);
            glass.setBoolean(replica, true); applyGlass.invoke(null, replica, target);
            replica.setAlpha(source.alpha()); replica.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            return new Part(replica, source.screenMatrix(), source.clip(), source.width(), source.height());
        }
        void bindSampling(Host host, List<Part> parts)
                throws ReflectiveOperationException {
            for (Part part : parts) chooseContainer.invoke(part.replica(), host);
        }
        void enableSampling(Host host, int radius) throws ReflectiveOperationException {
            // Same glass-container branch as ClockEffectUtils; one host for both native digits.
            // Invoke cached platform methods directly so failures are not swallowed by wrappers.
            Object enabled = passWindow.invoke(host, true);
            if (!Boolean.TRUE.equals(enabled)) throw new IllegalStateException("pass-window-refused");
            backgroundMode.invoke(host, 1);
            glassRadius.invoke(host, radius, radius);
            excludeBelow.invoke(host, true);
        }
        static Method capability(Class<?> blur, String name) throws ReflectiveOperationException {
            Object value = blur.getField(name).get(null);
            if (!(value instanceof Method method)) throw new NoSuchMethodException(name);
            return method;
        }
        static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
        }
    }

    private static final class Host extends ViewGroup {
        final List<Part> parts;
        final int[] position = new int[2];
        boolean failed, drawn;
        Host(Context context, List<Part> parts) {
            super(context); this.parts = parts;
            setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for (Part part : parts) addView(part.replica());
        }
        @Override protected void onMeasure(int width, int height) {
            setMeasuredDimension(MeasureSpec.getSize(width), MeasureSpec.getSize(height));
            for (Part part : parts) part.replica().measure(MeasureSpec.makeMeasureSpec(part.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(part.height(), MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            getLocationOnScreen(position);
            for (Part part : parts) {
                part.replica().layout(0, 0, part.width(), part.height());
                Matrix matrix = new Matrix(part.screenMatrix()); matrix.postTranslate(-position[0], -position[1]);
                part.replica().setAnimationMatrix(matrix);
            }
        }
        @Override protected void dispatchDraw(Canvas canvas) {
            if (failed) return;
            try {
                long time = getDrawingTime();
                for (int i = 0; i < parts.size(); i++) {
                    Part part = parts.get(i);
                    int save = canvas.save();
                    Rect clip = part.clip();
                    try {
                        canvas.clipRect(clip.left - position[0], clip.top - position[1],
                                clip.right - position[0], clip.bottom - position[1]);
                        drawChild(canvas, part.replica(), time);
                    } finally { canvas.restoreToCount(save); }
                }
                if (!drawn) { drawn = true; nativeDrawCount++; lastResult = "native-draw-submitted:visual-unverified"; }
            } catch (RuntimeException error) {
                failed = true; setVisibility(GONE);
                Candidate expected = candidate;
                MAIN.post(() -> { if (candidate == expected) close("draw-" + error.getClass().getSimpleName()); });
            }
        }
        @Override protected void onConfigurationChanged(Configuration config) {
            super.onConfigurationChanged(config);
            Candidate expected = candidate;
            MAIN.post(() -> { if (candidate == expected) close("configuration-changed"); });
        }
    }
}
