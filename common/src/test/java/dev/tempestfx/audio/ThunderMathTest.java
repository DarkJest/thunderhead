package dev.tempestfx.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThunderMathTest {
    @Test
    void soundDelayUsesPhysicalSpeedOfSound() {
        assertEquals(1.0, ThunderMath.delaySeconds(343), 1e-9);
        assertEquals(20, ThunderMath.delayTicks(343));
        assertEquals(10, ThunderMath.delayTicks(171.5));
        assertEquals(0, ThunderMath.delayTicks(-10));
    }

    @Test
    void spatialVolumeReproducesTheRequestedLoudnessAtAnyDistance() {
        // The engine derives its attenuation range from the volume argument, so a naive "volume 1"
        // would be silent past 16 blocks. Every one of these must round-trip.
        for (double distance : new double[] { 0, 4, 15.9, 16, 40, 120, 260, 500 }) {
            for (float target : new float[] { 0.1f, 0.35f, 0.6f, 0.9f }) {
                float volume = ThunderMath.spatialVolume(distance, target);
                assertEquals(target, ThunderMath.perceivedGain(distance, volume), 1e-3,
                    "distance " + distance + " target " + target);
            }
        }
    }

    @Test
    void distantThunderStaysAudible() {
        float gain = ThunderMath.thunderGain(300, 1f, 1f);
        assertTrue(gain > 0.15f, "distant thunder must not fall silent, was " + gain);
        assertTrue(ThunderMath.perceivedGain(300, ThunderMath.spatialVolume(300, gain)) > 0.15f);
    }

    @Test
    void loudnessFallsOffMonotonicallyWithDistance() {
        float previous = Float.MAX_VALUE;
        for (int distance = 0; distance <= 600; distance += 10) {
            float gain = ThunderMath.thunderGain(distance, 1f, 1f);
            assertTrue(gain <= previous, "loudness rose at " + distance);
            assertTrue(gain >= 0f && gain <= 0.98f);
            previous = gain;
        }
    }

    @Test
    void silentConfigurationProducesNoSound() {
        assertEquals(0f, ThunderMath.thunderGain(10, 1f, 0f));
        assertEquals(0f, ThunderMath.spatialVolume(10, 0f));
        assertEquals(0f, ThunderMath.perceivedGain(10, 0f));
    }
}
