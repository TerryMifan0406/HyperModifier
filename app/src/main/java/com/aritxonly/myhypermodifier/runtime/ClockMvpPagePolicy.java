package com.aritxonly.myhypermodifier;

/** Freshness fence: a previous unlock's cached first page is never sufficient. */
final class ClockMvpPagePolicy {
    record Snapshot(boolean ready, int page, long epoch, long suppressed) {}
    static boolean approve(Snapshot before, Snapshot after) {
        return before != null && after != null && before.ready() && after.ready()
                && after.page() == 0 && after.epoch() != before.epoch()
                && after.suppressed() > before.suppressed();
    }
    static Snapshot parse(String text) {
        boolean protocol = false, ready = false;
        int page = -1; long epoch = -1, suppressed = -1;
        for (String line : text.split("\\n")) {
            if (line.equals("MHM_CLOCK_MVP_V1")) protocol = true;
            else if (protocol && line.equals("ready=1")) ready = true;
            else if (protocol && line.startsWith("page=")) page = Integer.parseInt(line.substring(5));
            else if (protocol && line.startsWith("epoch=")) epoch = Long.parseLong(line.substring(6));
            else if (protocol && line.startsWith("suppressed=")) suppressed = Long.parseLong(line.substring(11));
        }
        return protocol && epoch >= 0 && epoch <= 0xffffffffL && page >= -1 && page <= 31 && suppressed >= 0
                ? new Snapshot(ready, page, epoch, suppressed) : null;
    }
}
