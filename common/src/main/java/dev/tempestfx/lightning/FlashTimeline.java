package dev.tempestfx.lightning;

import dev.tempestfx.math.StrikeSeed;
import java.util.ArrayList;
import java.util.List;

/**
 * The single immutable pulse plan for a flash. One tick is 50 ms; sub-tick times are retained.
 * Parameters are bounded game approximations, not a measured universal distribution.
 */
public record FlashTimeline(long seed, float leaderTicks, float decayTicks, List<Pulse> pulses) {
    public static final int GENERATOR_VERSION = 1;
    public record Pulse(int index, float atTicks, float strength) {}

    public FlashTimeline {
        pulses = List.copyOf(pulses);
        if (!(leaderTicks > 0) || !(decayTicks > 0) || !Float.isFinite(leaderTicks)
            || !Float.isFinite(decayTicks) || pulses.isEmpty() || pulses.size() > 5) {
            throw new IllegalArgumentException("Invalid flash timeline");
        }
        float previous = -1;
        for (int i = 0; i < pulses.size(); i++) {
            Pulse pulse = pulses.get(i);
            if (pulse.index() != i || !Float.isFinite(pulse.atTicks()) || pulse.atTicks() <= previous
                || pulse.atTicks() < leaderTicks || !(pulse.strength() > 0) || !Float.isFinite(pulse.strength())) {
                throw new IllegalArgumentException("Invalid flash pulse");
            }
            previous = pulse.atTicks();
        }
    }

    public static FlashTimeline plan(long seed, int maximumReturns, boolean realistic) {
        float leader = realistic ? 0.4f : 1.2f;
        List<Pulse> pulses = new ArrayList<>();
        pulses.add(new Pulse(0, leader, 1f));
        int cap = Math.max(0, Math.min(4, maximumReturns));
        // A mixture of single and multiple strokes, deliberately not labelled climatology.
        int count = cap == 0 || StrikeSeed.unit(seed, 0x5e01) < .25 ? 0
            : 1 + (int) (StrikeSeed.unit(seed, 0x5e02) * cap);
        float time = leader;
        for (int index = 1; index <= count; index++) {
            time += (float) (0.6 + StrikeSeed.unit(seed, 0x5e10 + index) * 1.4);
            float strength = (float) (0.4 + StrikeSeed.unit(seed, 0x5e20 + index) * 0.7);
            pulses.add(new Pulse(index, time, strength));
        }
        return new FlashTimeline(seed, leader, realistic ? .22f : .7f, pulses);
    }

    public float durationTicks() { return pulses.getLast().atTicks() + decayTicks * 8 + 1; }

    public float propagation(float time) { return Math.max(0, Math.min(1, time / leaderTicks)); }

    public float value(float time) {
        if (time < 0 || time >= durationTicks()) return 0;
        if (time < leaderTicks) return .04f;
        double sum = 0;
        for (Pulse pulse : pulses) {
            if (time >= pulse.atTicks()) sum += pulse.strength() * Math.exp(-(time - pulse.atTicks()) / decayTicks);
        }
        return (float) sum;
    }

    /** Analytic exposure over the last frame; a short impulse cannot fall between point samples. */
    public float average(float from, float to) {
        if (!(to > from)) return value(to);
        double integral = Math.max(0, Math.min(to, leaderTicks) - Math.max(from, 0)) * .04;
        for (Pulse pulse : pulses) {
            double lo = Math.max(from, pulse.atTicks()), hi = Math.min(to, durationTicks());
            if (hi > lo) integral += pulse.strength() * decayTicks
                * (Math.exp(-(lo - pulse.atTicks()) / decayTicks) - Math.exp(-(hi - pulse.atTicks()) / decayTicks));
        }
        return (float) (integral / (to - from));
    }
}
