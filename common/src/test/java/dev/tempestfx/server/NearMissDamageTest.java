package dev.tempestfx.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearMissDamageTest {
    @Test
    void vanillasOwnBoxIsNeverDoubleCounted() {
        // Vanilla already deals its 5 points inside 3 blocks; ours must start strictly outside.
        for (double distance = 0; distance <= NearMissDamage.VANILLA_RADIUS; distance += 0.25) {
            assertEquals(0f, NearMissDamage.damageAt(distance, 9, 5f), 1e-6,
                "overlapped vanilla at " + distance);
        }
    }

    @Test
    void theExclusionIsVanillasActualBoxAndNotASphere() {
        // LightningBolt#tick hits everything in x±3, y-3..y+9, z±3. The tall half is the part a
        // sphere gets wrong: someone five blocks above the strike is outside a 3-block sphere and
        // firmly inside vanilla's box, and would take both lots of damage.
        assertTrue(NearMissDamage.insideVanillaBox(0, 5, 0), "vanilla reaches nine blocks up");
        assertTrue(NearMissDamage.insideVanillaBox(0, 8.9, 0));
        assertTrue(NearMissDamage.insideVanillaBox(2.9, 0, -2.9), "the corner of the box is still inside");
        assertFalse(NearMissDamage.insideVanillaBox(0, 9.1, 0), "above the box is ours");
        assertFalse(NearMissDamage.insideVanillaBox(0, -3.1, 0), "vanilla only reaches three blocks down");
        assertFalse(NearMissDamage.insideVanillaBox(3.1, 0, 0));
        assertFalse(NearMissDamage.insideVanillaBox(0, 0, -3.1));
    }

    @Test
    void damageFallsToZeroAtTheConfiguredRadius() {
        assertTrue(NearMissDamage.damageAt(3.1, 9, 5f) > 0);
        assertEquals(0f, NearMissDamage.damageAt(9, 9, 5f), 1e-6);
        assertEquals(0f, NearMissDamage.damageAt(20, 9, 5f), 1e-6);
    }

    @Test
    void damageDecreasesMonotonicallyAndStaysWithinBounds() {
        float previous = Float.MAX_VALUE;
        for (double distance = 3.05; distance < 9; distance += 0.05) {
            float damage = NearMissDamage.damageAt(distance, 9, 5f);
            assertTrue(damage <= previous + 1e-6, "damage rose at " + distance);
            assertTrue(damage >= 0 && damage <= 5f);
            previous = damage;
        }
    }

    @Test
    void disabledOrDegenerateSettingsProduceNothing() {
        assertEquals(0f, NearMissDamage.damageAt(5, 9, 0f), 1e-6);
        assertEquals(0f, NearMissDamage.damageAt(5, 2, 5f), 1e-6, "radius inside vanilla's box is a no-op");
    }

    @Test
    void ignitionOnlyHappensInTheInnerPartOfTheRing() {
        assertFalse(NearMissDamage.ignites(2.0, 9, 0.45f), "inside vanilla's box vanilla already ignites");
        assertTrue(NearMissDamage.ignites(4.0, 9, 0.45f));
        assertFalse(NearMissDamage.ignites(8.0, 9, 0.45f));
        assertFalse(NearMissDamage.ignites(4.0, 9, 0f), "zero fraction disables ignition");
    }

    @Test
    void configurationIsClampedIntoUsableRanges() {
        ServerConfig config = new ServerConfig();
        config.nearMiss.radius = 1f;
        config.nearMiss.maxDamage = 999f;
        config.ballLightning.chancePerStrike = 5f;
        config.ballLightning.maxRadius = 0.01f;
        config.ballLightning.maxSeconds = 0.1f;
        config.validate();

        assertTrue(config.nearMiss.radius > NearMissDamage.VANILLA_RADIUS);
        assertEquals(40f, config.nearMiss.maxDamage);
        assertEquals(1f, config.ballLightning.chancePerStrike);
        assertTrue(config.ballLightning.maxRadius >= config.ballLightning.minRadius);
        assertTrue(config.ballLightning.maxSeconds >= config.ballLightning.minSeconds);
    }
}
