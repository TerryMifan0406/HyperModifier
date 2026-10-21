package com.aritxonly.myhypermodifier;

/** User-approved calibration of the current desktop page, not a grid-position heuristic. */
final class ClockMvpGeometry {
    // Read-only screencap 2026-10-21: visible white time ink, excluding date/weather.
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
    record Plan(float pivotX, float pivotY, float scaleX, float scaleY, float moveX, float moveY) {
        float mapX(float x) { return pivotX + (x - pivotX) * scaleX + moveX; }
        float mapY(float y) { return pivotY + (y - pivotY) * scaleY + moveY; }
    }
    static boolean calibratedDisplay(int width, int height, int density) {
        return width == WIDTH && height == HEIGHT && density == DENSITY;
    }
    static Plan plan(Box source, int width, int height, int density) {
        if (!calibratedDisplay(width, height, density) || source == null || !source.valid()
                || source.left() < 0f || source.top() < 0f || source.right() > width
                || source.bottom() > height) return null;
        float sx = TARGET.width() / source.width(), sy = TARGET.height() / source.height();
        if (sx < .05f || sx > 2f || sy < .05f || sy > 2f) return null;
        return new Plan(source.centerX(), source.centerY(), sx, sy,
                TARGET.centerX() - source.centerX(), TARGET.centerY() - source.centerY());
    }
}
