package com.aritxonly.myhypermodifier;

/** Fixed release geometry; the bounded constructor also supports native geometry regression fixtures. */
final class RoundedIconTuning {
    static final RoundedIconTuning DEFAULT = new RoundedIconTuning(1.1f, .15f, .1f, 101f, 98f, 94f, .12f);
    final float radius, mobileWeight, wifiWeight, wifiScale, batteryWidth, batteryHeight, mobileOffsetDp;

    RoundedIconTuning(float radius, float mobileWeight, float wifiWeight, float wifiPercent,
                      float batteryWidthPercent, float batteryHeightPercent, float mobileOffsetDp) {
        this.radius = bounded(radius, 1.1f, 0f, 1.2f);
        this.mobileWeight = bounded(mobileWeight, .15f, 0f, .3f);
        this.wifiWeight = bounded(wifiWeight, .1f, 0f, .3f);
        this.wifiScale = bounded(wifiPercent, 101f, 90f, 110f) / 100f;
        this.batteryWidth = bounded(batteryWidthPercent, 98f, 80f, 110f) / 100f;
        this.batteryHeight = bounded(batteryHeightPercent, 94f, 80f, 110f) / 100f;
        this.mobileOffsetDp = bounded(mobileOffsetDp, .12f, -2f, 2f);
    }

    private static float bounded(float value, float fallback, float min, float max) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }

}
