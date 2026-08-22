package dev.tempestfx.server;

import dev.tempestfx.math.FxMath;

/**
 * Damage curve for being close to a strike without being hit by it.
 *
 * <p>Starts outside vanilla's own box (see {@link #insideVanillaBox}) and falls to zero at the
 * configured radius, so nothing is damaged twice for one bolt.
 */
public final class NearMissDamage {
    /** Horizontal half-extent of vanilla's own lightning damage box, in blocks. */
    public static final double VANILLA_RADIUS = 3.0;
    /** Extra height vanilla's box carries above the strike, on top of {@link #VANILLA_RADIUS}. */
    public static final double VANILLA_EXTRA_HEIGHT = 6.0;

    private NearMissDamage() {}

    /**
     * Whether a target sits inside the box vanilla itself damages.
     *
     * <p>{@code LightningBolt#tick} hits everything in {@code x±3, y-3..y+9, z±3}. It is not
     * symmetric, so a 3-block sphere does not describe it.
     *
     * @param dx target minus strike, on x
     * @param dy target minus strike, on y
     * @param dz target minus strike, on z
     */
    public static boolean insideVanillaBox(double dx, double dy, double dz) {
        return Math.abs(dx) <= VANILLA_RADIUS
            && Math.abs(dz) <= VANILLA_RADIUS
            && dy >= -VANILLA_RADIUS
            && dy <= VANILLA_RADIUS + VANILLA_EXTRA_HEIGHT;
    }

    /**
     * @param distance horizontal-and-vertical distance from the strike, in blocks
     * @param radius   outer radius from the config
     * @param maximum  damage at the edge of vanilla's box
     * @return damage to apply, or 0 when the target is inside vanilla's box or beyond the radius
     */
    public static float damageAt(double distance, double radius, float maximum) {
        if (maximum <= 0 || radius <= VANILLA_RADIUS) return 0;
        if (distance <= VANILLA_RADIUS) return 0;
        if (distance >= radius) return 0;
        double t = (distance - VANILLA_RADIUS) / (radius - VANILLA_RADIUS);
        // Squared falloff: the drop is steep just outside the strike and gentle further out.
        double falloff = 1.0 - t;
        return (float) (maximum * falloff * falloff);
    }

    /** Whether a target this close should also catch fire. */
    public static boolean ignites(double distance, double radius, float igniteFraction) {
        if (igniteFraction <= 0) return false;
        double limit = FxMath.clamp(VANILLA_RADIUS + (radius - VANILLA_RADIUS) * igniteFraction,
            VANILLA_RADIUS, radius);
        return distance > VANILLA_RADIUS && distance <= limit;
    }
}
