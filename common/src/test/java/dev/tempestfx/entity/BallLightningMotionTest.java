package dev.tempestfx.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallLightningMotionTest {
    @Test
    void outputIgnitesQuicklyHoldsThenCollapses() {
        float lifetime = 120;
        assertEquals(0f, BallLightningMotion.output(0f, lifetime), 1e-6);
        assertTrue(BallLightningMotion.output(6f, lifetime) > 0.95f, "should be at full output early");
        assertTrue(BallLightningMotion.output(60f, lifetime) > 0.95f, "should hold through the middle");
        assertTrue(BallLightningMotion.output(115f, lifetime) < 0.3f, "should be collapsing at the end");
        assertEquals(0f, BallLightningMotion.output(lifetime, lifetime), 1e-6);
        assertEquals(0f, BallLightningMotion.output(-1f, lifetime), 1e-6);
    }

    @Test
    void verticalSpringSettlesAtTheHoverHeight() {
        double surface = 64;
        double y = surface + 4;
        double velocity = 0;
        for (int tick = 0; tick < 400; tick++) {
            velocity = BallLightningMotion.stepVerticalVelocity(y, surface, velocity);
            y += velocity;
        }
        assertEquals(surface + BallLightningMotion.HOVER_HEIGHT, y, 0.02);
    }

    @Test
    void verticalSpringDoesNotOscillateForever() {
        double surface = 64;
        double y = surface;
        double velocity = 0;
        double peak = 0;
        for (int tick = 0; tick < 200; tick++) {
            velocity = BallLightningMotion.stepVerticalVelocity(y, surface, velocity);
            y += velocity;
            if (tick > 120) peak = Math.max(peak, Math.abs(velocity));
        }
        assertTrue(peak < 0.005, "still moving after ten seconds: " + peak);
    }

    @Test
    void driftIsSlowSmoothAndDeterministic() {
        double previousX = BallLightningMotion.driftX(0x51, 0);
        for (double age = 0; age < 200; age += 0.5) {
            double x = BallLightningMotion.driftX(0x51, age);
            double z = BallLightningMotion.driftZ(0x51, age);
            assertTrue(Math.abs(x) <= 0.081 && Math.abs(z) <= 0.081, "drift too fast: " + x + "," + z);
            assertTrue(Math.abs(x - previousX) < 0.02, "drift changed direction too abruptly");
            assertEquals(x, BallLightningMotion.driftX(0x51, age), 1e-12);
            previousX = x;
        }
    }

    @Test
    void radiusBreathesAroundItsNominalSize() {
        for (float age = 0; age < 120; age += 0.5f) {
            float radius = BallLightningMotion.radius(7, age, 0.5f);
            assertTrue(radius > 0.42f && radius < 0.58f, "radius out of range: " + radius);
        }
    }
}
