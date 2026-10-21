package com.aritxonly.myhypermodifier;

import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Process;
import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import android.view.View;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Per-session native font morph. Measure once; frame callbacks only interpolate cached data. */
final class ClockMvpFontMorph {
    private static final int STEPS = 16;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); runnable.run();
            }, "MHMClockFont"));
    static boolean prepare(Runnable task) {
        try { WORKER.execute(task); return true; } catch (RejectedExecutionException ignored) { return false; }
    }
    interface Variation {
        void apply(float weight, float width, float height, Paint paint) throws ReflectiveOperationException;
    }
    static final class Access {
        final MethodHandle width, height, size, x, y, advance;
        final Variation variation;
        private WeakReference<Typeface> cachedFace = new WeakReference<>(null);
        private float cachedWeight, cachedWidth, cachedRatio;
        private HyperMusicCoverFontShapePolicy.Axes cachedAxes;
        Access(Class<?> time) throws ReflectiveOperationException {
            width = setter(time, "width"); height = setter(time, "height");
            size = setter(time, "textSizePx"); x = setter(time, "mDrawTextX"); y = setter(time, "mDrawTextY");
            advance = setter(time, "mColonAdvanceWidth");
            Method override;
            try { override = Paint.class.getDeclaredMethod("setFontVariationOverride", String.class); }
            catch (NoSuchMethodException absent) { override = Paint.class.getDeclaredMethod("setCustomVariationOverride", String.class); }
            override.setAccessible(true);
            Method nativeOverride = override;
            // Same backend as TextMeasureUtils, with an independent Paint. Do not
            // race its shared static width/height/string cache from this worker.
            this.variation = (weight, w, h, paint) -> nativeOverride.invoke(paint,
                    "'wght' " + weight + ", 'wdth' " + w + ", 'HGHT' " + h);
        }
        HyperMusicCoverFontShapePolicy.Axes solve(Paint paint, float requested, float weight, float width,
                                                 Path path, RectF bounds) throws ReflectiveOperationException {
            if (cachedAxes != null && cachedFace.get() == paint.getTypeface()
                    && cachedWeight == weight && cachedWidth == width && cachedRatio == requested) return cachedAxes;
            HyperMusicCoverFontShapePolicy.Axes axes = HyperMusicCoverFontShapePolicy.solve(requested, width, (w, h) -> {
                paint.setTextSize(100f); variation.apply(weight, w, h, paint);
                measure(paint, "00", 0, 2, path, bounds);
                return bounds.width() / bounds.height();
            });
            cachedFace = new WeakReference<>(paint.getTypeface());
            cachedWeight = weight; cachedWidth = width; cachedRatio = requested; cachedAxes = axes;
            return axes;
        }
        static MethodHandle setter(Class<?> owner, String name) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            if (field.getType() != float.class) throw new NoSuchFieldException(name + ":expected-float");
            field.setAccessible(true);
            return MethodHandles.lookup().unreflectSetter(field)
                    .asType(MethodType.methodType(void.class, Object.class, float.class));
        }
    }
    final RectF endInk;
    final float targetWidth, targetHeight, reachableRatio;
    private final View view;
    private final Access access;
    private final float[] widths = new float[STEPS + 1], heights = new float[STEPS + 1], sizes = new float[STEPS + 1];
    private final float[] xs = new float[STEPS + 1], ys = new float[STEPS + 1], advances = new float[STEPS + 1];

    ClockMvpFontMorph(View view, Access access, Object[] values, Paint sourcePaint,
                      Matrix screen, float ratioMultiplier) throws ReflectiveOperationException {
        this.view = view; this.access = access;
        String text = (String) values[7];
        int split = (Integer) values[3];
        float colonAlpha = (Float) values[2], fontAlpha = (Float) values[0];
        ClockGlyphCapturePolicy.Plan branch = ClockGlyphCapturePolicy.plan(text.length(), split, colonAlpha, fontAlpha);
        if (branch == null) throw new IllegalArgumentException("font-branch");
        float weight = (Float) values[10], width = (Float) values[11], height = (Float) values[12];
        float size = (Float) values[9], x = (Float) values[4], y = (Float) values[5];
        float advance = (Float) values[1];
        if (!Float.isFinite(ratioMultiplier) || ratioMultiplier <= 0f || size <= 0f)
            throw new IllegalArgumentException("font-target");
        Paint paint = new Paint(sourcePaint);
        Path path = new Path(); RectF bounds = new RectF(), scratch = new RectF();
        paint.setTextSize(size); access.variation.apply(weight, width, height, paint);
        measure(paint, "00", 0, 2, path, bounds);
        float referenceWidth = bounds.width(), referenceHeight = bounds.height();
        if (referenceWidth <= 0f || referenceHeight <= 0f) throw new IllegalArgumentException("font-reference");
        float requestedRatio = referenceWidth / referenceHeight * ratioMultiplier;
        HyperMusicCoverFontShapePolicy.Axes axes = access.solve(paint, requestedRatio, weight, width, path, bounds);
        targetWidth = axes.width; targetHeight = axes.height; reachableRatio = axes.ratio;
        paint.setTextSize(size); access.variation.apply(weight, width, height, paint);
        float originalAdvance = split > 0 ? paint.measureText(text, 0, split) : 0f;
        ink(paint, text, split, branch, advance, path, bounds, scratch);
        float centerX = bounds.centerX(), centerY = bounds.centerY();
        for (int i = 0; i <= STEPS; i++) {
            float p = (float) i / STEPS;
            widths[i] = lerp(width, axes.width, p); heights[i] = lerp(height, axes.height, p);
            paint.setTextSize(size); access.variation.apply(weight, widths[i], heights[i], paint);
            measure(paint, "00", 0, 2, path, bounds);
            sizes[i] = i == 0 ? size : HyperMusicCoverFontShapePolicy.fontSizeForWidth(size, referenceWidth, bounds.width());
            paint.setTextSize(sizes[i]);
            advances[i] = i == 0 || originalAdvance <= 0f ? advance
                    : advance * paint.measureText(text, 0, split) / originalAdvance;
            ink(paint, text, split, branch, advances[i], path, bounds, scratch);
            // Keep the original ink center, including bearing/baseline changes caused by HGHT.
            xs[i] = i == 0 ? x : x + centerX - bounds.centerX();
            ys[i] = i == 0 ? y : y + centerY - bounds.centerY();
            if (!Float.isFinite(sizes[i]) || !Float.isFinite(xs[i]) || !Float.isFinite(ys[i])
                    || !Float.isFinite(advances[i]) || sizes[i] <= 0f)
                throw new IllegalArgumentException("font-keyframe");
        }
        endInk = new RectF(bounds); endInk.offset(xs[STEPS], ys[STEPS]); screen.mapRect(endInk);
    }
    void apply(float progress) throws Throwable {
        float index = Math.max(0f, Math.min(1f, progress)) * STEPS;
        int low = Math.min((int) index, STEPS - 1), high = low + 1;
        float f = index - low;
        Object target = view;
        // Typed method handles are resolved once; no reflective invocation or boxing per frame.
        access.width.invokeExact(target, lerp(widths[low], widths[high], f));
        access.height.invokeExact(target, lerp(heights[low], heights[high], f));
        access.size.invokeExact(target, lerp(sizes[low], sizes[high], f));
        access.x.invokeExact(target, lerp(xs[low], xs[high], f));
        access.y.invokeExact(target, lerp(ys[low], ys[high], f));
        access.advance.invokeExact(target, lerp(advances[low], advances[high], f));
        view.invalidate(); // TimeView's existing variable-font and glass draw path.
    }
    private static float lerp(float start, float end, float p) { return start + (end - start) * p; }
    private static void measure(Paint paint, String text, int start, int end, Path path, RectF bounds) {
        path.rewind(); paint.getTextPath(text, start, end, 0f, 0f, path); path.computeBounds(bounds, true);
    }
    private static void ink(Paint paint, String text, int split, ClockGlyphCapturePolicy.Plan branch,
                            float advance, Path path, RectF bounds, RectF scratch) {
        if (!branch.split()) { measure(paint, text, 0, text.length(), path, bounds); return; }
        bounds.setEmpty();
        if (branch.firstAlpha() > 0f) { measure(paint, text, 0, split, path, scratch); bounds.union(scratch); }
        if (branch.secondAlpha() > 0f) {
            measure(paint, text, split, text.length(), path, scratch); scratch.offset(advance, 0f); bounds.union(scratch);
        }
        if (bounds.isEmpty()) throw new IllegalArgumentException("font-ink");
    }
}
