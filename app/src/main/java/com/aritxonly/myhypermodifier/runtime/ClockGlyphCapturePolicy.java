package com.aritxonly.myhypermodifier;

/** Matches TimeView's native colon fade branch; used only for one-shot snapshots. */
final class ClockGlyphCapturePolicy {
    record Plan(boolean split, float firstAlpha, float secondAlpha) {}

    static Plan plan(int length, int colonSplitIndex, float colonAlpha, float fontAlpha) {
        if (length <= 0 || length > 16 || colonSplitIndex < 0 || colonSplitIndex > length
                || !Float.isFinite(colonAlpha) || colonAlpha < 0 || colonAlpha > 1
                || !Float.isFinite(fontAlpha) || fontAlpha <= 0 || fontAlpha > 1) return null;
        if (colonSplitIndex > 0 && colonAlpha < 1)
            return new Plan(true, fontAlpha * colonAlpha, fontAlpha);
        return new Plan(false, fontAlpha, 0);
    }

    static String role(String className) {
        return switch (className) {
            case "com.miui.clock.allInOne.AllInOneHourClock" -> "HOUR_CLOCK";
            case "com.miui.clock.allInOne.AllInOneMinuteClock" -> "MINUTE_CLOCK";
            case "com.miui.clock.allInOne.AllInOneSingleClock" -> "SINGLE_CLOCK";
            default -> "UNKNOWN";
        };
    }
}
