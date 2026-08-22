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
    /** How much brighter the channel is where the return-stroke front is passing. */
    private static final double RETURN_STROKE_PEAK = 2.4;
    /** Width of that front in {@code along} units; wide enough to read, tight enough to travel. */
    private static final double FRONT_WIDTH = 0.16;
    /** How far past the cloud the front is tracked, so it fades out instead of stopping dead. */
    private static final double FRONT_TAIL = 1.35;

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

    /**
     * Fraction of the channel the leader has reached, {@code 0..1}.
     *
     * <p>A stepped leader does not slide down the channel, it jumps: a step advances, the channel
     * holds still for a moment, then the next one advances. Making that visible is a deliberate
     * dramatisation — a real stepped leader completes in tens of milliseconds, which at any frame
     * rate is one or two frames — so the leader is given a couple of ticks to work in and the steps
     * are spaced to be legible inside it. The alternative is a channel that simply appears, which is
     * what every earlier release did and what this exists to replace.
     */
    public float propagation(float timeTicks) {
        double raw = FxMath.clamp(timeTicks / profile.propagationTicks(), 0, 1);
        int steps = profile.leaderSteps();
        if (steps <= 0 || raw >= 1) return (float) raw;

        double scaled = raw * steps;
        double index = Math.floor(scaled);
        // Each step advances over the first part of its slot and then pauses for the rest of it.
        double within = FxMath.clamp((scaled - index) / EnvelopeProfile.STEP_ADVANCE_FRACTION, 0, 1);
        return (float) ((index + FxMath.smoothstep(0, 1, within)) / steps);
    }

    /**
     * Extra output on a segment as the return stroke climbs past it, {@code >= 1}.
     *
     * <p>Once the leader touches down, the channel is a conducting path and the actual current
     * flows the other way: a bright front races from the ground back up to the cloud in a fraction
     * of the time the leader took to come down. Before that front arrives a segment is just an
     * ionised trail, and after it has passed the whole channel is lit — so this is what makes a
     * close strike read as a direction rather than as a shape switching on.
     *
     * @param along position on the channel, 0 at the cloud and 1 at the ground
     */
    public float returnStrokeBoost(double along, float timeTicks) {
        if (!profile.stepped()) return 1;
        float start = profile.propagationTicks();
        float span = profile.returnStrokeTicks();
        if (timeTicks < start || timeTicks > start + span * FRONT_TAIL) return 1;

        // The front starts at the ground and climbs toward the cloud.
        double front = 1.0 - (timeTicks - start) / span;
        double offset = (along - front) / FRONT_WIDTH;
        return (float) (1.0 + RETURN_STROKE_PEAK * Math.exp(-offset * offset));
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
