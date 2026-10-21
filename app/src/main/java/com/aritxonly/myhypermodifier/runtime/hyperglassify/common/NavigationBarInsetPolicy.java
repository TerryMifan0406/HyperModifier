package com.aritxonly.myhypermodifier;

/** Remembers observed navigation-bar insets, including the app's pre-consumption value. */
final class NavigationBarInsetPolicy {
    private int orientation = Integer.MIN_VALUE;
    private int lastBottom;

    int resolve(int currentOrientation, int panelBottom, int decorBottom, int nativeBottom) {
        if (orientation != currentOrientation) {
            orientation = currentOrientation;
            lastBottom = 0;
        }
        int current = Math.max(0, Math.max(nativeBottom, Math.max(panelBottom, decorBottom)));
        if (current > 0) lastBottom = current;
        return lastBottom;
    }
}
