package dev.tempestfx.lightning;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashTimelineTest {
    @Test void deterministicBoundedAndSubtick() {
        boolean fractional = false, strongerRepeat = false;
        for (int seed = 0; seed < 500; seed++) {
            FlashTimeline plan = FlashTimeline.plan(seed, 4, true);
            assertEquals(plan, FlashTimeline.plan(seed, 4, true));
            assertTrue(plan.pulses().size() <= 5);
            float previousTime = -1, previousStrength = Float.MAX_VALUE;
            for (var pulse : plan.pulses()) {
                assertTrue(pulse.atTicks() > previousTime);
                fractional |= pulse.atTicks() % 1 != 0;
                strongerRepeat |= pulse.strength() > previousStrength;
                previousTime = pulse.atTicks(); previousStrength = pulse.strength();
            }
        }
        assertTrue(fractional);
        assertTrue(strongerRepeat, "repeats must not be forced to weaken monotonically");
    }

    @Test void exposureConservesEnergyAcrossFrameRates() {
        var plan = FlashTimeline.plan(12345, 4, true);
        float end = (float) Math.ceil(plan.durationTicks());
        double exact = plan.average(0, end) * end;
        for (int fps : new int[]{20, 30, 60, 144}) {
            double total = 0;
            float step = 20f / fps;
            for (float from = 0; from < end;) {
                float to = Math.min(end, from + step);
                total += plan.average(from, to) * (to - from);
                from = to;
            }
            assertEquals(exact, total, 1e-5, "energy at " + fps + " fps");
        }
    }

    @Test void noReturnsMeansOnePulseAndLeaderIsWeak() {
        for (long seed = 0; seed < 50; seed++) {
            var plan = FlashTimeline.plan(seed, 0, true);
            assertEquals(1, plan.pulses().size());
            assertTrue(plan.value(plan.leaderTicks() / 2) < .1f);
            assertEquals(0, plan.value(-1));
            assertEquals(0, plan.value(plan.durationTicks()));
        }
    }
}
