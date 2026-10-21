package com.aritxonly.myhypermodifier;

/** Scheduling and format rules independent of the SystemUI implementation. */
final class StatusBarClockSecondsPolicy {
    private StatusBarClockSecondsPolicy() {}

    static boolean shouldTick(boolean enabled, boolean screenOn, boolean attached,
                              boolean shown, boolean demo) {
        return enabled && screenOn && attached && shown && !demo;
    }

    static long delayToNextSecond(long wallTimeMillis) {
        return 1000L - Math.floorMod(wallTimeMillis, 1000L);
    }

    static String pattern(boolean is24, boolean showAmPm) {
        return pattern(is24, showAmPm, false);
    }

    static String pattern(boolean is24, boolean showAmPm, boolean hideLeadingZero) {
        return is24 ? (hideLeadingZero ? "H:mm:ss" : "HH:mm:ss")
                : showAmPm ? "h:mm:ss a" : "h:mm:ss";
    }

    /** Locate the hour even when a localized date or bidi mark precedes it. */
    static int leadingHourZero(CharSequence text) {
        if (text == null) return -1;
        for (int i = 0; i + 3 < text.length(); i++) {
            if (Character.digit(text.charAt(i), 10) == 0
                    && Character.digit(text.charAt(i + 1), 10) >= 0
                    && (text.charAt(i + 2) == ':' || text.charAt(i + 2) == '：')
                    && (i == 0 || (Character.digit(text.charAt(i - 1), 10) < 0
                        && text.charAt(i - 1) != ':' && text.charAt(i - 1) != '：'))) return i;
        }
        return -1;
    }

    /** Preserve the stock localized date, order and quoted literals in landscape. */
    static String withSeconds(String pattern, boolean hideLeadingZero) {
        StringBuilder result = new StringBuilder(pattern.length() + 3);
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            result.append(c);
            if (c == '\'') quoted = !quoted;
            if (!quoted && c == 'H' && hideLeadingZero) {
                while (i + 1 < pattern.length() && pattern.charAt(i + 1) == 'H') i++;
            }
            if (!quoted && c == 'm' && (i + 1 == pattern.length() || pattern.charAt(i + 1) != 'm')) {
                result.append(":ss");
            }
        }
        return result.toString();
    }
}
