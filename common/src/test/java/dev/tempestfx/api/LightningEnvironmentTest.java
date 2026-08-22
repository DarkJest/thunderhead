package dev.tempestfx.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightningEnvironmentTest {
    @Test
    void aSurfaceWithoutAMapColourNeverTintsParticlesBlack() {
        // MapColor.NONE packs to zero, and air and glass both report it. Debris is drawn untextured
        // and takes this colour directly, so zero here is a screenful of black squares.
        LightningEnvironment colourless = new LightningEnvironment(
            LightningEnvironment.Type.LAND, 0, false, 0f, 64, false, 1f);

        assertEquals(LightningEnvironment.NEUTRAL_GROUND, colourless.groundColor());
        assertTrue((colourless.groundColor() & 0xFF) > 0x40, "the stand-in should read as dust");
        assertEquals(LightningEnvironment.NEUTRAL_GROUND, LightningEnvironment.land(0, false).groundColor(),
            "the convenience factory is guarded too");
    }

    @Test
    void aRealMapColourIsPassedThroughUntouched() {
        for (int color : new int[] { 0x7FB238, 0x707070, 0xFFFFFF, 0x000001 }) {
            assertEquals(color, LightningEnvironment.land(color, false).groundColor());
        }
    }

    @Test
    void unlitParticlesStayVisibleAtBothEndsOfTheDay() {
        // The floor is what keeps night-time debris visible rather than invisible.
        assertTrue(LightningEnvironment.land(0x707070, false).litScale() > 0.9f);
        LightningEnvironment midnight = new LightningEnvironment(
            LightningEnvironment.Type.LAND, 0x707070, false, 0f, 64, false, 0f);
        assertTrue(midnight.litScale() >= 0.42f, "debris must not vanish at night");
    }
}
