package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockMvpGeometryTest {
    @Test public void mapsBothInkCornersExactlyToCalibratedTarget() {
        ClockMvpGeometry.Box source = new ClockMvpGeometry.Box(145, 592, 1069, 1253);
        ClockMvpGeometry.Plan p = ClockMvpGeometry.plan(source, 1220, 2656, 520);
        assertNotNull(p);
        assertEquals(236f, p.mapX(source.left()), .001f);
        assertEquals(988f, p.mapX(source.right()), .001f);
        assertEquals(468f, p.mapY(source.top()), .001f);
        assertEquals(699f, p.mapY(source.bottom()), .001f);
    }
    @Test public void changedViewportDensityAndInvalidSourcesCannotReuseCalibration() {
        ClockMvpGeometry.Box source = new ClockMvpGeometry.Box(145, 592, 1069, 1253);
        assertNull(ClockMvpGeometry.plan(source, 1080, 2400, 520));
        assertNull(ClockMvpGeometry.plan(source, 1220, 2656, 480));
        assertNull(ClockMvpGeometry.plan(new ClockMvpGeometry.Box(0, 0, 0, 0), 1220, 2656, 520));
        assertNull(ClockMvpGeometry.plan(new ClockMvpGeometry.Box(Float.NaN, 0, 500, 700), 1220, 2656, 520));
        assertNull(ClockMvpGeometry.plan(new ClockMvpGeometry.Box(-1, 500, 500, 700), 1220, 2656, 520));
    }
    @Test public void windowOriginCancelsWhenPivotIsConvertedToWindowSpace() {
        ClockMvpGeometry.Box source = new ClockMvpGeometry.Box(145, 592, 1069, 1253);
        ClockMvpGeometry.Plan p = ClockMvpGeometry.plan(source, 1220, 2656, 520);
        assertNotNull(p);
        float windowX = 30f, localX = source.left() - windowX, localPivot = p.pivotX() - windowX;
        float screenX = windowX + localPivot + (localX - localPivot) * p.scaleX() + p.moveX();
        assertEquals(236f, screenX, .001f);
    }
}
