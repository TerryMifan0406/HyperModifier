package com.aritxonly.myhypermodifier;

import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class StatusBarClockSecondsPolicyTest {
    @Test public void ticksOnlyWhenEveryLifecycleGateIsOpen() {
        for (int mask = 0; mask < 32; mask++) {
            boolean enabled = (mask & 1) != 0;
            boolean screen = (mask & 2) != 0;
            boolean attached = (mask & 4) != 0;
            boolean shown = (mask & 8) != 0;
            boolean demo = (mask & 16) != 0;
            assertEquals("mask " + mask, mask == 15,
                    StatusBarClockSecondsPolicy.shouldTick(enabled, screen, attached, shown, demo));
        }
    }

    @Test public void alignsToWallClockSecondsWithoutImmediateRescheduleLoops() {
        assertEquals(1000, StatusBarClockSecondsPolicy.delayToNextSecond(0));
        assertEquals(1000, StatusBarClockSecondsPolicy.delayToNextSecond(1000));
        assertEquals(1, StatusBarClockSecondsPolicy.delayToNextSecond(1999));
        assertEquals(750, StatusBarClockSecondsPolicy.delayToNextSecond(1250));
        assertEquals(1, StatusBarClockSecondsPolicy.delayToNextSecond(-1));
        assertEquals(1000, StatusBarClockSecondsPolicy.delayToNextSecond(-1000));
    }

    private String time(long millis, boolean is24, boolean amPm) {
        SimpleDateFormat format = new SimpleDateFormat(
                StatusBarClockSecondsPolicy.pattern(is24, amPm), Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    @Test public void preserves24HourLeadingZeroAndMinuteRollover() {
        assertEquals("00:00:59", time(59000, true, false));
        assertEquals("00:01:00", time(60000, true, false));
        assertEquals("00:00:00", time(86400000, true, true));
    }

    @Test public void preserves12HourAndAmPmVisibility() {
        assertEquals("12:00:00", time(0, false, false));
        assertEquals("12:00:00 AM", time(0, false, true));
        assertEquals("1:00:01 PM", time(46801000, false, true));
    }
    @Test public void removesOnlyHourPaddingAcrossLocalizedDateOrders() {
        assertEquals(0, StatusBarClockSecondsPolicy.leadingHourZero("08:05"));
        assertEquals(0, StatusBarClockSecondsPolicy.leadingHourZero("00:05:09"));
        assertEquals(11, StatusBarClockSecondsPolicy.leadingHourZero("Tue, Oct 6 08:05"));
        assertEquals(1, StatusBarClockSecondsPolicy.leadingHourZero("\u200f٠٨:٠٥"));
        for (String text : new String[]{"8:05:09", "18:05:09", "10:05", "12:00:09", "2026/10/06", "", "08"}) {
            assertEquals(text, -1, StatusBarClockSecondsPolicy.leadingHourZero(text));
        }
        assertEquals(-1, StatusBarClockSecondsPolicy.leadingHourZero(null));
    }

    @Test public void unpaddedSecondsPreserveMidnightAndTwelveHourMode() {
        SimpleDateFormat format = new SimpleDateFormat(
                StatusBarClockSecondsPolicy.pattern(true, false, true), Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals("0:00:00", format.format(new Date(0)));
        assertEquals("8:05:09", format.format(new Date(29109000)));
        assertEquals("10:00:00", format.format(new Date(36000000)));
        assertEquals("h:mm:ss a", StatusBarClockSecondsPolicy.pattern(false, true, true));
        assertEquals("h:mm:ss", StatusBarClockSecondsPolicy.pattern(false, false, true));
    }

    @Test public void landscapeKeepsLocalizedDatesAndQuotedLiterals() {
        assertEquals("H:mm:ss E, MMM d", StatusBarClockSecondsPolicy.withSeconds("HH:mm E, MMM d", true));
        assertEquals("M月d日 (E) HH:mm:ss", StatusBarClockSecondsPolicy.withSeconds("M月d日 (E) HH:mm", false));
        assertEquals("h:mm:ss a, E, MMM d", StatusBarClockSecondsPolicy.withSeconds("h:mm a, E, MMM d", true));
        assertEquals("'HH:mm' H:mm:ss", StatusBarClockSecondsPolicy.withSeconds("'HH:mm' HH:mm", true));
    }
}
