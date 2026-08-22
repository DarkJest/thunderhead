package dev.tempestfx.effect;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.StrikeTarget;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AshImprintSystemTest {
    @Test
    void onlyADirectPlayerHitLeavesAMark() {
        TempestConfig config = new TempestConfig().validate();
        AshImprintSystem system = new AshImprintSystem();

        assertNull(system.onStrike(terrainStrike(), config), "terrain strikes must not leave a mark");
        assertNull(system.onStrike(mobStrike(), config), "the imprint is a player effect");
        assertNotNull(system.onStrike(playerStrike(), config));
        assertEquals(1, system.activeCount());
    }

    @Test
    void markIsPlacedOnTheStruckPlayerAndSizedToThem() {
        TempestConfig config = new TempestConfig().validate();
        AshImprintSystem system = new AshImprintSystem();
        AshImprint imprint = system.onStrike(playerStrike(), config);

        assertNotNull(imprint);
        assertEquals(12.0, imprint.position().x(), 1e-9);
        assertEquals(-4.0, imprint.position().z(), 1e-9);
        assertEquals(70.0, imprint.position().y(), 1e-9, "the mark sits on the sampled surface");
        assertTrue(imprint.radius() >= 0.75f);
    }

    @Test
    void markCoolsFromEmberToAshAndExpiresOnSchedule() {
        TempestConfig config = new TempestConfig();
        config.impact.ashImprintSeconds = 2f;
        config.validate();
        AshImprintSystem system = new AshImprintSystem();
        AshImprint imprint = system.onStrike(playerStrike(), config);
        assertNotNull(imprint);

        assertTrue(imprint.emberGlow(0) > 0.5f, "a fresh mark is still glowing");
        for (int tick = 0; tick < 30; tick++) system.tick();
        assertTrue(imprint.emberGlow(0) < 0.4f, "the glow must cool down");

        for (int tick = 0; tick < 20 * 3; tick++) system.tick();
        assertEquals(0, system.activeCount(), "the mark must expire on its own");
    }

    @Test
    void disablingTheFeatureLeavesNothingBehind() {
        TempestConfig config = new TempestConfig();
        config.impact.ashImprint = false;
        config.validate();
        AshImprintSystem system = new AshImprintSystem();
        assertNull(system.onStrike(playerStrike(), config));
        assertEquals(0, system.activeCount());
    }

    @Test
    void marksAreBoundedSoARepeatedlyStruckPlayerCannotLeak() {
        TempestConfig config = new TempestConfig().validate();
        AshImprintSystem system = new AshImprintSystem();
        for (int index = 0; index < 200; index++) system.onStrike(playerStrike(), config);
        assertTrue(system.activeCount() <= 12, "imprint list grew to " + system.activeCount());
    }

    private static LightningEnvironment environment() {
        return new LightningEnvironment(LightningEnvironment.Type.LAND, 0x6d6758, true, 0.85f, 70.0, false, 1f);
    }

    private static LightningStrikeFxEvent terrainStrike() {
        return new LightningStrikeFxEvent(new Vec3d(12, 70, -4), 1, 1, environment(), StrikeTarget.none());
    }

    private static LightningStrikeFxEvent mobStrike() {
        return new LightningStrikeFxEvent(new Vec3d(12, 70, -4), 2, 1, environment(),
            new StrikeTarget(3, false, new Vec3d(12, 70, -4), 0.9f, 1.4f));
    }

    private static LightningStrikeFxEvent playerStrike() {
        return new LightningStrikeFxEvent(new Vec3d(12, 70, -4), 3, 1, environment(),
            new StrikeTarget(4, true, new Vec3d(12, 70.2, -4), 0.6f, 1.8f));
    }
}
