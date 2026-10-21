package com.aritxonly.myhypermodifier;

/** Small process-local history so a short logcat buffer cannot erase a probe run. */
final class ProbeEventHistory {
    private final String[] entries;
    private int next, size;
    ProbeEventHistory(int capacity) {
        if (capacity < 1 || capacity > 64) throw new IllegalArgumentException("capacity");
        entries = new String[capacity];
    }
    synchronized void add(String value) {
        if (value == null) return;
        entries[next] = value.length() > 3072 ? value.substring(0, 3072) + " [truncated]" : value;
        next = (next + 1) % entries.length;
        size = Math.min(size + 1, entries.length);
    }
    synchronized String[] snapshot() {
        String[] copy = new String[size];
        for (int i = 0; i < size; i++) copy[i] = entries[(next - size + i + entries.length) % entries.length];
        return copy; // Print outside the lock: dumpsys must never block the callback on I/O.
    }
}
