package dev.tempestfx.storm;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.StrikeSeed;

/**
 * How electrically active the storm overhead is, and which way it is facing.
 *
 * <p>Vanilla's thunder level is a step function that snaps on and off; a storm is not. This smooths
 * it into a charge that builds over a few seconds and bleeds away over rather longer, which is what
 * lets activity ramp up before the first ground strike and keep going for a while after the last
 * one.
 *
 * <p>The bearing is the axis the storm's cells are strung along. It is derived from the replicated
 * world time rather than rolled, so it drifts slowly, is the same for everyone in the session, and
 * costs nothing to keep.
 */
public final class StormElectricState {
    /** How long the charge takes to build, expressed as a per-tick approach rate. */
    private static final float RISE_RATE = 0.02f;
    /** Slower than the rise: the sky stays busy for a while after the rain eases. */
    private static final float FALL_RATE = 0.006f;
    /** Below this the storm counts as electrically quiet and nothing is scheduled. */
    private static final float ACTIVE_THRESHOLD = 0.04f;
    /** Ticks one storm identity lasts before the front is treated as a new one. */
    private static final long STORM_PERIOD_TICKS = 6000;

    private float charge;
    private long stormSeed;

    /** Folds this tick's weather into the running charge. */
    public void update(StormSample sample) {
        float target = sample.thundering()
            ? 0.3f + 0.7f * (float) FxMath.clamp(sample.thunderLevel(), 0, 1)
            // Rain without thunder still carries some charge, but not much.
            : 0.12f * (float) FxMath.clamp(sample.rainLevel(), 0, 1);
        float rate = target > charge ? RISE_RATE : FALL_RATE;
        charge += (target - charge) * rate;
        if (charge < 1.0e-4f) charge = 0;
        stormSeed = StrikeSeed.derive(0x57081, Math.floorDiv(sample.gameTime(), STORM_PERIOD_TICKS));
    }

    /** Electrical activity, {@code 0..1}. Drives how often anything happens at all. */
    public float activity() { return charge; }

    public boolean active() { return charge > ACTIVE_THRESHOLD; }

    /** Identity of the front currently overhead; changes only every few minutes. */
    public long stormSeed() { return stormSeed; }

    /** The compass bearing the storm's cells are strung along, in radians. */
    public double bearing() { return StrikeSeed.unit(stormSeed, 0x11) * Math.PI * 2; }

    public void clear() { charge = 0; stormSeed = 0; }
}
