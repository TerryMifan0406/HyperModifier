package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockGlyphCapturePolicyTest {
    @Test public void normalTextRemainsOneNativePath() {
        var plan = ClockGlyphCapturePolicy.plan(5, 1, 1, .8f);
        assertNotNull(plan); assertFalse(plan.split());
        assertEquals(.8f, plan.firstAlpha(), 0); assertEquals(0, plan.secondAlpha(), 0);
    }
    @Test public void hiddenColonRetainsMinutePathWithoutColonPath() {
        var plan = ClockGlyphCapturePolicy.plan(3, 1, 0, 1);
        assertTrue(plan.split()); assertEquals(0, plan.firstAlpha(), 0);
        assertEquals(1, plan.secondAlpha(), 0);
    }
    @Test public void fadingColonDoesNotFadeMinutes() {
        var plan = ClockGlyphCapturePolicy.plan(3, 1, .25f, .8f);
        assertTrue(plan.split()); assertEquals(.2f, plan.firstAlpha(), .0001f);
        assertEquals(.8f, plan.secondAlpha(), 0);
    }
    @Test public void hourOnlyHasNoColonSplit() {
        assertFalse(ClockGlyphCapturePolicy.plan(2, 0, 0, 1).split());
    }
    @Test public void invalidOrInvisibleStateIsRejected() {
        assertNull(ClockGlyphCapturePolicy.plan(0, 0, 1, 1));
        assertNull(ClockGlyphCapturePolicy.plan(17, 0, 1, 1));
        assertNull(ClockGlyphCapturePolicy.plan(2, 3, 0, 1));
        assertNull(ClockGlyphCapturePolicy.plan(2, -1, 0, 1));
        assertNull(ClockGlyphCapturePolicy.plan(2, 1, Float.NaN, 1));
        assertNull(ClockGlyphCapturePolicy.plan(2, 1, 0, Float.POSITIVE_INFINITY));
        assertNull(ClockGlyphCapturePolicy.plan(2, 1, 0, 0));
    }
    @Test public void separateNativeHourAndMinuteAreNotDeduplicatedByViewBounds() {
        assertEquals("HOUR_CLOCK", ClockGlyphCapturePolicy.role("com.miui.clock.allInOne.AllInOneHourClock"));
        assertEquals("MINUTE_CLOCK", ClockGlyphCapturePolicy.role("com.miui.clock.allInOne.AllInOneMinuteClock"));
        assertEquals("UNKNOWN", ClockGlyphCapturePolicy.role("other.AllInOneHourClock"));
    }
}
