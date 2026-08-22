package dev.tempestfx.effect;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.storm.AmbientDischarge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyDischargeSystemTest {
    private static AmbientDischarge discharge(DischargeType type, double span, long seed) {
        return new AmbientDischarge(type, new Vec3d(0, 190, 0), new Vec3d(span, 190, 0), 1f, seed);
    }

    @Test
    void everyAerialArchetypeProducesAChannel() {
        TempestConfig config = new TempestConfig().validate();
        for (DischargeType type : DischargeType.values()) {
            if (!type.aerial()) continue;
            SkyDischargeSystem system = new SkyDischargeSystem();
            ActiveLightningEffect effect = system.onDischarge(discharge(type, 260, 0x77), config);
            assertNotNull(effect, type + " produced nothing");
            assertTrue(effect.segments().size() > 4, type + " produced " + effect.segments().size() + " segments");
            assertEquals(type, effect.profile().type());
            assertEquals(type, effect.event().dischargeType());
        }
    }

    @Test
    void aMegaflashIsAllowedMoreGeometryThanAnIntracloudPulse() {
        TempestConfig config = new TempestConfig().validate();
        SkyDischargeSystem mega = new SkyDischargeSystem();
        SkyDischargeSystem buried = new SkyDischargeSystem();
        int megaSegments = mega.onDischarge(discharge(DischargeType.MEGAFLASH, 800, 0x1), config)
            .segments().size();
        int buriedSegments = buried.onDischarge(discharge(DischargeType.INTRACLOUD, 50, 0x1), config)
            .segments().size();
        assertTrue(megaSegments > buriedSegments, megaSegments + " vs " + buriedSegments);
        assertTrue(megaSegments <= 1900 + 64, "megaflash budget overrun: " + megaSegments);
    }

    @Test
    void theConcurrentCapIsHeld() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.maxAmbientDischarges = 3;
        SkyDischargeSystem system = new SkyDischargeSystem();
        for (int index = 0; index < 20; index++) {
            system.onDischarge(discharge(DischargeType.CLOUD_TO_CLOUD, 200, index), config);
        }
        assertEquals(3, system.activeCount());
    }

    @Test
    void switchingSkyActivityOffProducesNothing() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.skyActivity = false;
        SkyDischargeSystem system = new SkyDischargeSystem();
        assertNull(system.onDischarge(discharge(DischargeType.CLOUD_TO_CLOUD, 200, 1), config));
    }

    @Test
    void aerialChannelsExpireAndAreForgotten() {
        TempestConfig config = new TempestConfig().validate();
        SkyDischargeSystem system = new SkyDischargeSystem();
        ActiveLightningEffect effect = system.onDischarge(discharge(DischargeType.CLOUD_TO_CLOUD, 200, 5), config);
        assertNotNull(effect);
        for (int tick = 0; tick < 64; tick++) system.tick();
        assertEquals(0, system.activeCount());
    }

    @Test
    void anAerialDischargeCarriesNoImpactForAnythingDownstreamToActOn() {
        TempestConfig config = new TempestConfig().validate();
        SkyDischargeSystem system = new SkyDischargeSystem();
        ActiveLightningEffect effect = system.onDischarge(discharge(DischargeType.INTRACLOUD, 50, 9), config);
        assertNotNull(effect);
        assertTrue(!effect.event().target().present(), "an aerial discharge hits nothing");
        assertTrue(!effect.event().dischargeType().reachesGround());
    }
}
