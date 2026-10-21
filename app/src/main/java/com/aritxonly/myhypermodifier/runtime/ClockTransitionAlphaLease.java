package com.aritxonly.myhypermodifier;

import java.lang.ref.WeakReference;

/** A bounded, compare-before-restore lease; never owns ordinary View alpha. */
final class ClockTransitionAlphaLease<T> {
    interface Access<T> {
        float read(T target) throws ReflectiveOperationException;
        void write(T target, float value) throws ReflectiveOperationException;
    }
    private final WeakReference<T> target;
    private final Access<T> access;
    private boolean owned;

    ClockTransitionAlphaLease(T target, Access<T> access) {
        this.target = new WeakReference<>(target); this.access = access;
    }
    T target() { return target.get(); }

    boolean acquire() throws ReflectiveOperationException {
        T view = target.get();
        // A pre-existing transition fade belongs to the system or another module.
        if (owned || view == null || access.read(view) != 1f) return false;
        owned = true; // Restore also covers a setter that changes state before throwing.
        access.write(view, 0f);
        return access.read(view) == 0f;
    }

    void restore() throws ReflectiveOperationException {
        if (!owned) return;
        T view = target.get();
        if (view != null && access.read(view) == 0f) access.write(view, 1f);
        owned = false;
    }
}
