package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockTransitionAlphaLeaseTest {
    static final class Target { float alpha = 1f; }
    static final ClockTransitionAlphaLease.Access<Target> ACCESS = new ClockTransitionAlphaLease.Access<>() {
        public float read(Target target) { return target.alpha; }
        public void write(Target target, float value) { target.alpha = value; }
    };

    @Test public void restoresOnCancelAndCanBeCalledAgainWithoutTouchingNewState() throws Exception {
        Target target = new Target();
        ClockTransitionAlphaLease<Target> lease = new ClockTransitionAlphaLease<>(target, ACCESS);
        assertTrue(lease.acquire()); assertEquals(0f, target.alpha, 0f);
        assertFalse(lease.acquire());
        lease.restore(); assertEquals(1f, target.alpha, 0f);
        target.alpha = .6f;
        lease.restore(); assertEquals(.6f, target.alpha, 0f);
    }

    @Test public void preservesExternalChangesAndRejectsExistingFades() throws Exception {
        Target target = new Target();
        ClockTransitionAlphaLease<Target> lease = new ClockTransitionAlphaLease<>(target, ACCESS);
        assertTrue(lease.acquire()); target.alpha = .4f;
        lease.restore(); assertEquals(.4f, target.alpha, 0f);
        assertFalse(lease.acquire());
        target.alpha = 0f; assertFalse(lease.acquire());
        target.alpha = Float.NaN; assertFalse(lease.acquire());
    }

    @Test public void rollbackAlsoCoversPartiallyAppliedSetterFailure() throws Exception {
        Target target = new Target();
        ClockTransitionAlphaLease<Target> lease = new ClockTransitionAlphaLease<>(target,
                new ClockTransitionAlphaLease.Access<>() {
                    public float read(Target value) { return value.alpha; }
                    public void write(Target value, float alpha) throws ReflectiveOperationException {
                        value.alpha = alpha;
                        if (alpha == 0f) throw new ReflectiveOperationException("after write");
                    }
                });
        try { lease.acquire(); fail("expected setter failure"); }
        catch (ReflectiveOperationException expected) { }
        lease.restore(); assertEquals(1f, target.alpha, 0f);
    }
}
