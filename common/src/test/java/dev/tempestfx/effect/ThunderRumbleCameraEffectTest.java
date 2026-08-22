package dev.tempestfx.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThunderRumbleCameraEffectTest {
    @Test
    void idleByDefaultAndAfterEverythingHasDecayed() {
        ThunderRumbleCameraEffect rumble = new ThunderRumbleCameraEffect();
        assertFalse(rumble.active());
        assertEquals(0f, rumble.pitchOffset(0.5f), 1e-6);

        rumble.onTransient(1f, 0x1234);
        assertTrue(rumble.active());
        for (int tick = 0; tick < 300; tick++) rumble.tick();
        assertFalse(rumble.active(), "shakes must expire on their own");
        assertEquals(0f, rumble.yawOffset(0f), 1e-6);
    }

    @Test
    void aBoomThumpsAndThenSettlesInsteadOfRingingForever() {
        ThunderRumbleCameraEffect rumble = new ThunderRumbleCameraEffect();
        rumble.onTransient(1f, 0xfeed);

        // First second against the third: an exponential with a one-second constant is still
        // clearly moving at 0.6 s, so the windows have to be genuinely apart to mean anything.
        float firstSecond = peakOver(rumble, 20);
        peakOver(rumble, 20);
        float thirdSecond = peakOver(rumble, 20);
        assertTrue(firstSecond > 0.05f, "a full-strength boom should be felt, peak was " + firstSecond);
        assertTrue(thirdSecond < firstSecond * 0.35f,
            "the shake never settled: " + firstSecond + " then " + thirdSecond);
    }

    @Test
    void quietPulsesBarelyRegisterAndTinyOnesAreIgnored() {
        ThunderRumbleCameraEffect loud = new ThunderRumbleCameraEffect();
        ThunderRumbleCameraEffect quiet = new ThunderRumbleCameraEffect();
        loud.onTransient(1f, 0x11);
        quiet.onTransient(0.15f, 0x11);
        assertTrue(peakOver(loud, 30) > peakOver(quiet, 30) * 3, "quiet grumbles should barely move the camera");

        ThunderRumbleCameraEffect negligible = new ThunderRumbleCameraEffect();
        negligible.onTransient(0.01f, 0x11);
        assertFalse(negligible.active(), "a negligible transient must not queue a shake");
    }

    @Test
    void separateBoomsStackRatherThanRestartingOneSine() {
        // The whole point: each roll pushes its own oscillator, so overlapping rolls add up instead
        // of the newest one replacing a single running wave. Energy rather than peak, because
        // independent phases can momentarily cancel exactly as real pressure waves do.
        ThunderRumbleCameraEffect single = new ThunderRumbleCameraEffect();
        ThunderRumbleCameraEffect stacked = new ThunderRumbleCameraEffect();
        single.onTransient(0.6f, 0x21);
        for (int index = 0; index < 6; index++) stacked.onTransient(0.6f, 0x21 + index);

        float one = energyOver(single, 25);
        float many = energyOver(stacked, 25);
        // Independent phases add in quadrature rather than linearly, so the bar is well under six.
        assertTrue(many > one * 1.5f, "stacked booms carried " + many + " against " + one);
    }

    @Test
    void offsetsStayWithinAReasonableDeflection() {
        ThunderRumbleCameraEffect rumble = new ThunderRumbleCameraEffect();
        for (int index = 0; index < 20; index++) rumble.onTransient(1f, index);
        for (int tick = 0; tick < 200; tick++) {
            for (float partial = 0; partial < 1f; partial += 0.25f) {
                assertTrue(Math.abs(rumble.pitchOffset(partial)) < 6f, "pitch ran away");
                assertTrue(Math.abs(rumble.yawOffset(partial)) < 6f, "yaw ran away");
            }
            rumble.tick();
        }
    }

    @Test
    void clearingStopsEverythingImmediately() {
        ThunderRumbleCameraEffect rumble = new ThunderRumbleCameraEffect();
        rumble.onTransient(1f, 5);
        rumble.clear();
        assertFalse(rumble.active());
        assertEquals(0f, rumble.pitchOffset(0.5f), 1e-6);
    }

    /** Mean absolute deflection: robust to the phase cancellation independent oscillators show. */
    private static float energyOver(ThunderRumbleCameraEffect rumble, int ticks) {
        float total = 0;
        for (int tick = 0; tick < ticks; tick++) {
            for (float partial = 0; partial < 1f; partial += 0.25f) {
                total += Math.abs(rumble.pitchOffset(partial)) + Math.abs(rumble.yawOffset(partial));
            }
            rumble.tick();
        }
        return total / ticks;
    }

    private static float peakOver(ThunderRumbleCameraEffect rumble, int ticks) {
        float peak = 0;
        for (int tick = 0; tick < ticks; tick++) {
            for (float partial = 0; partial < 1f; partial += 0.25f) {
                peak = Math.max(peak, Math.abs(rumble.pitchOffset(partial)));
                peak = Math.max(peak, Math.abs(rumble.yawOffset(partial)));
            }
            rumble.tick();
        }
        return peak;
    }
}
