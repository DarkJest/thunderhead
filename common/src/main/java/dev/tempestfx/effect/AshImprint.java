package dev.tempestfx.effect;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * The scorched ash mark left where a player took a direct hit.
 */
public final class AshImprint {
    /** Ticks the crater stays visibly hot. */
    private static final float HOT_TICKS = 20;
    private static final float EMBER_TICKS = 90;

    private final Vec3d position;
    private final float radius;
    private final float rotation;
    private final long seed;
    private final int lifetime;
    private int age;

    public AshImprint(Vec3d position, float radius, long seed, int lifetime) {
        this.position = position;
        this.radius = Math.max(0.6f, radius);
        this.seed = seed;
        this.rotation = (float) (StrikeSeed.unit(seed, 0xa5) * Math.PI * 2);
        this.lifetime = Math.max(20, lifetime);
    }

    public void tick() { age++; }

    public boolean alive() { return age < lifetime; }

    public Vec3d position() { return position; }

    public float radius() { return radius; }

    public float rotation() { return rotation; }

    public long seed() { return seed; }

    public int age() { return age; }

    /** Opacity of the dark ash disc; holds, then fades out over the last third of the lifetime. */
    public float ashOpacity(float partialTick) {
        float time = age + FxMath.clamp(partialTick, 0, 1);
        float appear = (float) FxMath.clamp(time / 4.0, 0, 1);
        float fade = (float) (1.0 - FxMath.smoothstep(lifetime * 0.6, lifetime, time));
        return appear * fade;
    }

    /** Strength of the glowing rim; strong immediately, gone after a few seconds. */
    public float emberGlow(float partialTick) {
        float time = age + FxMath.clamp(partialTick, 0, 1);
        if (time >= EMBER_TICKS) return 0;
        float hot = (float) (1.0 - FxMath.smoothstep(0, HOT_TICKS, time));
        float cooling = (float) (1.0 - FxMath.smoothstep(HOT_TICKS, EMBER_TICKS, time));
        return Math.max(hot, cooling * 0.35f);
    }

    /** The mark spreads slightly as the debris settles. */
    public float spread(float partialTick) {
        float time = age + FxMath.clamp(partialTick, 0, 1);
        return 0.72f + 0.28f * (float) FxMath.clamp(time / 12.0, 0, 1);
    }
}
