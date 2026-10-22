package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedNetworkIconPolicyTest {
    @Test public void recognizesAllLevelsAndColorVariantsButNotBadgesOrDisconnectedShapes() {
        for (String suffix : new String[]{"", "_darkmode", "_tint"}) {
            for (int level = 0; level <= 4; level++) {
                assertEquals(1, RoundedNetworkIconPolicy.kind("stat_sys_signal_" + level + suffix));
                assertEquals(1, RoundedNetworkIconPolicy.kind("stat_sys_signal_" + level + "_no_voice" + suffix));
            }
            for (int level = 0; level <= 3; level++) {
                for (String prefix : new String[]{"stat_sys_wifi_signal_", "stat_sys_slave_wifi_signal_", "stat_sys_wifi_signal_unavailable_"}) {
                    assertEquals(2, RoundedNetworkIconPolicy.kind(prefix + level + suffix));
                }
            }
        }
        for (String name : new String[]{"stat_sys_signal_null", "stat_sys_signal_5", "stat_sys_wifi_signal_4",
                "stat_sys_wifi_in", "stat_sys_wifi_out", "stat_sys_signal_volte", "stat_sys_signal_flightmode",
                "ic_no_internet_wifi_signal_3", "unrelated_stat_sys_signal_4"}) {
            assertEquals(name, 0, RoundedNetworkIconPolicy.kind(name));
        }
    }

    @Test public void doesNotRoundWifiVersionDotOrWarningGlyphs() {
        for (String path : new String[]{"M9.778,15.752C", "M9.249,15.31C", "M16.194,13.623C", "M16.431,3.331C"}) {
            assertFalse(RoundedNetworkIconPolicy.roundPath(2, path));
        }
        assertTrue(RoundedNetworkIconPolicy.weightPath(2, "M9.778,15.752C"));
        assertTrue(RoundedNetworkIconPolicy.weightPath(2, "M9.249,15.31C"));
        assertFalse(RoundedNetworkIconPolicy.weightPath(2, "M16.194,13.623C"));
        assertFalse(RoundedNetworkIconPolicy.weightPath(2, "M16.431,3.331C"));
        assertFalse(RoundedNetworkIconPolicy.roundPath(1, "M4.0,2.0L10,10Z"));
        assertTrue(RoundedNetworkIconPolicy.roundPath(2, "M16.025,8.728C"));
        assertTrue(RoundedNetworkIconPolicy.roundPath(1, "M14.917,5.028C"));
    }
}
