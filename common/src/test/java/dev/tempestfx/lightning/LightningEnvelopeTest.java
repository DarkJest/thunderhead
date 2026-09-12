package dev.tempestfx.lightning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightningEnvelopeTest {
    @Test
    void channelLightsUpFromTheCloudDownAndFinishesWithinTheDuration() {
        LightningEnvelope envelope = new LightningEnvelope(0xfeed);
        assertEquals(0f, envelope.propagation(0f), 1e-6);
        assertTrue(envelope.propagation(0.6f) > 0 && envelope.propagation(0.6f) < 1);
        assertEquals(1f, envelope.propagation(2f), 1e-6);
        assertTrue(envelope.finished(LightningEnvelope.DURATION_TICKS));
    }

    @Test
    void outputIsBoundedAndReachesZeroAtTheEnd() {
        LightningEnvelope envelope = new LightningEnvelope(0x1234);
        for (float time = 0; time < LightningEnvelope.DURATION_TICKS; time += 0.05f) {
            float value = envelope.brightness(time, true, false);
            assertTrue(value >= 0f && value <= 1.2f, "brightness out of range at " + time + ": " + value);
        }
        assertEquals(0f, envelope.brightness(LightningEnvelope.DURATION_TICKS, true, false), 1e-6);
        assertEquals(0f, envelope.brightness(-1f, true, false), 1e-6);
    }

    @Test
    void sameSeedGivesIdenticalOutputAtTheSameTime() {
        LightningEnvelope first = new LightningEnvelope(0xabcdef);
        LightningEnvelope second = new LightningEnvelope(0xabcdef);
        for (float time = 0; time < LightningEnvelope.DURATION_TICKS; time += 0.25f) {
            assertEquals(first.brightness(time, true, false), second.brightness(time, true, false), 1e-9);
        }
    }

    @Test
    void reducedFlashingRemovesFlickerAndDecaysMonotonically() {
        LightningEnvelope envelope = new LightningEnvelope(0x5150);
        float previous = Float.MAX_VALUE;
        for (float time = 0; time < LightningEnvelope.DURATION_TICKS; time += 0.1f) {
            float value = envelope.brightness(time, true, true);
            assertTrue(value <= previous + 1e-6, "reduced flashing rose again at " + time);
            assertTrue(value <= 0.72f);
            previous = value;
        }
    }

    @Test
    void restrikeMakesTheChannelBrightenAgainAtLeastOnce() {
        LightningEnvelope envelope = new LightningEnvelope(0x77aa);
        boolean rose = false;
        float previous = envelope.brightness(0f, false, false);
        for (float time = 0.1f; time < LightningEnvelope.DURATION_TICKS - 2f; time += 0.1f) {
            float value = envelope.brightness(time, false, false);
            if (value > previous + 1e-4) rose = true;
            previous = value;
        }
        assertTrue(rose, "expected at least one re-strike");
    }

    @Test
    void forksStayVisibleWhileTheLeaderIsStillTravelling() {
        LightningEnvelope envelope = new LightningEnvelope(0);
        assertTrue(envelope.branchVisible(0L, 0.5f), "forks must not blink during propagation");
        assertFalse(envelope.branchVisible(0L, 5f), "an all-zero mask must be able to hide a fork");
    }
}
