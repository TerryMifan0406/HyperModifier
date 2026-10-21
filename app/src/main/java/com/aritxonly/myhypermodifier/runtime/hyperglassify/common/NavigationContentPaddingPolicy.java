package com.aritxonly.myhypermodifier;

/** Event-driven targets discover scrolling content on page/geometry changes, not display frames. */
final class NavigationContentPaddingPolicy {
    private boolean dirty = true;
    private int appliedInset = -1;

    void invalidate() { dirty = true; }

    boolean shouldApply(int inset, boolean continuous) {
        if (inset <= 0) return false;
        if (!continuous && !dirty && appliedInset == inset) return false;
        dirty = false;
        appliedInset = inset;
        return true;
    }
}
