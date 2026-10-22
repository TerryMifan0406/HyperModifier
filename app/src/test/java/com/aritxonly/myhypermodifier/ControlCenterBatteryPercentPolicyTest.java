package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ControlCenterBatteryPercentPolicyTest {
    @Test public void exposesOnlyTheHiddenPercentageModeWhileExpanded() {
        for (int stock = 0; stock <= 3; stock++) {
            for (int flags = 0; flags < 4; flags++) {
                boolean enabled = (flags & 1) != 0;
                boolean expanded = (flags & 2) != 0;
                ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
                assertEquals(stock == 0 && enabled && expanded ? 3 : stock,
                        policy.resolve(stock, enabled, expanded));
            }
        }
    }

    @Test public void repeatedEventsDoNotLoseTheHiddenStyleAndCollapseRestoresIt() {
        ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        assertEquals(0, policy.resolve(0, true, false));
        assertEquals(3, policy.resolve(0, true, true));
        for (int i = 0; i < 10; i++) assertEquals(3, policy.resolve(3, true, true));
        assertEquals(0, policy.resolve(3, true, false));
        assertFalse(policy.hasOverride());
        assertEquals(3, policy.resolve(0, true, true));
    }

    @Test public void disablingWhileExpandedRestoresWithoutChangingTheNativeSetting() {
        ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        assertEquals(3, policy.resolve(0, true, true));
        assertEquals(0, policy.resolve(3, false, true));
        assertFalse(policy.hasOverride());
        assertEquals(3, policy.resolve(0, true, true));
    }

    @Test public void nativeStyleCallbackSeesTheOriginalAndRefreshesEvenIfStockSkips() {
        ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        assertEquals(3, policy.resolve(0, true, true));
        assertEquals(0, policy.beforeStockStyle(3));
        assertEquals(0, policy.resolve(0, true, false));
        assertTrue(policy.takeDirty());
        assertFalse(policy.takeDirty());
        assertFalse(policy.hasOverride());
    }

    @Test public void nativeStyleChangesDuringExpansionBecomeTheNewRestorationTarget() {
        for (int updated : new int[]{1, 2, 3}) {
            ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
            policy.resolve(0, true, true);
            assertEquals(0, policy.beforeStockStyle(3));
            assertEquals(updated, policy.resolve(updated, true, true));
            assertEquals(updated, policy.resolve(updated, false, false));
            assertEquals(updated, policy.restore(updated));
        }
    }

    @Test public void detachRestoresOnlyOurOverrideAndClearsOwnership() {
        ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        policy.resolve(0, true, true);
        assertEquals(0, policy.restore(3));
        assertFalse(policy.hasOverride());
        assertEquals(3, policy.restore(3));
        assertEquals(1, policy.resolve(1, true, true));
        assertEquals(1, policy.restore(1));
    }

    @Test public void nativeAodOrExternalStyleIsPreservedAndHiddenStyleCanResume() {
        ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        policy.resolve(0, true, true);
        assertEquals(0, policy.beforeStockStyle(3));
        assertEquals(3, policy.resolve(3, false, true));
        assertFalse(policy.takeDirty() && policy.hasOverride());
        assertEquals(3, policy.beforeStockStyle(3));
        assertEquals(3, policy.resolve(0, true, true));
        assertEquals(0, policy.resolve(3, true, false));
    }
}
