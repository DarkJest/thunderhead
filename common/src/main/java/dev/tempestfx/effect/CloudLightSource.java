package dev.tempestfx.effect;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * One region of cloud lit from inside by a discharge.
 *
 * <p>Not a light in the world: nothing is relit, no block light is written and no volume is
 * ray-marched. It is a pulsing, irregular emissive volume the renderer draws as a handful of warped
 * billboards, which is the cheap approximation that reads correctly from a distance - and reading
 * correctly from a distance is the entire job, because this is what a storm on the horizon looks
 * like.
 *
 * @param position centre of the lit region
 * @param radius   how far the light spreads, in blocks
 * @param energy   peak output
 * @param warmth   0 for the blue-white of an ordinary flash, 1 for the violet of a positive one
 * @param seed     drives the pulse train and the shape offsets
 * @param age      age in ticks
 */
public record CloudLightSource(Vec3d position, float radius, float energy, float warmth, long seed, int age) {
    /** Ticks a lit region lasts. Longer than the channel: hot cloud does not stop glowing at once. */
    public static final int LIFETIME_TICKS = 16;
    /** Most pulses one region flickers through. */
    private static final int MAX_PULSES = 4;

    public CloudLightSource(Vec3d position, float radius, float energy, float warmth, long seed) {
        this(position, radius, energy, warmth, seed, 0);
    }

    public CloudLightSource next() {
        return new CloudLightSource(position, radius, energy, warmth, seed, age + 1);
    }

    public boolean expired() { return age >= LIFETIME_TICKS; }

    /**
     * Output at this instant, {@code 0..energy}.
     *
     * <p>Several overlapping pulses rather than one decay: an intracloud event is a train of
     * discharges inside the same cloud, and the cloud brightening two or three times is the thing
     * that tells a player it is one event rather than three.
     */
    public float intensity(float partialTick) {
        float time = age + (float) FxMath.clamp(partialTick, 0, 1);
        if (time >= LIFETIME_TICKS) return 0;
        int pulses = 1 + (int) (StrikeSeed.unit(seed, 0x41) * MAX_PULSES);
        float output = 0;
        for (int index = 0; index < pulses; index++) {
            float at = (float) (StrikeSeed.unit(seed, 0x50 + index) * LIFETIME_TICKS * 0.55);
            if (time < at) continue;
            float power = (float) (0.45 + StrikeSeed.unit(seed, 0x60 + index) * 0.55);
            output = Math.max(output, power * (float) Math.exp(-(time - at) * 0.55));
        }
        // Fade the whole region out rather than cutting it, so nothing pops off at the end.
        float fade = (float) (1.0 - FxMath.smoothstep(LIFETIME_TICKS - 4.0, LIFETIME_TICKS, time));
        return energy * output * fade;
    }

    /** The lit region swells a little as the light diffuses through the cloud. */
    public float radius(float partialTick) {
        float time = age + (float) FxMath.clamp(partialTick, 0, 1);
        return radius * (0.72f + 0.38f * (float) FxMath.clamp(time / 6.0, 0, 1));
    }
}
