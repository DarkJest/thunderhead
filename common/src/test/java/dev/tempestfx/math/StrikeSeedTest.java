package dev.tempestfx.math;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeSeedTest {
    @Test
    void sameReplicatedInputProducesTheSameSeed() {
        assertEquals(StrikeSeed.of(123.5, 64.0, -88.25, 4200L), StrikeSeed.of(123.5, 64.0, -88.25, 4200L));
    }

    @Test
    void positionAndTimeBothChangeTheSeed() {
        long base = StrikeSeed.of(10, 64, 10, 100);
        assertNotEquals(base, StrikeSeed.of(11, 64, 10, 100));
        assertNotEquals(base, StrikeSeed.of(10, 65, 10, 100));
        assertNotEquals(base, StrikeSeed.of(10, 64, 11, 100));
        assertNotEquals(base, StrikeSeed.of(10, 64, 10, 101));
    }

    @Test
    void positionsAreQuantisedSoFloatRoundTripsDoNotDesynchronise() {
        assertEquals(StrikeSeed.of(10.0, 64.0, 10.0, 5), StrikeSeed.of(10.0 + 1e-9, 64.0, 10.0 - 1e-9, 5));
    }

    @Test
    void nonFiniteCoordinatesDoNotThrow() {
        assertTrue(StrikeSeed.of(Double.NaN, Double.POSITIVE_INFINITY, 0, 1) != 0);
    }

    @Test
    void derivedStreamsAreIndependentAndUniform() {
        Set<Long> derived = new HashSet<>();
        for (int index = 0; index < 512; index++) derived.add(StrikeSeed.derive(0xfeed, index));
        assertEquals(512, derived.size(), "derived sub-seeds must not collide");

        double sum = 0;
        for (int index = 0; index < 4096; index++) {
            double value = StrikeSeed.unit(0xfeed, index);
            assertTrue(value >= 0 && value < 1, "unit sample out of range: " + value);
            sum += value;
        }
        assertEquals(0.5, sum / 4096, 0.02);
    }

    @Test
    void signedSamplesSpanBothDirections() {
        boolean negative = false;
        boolean positive = false;
        for (int index = 0; index < 64; index++) {
            double value = StrikeSeed.signed(1234, index);
            assertTrue(value >= -1 && value <= 1);
            negative |= value < 0;
            positive |= value > 0;
        }
        assertTrue(negative && positive);
    }
}
