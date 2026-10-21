package com.aritxonly.myhypermodifier;

/** Current desktop calibration. Font axes own the shape; placement uses uniform scale. */
final class ClockMvpGeometry {
    static final int WIDTH = 1220, HEIGHT = 2656, DENSITY = 520;
    static final Box TARGET = new Box(236f, 468f, 988f, 699f);
    record Box(float left, float top, float right, float bottom) {
        float width() { return right - left; }
        float height() { return bottom - top; }
        float centerX() { return (left + right) * .5f; }
        float centerY() { return (top + bottom) * .5f; }
        boolean valid() {
            return Float.isFinite(left) && Float.isFinite(top) && Float.isFinite(right)
                    && Float.isFinite(bottom) && width() > 0f && height() > 0f;
        }
    }
    record Plan(float pivotX, float pivotY, float scale, float moveX, float moveY) {
        float mapX(float x) { return pivotX + (x - pivotX) * scale + moveX; }
        float mapY(float y) { return pivotY + (y - pivotY) * scale + moveY; }
    }
    static boolean calibratedDisplay(int width, int height, int density) {
        return width == WIDTH && height == HEIGHT && density == DENSITY;
    }
    static float fontRatioMultiplier(Box source) {
        return TARGET.width() * source.height() / (source.width() * TARGET.height());
    }
    static Plan plan(Box source, int width, int height, int density) {
        return plan(source, source, width, height, density);
    }
    static Plan plan(Box source, Box shaped, int width, int height, int density) {
        if (!calibratedDisplay(width, height, density) || source == null || !source.valid()
                || shaped == null || !shaped.valid() || source.left() < 0f || source.top() < 0f
                || source.right() > width || source.bottom() > height) return null;
        // Unreachable native font shapes stay inside the destination, without stretching.
        float scale = Math.min(TARGET.width() / shaped.width(), TARGET.height() / shaped.height());
        if (!Float.isFinite(scale) || scale < .05f || scale > 2f) return null;
        return new Plan(source.centerX(), source.centerY(), scale,
                TARGET.centerX() - source.centerX() - (shaped.centerX() - source.centerX()) * scale,
                TARGET.centerY() - source.centerY() - (shaped.centerY() - source.centerY()) * scale);
    }
}
