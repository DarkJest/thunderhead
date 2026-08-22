package dev.tempestfx.math;

public final class FxMath {
    private FxMath() {}

    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) return value < edge0 ? 0.0 : 1.0;
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** 1 while below {@code fullUntil}, smoothly reaching 0 at {@code zeroAt}. */
    public static double distanceFalloff(double distance, double fullUntil, double zeroAt) {
        return 1.0 - smoothstep(fullUntil, zeroAt, distance);
    }

    public static double easeOutCubic(double t) { double q = 1.0 - clamp(t, 0, 1); return 1.0 - q * q * q; }
    public static double lerp(double from, double to, double t) { return from + (to - from) * t; }
    public static float lerp(float from, float to, float t) { return from + (to - from) * t; }
}
