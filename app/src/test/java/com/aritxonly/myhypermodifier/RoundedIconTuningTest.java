package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedIconTuningTest {
    @Test public void runtimeSnapshotBoundsInvalidValuesAndPreservesFineFractions() {
        RoundedIconTuning invalid = new RoundedIconTuning(Float.NaN, -1f, Float.POSITIVE_INFINITY,
                900f, 0f, Float.NaN, -99f);
        assertEquals(1.1f, invalid.radius, 0f);
        assertEquals(0f, invalid.mobileWeight, 0f);
        assertEquals(.1f, invalid.wifiWeight, 0f);
        assertEquals(1.1f, invalid.wifiScale, .00001f);
        assertEquals(.8f, invalid.batteryWidth, .00001f);
        assertEquals(.94f, invalid.batteryHeight, .00001f);
        assertEquals(-2f, invalid.mobileOffsetDp, 0f);
        RoundedIconTuning fine = new RoundedIconTuning(1.03f,.095f,.065f,101.25f,98.5f,93.25f,.125f);
        assertEquals(.125f, fine.mobileOffsetDp, 0f);
        assertEquals(1.0125f, fine.wifiScale, .00001f);
    }

    @Test public void releaseGeometryMatchesApprovedDeviceParameters() {
        RoundedIconTuning fixed = RoundedIconTuning.DEFAULT;
        assertEquals(1.1f, fixed.radius, 0f);
        assertEquals(.15f, fixed.mobileWeight, 0f);
        assertEquals(.1f, fixed.wifiWeight, 0f);
        assertEquals(1.01f, fixed.wifiScale, .00001f);
        assertEquals(.98f, fixed.batteryWidth, .00001f);
        assertEquals(.94f, fixed.batteryHeight, .00001f);
        assertEquals(.12f, fixed.mobileOffsetDp, 0f);
    }
}
