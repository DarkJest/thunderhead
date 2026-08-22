package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DischargeSelectorTest {
    @Test
    void aZeroChanceNeverProducesASuperbolt() {
        for (long seed = 0; seed < 5000; seed++) {
            assertEquals(DischargeType.NEGATIVE_CLOUD_TO_GROUND,
                DischargeSelector.forGroundStrike(seed, 0f));
        }
    }

    @Test
    void theConfiguredRateIsRoughlyWhatComesOut() {
        int positive = 0;
        int samples = 20000;
        for (long seed = 0; seed < samples; seed++) {
            if (DischargeSelector.forGroundStrike(seed, 0.04f) == DischargeType.POSITIVE_CLOUD_TO_GROUND) {
                positive++;
            }
        }
        double rate = positive / (double) samples;
        assertTrue(rate > 0.03 && rate < 0.05, "positive rate was " + rate);
    }

    @Test
    void theSameStrikeAlwaysGetsTheSamePolarity() {
        for (long seed = 1; seed < 200; seed++) {
            assertEquals(DischargeSelector.forGroundStrike(seed, 0.5f),
                DischargeSelector.forGroundStrike(seed, 0.5f));
        }
    }

    @Test
    void everyStrikeIsASuperboltAtFullRate() {
        for (long seed = 0; seed < 500; seed++) {
            assertEquals(DischargeType.POSITIVE_CLOUD_TO_GROUND,
                DischargeSelector.forGroundStrike(seed, 1f));
        }
    }
}
