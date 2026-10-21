package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockMaterialPreviewPolicyTest {
    @Test public void requiresKeyguardGoingAwayAndOpeningHomeForCurrentUser() {
        assertTrue(ClockMaterialPreviewPolicy.homeTarget(0x302, 3, 2, "com.miui.home", 0, 0));
        assertFalse(ClockMaterialPreviewPolicy.homeTarget(0, 3, 2, "com.miui.home", 0, 0));
        assertFalse(ClockMaterialPreviewPolicy.homeTarget(0x302, 2, 2, "com.miui.home", 0, 0));
        assertFalse(ClockMaterialPreviewPolicy.homeTarget(0x302, 3, 1, "com.miui.home", 0, 0));
        assertFalse(ClockMaterialPreviewPolicy.homeTarget(0x302, 3, 2, "other", 0, 0));
        assertFalse(ClockMaterialPreviewPolicy.homeTarget(0x302, 3, 2, "com.miui.home", 10, 0));
    }
    @Test public void requiresApprovedHomeAndFreshSnapshot() {
        assertTrue(ClockMaterialPreviewPolicy.show(true, true, 300, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.show(false, true, 300, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.show(true, false, 300, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.show(true, true, 1001, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.show(true, true, -1, 0, 2));
    }
    @Test public void unsupportedOrientationAndInvalidPartCountsAbort() {
        assertFalse(ClockMaterialPreviewPolicy.show(true, true, 300, 1, 2));
        assertFalse(ClockMaterialPreviewPolicy.show(true, true, 300, 0, 0));
        assertFalse(ClockMaterialPreviewPolicy.show(true, true, 300, 0, 5));
    }
    @Test public void transparentPreparationDoesNotApproveDisplayTarget() {
        assertTrue(ClockMaterialPreviewPolicy.prepare(true, 100, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.show(true, false, 100, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.prepare(false, 100, 0, 2));
        assertFalse(ClockMaterialPreviewPolicy.prepare(true, 1001, 0, 2));
    }
    @Test public void handoffDoesNotRestoreOpacityLostSinceCapture() {
        assertEquals(.3f, ClockMaterialPreviewPolicy.opacity(1f, 1f, .3f, 1f), .0001f);
        // Captured font alpha is retained in the native paint path: compensate View alpha.
        float viewAlpha = ClockMaterialPreviewPolicy.opacity(.8f, .5f, .7f, .2f);
        assertEquals(.7f * .2f, viewAlpha * .5f, .0001f);
        assertEquals(.4f, ClockMaterialPreviewPolicy.opacity(.4f, 1f, .8f, 1f), 0f);
    }
    @Test public void invisibleOrInvalidSourceCannotFlashBackOnScreen() {
        assertEquals(0f, ClockMaterialPreviewPolicy.opacity(1f, 1f, 0f, 1f), 0f);
        assertEquals(0f, ClockMaterialPreviewPolicy.opacity(1f, 1f, 1f, 0f), 0f);
        assertEquals(0f, ClockMaterialPreviewPolicy.opacity(1f, 0f, 1f, 1f), 0f);
        assertEquals(0f, ClockMaterialPreviewPolicy.opacity(1f, 1f, Float.NaN, 1f), 0f);
    }
}
