package com.aritxonly.myhypermodifier;

import java.util.regex.Pattern;

final class RoundedNetworkIconPolicy {
    static final int MOBILE = 1;
    static final int WIFI = 2;
    private static final Pattern MOBILE_NAME = Pattern.compile("stat_sys_signal_[0-4](?:_no_voice)?(?:_darkmode|_tint)?");
    private static final Pattern WIFI_NAME = Pattern.compile("stat_sys_(?:slave_wifi_signal_|wifi_signal_(?:unavailable_)?)[0-3](?:_darkmode|_tint)?");

    private RoundedNetworkIconPolicy() {}

    static int kind(String name) {
        if (MOBILE_NAME.matcher(name).matches()) return MOBILE;
        if (WIFI_NAME.matcher(name).matches()) return WIFI;
        return 0;
    }

    static boolean weightPath(int kind, String data) {
        return roundPath(kind, data) || (kind == WIFI
                && (data.startsWith("M9.778,") || data.startsWith("M9.249,")));
    }

    // Restrict geometry edits to known signal components, leaving dots, badges and warning marks intact.
    static boolean roundPath(int kind, String data) {
        if (kind == MOBILE) return data.startsWith("M14.917,") || data.startsWith("M10.665,")
                || data.startsWith("M6.412,") || data.startsWith("M2.16,");
        // Partial arcs near dual-WLAN/no-internet badges have intentionally narrow cutouts.
        // Keep those paths unchanged rather than shortening their thin ends during rounding.
        if (kind == WIFI) return data.startsWith("M16.025,") || data.startsWith("M5.678,")
                || data.startsWith("M6.057,");
        return false;
    }
}
