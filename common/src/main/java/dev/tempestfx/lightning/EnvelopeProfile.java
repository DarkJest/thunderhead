package dev.tempestfx.lightning;

/**
 * The time behaviour of one discharge, separated from the seed that instantiates it.
 *
 * <p>A negative ground flash is a hard transient with two or three re-strikes inside half a second;
 * a positive one is a single stroke that holds; an intracloud event is a slow train of pulses; a
 * megaflash propagates for more than a second before it decays. Those are different curves, not
 * different multipliers on one curve, which is why they are described here rather than scaled at the
 * call site.
 *
 * @param durationTicks       how long the channel exists at all
 * @param propagationTicks    time the leader needs to travel the whole channel
 * @param decay               exponential decay rate of the primary stroke
 * @param maxRestrikes        upper bound on seeded re-strikes; 0 gives a single stroke
 * @param restrikeSpreadTicks window the re-strikes land in, after {@link #RESTRIKE_MIN_DELAY_TICKS}
 * @param restrikePowerMin    weakest re-strike output relative to the peak
 * @param restrikePowerMax    strongest re-strike output relative to the peak
 */
public record EnvelopeProfile(float durationTicks, float propagationTicks, float decay,
                              int maxRestrikes, float restrikeSpreadTicks,
                              float restrikePowerMin, float restrikePowerMax) {
    /** Earliest a re-strike may land, in ticks after the first stroke. */
    public static final float RESTRIKE_MIN_DELAY_TICKS = 1.4f;

    /** The negative cloud-to-ground timeline every earlier release used. */
    public static final EnvelopeProfile DEFAULT =
        new EnvelopeProfile(8f, 1.2f, 0.62f, 3, 4.2f, 0.45f, 0.95f);

    public EnvelopeProfile {
        if (!(durationTicks > 0)) throw new IllegalArgumentException("durationTicks must be positive");
        if (!(propagationTicks > 0)) throw new IllegalArgumentException("propagationTicks must be positive");
        if (!(decay > 0)) throw new IllegalArgumentException("decay must be positive");
        if (maxRestrikes < 0) throw new IllegalArgumentException("maxRestrikes must not be negative");
        if (restrikeSpreadTicks < 0) throw new IllegalArgumentException("restrikeSpreadTicks must not be negative");
        if (restrikePowerMin < 0 || restrikePowerMax < restrikePowerMin) {
            throw new IllegalArgumentException("invalid re-strike power range");
        }
    }

    /** How long the channel spends fading out, so a long event does not snap off at the end. */
    public float fadeTicks() {
        return Math.min(1.6f, durationTicks * 0.25f);
    }
}
