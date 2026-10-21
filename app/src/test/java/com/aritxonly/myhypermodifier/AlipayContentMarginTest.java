package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class AlipayContentMarginTest {
    @Test public void wealthPageReassertionsNeverReachTheReplacementLayout() {
        AlipayContentMargin margin = new AlipayContentMargin(148);
        assertEquals(0, margin.replace(true, 148));
        for (int nativeMargin : new int[] {148, 196, 164, 196}) {
            assertEquals(0, margin.nativeRequest(nativeMargin));
        }
        // Disable restores the current page's reservation, not the initial home-page value.
        assertEquals(196, margin.replace(false, 0));
        assertEquals(210, margin.nativeRequest(210));
    }

    @Test public void pauseResumeAndNativeZeroReservationPreserveLatestAppState() {
        AlipayContentMargin margin = new AlipayContentMargin(148);
        assertEquals(0, margin.replace(true, 148));
        assertEquals(148, margin.replace(false, 0));
        assertEquals(0, margin.nativeRequest(0));
        assertEquals(0, margin.replace(true, 0));
        assertEquals(0, margin.replace(true, 0));
        assertEquals(0, margin.replace(false, 0));
    }

    @Test public void repeatedEnableDoesNotOverwriteTheNativeRestoreValueWithZero() {
        AlipayContentMargin margin = new AlipayContentMargin(120);
        assertEquals(0, margin.replace(true, 120));
        assertEquals(0, margin.replace(true, 0));
        assertEquals(120, margin.replace(false, 0));
    }
}
