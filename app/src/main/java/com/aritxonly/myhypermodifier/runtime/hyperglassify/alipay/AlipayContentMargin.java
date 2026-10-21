package com.aritxonly.myhypermodifier;

/** Tracks the latest app-requested reservation independently from our effective layout value. */
final class AlipayContentMargin {
    private int nativeBottom;
    private boolean replacing;

    AlipayContentMargin(int originalBottom) { nativeBottom = originalBottom; }

    int nativeRequest(int bottom) {
        nativeBottom = bottom;
        return replacing ? 0 : bottom;
    }

    int replace(boolean enabled, int currentBottom) {
        if (enabled && !replacing) nativeBottom = currentBottom;
        replacing = enabled;
        return replacing ? 0 : nativeBottom;
    }
}
