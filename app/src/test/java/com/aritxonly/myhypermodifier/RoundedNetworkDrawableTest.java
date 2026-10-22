package com.aritxonly.myhypermodifier;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Paint;
import android.widget.ImageView;
import org.robolectric.RuntimeEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import androidx.core.graphics.PathParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
@org.robolectric.annotation.ResourcesMode(org.robolectric.annotation.ResourcesMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoundedNetworkDrawableTest {
    @Test public void resourceAttributeTablesFollowAndroidsAscendingIdContract() throws Exception {
        for (String name : new String[]{"VECTOR_ATTRS", "PATH_ATTRS"}) {
            java.lang.reflect.Field field = RoundedNetworkDrawable.class.getDeclaredField(name);
            field.setAccessible(true);
            int[] ids = (int[]) field.get(null);
            for (int i = 1; i < ids.length; i++) assertTrue(name + " must be sorted", ids[i - 1] < ids[i]);
        }
    }

    @Test public void compiledSystemVectorsCanBeReadThroughAndroidResources() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        android.content.res.Resources resources = context.getResources();
        for (String kind : new String[]{"mobile", "wifi"}) {
            int id = resources.getIdentifier("rounded_network_fixture_" + kind, "drawable", context.getPackageName());
            assertNotEquals("Compiled vector fixture must be present", 0, id);
            RoundedNetworkDrawable.Definition definition = RoundedNetworkDrawable.read(resources,
                    context.getTheme(), id, kind.equals("mobile") ? 1 : 2);
            assertNotNull("Actual compiled " + kind + " vector must not silently fall back", definition);
            assertTrue(definition.width > 0);
            assertTrue(definition.height > 0);
            assertTrue(definition.paths.length > 0);
            RectF visualBounds = new RectF();
            for (Path path : definition.paths) visualBounds.union(contourBounds(path));
            assertEquals("Both native icon contours share the viewport center", 10f, visualBounds.centerY(), .002f);
            assertEquals("Keep subtle weight expansion and enlarge WLAN by one percent",
                    kind.equals("mobile") ? 12.202f : 11.67f * 1.01f, visualBounds.height(), .01f);

        }
    }

    @Test public void editableGeometryRangeKeepsCompiledResourcesNonemptyAndLayoutsNative() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        for (float radius : new float[]{0f, .5f, 1.1f, 1.2f}) for (float weight : new float[]{0f, .09f, .3f}) {
            RoundedIconTuning tuning = new RoundedIconTuning(radius, weight, weight, 110f, 98f, 94f, .1f);
            for (String kind : new String[]{"mobile", "wifi"}) {
                int id = context.getResources().getIdentifier("rounded_network_fixture_" + kind,
                        "drawable", context.getPackageName());
                RoundedNetworkDrawable.Definition result = RoundedNetworkDrawable.read(context.getResources(),
                        context.getTheme(), id, kind.equals("mobile") ? 1 : 2, tuning);
                assertNotNull(kind + " radius=" + radius + " weight=" + weight, result);
                for (Path path : result.paths) assertFalse(path.isEmpty());
                assertEquals(context.getDrawable(id).getIntrinsicWidth(), result.width);
                assertEquals(context.getDrawable(id).getIntrinsicHeight(), result.height);
            }
        }
    }

    @Test public void nativeBaselinePathsRemainInsideOriginalSilhouettesWithoutRescaling() throws Exception {
        Method round = RoundedNetworkDrawable.class.getDeclaredMethod("round", Path.class, float.class);
        round.setAccessible(true);
        int changed = 0, unchanged = 0;
        Path previewMobile = null;
        Path previewWifi = new Path();
        Path previewDot = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rounded-network-paths.tsv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 2);
                int kind = Integer.parseInt(parts[0]);
                if (kind == 1 && previewMobile == null) previewMobile = PathParser.createPathFromPathData(parts[1]);
                if (parts[1].startsWith("M9.778,") && previewDot == null) previewDot = PathParser.createPathFromPathData(parts[1]);
                if (parts[1].startsWith("M16.025,") || parts[1].startsWith("M5.678,")) {
                    // First occurrence of each component is sufficient; Path union prevents overdraw.
                    Path component = PathParser.createPathFromPathData(parts[1]);
                    previewWifi.op(component, Path.Op.UNION);
                }
                if (!RoundedNetworkIconPolicy.roundPath(kind, parts[1])) { unchanged++; continue; }
                Path source = PathParser.createPathFromPathData(parts[1]);
                Path rounded = (Path) round.invoke(null, source, RoundedNetworkDrawable.CORNER_RADIUS);
                assertNotNull(parts[1].substring(0, 20), rounded);
                assertFalse(rounded.isEmpty());
                RectF originalBounds = new RectF(), newBounds = new RectF();
                originalBounds.set(contourBounds(source));
                newBounds.set(contourBounds(rounded));
                float tolerance = kind == 1 ? .01f : .45f;
                assertEquals(originalBounds.left, newBounds.left, tolerance);
                assertEquals(parts[1].substring(0, 40), originalBounds.right, newBounds.right, tolerance);
                assertEquals(originalBounds.top, newBounds.top, tolerance);
                assertEquals(originalBounds.bottom, newBounds.bottom, tolerance);
                Path outside = new Path();
                assertTrue(outside.op(rounded, source, Path.Op.DIFFERENCE));
                assertTrue("Rounded paths must stay inside stock silhouettes without envelope expansion", outside.isEmpty());
                Path removed = new Path();
                removed.op(source, rounded, Path.Op.DIFFERENCE);
                assertFalse("Rounded shape should differ from stock", removed.isEmpty());
                changed++;
            }
        }
        assertTrue(changed >= 10);
        assertTrue("Badges/dot shapes are excluded", unchanged >= 3);
        writePreview(previewMobile, previewWifi, previewDot, round);
    }

    @Test public void roundingRetainsInteriorThicknessWhileRemovingCornerArea() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        Method round = RoundedNetworkDrawable.class.getDeclaredMethod("round", Path.class, float.class);
        round.setAccessible(true);
        for (String kind : new String[]{"mobile", "wifi"}) {
            int id = context.getResources().getIdentifier("rounded_network_fixture_" + kind,
                    "drawable", context.getPackageName());
            Path source = new Path(), rounded = new Path();
            try (android.content.res.XmlResourceParser parser = context.getResources().getXml(id)) {
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG
                            || !"path".equals(parser.getName())) continue;
                    String data = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData");
                    Path path = PathParser.createPathFromPathData(data);
                    source.addPath(path);
                    rounded.addPath(RoundedNetworkIconPolicy.roundPath(kind.equals("mobile") ? 1 : 2, data)
                            ? (Path) round.invoke(null, path, RoundedNetworkDrawable.CORNER_RADIUS) : path);
                }
            }
            Bitmap stock = geometryBitmap(source), result = geometryBitmap(rounded);
            try {
                double before = alphaArea(stock), after = alphaArea(result);
                System.out.println("Thickness " + kind + " filled area ratio=" + after / before + " native area=" + before);
                assertTrue("Rounding removes filled corner area", after < before);
                // Mobile: horizontal section through the straight middle of all four bars.
                // WLAN: vertical section through the apex of both arcs and the unchanged bottom marker.
                java.util.List<Integer> original = sectionRuns(stock, kind.equals("mobile"));
                java.util.List<Integer> changed = sectionRuns(result, kind.equals("mobile"));
                System.out.println("Thickness " + kind + " stock=" + original + " rounded=" + changed
                        + " pixels at 50px/viewport unit");
                assertEquals(kind.equals("mobile") ? 4 : 3, original.size());
                assertEquals(original.size(), changed.size());
                for (int i = 0; i < original.size(); i++) assertEquals("Interior thickness remains native",
                        original.get(i), changed.get(i), 1);
            } finally { stock.recycle(); result.recycle(); }
        }
    }

    @Test public void cachedWeightedGeometryAddsOnlyASmallAmountOfInteriorThickness() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        for (String kind : new String[]{"mobile", "wifi"}) {
            int id = context.getResources().getIdentifier("rounded_network_fixture_" + kind,
                    "drawable", context.getPackageName());
            RoundedNetworkDrawable.Definition definition = RoundedNetworkDrawable.read(context.getResources(),
                    context.getTheme(), id, kind.equals("mobile") ? 1 : 2);
            assertNotNull(definition);
            Path geometry = new Path();
            for (Path path : definition.paths) geometry.addPath(path);
            if (kind.equals("wifi")) {
                android.graphics.Matrix undoZoom = new android.graphics.Matrix();
                undoZoom.setScale(1f / 1.01f, 1f / 1.01f, 10f, 10f);
                geometry.transform(undoZoom);
            }
            Bitmap result = geometryBitmap(geometry);
            try {
                java.util.List<Integer> widths = sectionRuns(result, kind.equals("mobile"));
                int[] nativeWidths = kind.equals("mobile") ? new int[]{124,124,124,124} : new int[]{118,117,175};
                assertEquals(nativeWidths.length, widths.size());
                for (int i = 0; i < nativeWidths.length; i++) assertEquals("Final modestly heavier stems/arcs",
                        nativeWidths[i] + (kind.equals("mobile") ? 8 : 5), widths.get(i), 1);
                System.out.println("Weighted " + kind + " thickness=" + widths + " area=" + alphaArea(result));
            } finally { result.recycle(); }
        }
    }

    private static Bitmap geometryBitmap(Path path) {
        Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(50f, 50f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        canvas.drawPath(path, paint);
        return bitmap;
    }

    private static double alphaArea(Bitmap bitmap) {
        int[] row = new int[bitmap.getWidth()];
        double area = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int color : row) area += Color.alpha(color) / 255.0;
        }
        return area;
    }

    private static java.util.List<Integer> sectionRuns(Bitmap bitmap, boolean horizontal) {
        java.util.List<Integer> runs = new ArrayList<>();
        int length = 0;
        for (int coordinate = 0; coordinate < 1000; coordinate++) {
            boolean filled = Color.alpha(horizontal ? bitmap.getPixel(coordinate, 700)
                    : bitmap.getPixel(500, coordinate)) >= 128;
            if (filled) length++;
            else if (length != 0) { runs.add(length); length = 0; }
        }
        if (length != 0) runs.add(length);
        return runs;
    }

    private static RectF contourBounds(Path path) {
        float[] points = path.approximate(.001f);
        RectF bounds = new RectF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (int i = 0; i < points.length; i += 3) {
            bounds.left = Math.min(bounds.left, points[i + 1]);
            bounds.right = Math.max(bounds.right, points[i + 1]);
            bounds.top = Math.min(bounds.top, points[i + 2]);
            bounds.bottom = Math.max(bounds.bottom, points[i + 2]);
        }
        return bounds;
    }

    @Test public void replacementRetainsNativeImageViewSizeMatrixAndVisibleAlignment() {
        android.content.Context base = RuntimeEnvironment.getApplication();
        for (int dpi : new int[]{160, 320, 440, 560}) {
            android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
            config.densityDpi = dpi;
            android.content.Context context = base.createConfigurationContext(config);
            for (String kind : new String[]{"mobile", "wifi"}) {
                int id = context.getResources().getIdentifier("rounded_network_fixture_" + kind,
                        "drawable", context.getPackageName());
                for (boolean fixedSize : new boolean[]{false, true}) {
                    android.graphics.drawable.Drawable stock = context.getDrawable(id);
                    ImageView view = new ImageView(context);
                    view.setAdjustViewBounds(true);
                    view.setPadding(3, 2, 5, 4);
                    view.setImageDrawable(stock);
                    int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(160,
                            fixedSize ? android.view.View.MeasureSpec.EXACTLY : android.view.View.MeasureSpec.AT_MOST);
                    int heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(120,
                            fixedSize ? android.view.View.MeasureSpec.EXACTLY : android.view.View.MeasureSpec.AT_MOST);
                    view.measure(widthSpec, heightSpec);
                    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    int width = view.getMeasuredWidth(), height = view.getMeasuredHeight();
                    android.graphics.Matrix matrix = new android.graphics.Matrix(view.getImageMatrix());
                    android.graphics.Rect bounds = new android.graphics.Rect(stock.getBounds());
                    android.graphics.Rect pixels = visibleBounds(view, kind.equals("wifi") ? 1.01f : 1f);
                    // Deliberately read geometry from another density: native size must remain authoritative.
                    RoundedNetworkDrawable.Definition definition = RoundedNetworkDrawable.read(base.getResources(),
                            base.getTheme(), id, kind.equals("mobile") ? 1 : 2);
                    assertNotNull(definition);
                    RoundedNetworkDrawable replacement = new RoundedNetworkDrawable(definition);
                    replacement.copyAppearance(view, stock);
                    assertEquals(stock.getIntrinsicWidth(), replacement.getIntrinsicWidth());
                    assertEquals(stock.getIntrinsicHeight(), replacement.getIntrinsicHeight());
                    view.setImageDrawable(replacement);
                    view.measure(widthSpec, heightSpec);
                    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    assertEquals(width, view.getMeasuredWidth());
                    assertEquals(height, view.getMeasuredHeight());
                    assertEquals(matrix, view.getImageMatrix());
                    assertEquals(bounds, replacement.getBounds());
                    android.graphics.Rect actual = visibleBounds(view);
                    String label = kind + " dpi=" + dpi + " fixed=" + fixedSize;
                    // Larger round tips can remove up to two edge pixels at this enlarged test size.
                    // View layout/matrix stay native; WLAN paths grow uniformly to mobile's height.
                    int horizontalTolerance = kind.equals("mobile") ? 1 : 2;
                    int verticalTolerance = 1;
                    assertEquals(label + " left", pixels.left, actual.left, horizontalTolerance);
                    assertEquals(label + " right", pixels.right, actual.right, horizontalTolerance);
                    assertEquals(label + " top", pixels.top, actual.top, verticalTolerance);
                    assertEquals(label + " bottom", pixels.bottom, actual.bottom, verticalTolerance);
                }
            }
        }
    }

    private static android.graphics.Rect visibleBounds(ImageView view) {
        return visibleBounds(view, 1f);
    }

    private static android.graphics.Rect visibleBounds(ImageView view, float scale) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale,
                (view.getPaddingLeft() + view.getWidth() - view.getPaddingRight()) / 2f,
                (view.getPaddingTop() + view.getHeight() - view.getPaddingBottom()) / 2f);
        view.draw(canvas);
        android.graphics.Rect bounds = new android.graphics.Rect(bitmap.getWidth(), bitmap.getHeight(), 0, 0);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if (Color.alpha(bitmap.getPixel(x, y)) < 128) continue;
            bounds.left = Math.min(bounds.left, x);
            bounds.top = Math.min(bounds.top, y);
            bounds.right = Math.max(bounds.right, x + 1);
            bounds.bottom = Math.max(bounds.bottom, y + 1);
        }
        bitmap.recycle();
        assertFalse("Icon must draw visible pixels", bounds.isEmpty());
        return bounds;
    }

    private void writePreview(Path mobile, Path wifi, Path dot, Method round) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(900, 470, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xff20242c);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(23);
        canvas.drawText("Stock", 200, 38, paint);
        canvas.drawText("Rounded", 610, 38, paint);
        Path[] source = {mobile, wifi};
        for (int row = 0; row < 2; row++) {
            Path result = (Path) round.invoke(null, source[row], RoundedNetworkDrawable.CORNER_RADIUS);
            Method emphasize = RoundedNetworkDrawable.class.getDeclaredMethod("emphasize", Path.class, float.class);
            emphasize.setAccessible(true);
            result = (Path) emphasize.invoke(null, result, row == 0
                    ? RoundedNetworkDrawable.MOBILE_WEIGHT_STROKE_WIDTH : RoundedNetworkDrawable.WEIGHT_STROKE_WIDTH);
            if (row == 1) {
                source[row] = new Path(source[row]);
                source[row].addPath(dot);
                result.addPath((Path) emphasize.invoke(null, dot, RoundedNetworkDrawable.WEIGHT_STROKE_WIDTH));
                android.graphics.Matrix wifiScale = new android.graphics.Matrix();
                wifiScale.setScale(RoundedNetworkDrawable.WIFI_VISUAL_SCALE,
                        RoundedNetworkDrawable.WIFI_VISUAL_SCALE, 10f, 10f);
                result.transform(wifiScale);
            }
            for (int column = 0; column < 2; column++) {
                int saved = canvas.save();
                canvas.translate(130 + column * 410, 55 + row * 205);
                canvas.scale(10, 10);
                canvas.drawPath(column == 0 ? source[row] : result, paint);
                canvas.restoreToCount(saved);
            }
        }
        File file = new File("build/outputs/rounded-network/native-preview.png");
        file.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
        System.out.println("Native preview: " + file.getAbsolutePath());
    }

    private RoundedNetworkDrawable drawable(float layerAlpha, float rootAlpha) {
        ArrayList<Path> paths = new ArrayList<>();
        Path path = new Path();
        path.addRoundRect(2, 3, 18, 17, 2, 2, Path.Direction.CW);
        paths.add(path);
        ArrayList<ColorStateList> colors = new ArrayList<>();
        colors.add(ColorStateList.valueOf(Color.WHITE));
        ArrayList<Float> alphas = new ArrayList<>();
        alphas.add(layerAlpha);
        RoundedNetworkDrawable result = new RoundedNetworkDrawable(
                new RoundedNetworkDrawable.Definition(paths, colors, alphas, 200, 200, rootAlpha));
        result.setBounds(0, 0, 200, 200);
        return result;
    }

    private int pixel(RoundedNetworkDrawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        int pixel = bitmap.getPixel(100, 100);
        bitmap.recycle();
        return pixel;
    }

    @Test public void dynamicTintChangesColorWithoutMakingInactiveSignalLayersOpaque() {
        RoundedNetworkDrawable drawable = drawable(.24f, 1f);
        drawable.setTintList(ColorStateList.valueOf(Color.RED));
        drawable.setTintBlendMode(BlendMode.SRC_IN);
        int red = pixel(drawable);
        assertEquals(61, Color.alpha(red), 1);
        assertEquals(255, Color.red(red), 1);
        drawable.setTintList(ColorStateList.valueOf(Color.BLUE));
        int blue = pixel(drawable);
        assertEquals(61, Color.alpha(blue), 1);
        assertEquals(255, Color.blue(blue), 1);
        assertEquals(0, Color.red(blue));
        drawable.setTintList(null);
        assertEquals(255, Color.red(pixel(drawable)), 1);
    }

    @Test public void statefulTintAndIndependentInstancesDoNotContaminateEachOther() {
        RoundedNetworkDrawable first = drawable(1f, 1f), second = drawable(1f, 1f);
        ColorStateList tint = new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}},
                new int[]{Color.RED, Color.BLUE});
        first.setTintList(tint);
        second.setTintList(ColorStateList.valueOf(Color.GREEN));
        assertTrue(first.isStateful());
        assertEquals(Color.BLUE, pixel(first));
        first.setState(new int[]{android.R.attr.state_pressed});
        assertEquals(Color.RED, pixel(first));
        assertEquals(Color.GREEN, pixel(second));
    }

    @Test public void imageViewRetintsTheReplacementWithoutFreezingItsOldFilter() {
        ImageView view = new ImageView(RuntimeEnvironment.getApplication());
        view.setImageTintList(ColorStateList.valueOf(Color.RED));
        RoundedNetworkDrawable stock = drawable(.24f, 1f);
        view.setImageDrawable(stock);
        assertNotNull(stock.getColorFilter());
        RoundedNetworkDrawable replacement = drawable(.24f, 1f);
        replacement.copyAppearance(view, stock);
        view.setImageDrawable(replacement);
        replacement.setBounds(0, 0, 200, 200);
        view.setImageTintList(ColorStateList.valueOf(Color.BLUE));
        int blue = pixel(replacement);
        assertEquals(255, Color.blue(blue), 1);
        assertEquals(0, Color.red(blue));
        assertEquals(61, Color.alpha(blue), 1);
    }

    @Test public void rootAndDrawableAlphaAreAppliedExactlyOnce() {
        RoundedNetworkDrawable drawable = drawable(1f, .5f);
        assertEquals(128, Color.alpha(pixel(drawable)), 1);
        drawable.setAlpha(64);
        assertEquals(64, Color.alpha(pixel(drawable)), 1);
        assertEquals(200, drawable.getIntrinsicWidth());
        assertEquals(200, drawable.getIntrinsicHeight());
    }
}
