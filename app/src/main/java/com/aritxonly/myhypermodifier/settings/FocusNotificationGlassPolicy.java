package com.aritxonly.myhypermodifier;

/** Three native recipes, identical in the old and OS4.0.2.0 reference APKs. */
final class FocusNotificationGlassPolicy {
    static final int NORMAL = 0;
    static final int KEYGUARD = 1;
    static final int AOD = 2;
    private static final float[] DARK_NORMAL = {
            0.6f, 0.22f, 0.24f, 0.0f, 0.2f, 1.8f, 0.1f, 0.3f, 0.6f, 1.0f,
            0.02f, 0.06f, 0.06f, 0.06f, 0.70f, 0.2f, 0.3f, 1.0f, 1.0f, 72.0f,
            3.8f, 80.0f, 1200.0f, 1.4f, 1.2f, -0.4f, 0.6f, -0.8f, 1.3f, 0.9f,
            1.4f, 1.15f, 3.0f, 4.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f
    };
    private static final float[] DARK_KEYGUARD = {
            0.0f, 2.0f, 0.5f, 0.8f, 0.15f, 1.4f, 0.3f, 0.2f, 0.6f, 1.0f,
            0.02f, 0.06f, 0.06f, 0.06f, 0.5f, 0.15f, 0.4f, 1.36f, 1.0f, 72.0f,
            3.8f, 80.0f, 1000.0f, 1.4f, 0.8f, -0.4f, 0.6f, -0.8f, 1.6f, 1.2f,
            0.8f, 1.15f, 3.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f
    };
    private static final float[] DARK_AOD = {
            2.0f, 2.0f, 0.4f, 0.2f, 0.4f, 1.6f, 0.15f, 0.4f, 0.6f, 1.0f,
            0.02f, 0.06f, 0.06f, 0.06f, 0.50f, 0.26f, 0.3f, 1.8f, 1.0f, 72.0f,
            3.8f, 80.0f, 1000.0f, 1.0f, 0.8f, -0.4f, 0.6f, -0.8f, 1.6f, 1.2f,
            0.8f, 1.15f, 3.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f
    };

    static float[] apply(float[] stock, int kind, int percent, float[] output) {
        float[] recipe = kind == KEYGUARD ? DARK_KEYGUARD : kind == AOD ? DARK_AOD : DARK_NORMAL;
        System.arraycopy(recipe, 0, output, 0, recipe.length);
        output[14] = Math.max(0, Math.min(100, percent)) / 100f;
        // Alpha is owned by the system transition, including fade-out. Never force it to 1.
        output[18] = stock[18];
        return output;
    }
}
