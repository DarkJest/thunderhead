package dev.tempestfx.effect;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.sky.TransientLuminousEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransientLuminousSystemTest {
    private static final Vec3d LISTENER = new Vec3d(0, 70, 0);
    private static final double CLOUD_BASE = 192;

    /** Distant enough to be looked at rather than stood under. */
    private static Vec3d distant() { return new Vec3d(300, 70, 0); }

    private static int spritesOver(TempestConfig config, DischargeType type, int attempts) {
        int raised = 0;
        for (long seed = 0; seed < attempts; seed++) {
            TransientLuminousSystem system = new TransientLuminousSystem();
            if (system.onPowerfulDischarge(type, distant(), LISTENER, CLOUD_BASE, seed, 1f, config) != null) {
                raised++;
            }
        }
        return raised;
    }

    @Test
    void onlyThePowerfulArchetypesRaiseASprite() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.spriteChance = 1f;
        for (DischargeType type : DischargeType.values()) {
            TransientLuminousSystem system = new TransientLuminousSystem();
            ActiveLuminousEvent event =
                system.onPowerfulDischarge(type, distant(), LISTENER, CLOUD_BASE, 1, 1f, config);
            boolean expected = type == DischargeType.POSITIVE_CLOUD_TO_GROUND
                || type == DischargeType.MEGAFLASH;
            assertEquals(expected, event != null, type + " raised a sprite: " + (event != null));
        }
    }

    @Test
    void aDischargeTheePlayerIsStandingUnderRaisesNothingVisible() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.spriteChance = 1f;
        TransientLuminousSystem system = new TransientLuminousSystem();
        // Overhead: the cloud deck is in the way, so there would be nothing to see.
        assertNull(system.onPowerfulDischarge(DischargeType.POSITIVE_CLOUD_TO_GROUND,
            new Vec3d(20, 70, 0), LISTENER, CLOUD_BASE, 1, 1f, config));
    }

    @Test
    void aSpriteSitsFarAboveTheCloudLayer() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.spriteChance = 1f;
        TransientLuminousSystem system = new TransientLuminousSystem();
        ActiveLuminousEvent event = system.onPowerfulDischarge(DischargeType.POSITIVE_CLOUD_TO_GROUND,
            distant(), LISTENER, CLOUD_BASE, 1, 1f, config);
        assertNotNull(event);
        assertTrue(event.anchor().y() > CLOUD_BASE + 150,
            "a sprite at " + event.anchor().y() + " is inside the storm, not above it");
    }

    @Test
    void aMegaflashIsFarMoreLikelyToRaiseOneThanASuperbolt() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.spriteChance = 0.1f;
        int fromSuperbolts = spritesOver(config, DischargeType.POSITIVE_CLOUD_TO_GROUND, 2000);
        int fromMegaflashes = spritesOver(config, DischargeType.MEGAFLASH, 2000);
        assertTrue(fromMegaflashes > fromSuperbolts * 2,
            fromMegaflashes + " against " + fromSuperbolts);
    }

    @Test
    void theConfiguredRateIsRoughlyWhatComesOut() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.spriteChance = 0.1f;
        double rate = spritesOver(config, DischargeType.POSITIVE_CLOUD_TO_GROUND, 4000) / 4000.0;
        assertTrue(rate > 0.07 && rate < 0.13, "sprite rate was " + rate);
    }

    @Test
    void switchingThemOffRaisesNothing() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.spriteChance = 1f;
        config.sky.blueJetChance = 1f;
        config.sky.redSprites = false;
        config.sky.blueJets = false;
        TransientLuminousSystem system = new TransientLuminousSystem();
        assertNull(system.onPowerfulDischarge(DischargeType.MEGAFLASH, distant(), LISTENER,
            CLOUD_BASE, 1, 1f, config));
        assertNull(system.onCloudTopActivity(new Vec3d(200, CLOUD_BASE, 0), 1, 1f, config));
    }

    @Test
    void reducedFlashingRemovesThemEvenIfTheyAreConfiguredOn() {
        TempestConfig config = new TempestConfig();
        config.sky.spriteChance = 1f;
        config.sky.blueJetChance = 1f;
        config.general.reducedFlashing = true;
        config.validate();
        TransientLuminousSystem system = new TransientLuminousSystem();
        assertNull(system.onPowerfulDischarge(DischargeType.MEGAFLASH, distant(), LISTENER,
            CLOUD_BASE, 1, 1f, config));
        assertNull(system.onCloudTopActivity(new Vec3d(200, CLOUD_BASE, 0), 1, 1f, config));
    }

    @Test
    void theCapIsHeldAndEventsExpire() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.maxLuminousEvents = 2;
        TransientLuminousSystem system = new TransientLuminousSystem();
        for (int index = 0; index < 10; index++) {
            system.trigger(TransientLuminousEvent.RED_SPRITE, new Vec3d(0, 450, 0), index, config);
        }
        assertEquals(2, system.activeCount());
        for (int tick = 0; tick < 40; tick++) system.tick();
        assertEquals(0, system.activeCount(), "sprites must not linger");
    }

    @Test
    void aJetIsSeenToClimbWhileASpriteIsAlreadyThere() {
        TempestConfig config = new TempestConfig().validate();
        TransientLuminousSystem system = new TransientLuminousSystem();
        ActiveLuminousEvent sprite =
            system.trigger(TransientLuminousEvent.RED_SPRITE, new Vec3d(0, 450, 0), 1, config);
        ActiveLuminousEvent jet =
            system.trigger(TransientLuminousEvent.BLUE_JET, new Vec3d(0, 210, 0), 1, config);
        assertNotNull(sprite);
        assertNotNull(jet);
        // One tick in: the sprite is essentially complete, the jet has barely left the cloud.
        assertTrue(sprite.reveal(1f) > 0.9f, "a sprite appears, it does not grow: " + sprite.reveal(1f));
        assertTrue(jet.reveal(1f) < 0.3f, "a jet must be seen to climb: " + jet.reveal(1f));
    }

    @Test
    void outputIsBoundedOverTheWholeLife() {
        TempestConfig config = new TempestConfig().validate();
        TransientLuminousSystem system = new TransientLuminousSystem();
        ActiveLuminousEvent event =
            system.trigger(TransientLuminousEvent.RED_SPRITE, new Vec3d(0, 450, 0), 5, config);
        assertNotNull(event);
        for (int tick = 0; tick < 20; tick++) {
            float brightness = event.brightness(0.5f, false);
            assertTrue(brightness >= 0 && brightness <= 2f, "brightness out of range: " + brightness);
            event.tick();
        }
        assertTrue(!event.alive());
    }
}
