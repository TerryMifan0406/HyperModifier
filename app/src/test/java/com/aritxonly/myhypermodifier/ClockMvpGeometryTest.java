package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockMvpGeometryTest {
    private static final ClockMvpGeometry.Box SOURCE = new ClockMvpGeometry.Box(145, 592, 1069, 1253);
    @Test public void nativeShapedInkMapsToTargetWithUniformScale() {
        ClockMvpGeometry.Box shaped = new ClockMvpGeometry.Box(145, 750, 1069, 750f + 231f * 924f / 752f);
        ClockMvpGeometry.Plan p = ClockMvpGeometry.plan(SOURCE, shaped, 1220, 2656, 520);
        assertNotNull(p);
        assertEquals(236f, p.mapX(shaped.left()), .001f);
        assertEquals(988f, p.mapX(shaped.right()), .001f);
        assertEquals(468f, p.mapY(shaped.top()), .001f);
        assertEquals(699f, p.mapY(shaped.bottom()), .001f);
        assertEquals(p.mapX(100f) - p.mapX(0f), p.mapY(100f) - p.mapY(0f), .001f);
    }
    @Test public void unreachableAxesFitInsideTargetWithoutStretch() {
        ClockMvpGeometry.Plan p = ClockMvpGeometry.plan(SOURCE, 1220, 2656, 520);
        assertNotNull(p);
        assertEquals(ClockMvpGeometry.TARGET.centerX(), p.mapX(SOURCE.centerX()), .001f);
        assertEquals(ClockMvpGeometry.TARGET.centerY(), p.mapY(SOURCE.centerY()), .001f);
        assertTrue(p.mapX(SOURCE.left()) >= ClockMvpGeometry.TARGET.left());
        assertTrue(p.mapX(SOURCE.right()) <= ClockMvpGeometry.TARGET.right());
        assertEquals(661f * p.scale(), p.mapX(661f) - p.mapX(0f), .001f);
    }
    @Test public void changedViewportDensityAndInvalidSourcesCannotReuseCalibration() {
        assertNull(ClockMvpGeometry.plan(SOURCE, 1080, 2400, 520));
        assertNull(ClockMvpGeometry.plan(SOURCE, 1220, 2656, 480));
        assertNull(ClockMvpGeometry.plan(new ClockMvpGeometry.Box(0, 0, 0, 0), 1220, 2656, 520));
        assertNull(ClockMvpGeometry.plan(new ClockMvpGeometry.Box(Float.NaN, 0, 500, 700), 1220, 2656, 520));
        assertNull(ClockMvpGeometry.plan(new ClockMvpGeometry.Box(-1, 500, 500, 700), 1220, 2656, 520));
        assertNull(ClockMvpGeometry.plan(SOURCE, new ClockMvpGeometry.Box(0, 0, 0, 0), 1220, 2656, 520));
    }
    @Test public void windowOriginCancelsWhenPivotIsConvertedToWindowSpace() {
        ClockMvpGeometry.Plan p = ClockMvpGeometry.plan(SOURCE, 1220, 2656, 520);
        assertNotNull(p);
        float windowX = 30f, localX = SOURCE.left() - windowX, localPivot = p.pivotX() - windowX;
        float screenX = windowX + localPivot + (localX - localPivot) * p.scale() + p.moveX();
        assertEquals(p.mapX(SOURCE.left()), screenX, .001f);
    }
    @Test public void nativeRatioAccountsForUniformWidthFit() {
        float compression = ClockMvpGeometry.fontRatioMultiplier(SOURCE);
        assertEquals(SOURCE.height() * ClockMvpGeometry.TARGET.width() / SOURCE.width()
                / ClockMvpGeometry.TARGET.height(), compression, .00001f);
        assertTrue(compression > 1f);
    }
}
