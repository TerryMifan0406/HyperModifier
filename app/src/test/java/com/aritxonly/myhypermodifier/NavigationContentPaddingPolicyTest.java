package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationContentPaddingPolicyTest {
    @Test public void wealthPageFramesDoNotRepeatedlyRediscoverScrollingContent() {
        NavigationContentPaddingPolicy policy = new NavigationContentPaddingPolicy();
        int scans = 0;
        for (int frame = 0; frame < 1200; frame++) {
            if (policy.shouldApply(180, false)) scans++;
        }
        assertEquals(1, scans);
        policy.invalidate(); // Tab/page replacement, including native page changes.
        assertTrue(policy.shouldApply(180, false));
        assertFalse(policy.shouldApply(180, false));
    }

    @Test public void changedInsetsAndDelayedFirstMeasurementAreNotLost() {
        NavigationContentPaddingPolicy policy = new NavigationContentPaddingPolicy();
        assertFalse(policy.shouldApply(0, false));
        assertTrue(policy.shouldApply(180, false));
        assertTrue(policy.shouldApply(200, false));
        assertFalse(policy.shouldApply(200, false));
        policy.invalidate(); // Size change or return after restoring original padding.
        assertFalse(policy.shouldApply(0, false));
        assertTrue(policy.shouldApply(200, false));
    }

    @Test public void existingAdaptersKeepContinuousContentDiscovery() {
        NavigationContentPaddingPolicy policy = new NavigationContentPaddingPolicy();
        for (int frame = 0; frame < 10; frame++) assertTrue(policy.shouldApply(180, true));
    }
}
