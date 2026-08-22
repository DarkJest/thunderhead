package dev.tempestfx.lightning;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Noise;
import dev.tempestfx.math.StrikeSeed;

/**
 * Time behaviour of a single bolt: propagation, brightness decay, re-strikes and flicker.
 *
 * <p>Reference timeline at intensity 1 (milliseconds, matching the design target):
 * <pre>
 *   0    leader reaches the ground, peak output
 *   40   ~55% output
 *   80   first re-strike, output jumps back up
 *   150  ~25% output
 *   250  branches drop out
 *   400  channel gone
 * </pre>
 */
public final class LightningEnvelope {
    public static final float DURATION_TICKS = 8f;
    /** Ticks the leader needs to travel from cloud to ground. */
    private static final float PROPAGATION_TICKS = 1.2f;
    private static final int MAX_RESTRIKES = 3;

    private final long seed;
    private final float[] restrikeAt;
    private final float[] restrikePower;

    public LightningEnvelope(long seed) {
        this.seed = seed;
        int count = 1 + (int) (StrikeSeed.unit(seed, 0x1f1a) * MAX_RESTRIKES);
        this.restrikeAt = new float[count];
        this.restrikePower = new float[count];
        for (int index = 0; index < count; index++) {
            restrikeAt[index] = (float) (1.4 + StrikeSeed.unit(seed, 0x2b00 + index) * 4.2);
            restrikePower[index] = (float) (0.45 + StrikeSeed.unit(seed, 0x3c00 + index) * 0.5);
        }
    }

    /** Fraction of the channel the leader has reached, {@code 0..1}. */
    public float propagation(float timeTicks) {
        return (float) FxMath.clamp(timeTicks / PROPAGATION_TICKS, 0, 1);
    }

    /** Channel output at {@code timeTicks}, {@code 0..1}. */
    public float brightness(float timeTicks, boolean flicker, boolean reducedFlashing) {
        if (timeTicks < 0 || timeTicks >= DURATION_TICKS) return 0;
        if (reducedFlashing) return (float) Math.max(0, 1.0 - timeTicks / 6.0) * 0.72f;

        float output = (float) Math.exp(-timeTicks * 0.62);
        for (int index = 0; index < restrikeAt.length; index++) {
            if (timeTicks < restrikeAt[index]) continue;
            float since = timeTicks - restrikeAt[index];
            output = Math.max(output, restrikePower[index] * (float) Math.exp(-since * 2.1));
        }
        output *= (float) (1.0 - FxMath.smoothstep(DURATION_TICKS - 1.6, DURATION_TICKS, timeTicks));
        if (!flicker) return output;
        return output * (0.78f + 0.22f * (float) Noise.flicker(seed, timeTicks, 9.0));
    }

    /** Sharp ground flash at the impact point, {@code 0..1}. */
    public float impactFlash(float timeTicks) {
        if (timeTicks < 0) return 0;
        return (float) Math.exp(-timeTicks * 3.1);
    }

    /**
     * Whether a fork is currently conducting. Sampled from the segment's own deterministic mask at
     * 60 Hz, which reads as electrical instability without ever rebuilding geometry.
     */
    public boolean branchVisible(long visibilityMask, float timeTicks) {
        int bucket = (int) Math.floor(Math.max(0, timeTicks) * 3.0) & 63;
        return ((visibilityMask >>> bucket) & 1L) != 0L || timeTicks < PROPAGATION_TICKS;
    }

    public boolean finished(float timeTicks) { return timeTicks >= DURATION_TICKS; }
}
