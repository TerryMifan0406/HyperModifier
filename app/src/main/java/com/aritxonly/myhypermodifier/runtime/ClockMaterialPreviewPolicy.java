package com.aritxonly.myhypermodifier;

/** Conservative gates for the native clock handoff experiment. */
final class ClockMaterialPreviewPolicy {
    static boolean homeTarget(int flags, int mode, int activityType, String packageName,
                              int taskUser, int currentUser) {
        // 0x100 is checked by the matched KeyguardService's going-away path.
        return (flags & 0x100) != 0 && (mode == 1 || mode == 3) && activityType == 2
                && "com.miui.home".equals(packageName) && taskUser == currentUser;
    }
    static boolean show(boolean enabled, boolean home,
                        long ageMillis, int rotation, int parts) {
        return home && prepare(enabled, ageMillis, rotation, parts);
    }
    static boolean prepare(boolean enabled, long ageMillis, int rotation, int parts) {
        return enabled && ageMillis >= 0 && ageMillis <= 1000
                && rotation == 0 && parts > 0 && parts <= 4;
    }
    static float opacity(float capturedAlpha, float capturedFontAlpha, float currentAlpha, float currentFontAlpha) {
        if (!Float.isFinite(capturedAlpha) || !Float.isFinite(capturedFontAlpha)
                || !Float.isFinite(currentAlpha) || !Float.isFinite(currentFontAlpha)
                || capturedAlpha <= 0f || capturedAlpha > 1f || capturedFontAlpha <= 0f
                || capturedFontAlpha > 1f || currentAlpha <= 0f || currentAlpha > 1f
                || currentFontAlpha <= 0f || currentFontAlpha > 1f) return 0f;
        // Preserve the captured font alpha and compensate only via replica View alpha.
        return Math.min(capturedAlpha, currentAlpha * currentFontAlpha / capturedFontAlpha);
    }
}
