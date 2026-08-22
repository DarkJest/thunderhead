package dev.tempestfx.lightning;

import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Noise;
import dev.tempestfx.math.StrikeSeed;

/**
 * Time behaviour of a single bolt: propagation, brightness decay, re-strikes and flicker.
 *
 * <p>The shape of the curve comes from an {@link EnvelopeProfile}, the values on it from the seed,
 * so two discharges of the same type differ in their details while a positive flash and an
 * intracloud pulse train stay recognisably different events.
 *
 * <p>Reference timeline for {@link EnvelopeProfile#DEFAULT} at intensity 1 (milliseconds, matching
 * the design target):
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
    /** Duration of the default negative cloud-to-ground profile, in ticks. */
    public static final float DURATION_TICKS = EnvelopeProfile.DEFAULT.durationTicks();

    private final long seed;
    private final EnvelopeProfile profile;
    private final float[] restrikeAt;
    private final float[] restrikePower;

    public LightningEnvelope(long seed) {
        this(seed, EnvelopeProfile.DEFAULT);
    }

    public LightningEnvelope(long seed, EnvelopeProfile profile) {
        this.seed = seed;
        this.profile = profile;
        int count = profile.maxRestrikes() <= 0 ? 0
            : 1 + (int) (StrikeSeed.unit(seed, 0x1f1a) * profile.maxRestrikes());
        this.restrikeAt = new float[count];
        this.restrikePower = new float[count];
        float powerSpread = profile.restrikePowerMax() - profile.restrikePowerMin();
        for (int index = 0; index < count; index++) {
            restrikeAt[index] = (float) (EnvelopeProfile.RESTRIKE_MIN_DELAY_TICKS
                + StrikeSeed.unit(seed, 0x2b00 + index) * profile.restrikeSpreadTicks());
            restrikePower[index] = (float) (profile.restrikePowerMin()
                + StrikeSeed.unit(seed, 0x3c00 + index) * powerSpread);
        }
    }

    public EnvelopeProfile profile() { return profile; }

    /** How long this channel exists, in ticks. */
    public float duration() { return profile.durationTicks(); }

    /** Fraction of the channel the leader has reached, {@code 0..1}. */
    public float propagation(float timeTicks) {
        return (float) FxMath.clamp(timeTicks / profile.propagationTicks(), 0, 1);
    }

    /** Channel output at {@code timeTicks}, {@code 0..1}. */
    public float brightness(float timeTicks, boolean flicker, boolean reducedFlashing) {
        float duration = profile.durationTicks();
        if (timeTicks < 0 || timeTicks >= duration) return 0;
        if (reducedFlashing) return (float) Math.max(0, 1.0 - timeTicks / (duration * 0.75)) * 0.72f;

        float output = (float) Math.exp(-timeTicks * profile.decay());
        for (int index = 0; index < restrikeAt.length; index++) {
            if (timeTicks < restrikeAt[index]) continue;
            float since = timeTicks - restrikeAt[index];
            output = Math.max(output, restrikePower[index] * (float) Math.exp(-since * 2.1));
        }
        float fade = profile.fadeTicks();
        output *= (float) (1.0 - FxMath.smoothstep(duration - fade, duration, timeTicks));
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
        return ((visibilityMask >>> bucket) & 1L) != 0L || timeTicks < profile.propagationTicks();
    }

    public boolean finished(float timeTicks) { return timeTicks >= profile.durationTicks(); }
}
