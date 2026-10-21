package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnifiedGlassPolicyTest {
    @Test public void unifiesOnlyOpticalSlotsAndKeepsSourceUntouched() {
        float[] stock = HeadsUpGlassDefaults.dark();
        float[] before = stock.clone();
        float[] config = UnifiedGlassPolicy.sanitize(ShadeCardGlassPolicy.defaults());
        float[] output = new float[42];
        assertTrue(UnifiedGlassPolicy.apply(stock, config, output));
        for (int i = 0; i < 42; i++) {
            boolean optical = i >= 19 && i <= 24 || i == 32;
            assertEquals(optical ? config[i] : stock[i], output[i], 0f);
        }
        assertArrayEquals(before, stock, 0f);
        assertFalse(UnifiedGlassPolicy.apply(output.clone(), config, output));
    }

    @Test public void unrelatedPresetValuesCannotEnterUnifiedConfig() {
        float[] source = ShadeCardGlassPolicy.defaults();
        source[11] = 100f;
        source[14] = 99f;
        source[25] = -99f;
        source[40] = 1f;
        assertArrayEquals(ShadeCardGlassPolicy.defaults(), UnifiedGlassPolicy.sanitize(source), 0f);
    }

    @Test public void validatesImportedValuesAndProtectsOpticalBounds() {
        float[] source = ShadeCardGlassPolicy.defaults();
        source[19] = Float.NaN;
        source[20] = Float.POSITIVE_INFINITY;
        source[21] = -10f;
        source[22] = 2000f;
        source[23] = 3f;
        source[24] = -1f;
        source[32] = 0f;
        float[] clean = UnifiedGlassPolicy.sanitize(source);
        assertEquals(72f, clean[19], 0f);
        assertEquals(3.8f, clean[20], 0f);
        assertEquals(0f, clean[21], 0f);
        assertEquals(1000f, clean[22], 0f);
        assertEquals(2f, clean[23], 0f);
        assertEquals(0f, clean[24], 0f);
        assertEquals(1f, clean[32], 0f);
        assertArrayEquals(ShadeCardGlassPolicy.defaults(), UnifiedGlassPolicy.sanitize(null), 0f);
        assertArrayEquals(ShadeCardGlassPolicy.defaults(), UnifiedGlassPolicy.sanitize(new float[4]), 0f);
    }

    @Test public void repeatedAnimatedStockUpdatesPreserveNewLocalColors() {
        float[] stock = HeadsUpGlassDefaults.regular();
        float[] config = UnifiedGlassPolicy.sanitize(ShadeCardGlassPolicy.defaults());
        float[] output = new float[42];
        UnifiedGlassPolicy.apply(stock, config, output);
        stock[14] = .25f;
        stock[34] = -.2f;
        stock[21] = 12f;
        UnifiedGlassPolicy.apply(stock, config, output);
        assertEquals(.25f, output[14], 0f);
        assertEquals(-.2f, output[34], 0f);
        assertEquals(80f, output[21], 0f);
        assertEquals(12f, stock[21], 0f);
    }

    @Test public void clearAndInvalidRecipesAreNeverCandidates() {
        assertFalse(ShadeCardGlassPolicy.isActiveRecipe(new float[42]));
        assertFalse(ShadeCardGlassPolicy.isActiveRecipe(null));
        float[] invalid = ShadeCardGlassPolicy.defaults();
        invalid[0] = Float.NaN;
        assertFalse(ShadeCardGlassPolicy.isActiveRecipe(invalid));
    }
}
