package dev.tempestfx.server;

/** Game damage approximation: connected surfaces attenuate current; gaps and airborne targets break it. */
public final class GroundCurrent {
    private GroundCurrent() {}
    public static float damage(double distance, double radius, float maximum, float conductivity, boolean grounded) {
        if (!grounded || !Float.isFinite(conductivity) || conductivity <= 0) return 0;
        return Math.min(maximum, NearMissDamage.damageAt(distance, radius, maximum) * Math.min(1.5f, conductivity));
    }
    public static float sideFlash(double distance, double radius, float maximum, boolean conductor, boolean unobstructed) {
        if (!conductor || !unobstructed || radius <= 0 || distance >= radius || distance < 0) return 0;
        return (float) (maximum * .5 * Math.pow(1-distance/radius,2));
    }
}
