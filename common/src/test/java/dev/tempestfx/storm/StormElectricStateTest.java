package dev.tempestfx.storm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormElectricStateTest {
    private static StormSample storming(long time) {
        return new StormSample(true, 1f, 1f, time, 192, 256);
    }

    @Test
    void chargeBuildsOverSecondsRatherThanSnappingOn() {
        StormElectricState state = new StormElectricState();
        state.update(storming(0));
        assertFalse(state.active(), "one tick of rain is not a charged storm");

        for (int tick = 1; tick < 400; tick++) state.update(storming(tick));
        assertTrue(state.active());
        assertTrue(state.activity() > 0.8f, "a sustained storm should saturate: " + state.activity());
    }

    @Test
    void chargeBleedsAwayMoreSlowlyThanItBuilds() {
        StormElectricState state = new StormElectricState();
        for (int tick = 0; tick < 400; tick++) state.update(storming(tick));
        float charged = state.activity();

        for (int tick = 400; tick < 500; tick++) state.update(StormSample.calm(tick, 192, 256));
        float after = state.activity();
        assertTrue(after < charged, "the storm must decay");
        assertTrue(after > charged * 0.4f, "the sky should stay busy for a while after the rain eases");
    }

    @Test
    void activityIsAlwaysBounded() {
        StormElectricState state = new StormElectricState();
        for (int tick = 0; tick < 5000; tick++) {
            state.update(storming(tick));
            assertTrue(state.activity() >= 0 && state.activity() <= 1.001f);
        }
    }

    @Test
    void theBearingIsStableWithinOneFrontAndSharedBetweenClients() {
        StormElectricState first = new StormElectricState();
        StormElectricState second = new StormElectricState();
        first.update(storming(1000));
        second.update(storming(1400));
        assertEquals(first.bearing(), second.bearing(), 1e-12,
            "two clients in the same storm must agree about which way it faces");
        assertTrue(first.bearing() >= 0 && first.bearing() <= Math.PI * 2);
    }

    @Test
    void aNewFrontEventuallyGetsANewBearing() {
        StormElectricState state = new StormElectricState();
        state.update(storming(0));
        double first = state.bearing();
        state.update(storming(60000));
        assertTrue(Math.abs(state.bearing() - first) > 1e-9, "the front should not last for ever");
    }

    @Test
    void clearingReturnsItToAQuietSky() {
        StormElectricState state = new StormElectricState();
        for (int tick = 0; tick < 400; tick++) state.update(storming(tick));
        state.clear();
        assertFalse(state.active());
        assertEquals(0f, state.activity());
    }
}
