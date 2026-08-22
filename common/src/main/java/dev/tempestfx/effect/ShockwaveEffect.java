package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.math.FxMath;

/**
 * Expanding pressure ring on the struck surface.
 */
public final class ShockwaveEffect {
    private static final float LIFETIME_TICKS = 16;
    private static final float RISE_TICKS = 12;
    private static final double LAND_RADIUS = 17;
    private static final double WATER_RADIUS = 21;

    private final LightningStrikeFxEvent event;
    private final double maximumRadius;
    private final double surfaceY;
    private int age;

    public ShockwaveEffect(LightningStrikeFxEvent event) {
        this.event = event;
        this.maximumRadius = event.environment().water() ? WATER_RADIUS : LAND_RADIUS;
        this.surfaceY = event.environment().surfaceY(event.position().y());
    }

    public void tick() { age++; }

    public boolean alive() { return age < LIFETIME_TICKS; }

    public double radius(float partialTick) {
        double t = FxMath.clamp((age + partialTick) / RISE_TICKS, 0, 1);
        return maximumRadius * Math.pow(t, 0.92);
    }

    public float opacity(float partialTick) {
        float time = age + partialTick;
        double decay = 1.0 - FxMath.clamp(time / LIFETIME_TICKS, 0, 1);
        double attack = FxMath.clamp(time * 2.5, 0, 1);
        return (float) (decay * decay * attack);
    }

    /** Height of the ring geometry, so it hugs the surface the bolt actually hit. */
    public double surfaceY() { return surfaceY; }

    public LightningStrikeFxEvent event() { return event; }

    public int age() { return age; }
}
