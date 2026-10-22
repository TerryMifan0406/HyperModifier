package com.aritxonly.myhypermodifier;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import android.widget.ImageView;

import androidx.core.graphics.PathParser;
import org.xmlpull.v1.XmlPullParser;
import java.util.ArrayList;
import java.util.Arrays;

/** Cached filled paths, native per-layer colors/alpha and ImageView tint. No per-frame geometry work. */
final class RoundedNetworkDrawable extends Drawable {
    static final float CORNER_RADIUS = 1.1f;
    // Slightly enlarge WLAN while keeping its aspect ratio and center.
    static final float WIFI_VISUAL_SCALE = 1.01f;
    static final float WEIGHT_STROKE_WIDTH = .1f;
    static final float MOBILE_WEIGHT_STROKE_WIDTH = .15f;

    private static final int[] VECTOR_ATTRS = sorted(android.R.attr.width, android.R.attr.height,
            android.R.attr.viewportWidth, android.R.attr.viewportHeight, android.R.attr.alpha, android.R.attr.tint);
    private static final int[] PATH_ATTRS = sorted(android.R.attr.pathData, android.R.attr.fillColor,
            android.R.attr.fillAlpha, android.R.attr.fillType, android.R.attr.strokeColor,
            android.R.attr.trimPathStart, android.R.attr.trimPathEnd, android.R.attr.trimPathOffset);

    // Android requires attribute IDs in ascending order; cache all indices once.
    private static final int V_WIDTH = Arrays.binarySearch(VECTOR_ATTRS, android.R.attr.width);
    private static final int V_HEIGHT = Arrays.binarySearch(VECTOR_ATTRS, android.R.attr.height);
    private static final int V_VIEWPORT_WIDTH = Arrays.binarySearch(VECTOR_ATTRS, android.R.attr.viewportWidth);
    private static final int V_VIEWPORT_HEIGHT = Arrays.binarySearch(VECTOR_ATTRS, android.R.attr.viewportHeight);
    private static final int V_ALPHA = Arrays.binarySearch(VECTOR_ATTRS, android.R.attr.alpha);
    private static final int V_TINT = Arrays.binarySearch(VECTOR_ATTRS, android.R.attr.tint);
    private static final int P_DATA = Arrays.binarySearch(PATH_ATTRS, android.R.attr.pathData);
    private static final int P_COLOR = Arrays.binarySearch(PATH_ATTRS, android.R.attr.fillColor);
    private static final int P_ALPHA = Arrays.binarySearch(PATH_ATTRS, android.R.attr.fillAlpha);
    private static final int P_TYPE = Arrays.binarySearch(PATH_ATTRS, android.R.attr.fillType);
    private static final int P_STROKE = Arrays.binarySearch(PATH_ATTRS, android.R.attr.strokeColor);
    private static final int P_TRIM_START = Arrays.binarySearch(PATH_ATTRS, android.R.attr.trimPathStart);
    private static final int P_TRIM_END = Arrays.binarySearch(PATH_ATTRS, android.R.attr.trimPathEnd);
    private static final int P_TRIM_OFFSET = Arrays.binarySearch(PATH_ATTRS, android.R.attr.trimPathOffset);

    private static int[] sorted(int... attributes) {
        Arrays.sort(attributes);
        return attributes;
    }

    static final class Definition {
        final Path[] paths;
        final ColorStateList[] colors;
        final float[] alphas;
        final int width, height;
        final float alpha;

        Definition(ArrayList<Path> paths, ArrayList<ColorStateList> colors, ArrayList<Float> alphas,
                   int width, int height, float alpha) {
            this.paths = paths.toArray(new Path[0]);
            this.colors = colors.toArray(new ColorStateList[0]);
            this.alphas = new float[alphas.size()];
            for (int i = 0; i < alphas.size(); i++) this.alphas[i] = alphas.get(i);
            this.width = width;
            this.height = height;
            this.alpha = alpha;
        }
    }

    static Definition read(Resources resources, Resources.Theme theme, int id, int kind) {
        return read(resources, theme, id, kind, RoundedIconTuning.DEFAULT);
    }

    static Definition read(Resources resources, Resources.Theme theme, int id, int kind, RoundedIconTuning tuning) {
        try (XmlResourceParser parser = resources.getXml(id)) {
            int event;
            do { event = parser.next(); } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
            if (!"vector".equals(parser.getName())) return null;
            TypedArray root = theme.obtainStyledAttributes(Xml.asAttributeSet(parser), VECTOR_ATTRS, 0, 0);
            int width, height;
            float alpha;
            try {
                if (root.getFloat(V_VIEWPORT_WIDTH, 0) != 20f || root.getFloat(V_VIEWPORT_HEIGHT, 0) != 20f || root.hasValue(V_TINT)) return null;
                width = root.getDimensionPixelSize(V_WIDTH, 0);
                height = root.getDimensionPixelSize(V_HEIGHT, 0);
                alpha = root.getFloat(V_ALPHA, 1f);
            } finally { root.recycle(); }
            if (width <= 0 || height <= 0) return null;
            ArrayList<Path> paths = new ArrayList<>();
            ArrayList<ColorStateList> colors = new ArrayList<>();
            ArrayList<Float> alphas = new ArrayList<>();
            Matrix wifiScale = null;
            if (kind == RoundedNetworkIconPolicy.WIFI) {
                wifiScale = new Matrix();
                wifiScale.setScale(tuning.wifiScale, tuning.wifiScale, 10f, 10f);
            }
            boolean changed = false;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if (!"path".equals(parser.getName()) || parser.getDepth() != 2) return null;
                TypedArray attrs = theme.obtainStyledAttributes(Xml.asAttributeSet(parser), PATH_ATTRS, 0, 0);
                try {
                    if (attrs.hasValue(P_STROKE) || attrs.hasValue(P_TRIM_START) || attrs.hasValue(P_TRIM_END) || attrs.hasValue(P_TRIM_OFFSET)) return null;
                    String data = attrs.getString(P_DATA);
                    ColorStateList color = attrs.getColorStateList(P_COLOR);
                    if (data == null || color == null) return null;
                    Path path = PathParser.createPathFromPathData(data);
                    if (path == null) return null;
                    path.setFillType(attrs.getInt(P_TYPE, 0) == 1 ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
                    if (RoundedNetworkIconPolicy.roundPath(kind, data)) {
                        Path rounded = round(path, tuning.radius);
                        // A radius larger than a themed thin segment must not remove the entire icon.
                        if (rounded != null) path = rounded;
                        changed = true;
                    }
                    if (RoundedNetworkIconPolicy.weightPath(kind, data)) {
                        path = emphasize(path, kind == RoundedNetworkIconPolicy.MOBILE
                                ? tuning.mobileWeight : tuning.wifiWeight);
                        if (path == null) return null;
                    }
                    if (wifiScale != null) path.transform(wifiScale);
                    paths.add(path);
                    colors.add(color);
                    alphas.add(attrs.getFloat(P_ALPHA, 1f));
                } finally { attrs.recycle(); }
            }
            return changed ? new Definition(paths, colors, alphas, width, height, alpha) : null;
        } catch (Exception ignored) {
            // Unsupported themes/resources keep the original drawable, without exception probing on draw.
            return null;
        }
    }

    /** Vector opening: round inside the original silhouette, without rescaling its geometry. */
    private static Path round(Path source, float radius) {
        if (radius <= 0f) return new Path(source);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(radius * 2f);
        edge.setStrokeJoin(Paint.Join.ROUND);
        edge.setStrokeCap(Paint.Cap.ROUND);
        Path boundary = new Path();
        if (!edge.getFillPath(source, boundary)) return null;
        Path core = new Path();
        if (!core.op(source, boundary, Path.Op.DIFFERENCE) || core.isEmpty()) return null;
        Path rim = new Path();
        if (!edge.getFillPath(core, rim)) return null;
        Path result = new Path();
        if (!result.op(core, rim, Path.Op.UNION)
                || !result.op(source, Path.Op.INTERSECT) || result.isEmpty()) return null;
        return result;
    }

    /** Bake a subtle fill expansion into cached geometry; no extra stroke work while drawing. */
    private static Path emphasize(Path source, float strokeWidth) {
        if (strokeWidth <= 0f) return source;
        Paint weight = new Paint(Paint.ANTI_ALIAS_FLAG);
        weight.setStyle(Paint.Style.STROKE);
        weight.setStrokeWidth(strokeWidth);
        weight.setStrokeJoin(Paint.Join.ROUND);
        weight.setStrokeCap(Paint.Cap.ROUND);
        Path boundary = new Path();
        if (!weight.getFillPath(source, boundary)) return null;
        Path result = new Path();
        return result.op(source, boundary, Path.Op.UNION) && !result.isEmpty() ? result : null;
    }

    private final Definition definition;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors;
    private int alpha = 255;
    private int intrinsicWidth, intrinsicHeight;
    private ColorStateList tint;
    private BlendMode blendMode = BlendMode.SRC_IN;
    private PorterDuff.Mode legacyMode;
    private ColorFilter explicitFilter, tintFilter;
    private int tintColor;
    private boolean tintDirty;

    RoundedNetworkDrawable(Definition definition) {
        this.definition = definition;
        intrinsicWidth = definition.width;
        intrinsicHeight = definition.height;
        alpha = Math.round(definition.alpha * 255f);
        colors = new int[definition.colors.length];
        updateColors(getState());
    }

    void copyAppearance(ImageView view, Drawable source) {
        // Use the loaded system drawable's density/theme-resolved size, not a second XML calculation.
        intrinsicWidth = source.getIntrinsicWidth();
        intrinsicHeight = source.getIntrinsicHeight();
        setAlpha(source.getAlpha());
        // Internal VectorDrawable tint must not become an explicit filter that freezes future tint.
        if (view.getImageTintList() == null) setColorFilter(source.getColorFilter());
        setLevel(source.getLevel());
        setState(source.getState());
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        int saved = canvas.save();
        try {
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 20f, bounds.height() / 20f);
            for (int i = 0; i < definition.paths.length; i++) {
                paint.setColor(colors[i]);
                paint.setAlpha(Math.round(Color.alpha(colors[i]) * definition.alphas[i] * alpha / 255f));
                canvas.drawPath(definition.paths[i], paint);
            }
        } finally { canvas.restoreToCount(saved); }
    }

    private boolean updateColors(int[] state) {
        boolean changed = false;
        for (int i = 0; i < colors.length; i++) {
            int value = definition.colors[i].getColorForState(state, definition.colors[i].getDefaultColor());
            if (value != colors[i]) { colors[i] = value; changed = true; }
        }
        if (tint != null) {
            int value = tint.getColorForState(state, tint.getDefaultColor());
            if (tintDirty || tintFilter == null || value != tintColor) {
                tintColor = value;
                tintFilter = legacyMode == null ? new BlendModeColorFilter(value, blendMode)
                        : new PorterDuffColorFilter(value, legacyMode);
                tintDirty = false;
                changed = true;
            }
        } else if (tintFilter != null) { tintFilter = null; changed = true; }
        paint.setColorFilter(explicitFilter != null ? explicitFilter : tintFilter);
        return changed;
    }

    @Override protected boolean onStateChange(int[] state) {
        boolean changed = updateColors(state);
        if (changed) invalidateSelf();
        return changed;
    }
    @Override public boolean isStateful() {
        if (tint != null && tint.isStateful()) return true;
        for (ColorStateList color : definition.colors) if (color.isStateful()) return true;
        return false;
    }
    @Override public void setTintList(ColorStateList value) {
        if (tint == value) return;
        tint = value;
        tintDirty = true;
        updateColors(getState());
        invalidateSelf();
    }
    @Override public void setTintBlendMode(BlendMode value) {
        if (blendMode == value && legacyMode == null) return;
        blendMode = value == null ? BlendMode.SRC_IN : value;
        legacyMode = null;
        tintDirty = true;
        updateColors(getState());
        invalidateSelf();
    }
    @Override public void setTintMode(PorterDuff.Mode value) {
        if (legacyMode == value) return;
        legacyMode = value;
        tintDirty = true;
        updateColors(getState());
        invalidateSelf();
    }
    @Override public void setAlpha(int value) {
        if (alpha != value) { alpha = value; invalidateSelf(); }
    }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter value) {
        explicitFilter = value;
        paint.setColorFilter(value != null ? value : tintFilter);
        invalidateSelf();
    }
    @Override public ColorFilter getColorFilter() { return explicitFilter != null ? explicitFilter : tintFilter; }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return intrinsicWidth; }
    @Override public int getIntrinsicHeight() { return intrinsicHeight; }
}
