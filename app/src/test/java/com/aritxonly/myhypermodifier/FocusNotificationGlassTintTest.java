package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class FocusNotificationGlassTintTest {
    @Test public void optInReplacesEntireWhiteRecipeAndDisablingRestoresLatestStock() {
        FocusNotificationGlassTintHooks.State state = new FocusNotificationGlassTintHooks.State();
        float[] stock = ShadeCardGlassPolicy.defaults();
        float[] snapshot = stock.clone();
        assertSame(stock, state.observe(stock, -1)[0]);
        float[] dark = (float[]) state.replay(70)[0];
        assertEquals(.6f, dark[0], 0f);
        assertEquals(1.8f, dark[5], 0f);
        assertEquals(.06f, dark[11], 0f);
        assertEquals(.06f, dark[12], 0f);
        assertEquals(.06f, dark[13], 0f);
        assertEquals(.70f, dark[14], 0f);
        assertEquals(1200f, dark[22], 0f);
        assertArrayEquals(snapshot, stock, 0f);
        assertArrayEquals(snapshot, (float[]) state.replay(-1)[0], 0f);
        stock[11] = .9f;
        stock[14] = .15f;
        state.observe(stock, 65);
        assertArrayEquals(stock, (float[]) state.replay(-1)[0], 0f);
    }

    @Test public void opacityExtremesRetainSystemFadeAndReuseAllBuffers() {
        FocusNotificationGlassTintHooks.State state = new FocusNotificationGlassTintHooks.State();
        float[] stock = ShadeCardGlassPolicy.defaults();
        float[] savedStock = state.stock;
        float[] tuned = state.tuned;
        Object[] args = state.args;
        for (int frame = 0; frame < 120; frame++) {
            stock[18] = frame / 119f;
            assertSame(args, state.observe(stock, 65));
            assertSame(tuned, args[0]);
            assertSame(savedStock, state.stock);
            assertEquals(.65f, tuned[14], 0f);
            assertEquals(stock[18], tuned[18], 0f);
        }
        assertEquals(0f, ((float[]) state.replay(0)[0])[14], 0f);
        assertEquals(1f, ((float[]) state.replay(100)[0])[14], 0f);
        assertArrayEquals(stock, (float[]) state.replay(-1)[0], 0f);
    }
    @Test public void lockscreenAndAodUseTheirOwnRecipeAndRestoreStockAfterModeChanges() {
        FocusNotificationGlassTintHooks.State state = new FocusNotificationGlassTintHooks.State();
        float[] stock = ShadeCardGlassPolicy.defaults();
        state.kind = FocusNotificationGlassPolicy.KEYGUARD;
        stock[18] = .35f;
        float[] dark = (float[]) state.observe(stock, 45)[0];
        assertEquals(0f, dark[0], 0f);
        assertEquals(.8f, dark[3], 0f);
        assertEquals(1.36f, dark[17], 0f);
        assertEquals(.45f, dark[14], 0f);
        assertEquals(.35f, dark[18], 0f);
        state.kind = FocusNotificationGlassPolicy.AOD;
        assertSame(dark, state.observe(stock, 45)[0]);
        assertEquals(2f, dark[0], 0f);
        assertEquals(.2f, dark[3], 0f);
        assertEquals(1.8f, dark[17], 0f);
        assertEquals(.35f, dark[18], 0f);
        assertArrayEquals(stock, (float[]) state.replay(-1)[0], 0f);
        state.kind = FocusNotificationGlassPolicy.NORMAL;
        state.observe(stock, 70);
        assertEquals(.6f, dark[0], 0f);
        assertEquals(1f, dark[17], 0f);
        assertArrayEquals(stock, (float[]) state.replay(-1)[0], 0f);
    }
}
