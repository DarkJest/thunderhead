package dev.tempestfx.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FxMathTest {
    @Test void intensityFalloffIsSmoothAndBounded() {
        assertEquals(1, FxMath.distanceFalloff(0, 15, 100));
        assertEquals(1, FxMath.distanceFalloff(15, 15, 100));
        assertEquals(0, FxMath.distanceFalloff(100, 15, 100));
        double previous=1;
        for(int distance=16;distance<=100;distance++) {
            double current=FxMath.distanceFalloff(distance,15,100);
            assertTrue(current>=0 && current<=1); assertTrue(current<=previous); previous=current;
        }
    }
}
