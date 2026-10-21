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
}
