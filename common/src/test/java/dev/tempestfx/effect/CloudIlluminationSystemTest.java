package dev.tempestfx.effect;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.lightning.DischargeProfiles;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudIlluminationSystemTest {
    private static final DischargeProfile INTRACLOUD = DischargeProfiles.of(DischargeType.INTRACLOUD);
    private static final DischargeProfile CLOUD_TO_CLOUD = DischargeProfiles.of(DischargeType.CLOUD_TO_CLOUD);

    private static int light(CloudIlluminationSystem system, TempestConfig config, double span,
                            DischargeProfile profile) {
        return system.illuminate(new Vec3d(0, 190, 0), new Vec3d(span, 190, 0), 0xc0ffee, 1f, profile, config);
    }

    @Test
    void aLongChannelLightsSeveralRegionsAndAShortOneJustTheOne() {
        TempestConfig config = new TempestConfig().validate();
        CloudIlluminationSystem system = new CloudIlluminationSystem();
        assertEquals(1, light(system, config, 40, INTRACLOUD));
        system.clear();
        assertTrue(light(system, config, 400, CLOUD_TO_CLOUD) > 2, "a channel across the sky lights a row");
    }

    @Test
    void theCapIsNeverExceededHoweverManyDischargesArrive() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.maxCloudLightSources = 5;
        CloudIlluminationSystem system = new CloudIlluminationSystem();
        for (int index = 0; index < 50; index++) light(system, config, 500, CLOUD_TO_CLOUD);
        assertTrue(system.activeCount() <= 5, "held " + system.activeCount() + " regions");
    }

    @Test
    void switchingItOffLightsNothing() {
        TempestConfig config = new TempestConfig().validate();
        config.sky.cloudIllumination = false;
        CloudIlluminationSystem system = new CloudIlluminationSystem();
        assertEquals(0, light(system, config, 300, CLOUD_TO_CLOUD));
        assertEquals(0, system.activeCount());
    }

    @Test
    void everyRegionExpiresOnItsOwn() {
        TempestConfig config = new TempestConfig().validate();
        CloudIlluminationSystem system = new CloudIlluminationSystem();
        light(system, config, 300, CLOUD_TO_CLOUD);
        assertTrue(system.activeCount() > 0);
        for (int tick = 0; tick < CloudLightSource.LIFETIME_TICKS + 2; tick++) system.tick();
        assertEquals(0, system.activeCount(), "lit cloud must not accumulate");
    }

    @Test
    void outputIsBoundedAndPulsesMoreThanOnce() {
        CloudLightSource source = new CloudLightSource(Vec3d.ZERO, 60, 1f, 0f, 0x1234);
        float previous = source.intensity(0f);
        int rises = 0;
        for (int tick = 0; tick < CloudLightSource.LIFETIME_TICKS; tick++) {
            CloudLightSource aged = new CloudLightSource(Vec3d.ZERO, 60, 1f, 0f, 0x1234, tick);
            float value = aged.intensity(0f);
            assertTrue(value >= 0 && value <= 1.01f, "cloud light out of range: " + value);
            if (value > previous + 1e-4) rises++;
            previous = value;
        }
        assertTrue(rises >= 1, "a lit cloud should pulse rather than decay once");
        assertEquals(0f, new CloudLightSource(Vec3d.ZERO, 60, 1f, 0f, 0x1234,
            CloudLightSource.LIFETIME_TICKS).intensity(0f));
    }

    @Test
    void anIntracloudEventLightsTheCloudHarderThanAHorizontalOne() {
        TempestConfig config = new TempestConfig().validate();
        CloudIlluminationSystem buried = new CloudIlluminationSystem();
        CloudIlluminationSystem horizontal = new CloudIlluminationSystem();
        light(buried, config, 60, INTRACLOUD);
        light(horizontal, config, 60, CLOUD_TO_CLOUD);
        assertTrue(buried.sources().getFirst().energy() > horizontal.sources().getFirst().energy());
    }
}
