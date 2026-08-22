package dev.tempestfx.entity;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Noise;

/**
 * Drift model for ball lightning, kept free of Minecraft types so it can be reasoned about and
 * tested on its own.
 */
public final class BallLightningMotion {
    /** Target hover height above the surface, in blocks. */
    public static final double HOVER_HEIGHT = 1.15;
    /** Peak horizontal drift speed, blocks per tick (~1.6 m/s at the maximum). */
    private static final double DRIFT_SPEED = 0.08;
    /** How quickly the ball corrects its height towards the hover target. */
    private static final double VERTICAL_STIFFNESS = 0.09;
    private static final double VERTICAL_DAMPING = 0.82;
    /** Noise frequency; low enough that a direction change takes about a second. */
    private static final double WANDER_RATE = 0.05;

    private BallLightningMotion() {}

    /** Horizontal drift for the given age, in blocks per tick. */
    public static double driftX(long seed, double ageTicks) {
        return Noise.value(seed, ageTicks * WANDER_RATE) * DRIFT_SPEED;
    }

    /** Horizontal drift for the given age, in blocks per tick. */
    public static double driftZ(long seed, double ageTicks) {
        return Noise.value(seed ^ 0x5f3759dfL, ageTicks * WANDER_RATE) * DRIFT_SPEED;
    }

    /**
     * Vertical velocity after one step of a damped spring towards the hover height.
     *
     * @param currentY   world Y of the ball
     * @param surfaceY   world Y of the surface underneath it
     * @param velocityY  current vertical velocity
     * @return the new vertical velocity
     */
    public static double stepVerticalVelocity(double currentY, double surfaceY, double velocityY) {
        double error = (surfaceY + HOVER_HEIGHT) - currentY;
        return (velocityY + error * VERTICAL_STIFFNESS) * VERTICAL_DAMPING;
    }

    /**
     * Output of the ball over its life, {@code 0..1}.
     */
    public static float output(float ageTicks, float lifetimeTicks) {
        if (ageTicks < 0 || ageTicks >= lifetimeTicks) return 0;
        float ignition = (float) FxMath.clamp(ageTicks / 4.0, 0, 1);
        float collapse = (float) (1.0 - FxMath.smoothstep(lifetimeTicks * 0.8, lifetimeTicks, ageTicks));
        return ignition * collapse;
    }

    /** Visible radius in blocks, breathing slightly around its nominal size. */
    public static float radius(long seed, float ageTicks, float nominalRadius) {
        double breathe = 1.0 + Noise.value(seed ^ 0x9e37L, ageTicks * 0.18) * 0.12;
        return (float) (nominalRadius * breathe);
    }
}
