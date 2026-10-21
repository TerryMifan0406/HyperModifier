package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProbeEventHistoryTest {
    @Test public void retainsNewestEventsInChronologicalOrder() {
        var history = new ProbeEventHistory(2);
        history.add("start"); history.add("inner"); history.add("finish");
        assertArrayEquals(new String[]{"inner", "finish"}, history.snapshot());
        String[] copy = history.snapshot(); copy[0] = "changed";
        assertArrayEquals(new String[]{"inner", "finish"}, history.snapshot());
    }
    @Test public void limitsSingleEventAndIgnoresNull() {
        var history = new ProbeEventHistory(2);
        history.add(null); history.add("x".repeat(4000));
        assertEquals(1, history.snapshot().length);
        assertTrue(history.snapshot()[0].endsWith(" [truncated]"));
        assertTrue(history.snapshot()[0].length() < 3100);
    }
    @Test public void concurrentReadersSeeCompleteBoundedSnapshots() throws Exception {
        var history = new ProbeEventHistory(16);
        Thread writer = new Thread(() -> { for (int i = 0; i < 10000; i++) history.add(Integer.toString(i)); });
        writer.start();
        for (int i = 0; i < 10000; i++) {
            String[] copy = history.snapshot(); assertTrue(copy.length <= 16);
            for (int j = 1; j < copy.length; j++)
                assertEquals(Integer.parseInt(copy[j - 1]) + 1, Integer.parseInt(copy[j]));
        }
        writer.join(); assertEquals("9999", history.snapshot()[15]);
    }
}
