package com.aritxonly.myhypermodifier;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
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
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.view.animation.LinearInterpolator;

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
            Point viewport = new Point();
            clocks.get(0).getDisplay().getRealSize(viewport);
            RectF sourceInk = new RectF();
            for (CopiedPart part : parts) sourceInk.union(part.ink());
            ClockMvpGeometry.Plan motion = ClockMvpGeometry.plan(new ClockMvpGeometry.Box(
                    sourceInk.left, sourceInk.top, sourceInk.right, sourceInk.bottom), viewport.x, viewport.y,
                    clocks.get(0).getResources().getDisplayMetrics().densityDpi);
            if (motion == null) { state = "skip:uncalibrated-display-or-ink"; return; }
            Candidate value = new Candidate(context, clocks.get(0).getDisplay(), parts, sources, motion);
            value.sourceInk = new ClockMvpGeometry.Box(sourceInk.left, sourceInk.top, sourceInk.right, sourceInk.bottom);
            candidate = value; targetStatus = "not-seen"; lastResult = "sampling-radius=" + parts.get(0).blurRadius();
            state = "prepared:native-views=" + parts.size();
            MAIN.postDelayed(value.timeout, 1500L); // Bounded preparation watchdog, no polling.
            ClockMvpPageBridge.read(context, before -> value.pageBefore = before);
            MAIN.post(value.warm); // Leave original GoingAway work first; prepare before remote target arrives.
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
                if (home && value.pageQueryRequested.compareAndSet(false, true)) ClockMvpPageBridge.read(value.context, after -> MAIN.post(() -> {
                    if (candidate != value) return;
                    value.pageApproved = ClockMvpPagePolicy.approve(value.pageBefore, after);
                    targetStatus = "home=true before=" + value.pageBefore + " after=" + after;
                    if (value.pageApproved) value.show.run();
                    else close("page-not-first-or-not-fresh");
                }));
                else if (!home) MAIN.post(() -> { if (candidate == value) close("target-not-home"); });
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
        state = value.arrived ? "arrived:exit-finished" : "moving:exit-finished";
        lastResult = "handoff-delay-ms=" + (value.handoffAt - value.remoteAt)
                + " exit-after-handoff-ms=" + (SystemClock.uptimeMillis() - value.handoffAt)
                + " preparation-ms=" + value.preparationCost
                + " prepared-before-remote-ms=" + (value.remoteAt - value.preparedAt)
                + " activation-alpha=" + value.activationAlpha + " " + value.fontShape;
        finishAtTarget(value);
    }

    private static void finishAtTarget(Candidate value) {
        if (candidate != value || value.root == null || !value.exitFinished || !value.arrived || value.fading) return;
        value.fading = true;
        state = "fading:calibrated-target";
        // The desktop clock remains visible in this explicitly calibrated MVP.
        value.root.animate().alpha(0f).setDuration(120L).setInterpolator(new LinearInterpolator())
                .withEndAction(() -> { if (candidate == value) close("mvp-complete"); }).start();
    }

    static void sourceDetached(View source) {
        Candidate value = candidate;
        if (value == null || value.handedOff) return;
        // After commit the independent native glyphs must outlive keyguard detachment.
        for (WeakReference<View> ref : value.sources) {
            if (ref.get() == source) { close("source-detached"); return; }
        }
    }

    private static void handoff(Candidate value) {
        if (candidate != value || value.handedOff || value.root == null) return;
        if (!enabled() || !value.home || !value.pageApproved || value.exitFinished || !value.root.isAttachedToWindow()
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
            if (value.fonts != null) value.root.startMotion();
            else state = "handed-off:awaiting-native-font";
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
        if (candidate != value || value.activationRequested) return;
        if (!ClockMaterialPreviewPolicy.show(enabled(), value.home,
                SystemClock.uptimeMillis() - value.created, value.display.getRotation(), value.parts.size())
                || value.exitFinished) { close("activation-stale"); return; }
        value.activationRequested = true;
        if (value.root != null) value.root.arm();
    }

    private static void warm(Candidate value) {
        if (candidate != value || value.root != null) return;
        int rotation = value.display.getRotation();
        if (!ClockMaterialPreviewPolicy.prepare(enabled(),
                SystemClock.uptimeMillis() - value.created, rotation, value.parts.size())
                || rotation != value.rotation || value.exitFinished) {
            close("prewarm-stale"); return;
        }
        long start = SystemClock.uptimeMillis();
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
            if (!ClockMvpFontMorph.prepare(() -> prepareFonts(value, reader, replicas)))
                throw new IllegalStateException("font-worker-busy");
            value.preparedAt = SystemClock.uptimeMillis();
            value.preparationCost = value.preparedAt - start;
            if (!value.shown) state = "window-prepared:awaiting-attach";
            // One preparation/session watchdog also bounds a missing commit or exit callback.
        } catch (ReflectiveOperationException | RuntimeException error) { close("window-" + failure(error)); }
    }

    private static void prepareFonts(Candidate value, Factory reader, List<Part> replicas) {
        if (candidate != value) return;
        long start = SystemClock.uptimeMillis();
        try {
            List<ClockMvpFontMorph> fonts = new ArrayList<>(replicas.size());
            RectF ink = new RectF();
            float ratio = ClockMvpGeometry.fontRatioMultiplier(value.sourceInk);
            for (int i = 0; i < replicas.size(); i++) {
                if (candidate != value) return;
                CopiedPart part = value.parts.get(i);
                // No View reads or writes here: only immutable copied parameters,
                // independent Paint/Path and cached native metadata.
                ClockMvpFontMorph font = new ClockMvpFontMorph(replicas.get(i).replica(), reader.fontAccess,
                        part.values(), part.paint(), part.screenMatrix(), ratio);
                fonts.add(font); ink.union(font.endInk);
            }
            ClockMvpGeometry.Plan motion = ClockMvpGeometry.plan(value.sourceInk,
                    new ClockMvpGeometry.Box(ink.left, ink.top, ink.right, ink.bottom),
                    ClockMvpGeometry.WIDTH, ClockMvpGeometry.HEIGHT, ClockMvpGeometry.DENSITY);
            if (motion == null) throw new IllegalArgumentException("shaped-target");
            long cost = SystemClock.uptimeMillis() - start;
            MAIN.post(() -> {
                if (candidate != value || value.root == null) return;
                value.fonts = fonts; value.motion = motion;
                value.fontShape = "native-wdth=" + fonts.get(0).targetWidth + " HGHT=" + fonts.get(0).targetHeight
                        + " reachable-ratio=" + fonts.get(0).reachableRatio + " uniform-scale=" + motion.scale()
                        + " font-preparation-ms=" + cost;
                lastResult += " " + value.fontShape;
                if (value.handedOff) value.root.startMotion();
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            MAIN.post(() -> { if (candidate == value) close("font-prepare-" + failure(error)); });
        }
    }

    private static String failure(Throwable error) {
        String text = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static void close(String reason) {
        Candidate value = candidate; candidate = null;
        if (value != null) {
            MAIN.removeCallbacks(value.warm); MAIN.removeCallbacks(value.show); MAIN.removeCallbacks(value.timeout);
            try {
                if (value.root != null) {
                    value.root.disarm();
                    value.root.setVisibility(View.GONE);
                    value.root.animate().cancel();
                    if (value.motionAnimator != null) {
                        value.motionAnimator.removeAllUpdateListeners(); value.motionAnimator.removeAllListeners();
                        value.motionAnimator.cancel();
                    }
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
        ClockMvpGeometry.Plan motion;
        ClockMvpGeometry.Box sourceInk;
        ValueAnimator motionAnimator;
        String fontShape = "not-prepared";
        List<ClockMvpFontMorph> fonts;
        final long created = SystemClock.uptimeMillis();
        WindowManager manager;
        volatile boolean home, shown;
        volatile long remoteAt;
        long handoffAt, preparedAt, preparationCost;
        float activationAlpha;
        boolean handedOff, exitFinished, fading, activationRequested, arrived, pageApproved, motionStarted;
        volatile ClockMvpPagePolicy.Snapshot pageBefore;
        final AtomicBoolean pageQueryRequested = new AtomicBoolean();
        final Runnable committed = () -> MAIN.post(() -> handoff(this));
        final Runnable warm = () -> { if (candidate == this) ClockMaterialPreview.warm(this); };
        final Runnable show = () -> { if (candidate == this) ClockMaterialPreview.show(this); };
        final Runnable timeout = () -> { if (candidate == this) close("timeout"); };
        Candidate(Context context, Display display, List<CopiedPart> parts, List<WeakReference<View>> sources,
                  ClockMvpGeometry.Plan motion) {
            this.context = context; this.display = display; this.parts = parts; this.motion = motion;
            this.sources = sources; rotation = display.getRotation();
        }
    }

    private record CopiedPart(Object[] values, Paint paint, float[] glassData, Matrix screenMatrix,
                              Rect clip, int width, int height, float alpha, int blurRadius,
                              ClockTransitionAlphaLease<View> lease, RectF ink) {}
    private record Part(View replica, Matrix screenMatrix, Rect clip, int width, int height) {}

    private static final class Factory {
        final Constructor<?> constructor;
        final ClockMvpFontMorph.Access fontAccess;
        final ClockTransitionAlphaLease.Access<View> transitionAlpha;
        final Field paint, glass, data, style, transparency, passEnabled, textPath, colonPath, minutePath;
        final Method chooseContainer, passWindow, backgroundMode, glassRadius, excludeBelow;
        final Field[] values;
        final Method applyGlass, flags, changes, task, mode, activityType;
        final Field user;
        Factory(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> time = Class.forName("com.miui.clock.allInOne.TimeView", false, loader);
            constructor = time.getConstructor(Context.class);
            fontAccess = new ClockMvpFontMorph.Access(time);
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
            textPath = field(time, "mTextPath"); colonPath = field(time, "mColonPath");
            minutePath = field(time, "mMinuteOnlyPath");
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
                    source.getWidth(), source.getHeight(), alpha, radius, new ClockTransitionAlphaLease<>(source, transitionAlpha),
                    ink(source, copied, matrix)));
        }
        RectF ink(View source, Object[] copied, Matrix matrix) throws ReflectiveOperationException {
            String text = (String) copied[7];
            ClockGlyphCapturePolicy.Plan plan = ClockGlyphCapturePolicy.plan(text.length(),
                    (Integer) copied[3], (Float) copied[2], (Float) copied[0]);
            if (plan == null) throw new IllegalArgumentException("ink-plan");
            RectF result = new RectF();
            float x = (Float) copied[4], y = (Float) copied[5];
            if (plan.split()) {
                if (plan.firstAlpha() > 0f) includeInk(result, (Path) colonPath.get(source), x, y, matrix);
                if (plan.secondAlpha() > 0f) includeInk(result, (Path) minutePath.get(source),
                        x + (Float) copied[1], y, matrix);
            } else includeInk(result, (Path) textPath.get(source), x, y, matrix);
            if (result.isEmpty()) throw new IllegalArgumentException("empty-ink");
            return result;
        }
        static void includeInk(RectF result, Path path, float x, float y, Matrix matrix) {
            if (path == null || path.isEmpty()) return;
            RectF ink = new RectF(); path.computeBounds(ink, true);
            ink.offset(x, y); matrix.mapRect(ink); result.union(ink);
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
        float currentOpacity(CopiedPart part) throws ReflectiveOperationException {
            View source = part.lease().target();
            if (source == null || !source.isAttachedToWindow() || !source.isShown()
                    || source.getWindowVisibility() != View.VISIBLE) return 0f;
            float alpha = 1f;
            View parent = source;
            for (int i = 0; parent != null && i < 64; i++) {
                alpha *= parent.getAlpha() * transitionAlpha.read(parent);
                parent = parent.getParent() instanceof View next ? next : null;
            }
            if (parent != null) return 0f;
            return ClockMaterialPreviewPolicy.opacity(part.alpha(), (Float) part.values()[0],
                    alpha, values[0].getFloat(source));
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

    private static final class Host extends ViewGroup implements ViewTreeObserver.OnPreDrawListener {
        final List<Part> parts;
        final int[] position = new int[2];
        final Candidate owner;
        final Factory reader;
        final GlyphLayer layer;
        boolean failed, drawn, samplingReady, armed;
        Host(Context context, List<Part> parts, Candidate owner, Factory reader) {
            super(context); this.parts = parts; this.owner = owner; this.reader = reader;
            setAlpha(0f); // Warm window, fonts and native display lists without covering the source.
            setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            layer = new GlyphLayer(this);
            addView(layer);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (candidate != owner || !enabled()) { fail("stale-attach"); return; }
            try {
                reader.enableSampling(this, owner.parts.get(0).blurRadius());
                reader.bindSampling(this, parts);
                samplingReady = true;
                state = "prewarmed:sampling-ready";
                if (owner.activationRequested) arm();
            } catch (ReflectiveOperationException | RuntimeException error) {
                fail("sampling-" + failure(error));
            }
        }
        void arm() {
            if (armed || failed || !samplingReady || owner.shown) return;
            armed = true;
            getViewTreeObserver().addOnPreDrawListener(this);
            invalidate();
        }
        void disarm() {
            if (armed && getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(this);
            armed = false;
            if (getViewTreeObserver().isAlive()) getViewTreeObserver().unregisterFrameCommitCallback(owner.committed);
        }
        @Override public boolean onPreDraw() {
            disarm(); // One-shot; no per-frame metadata traversal or reflection.
            if (candidate != owner || owner.exitFinished || !owner.home || !enabled()
                    || owner.display.getRotation() != owner.rotation) { fail("activation-stale"); return true; }
            try {
                float minimum = 1f;
                for (int i = 0; i < parts.size(); i++) {
                    float opacity = reader.currentOpacity(owner.parts.get(i));
                    if (opacity <= 0f) { fail("source-already-faded"); return true; }
                    parts.get(i).replica().setAlpha(opacity);
                    minimum = Math.min(minimum, opacity);
                }
                owner.activationAlpha = minimum;
                getViewTreeObserver().registerFrameCommitCallback(owner.committed);
                setAlpha(1f);
                owner.shown = true; shownCount++;
                state = "activated:opacity-matched";
            } catch (ReflectiveOperationException | RuntimeException error) {
                fail("activation-" + failure(error));
            }
            return true;
        }
        private void fail(String reason) {
            failed = true; setVisibility(GONE);
            MAIN.post(() -> { if (candidate == owner) close(reason); });
        }
        void startMotion() {
            if (owner.motionStarted || owner.fonts == null) return;
            owner.motionStarted = true;
            ClockMvpGeometry.Plan p = owner.motion;
            state = "moving:calibrated-desktop";
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            owner.motionAnimator = animator;
            animator.setDuration(420L);
            animator.setInterpolator(new PathInterpolator(.22f, 0f, .15f, 1f));
            animator.addUpdateListener(value -> {
                if (candidate != owner || failed) return;
                float fraction = value.getAnimatedFraction();
                try {
                    for (int i = 0; i < owner.fonts.size(); i++) owner.fonts.get(i).apply(fraction);
                    float scale = 1f + (p.scale() - 1f) * fraction;
                    layer.setScaleX(scale); layer.setScaleY(scale);
                    layer.setTranslationX(p.moveX() * fraction); layer.setTranslationY(p.moveY() * fraction);
                } catch (Throwable error) { fail("font-morph-" + failure(error)); }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (candidate != owner || failed) return;
                    owner.arrived = true;
                    state = "arrived:native-font-target";
                    finishAtTarget(owner);
                }
            });
            animator.start();
        }
        @Override protected void onMeasure(int width, int height) {
            setMeasuredDimension(MeasureSpec.getSize(width), MeasureSpec.getSize(height));
            layer.measure(width, height);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            getLocationOnScreen(position);
            layer.layout(0, 0, r - l, b - t);
            layer.setPivotX(owner.motion.pivotX() - position[0]);
            layer.setPivotY(owner.motion.pivotY() - position[1]);
        }
        @Override protected void onConfigurationChanged(Configuration config) {
            super.onConfigurationChanged(config);
            Candidate expected = candidate;
            MAIN.post(() -> { if (candidate == expected) close("configuration-changed"); });
        }
    }
    /** Only this RenderNode moves; the native sampling container stays in screen space. */
    private static final class GlyphLayer extends ViewGroup {
        final Host host;
        GlyphLayer(Host host) {
            super(host.getContext()); this.host = host;
            setClipChildren(false); setClipToPadding(false);
            for (Part part : host.parts) addView(part.replica());
        }
        @Override protected void onMeasure(int width, int height) {
            setMeasuredDimension(MeasureSpec.getSize(width), MeasureSpec.getSize(height));
            for (Part part : host.parts) part.replica().measure(
                    MeasureSpec.makeMeasureSpec(part.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(part.height(), MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (Part part : host.parts) {
                part.replica().layout(0, 0, part.width(), part.height());
                Matrix matrix = new Matrix(part.screenMatrix());
                matrix.postTranslate(-host.position[0], -host.position[1]);
                part.replica().setAnimationMatrix(matrix);
            }
        }
        @Override protected void dispatchDraw(Canvas canvas) {
            if (host.failed || !host.samplingReady) return;
            try {
                long time = getDrawingTime();
                for (int i = 0; i < host.parts.size(); i++) {
                    Part part = host.parts.get(i);
                    int save = canvas.save();
                    Rect clip = part.clip();
                    try {
                        canvas.clipRect(clip.left - host.position[0], clip.top - host.position[1],
                                clip.right - host.position[0], clip.bottom - host.position[1]);
                        drawChild(canvas, part.replica(), time);
                    } finally { canvas.restoreToCount(save); }
                }
                if (!host.drawn) { host.drawn = true; nativeDrawCount++; }
            } catch (RuntimeException error) { host.fail("draw-" + failure(error)); }
        }
    }

}
