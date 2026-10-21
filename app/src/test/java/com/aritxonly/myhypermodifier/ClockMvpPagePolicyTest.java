package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockMvpPagePolicyTest {
    private static ClockMvpPagePolicy.Snapshot s(boolean ready, int page, long epoch) {
        return new ClockMvpPagePolicy.Snapshot(ready, page, epoch, epoch);
    }
    @Test public void firstPageRequiresNewUnlockObservation() {
        assertTrue(ClockMvpPagePolicy.approve(s(true, 1, 3), s(true, 0, 4)));
        assertFalse(ClockMvpPagePolicy.approve(s(true, 0, 3), s(true, 0, 3)));
        assertFalse(ClockMvpPagePolicy.approve(s(true, 0, 3), s(true, 1, 4)));
        assertFalse(ClockMvpPagePolicy.approve(s(true, 0, 3), s(true, -1, 4)));
        assertFalse(ClockMvpPagePolicy.approve(s(true, 0, 3), new ClockMvpPagePolicy.Snapshot(true, 0, 4, 3)));
    }
    @Test public void missingBridgeNeverApproves() {
        assertFalse(ClockMvpPagePolicy.approve(null, s(true, 0, 4)));
        assertFalse(ClockMvpPagePolicy.approve(s(true, 0, 3), null));
        assertFalse(ClockMvpPagePolicy.approve(s(false, 0, 3), s(true, 0, 4)));
        assertFalse(ClockMvpPagePolicy.approve(s(true, 0, 3), s(false, 0, 4)));
    }
    @Test public void responseAcceptsOnlyExplicitProtocolAndBounds() {
        assertEquals(s(true, 0, 4), ClockMvpPagePolicy.parse("MHM_WIDGET_STATUS_V1\nconfig=1\nMHM_CLOCK_MVP_V1\nready=1\npage=0\nepoch=4\nsuppressed=4\n"));
        assertNull(ClockMvpPagePolicy.parse("ready=1\npage=0\nepoch=4\nsuppressed=4\n"));
        assertNull(ClockMvpPagePolicy.parse("MHM_CLOCK_MVP_V1\nready=1\npage=32\nepoch=4\n"));
        assertNull(ClockMvpPagePolicy.parse("MHM_CLOCK_MVP_V1\nready=1\npage=0\nepoch=-1\n"));
    }
    @Test public void epochWrapRemainsFresh() {
        assertTrue(ClockMvpPagePolicy.approve(new ClockMvpPagePolicy.Snapshot(true, 0, 0xffffffffL, 3), new ClockMvpPagePolicy.Snapshot(true, 0, 0, 4)));
    }
}
