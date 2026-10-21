package com.aritxonly.myhypermodifier;

/** Main-thread request ownership; native callbacks can outlive pause, crop and page changes. */
final class GpuBackdropFrameGate {
    private long sequence, pending, epoch, pendingEpoch;
    private boolean active, dirty;
    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        epoch++;
        dirty = value;
    }
    void invalidate() { epoch++; dirty = active; }
    void cancel() { pending = 0; epoch++; dirty = false; }
    void request() { if (active) dirty = true; }
    boolean ready() { return active && dirty && pending == 0; }
    long begin() {
        if (!ready()) return 0;
        pending = ++sequence;
        pendingEpoch = epoch;
        dirty = false;
        return pending;
    }
    boolean owns(long token) { return token != 0 && token == pending; }
    boolean complete(long token) {
        if (!owns(token)) return false;
        pending = 0;
        return active && pendingEpoch == epoch;
    }
}
