package com.aritxonly.myhypermodifier;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Copies last-drawn native paths without mutating Paint, Path, View or clock state. */
final class ClockGlyphSnapshot {
    // No View, Context, Paint, Shader or reflective owner retained in a snapshot.
    record Glyph(String role, String part, Path screenPath, RectF inkBounds,
                 Rect visibleClip, float alpha, boolean glass) {}
    final List<Glyph> glyphs;
    final String summary;

    private ClockGlyphSnapshot(List<Glyph> glyphs, String summary) {
        this.glyphs = List.copyOf(glyphs); this.summary = summary;
    }

    static final class Reader {
        private final Field path, colonPath, minutePath, drawX, drawY, splitIndex,
                colonAlpha, fontAlpha, colonAdvance, text, glass;
        Reader(Class<?> time) throws NoSuchFieldException {
            path = field(time, "mTextPath"); colonPath = field(time, "mColonPath");
            minutePath = field(time, "mMinuteOnlyPath"); drawX = field(time, "mDrawTextX");
            drawY = field(time, "mDrawTextY"); splitIndex = field(time, "mColonSplitIndex");
            colonAlpha = field(time, "mColonAlpha"); fontAlpha = field(time, "fontAlpha");
            colonAdvance = field(time, "mColonAdvanceWidth"); text = field(time, "mText");
            glass = field(time, "isMiGlassEffectEnable");
        }

        void append(List<Glyph> out, String role, String part, View view) throws IllegalAccessException {
            if (view == null || !view.isAttachedToWindow() || !view.isShown() || view.getAlpha() <= 0
                    || out.size() >= 32) return;
            String value = (String) text.get(view);
            ClockGlyphCapturePolicy.Plan plan = ClockGlyphCapturePolicy.plan(value == null ? 0 : value.length(),
                    splitIndex.getInt(view), colonAlpha.getFloat(view), fontAlpha.getFloat(view));
            if (plan == null) return;
            Rect clip = new Rect();
            if (!view.getGlobalVisibleRect(clip) || clip.isEmpty()) return;
            float alpha = 1;
            View current = view;
            for (int depth = 0; current != null && depth < 64; depth++) {
                alpha *= current.getAlpha();
                current = current.getParent() instanceof View parent ? parent : null;
            }
            if (!Float.isFinite(alpha) || alpha <= 0 || current != null) return;
            Matrix matrix = new Matrix(); view.transformMatrixToGlobal(matrix);
            float x = drawX.getFloat(view), y = drawY.getFloat(view);
            if (!Float.isFinite(x) || !Float.isFinite(y)) return;
            boolean material = glass.getBoolean(view);
            if (plan.split()) {
                copy(out, role, part + ":colon", (Path) colonPath.get(view), x, y,
                        matrix, clip, alpha * plan.firstAlpha(), material);
                float advance = colonAdvance.getFloat(view);
                if (Float.isFinite(advance)) copy(out, role, part + ":minute", (Path) minutePath.get(view),
                        x + advance, y, matrix, clip, alpha * plan.secondAlpha(), material);
            } else copy(out, role, part, (Path) path.get(view), x, y,
                    matrix, clip, alpha * plan.firstAlpha(), material);
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
        }
    }

    private static void copy(List<Glyph> out, String role, String part, Path original,
                             float x, float y, Matrix matrix, Rect clip, float alpha, boolean glass) {
        if (alpha <= 0 || original == null || original.isEmpty() || out.size() >= 32) return;
        Path copy = new Path(original); // Never offset/transform the native path itself.
        copy.offset(x, y); copy.transform(matrix);
        RectF ink = new RectF(); copy.computeBounds(ink, true);
        if (!Float.isFinite(ink.left) || !Float.isFinite(ink.top) || !Float.isFinite(ink.right)
                || !Float.isFinite(ink.bottom) || ink.isEmpty() || !RectF.intersects(ink, new RectF(clip))) return;
        out.add(new Glyph(role, part, copy, ink, new Rect(clip), alpha, glass));
    }

    static ClockGlyphSnapshot of(List<Glyph> glyphs, String candidates) {
        StringBuilder result = new StringBuilder(candidates).append(" glyphs=").append(glyphs.size());
        for (Glyph glyph : glyphs) {
            RectF ink = glyph.inkBounds();
            result.append(" {").append(glyph.role()).append('/').append(glyph.part())
                    .append(" ink=[").append(ink.left).append(',').append(ink.top).append(',')
                    .append(ink.right).append(',').append(ink.bottom).append("] alpha=").append(glyph.alpha())
                    .append(" glass=").append(glyph.glass()).append('}');
        }
        return new ClockGlyphSnapshot(glyphs, result.toString());
    }

    static List<Glyph> newBuffer() { return new ArrayList<>(6); }
}
