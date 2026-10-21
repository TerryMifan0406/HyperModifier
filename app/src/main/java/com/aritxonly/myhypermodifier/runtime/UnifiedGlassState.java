package com.aritxonly.myhypermodifier;

import java.util.concurrent.atomic.AtomicBoolean;

/** Per-target arrays contain no View references. Only observed calls may update stock. */
final class UnifiedGlassState {
    final float[] stock = new float[ShadeCardGlassPolicy.COUNT];
    final float[] tuned = new float[ShadeCardGlassPolicy.COUNT];
    final Object[] args = {tuned};
    final AtomicBoolean pending = new AtomicBoolean();
    private boolean applied;
    private boolean preparedOverride;

    boolean observe(float[] original, boolean enabled, float[] config) {
        System.arraycopy(original, 0, stock, 0, original.length);
        return enabled && UnifiedGlassPolicy.apply(stock, config, tuned);
    }

    void recordApplied(boolean override) { applied = override; }

    boolean prepareRefresh(boolean enabled, float[] config) {
        boolean override = enabled && UnifiedGlassPolicy.apply(stock, config, tuned);
        preparedOverride = override;
        if (!override && !applied) return false;
        if (!override) System.arraycopy(stock, 0, tuned, 0, stock.length);
        return true;
    }

    void recordRefresh() { applied = preparedOverride; }
}
