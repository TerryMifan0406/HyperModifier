package com.aritxonly.myhypermodifier;

/** Per-view ownership of a temporary effective style; never stores the user's setting. */
final class ControlCenterBatteryPercentPolicy {
    private int stockStyle;
    private boolean applied;
    private boolean dirty;

    boolean hasOverride() {
        return applied || dirty;
    }

    int beforeStockStyle(int currentStyle) {
        if (!applied) return currentStyle;
        applied = false;
        dirty = true; // Stock may skip its update when the original style has not changed.
        return stockStyle;
    }

    int resolve(int currentStyle, boolean enabled, boolean expanded) {
        if (!applied || currentStyle != 3) stockStyle = currentStyle;
        int desired = enabled && expanded && stockStyle == 0 ? 3 : stockStyle;
        applied = desired != stockStyle;
        return desired;
    }

    boolean takeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    int restore(int currentStyle) {
        int result = applied ? stockStyle : currentStyle;
        applied = false;
        dirty = false;
        return result;
    }
}
