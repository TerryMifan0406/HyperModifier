package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnifiedGlassStateTest {
    @Test public void disablingRestoresLatestStockAndStopsReplaying() {
        UnifiedGlassState state = new UnifiedGlassState();
        float[] config = UnifiedGlassPolicy.sanitize(ShadeCardGlassPolicy.defaults());
        float[] stock = HeadsUpGlassDefaults.regular();
        state.recordApplied(state.observe(stock, true, config));
        stock[14] = .12f;
        stock[19] = 45f;
        state.recordApplied(state.observe(stock, true, config));
        assertTrue(state.prepareRefresh(false, config));
        assertArrayEquals(stock, state.tuned, 0f);
        state.recordRefresh();
        assertFalse(state.prepareRefresh(false, config));
    }

    @Test public void enablingUsesObservedStockWithoutMutatingItOrAllocatingBuffers() {
        UnifiedGlassState state = new UnifiedGlassState();
        float[] stock = HeadsUpGlassDefaults.dark();
        float[] config = UnifiedGlassPolicy.sanitize(ShadeCardGlassPolicy.defaults());
        assertFalse(state.observe(stock, false, config));
        state.recordApplied(false);
        Object[] args = state.args;
        assertTrue(state.prepareRefresh(true, config));
        state.recordRefresh();
        assertArrayEquals(stock, state.stock, 0f);
        config[19] = 35f;
        assertTrue(state.prepareRefresh(true, config));
        state.recordRefresh();
        assertSame(args, state.args);
        assertSame(state.tuned, state.args[0]);
        assertEquals(35f, state.tuned[19], 0f);
        assertArrayEquals(stock, state.stock, 0f);
    }

    @Test public void matchingConfigRestoresPriorOverrideThenBecomesNoOp() {
        UnifiedGlassState state = new UnifiedGlassState();
        float[] stock = HeadsUpGlassDefaults.regular();
        float[] config = UnifiedGlassPolicy.sanitize(ShadeCardGlassPolicy.defaults());
        state.recordApplied(state.observe(stock, true, config));
        float[] same = UnifiedGlassPolicy.sanitize(stock);
        assertTrue(state.prepareRefresh(true, same));
        assertArrayEquals(stock, state.tuned, 0f);
        state.recordRefresh();
        assertFalse(state.prepareRefresh(true, same));
    }
}
