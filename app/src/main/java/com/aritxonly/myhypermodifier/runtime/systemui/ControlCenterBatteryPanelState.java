package com.aritxonly.myhypermodifier;

/** Raw EXPANDING means either opening or closing; retain the direction from the last stable state. */
final class ControlCenterBatteryPanelState {
    static final int COLLAPSED = 0;
    static final int EXPANDING = 1;
    static final int EXPANDED = 2;
    private int phase = COLLAPSED;
    private boolean showing;

    boolean update(int next) {
        boolean previous = showing;
        if (next == EXPANDED) showing = true;
        else if (next == COLLAPSED) showing = false;
        else if (next == EXPANDING && phase != EXPANDING) showing = phase != EXPANDED;
        phase = next;
        return previous != showing;
    }

    boolean showing() { return showing; }
}
