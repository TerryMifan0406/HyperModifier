package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class GpuBackdropFrameGateTest {
    @Test public void coalescesDrawsToOneInflightRequestAndKeepsLatestDirtyFrame() {
        GpuBackdropFrameGate gate = new GpuBackdropFrameGate();
        gate.setActive(true);
        long first = gate.begin();
        for (int i = 0; i < 100; i++) gate.request();
        assertEquals(0, gate.begin());
        assertTrue(gate.complete(first));
        assertTrue(gate.ready());
        long next = gate.begin();
        assertTrue(gate.complete(next));
        assertFalse(gate.ready()); // Idle sources never start a permanent capture loop.
    }
    @Test public void navigationCropChangeRejectsOldFrameWithoutStartingParallelCapture() {
        GpuBackdropFrameGate gate = new GpuBackdropFrameGate();
        gate.setActive(true);
        long old = gate.begin();
        gate.invalidate();
        assertEquals(0, gate.begin());
        assertFalse(gate.complete(old));
        long fresh = gate.begin();
        assertTrue(fresh > old);
        assertTrue(gate.complete(fresh));
    }
    @Test public void pauseAndResumeNeverPublishASecondaryPageFrame() {
        GpuBackdropFrameGate gate = new GpuBackdropFrameGate();
        gate.setActive(true);
        long old = gate.begin();
        gate.setActive(false);
        gate.setActive(true);
        assertFalse(gate.complete(old));
        assertTrue(gate.complete(gate.begin()));
        gate.setActive(false);
        gate.request();
        assertFalse(gate.ready());
    }
    @Test public void timeoutOrDisposeInvalidatesLateAndDuplicateCallbacks() {
        GpuBackdropFrameGate gate = new GpuBackdropFrameGate();
        gate.setActive(true);
        long token = gate.begin();
        gate.setActive(false);
        gate.cancel();
        assertFalse(gate.owns(token));
        assertFalse(gate.complete(token));
        gate.setActive(true);
        long next = gate.begin();
        assertFalse(gate.complete(token));
        assertTrue(gate.complete(next));
        assertFalse(gate.complete(next));
    }
}
