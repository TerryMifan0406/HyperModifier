package com.aritxonly.myhypermodifier;

/** Conservative optical-only allowlist. Color, lighting, masks and reserved slots stay local. */
final class UnifiedGlassPolicy {
    private static final int[] INDICES = {19, 20, 21, 22, 23, 24, 32};
    private static final float[] MIN = {0f, .1f, 0f, 0f, 0f, 0f, 1f};
    private static final float[] MAX = {100f, 8f, 120f, 1000f, 2f, 2f, 5f};

    private UnifiedGlassPolicy() {}

    static int[] indices() { return INDICES.clone(); }

    static float[] sanitize(float[] input) {
        float[] result = ShadeCardGlassPolicy.defaults();
        if (input == null || input.length != result.length) return result;
        for (int n = 0; n < INDICES.length; n++) {
            int i = INDICES[n];
            if (Float.isFinite(input[i])) result[i] = Math.max(MIN[n], Math.min(MAX[n], input[i]));
        }
        return result;
    }

    /** Destination belongs to the target; no allocation on repeated native material updates. */
    static boolean apply(float[] stock, float[] configured, float[] destination) {
        System.arraycopy(stock, 0, destination, 0, stock.length);
        boolean changed = false;
        for (int i : INDICES) {
            changed |= stock[i] != configured[i];
            destination[i] = configured[i];
        }
        return changed;
    }
}
