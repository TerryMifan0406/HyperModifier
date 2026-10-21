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

/** Native TimeView host with a bounded, restorable handoff from source glyphs. */
final class ClockMaterialPreview {
    // Same existing SystemUI internal window privilege; no new permission or endpoint.
    private static final int WINDOW_TYPE = 2017; // TYPE_STATUS_BAR_SUB_PANEL
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean METADATA_ATTEMPTED = new AtomicBoolean();
    private static volatile Factory factory;
    private static volatile Candidate candidate;
    private static volatile String state = "off", metadata = "not-requested";
    private static volatile int shownCount, nativeDrawCount, handoffCount;
    private static volatile String targetStatus = "not-seen", lastResult = "none";

    static Object token() { return candidate; }
    static boolean needsPreparation() { return enabled() && candidate == null; }

    static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && ModuleSettings.clockTransitionProbeEnabled;
    }

    static void settingsChanged(ClassLoader loader) {
        if (enabled() && METADATA_ATTEMPTED.compareAndSet(false, true)) {
            try { factory = new Factory(loader); metadata = "ready"; }
            catch (ReflectiveOperationException | RuntimeException error) {
                metadata = "unavailable:" + failure(error);
            }
        }
        MAIN.post(() -> {
            if (!enabled()) close("disabled");
        });
    }

    static void prepare(List<View> clocks, Field hour, Field minute, Field colon) {
        if (!needsPreparation()) return;
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
                if (home && value.remoteAt == 0) value.remoteAt = SystemClock.uptimeMillis();
                value.home = home; targetStatus = "going-away home=" + home;
                if (home) MAIN.post(value.show);
                else MAIN.post(() -> { if (candidate == value) close("target-not-home"); });
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (candidate == value) {
                value.home = false;
                MAIN.post(() -> { if (candidate == value) close("target-unavailable"); });
            }
        }
    }

    static void finished(Object expected) {
        Candidate value = candidate;
        if (value == null || value != expected) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> finished(expected)); return;
        }
        value.exitFinished = true;
        if (!value.handedOff || value.root == null) { close("exit-before-handoff"); return; }
        if (value.fading) return;
        value.fading = true;
        state = "fading:exit-finished";
        lastResult = "handoff-delay-ms=" + (value.handoffAt - value.remoteAt)
                + " exit-after-handoff-ms=" + (SystemClock.uptimeMillis() - value.handoffAt);
        // No guessed desktop target: retain source pose and only fade this handoff experiment.
        value.root.animate().alpha(0f).setDuration(160L).withEndAction(() -> {
            if (candidate == value) close("handoff-complete");
        }).start();
    }

    static void sourceDetached(View source) {
        Candidate value = candidate;
        if (value == null) return;
        for (WeakReference<View> ref : value.sources) {
            if (ref.get() == source) { close("source-detached"); return; }
        }
    }

    private static void handoff(Candidate value) {
        if (candidate != value || value.handedOff || value.root == null) return;
        if (!enabled() || !value.home || value.exitFinished || !value.root.isAttachedToWindow()
                || !value.root.isShown() || value.root.getWindowVisibility() != View.VISIBLE
                || value.display.getRotation() != value.rotation) { close("handoff-stale"); return; }
        try {
            for (CopiedPart part : value.parts) {
                View source = part.lease().target();
                if (source == null || !source.isAttachedToWindow() || source.getDisplay() == null
                        || source.getDisplay().getDisplayId() != 0 || !part.lease().acquire())
                    throw new IllegalStateException("source-unavailable-or-owned");
            }
            value.handedOff = true; handoffCount++;
            value.handoffAt = SystemClock.uptimeMillis();
            state = "handed-off:first-frame-committed";
            lastResult = "handoff-delay-ms=" + (value.handoffAt - value.remoteAt);
        } catch (ReflectiveOperationException | RuntimeException error) {
            close("handoff-" + failure(error));
        }
    }

    static void cancel(Object expected) {
        if (candidate != expected) return;
        if (Looper.myLooper() == Looper.getMainLooper()) close("cancelled");
        else MAIN.post(() -> { if (candidate == expected) close("cancelled"); });
    }

    private static void show(Candidate value) {
        if (candidate != value || value.root != null) return;
        int rotation = value.display.getRotation();
        if (!ClockMaterialPreviewPolicy.show(enabled(), value.home,
                SystemClock.uptimeMillis() - value.created, rotation, value.parts.size())
                || rotation != value.rotation || value.exitFinished) {
            close("guard home=" + value.home); return;
        }
        try {
            Context windowContext = value.context.createWindowContext(value.display, WINDOW_TYPE, null);
            List<Part> replicas = new ArrayList<>();
            Factory reader = factory;
            if (reader == null) { close("metadata-lost"); return; }
            for (CopiedPart part : value.parts) replicas.add(reader.rebuild(part, windowContext));
            value.root = new Host(windowContext, replicas, value, reader);
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
            if (!value.shown) state = "window-added:awaiting-attach";
            // One preparation/session watchdog also bounds a missing commit or exit callback.
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
            try {
                if (value.root != null) {
                    value.root.setVisibility(View.GONE);
                    value.root.animate().cancel();
                    try { if (value.root.getParent() != null) value.manager.removeViewImmediate(value.root); }
                    catch (RuntimeException error) {
                        // Make it invisible first; request normal removal if immediate removal was refused.
                        try { value.manager.removeView(value.root); } catch (RuntimeException ignored) {}
                        reason += ":remove-" + error.getClass().getSimpleName();
                    }
                    value.root.removeAllViews();
                }
            } catch (RuntimeException error) {
                reason += ":cleanup-" + error.getClass().getSimpleName();
            }
            for (CopiedPart part : value.parts) {
                try { part.lease().restore(); }
                catch (ReflectiveOperationException | RuntimeException error) {
                    reason += ":restore-" + error.getClass().getSimpleName();
                }
            }
        }
        state = "closed:" + reason;
    }

    static void dump(PrintWriter out) {
        out.println("    materialPreview: enabled=" + enabled() + " metadata=" + metadata + " state=" + state
                + " shown=" + shownCount + " nativeDrawn=" + nativeDrawCount + " handoffs=" + handoffCount + " target=" + targetStatus
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
        volatile long remoteAt;
        long handoffAt;
        boolean handedOff, exitFinished, fading;
        final Runnable committed = () -> MAIN.post(() -> handoff(this));
        final Runnable show = () -> { if (candidate == this) ClockMaterialPreview.show(this); };
        final Runnable timeout = () -> { if (candidate == this) close("timeout"); };
        Candidate(Context context, Display display, List<CopiedPart> parts, List<WeakReference<View>> sources) {
            this.context = context; this.display = display; this.parts = parts;
            this.sources = sources; rotation = display.getRotation();
        }
    }

    private record CopiedPart(Object[] values, Paint paint, float[] glassData, Matrix screenMatrix,
                              Rect clip, int width, int height, float alpha, int blurRadius,
                              ClockTransitionAlphaLease<View> lease) {}
    private record Part(View replica, Matrix screenMatrix, Rect clip, int width, int height) {}

    private static final class Factory {
        final Constructor<?> constructor;
        final ClockTransitionAlphaLease.Access<View> transitionAlpha;
        final Field paint, glass, data, style, transparency, passEnabled;
        final Method chooseContainer, passWindow, backgroundMode, glassRadius, excludeBelow;
        final Field[] values;
        final Method applyGlass, flags, changes, task, mode, activityType;
        final Field user;
        Factory(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> time = Class.forName("com.miui.clock.allInOne.TimeView", false, loader);
            constructor = time.getConstructor(Context.class);
            passEnabled = field(View.class, "mNeedPassWindowBlur");
            Method getAlpha = View.class.getDeclaredMethod("getTransitionAlpha");
            Method setAlpha = View.class.getDeclaredMethod("setTransitionAlpha", float.class);
            getAlpha.setAccessible(true); setAlpha.setAccessible(true);
            transitionAlpha = new ClockTransitionAlphaLease.Access<>() {
                public float read(View target) throws ReflectiveOperationException {
                    return (Float) getAlpha.invoke(target);
                }
                public void write(View target, float alpha) throws ReflectiveOperationException {
                    setAlpha.invoke(target, alpha);
                }
            };
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
            if (transitionAlpha.read(source) != 1f) throw new IllegalArgumentException("source-transition-owned");
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
                    source.getWidth(), source.getHeight(), alpha, radius, new ClockTransitionAlphaLease<>(source, transitionAlpha)));
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
            if (!host.isAttachedToWindow()) throw new IllegalStateException("sampling-before-attach");
            Object changed = passWindow.invoke(host, true);
            // Framework returns false both for deferral and for an already-enabled request.
            // Check the resulting state, not whether this call changed it.
            if (!passEnabled.getBoolean(host))
                throw new IllegalStateException("pass-window-disabled:changed=" + changed);
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
        final Candidate owner;
        final Factory reader;
        boolean failed, drawn, samplingReady;
        Host(Context context, List<Part> parts, Candidate owner, Factory reader) {
            super(context); this.parts = parts; this.owner = owner; this.reader = reader;
            setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for (Part part : parts) addView(part.replica());
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (candidate != owner || !enabled()) { fail("stale-attach"); return; }
            try {
                reader.enableSampling(this, owner.parts.get(0).blurRadius());
                reader.bindSampling(this, parts);
                samplingReady = true;
                owner.shown = true; shownCount++;
                state = "shown:native-sampling-container-unverified";
                // One commit callback, no per-frame observation. Sources remain untouched
                // until this independently rendered window has submitted a first frame.
                getViewTreeObserver().registerFrameCommitCallback(owner.committed);
            } catch (ReflectiveOperationException | RuntimeException error) {
                fail("sampling-" + failure(error));
            }
        }
        private void fail(String reason) {
            failed = true; setVisibility(GONE);
            MAIN.post(() -> { if (candidate == owner) close(reason); });
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
            if (failed || !samplingReady) return;
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
                if (!drawn) { drawn = true; nativeDrawCount++; }
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
