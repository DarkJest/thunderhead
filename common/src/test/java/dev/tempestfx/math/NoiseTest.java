package dev.tempestfx.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseTest {
    @Test
    void ringNoiseIsExactlyPeriodicSoRingsHaveNoSeam() {
        for (long seed : new long[] { 1, 0xfeed, -991 }) {
            assertEquals(Noise.ring(seed, 0, 5), Noise.ring(seed, Math.PI * 2, 5), 1e-12,
                "ring noise must close on itself");
            assertEquals(Noise.ring(seed, 0.37, 5), Noise.ring(seed, 0.37 + Math.PI * 2, 5), 1e-12);
        }
    }

    @Test
    void ringNoiseStaysBounded() {
        for (int step = 0; step < 720; step++) {
            double value = Noise.ring(0x1234, Math.toRadians(step), 5);
            assertTrue(value >= -1.001 && value <= 1.001, "ring noise out of range: " + value);
        }
    }

    @Test
    void valueNoiseIsContinuousAcrossCellBoundaries() {
        double left = Noise.value(9, 3.0 - 1e-7);
        double right = Noise.value(9, 3.0 + 1e-7);
        assertEquals(left, right, 1e-5, "value noise must not jump at integer coordinates");
    }

    @Test
    void flickerIsBoundedAndDeterministic() {
        for (double time = 0; time < 8; time += 0.05) {
            double value = Noise.flicker(0x51, time, 9);
            assertTrue(value >= -0.001 && value <= 1.001, "flicker out of range: " + value);
            assertEquals(value, Noise.flicker(0x51, time, 9), 1e-12);
        }
    }
}
