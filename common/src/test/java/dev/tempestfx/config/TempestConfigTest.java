package dev.tempestfx.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempestConfigTest {
    @Test
    void validationClampsUnsafeValues() {
        TempestConfig config = new TempestConfig();
        config.lightning.geometryQuality = 99;
        config.performance.maxParticles = 1;
        config.camera.flashStrength = 8;
        config.impact.entityDischargeRadius = 900;
        config.impact.ashImprintSeconds = 0.01f;
        config.lighting.worldFlashTicks = 99;
        config.lightning.returnStrokes = 99;
        config.audio.maxThunderDistance = 1;
        config.validate();

        assertEquals(9, config.lightning.geometryQuality);
        assertEquals(128, config.performance.maxParticles);
        assertEquals(1f, config.camera.flashStrength);
        assertEquals(48f, config.impact.entityDischargeRadius);
        assertEquals(1f, config.impact.ashImprintSeconds);
        assertEquals(12, config.lighting.worldFlashTicks);
        assertEquals(4, config.lightning.returnStrokes);
        assertEquals(16f, config.audio.maxThunderDistance);
    }

    @Test
    void reducedFlashingOverridesRapidChanges() {
        TempestConfig config = new TempestConfig();
        config.general.reducedFlashing = true;
        config.validate();

        assertFalse(config.lightning.flicker);
        assertTrue(config.camera.flashStrength <= 0.25f);
        assertTrue(config.camera.impulseStrength <= 0.2f);
        assertEquals(0, config.lighting.worldFlashTicks, "reduced flashing must not extend the sky flash");
        assertEquals(0, config.lightning.returnStrokes, "a multi-stroke flash is a rapid brightness change");
    }

    @Test
    void missingSectionsAreRebuiltInsteadOfCrashing() {
        // Models a hand-edited or partially written config file deserialised by Gson.
        TempestConfig config = new TempestConfig();
        config.impact = null;
        config.performance = null;
        config.compatibility = null;
        config.validate();

        assertEquals(QualityPreset.HIGH, config.performance.qualityPreset);
        assertEquals(BloomMode.AUTO, config.compatibility.bloomMode);
        assertTrue(config.impact.sparks);
    }

    @Test
    void validationIsIdempotent() {
        TempestConfig config = new TempestConfig();
        config.general.reducedFlashing = true;
        config.validate();
        float flash = config.camera.flashStrength;
        config.validate();
        assertEquals(flash, config.camera.flashStrength);
    }

    @Test
    void particleBudgetThinsOutWithDistanceAndStopsBeyondRange() {
        TempestConfig config = new TempestConfig().validate();
        int near = config.particleBudget(4);
        int mid = config.particleBudget(64);
        int far = config.particleBudget(200);

        assertTrue(near > mid && mid > far && far > 0);
        assertEquals(0, config.particleBudget(400));

        config.performance.lod = false;
        assertEquals(near, config.particleBudget(400), "LOD off keeps full detail everywhere");
    }
}
