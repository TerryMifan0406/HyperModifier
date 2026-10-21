package com.aritxonly.myhypermodifier;

/** Conservative gates for an opt-in, one-shot material host experiment. */
final class ClockMaterialPreviewPolicy {
    static boolean homeTarget(int flags, int mode, int activityType, String packageName,
                              int taskUser, int currentUser) {
        // 0x100 is checked by the matched KeyguardService's going-away path.
        return (flags & 0x100) != 0 && (mode == 1 || mode == 3) && activityType == 2
                && "com.miui.home".equals(packageName) && taskUser == currentUser;
    }
    static boolean show(boolean enabled, boolean home, boolean originalsHidden,
                        long ageMillis, int rotation, int parts) {
        return enabled && home && originalsHidden && ageMillis >= 0 && ageMillis <= 1000
                && rotation == 0 && parts > 0 && parts <= 4;
    }
}
