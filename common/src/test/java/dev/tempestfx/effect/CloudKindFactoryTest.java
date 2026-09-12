package dev.tempestfx.effect;

import dev.tempestfx.api.*;
import dev.tempestfx.config.*;
import dev.tempestfx.lightning.*;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudKindFactoryTest {
    private LightningStrikeFxEvent event(LightningKind kind, Vec3d origin) {
        return new LightningStrikeFxEvent(new Vec3d(100, 192, 0), 12345, 1,
            LightningEnvironment.land(0, false), StrikeTarget.none(), 0,
            StrikeOptions.builder().kind(kind).origin(origin).build());
    }

    @Test void cloudOrientationSurvivesBothPresentationProfiles() {
        var factory = new LightningEffectFactory(new MidpointDisplacementStrategy());
        for (var profile : RealismProfile.values()) for (var kind : new LightningKind[]{LightningKind.INTRACLOUD, LightningKind.INTERCLOUD}) {
            var config = new TempestConfig(); config.general.profile = profile;
            var effect = factory.create(event(kind, null), LightningLod.FULL, config);
            var trunk = effect.geometry().branches().getFirst().segments();
            assertTrue(Math.abs(trunk.getFirst().start().y() - trunk.getLast().end().y()) <= 12);
        }
    }

    @Test void explicitApiCloudKindsHaveDifferentGeometryAndTiming() {
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        var factory = new LightningEffectFactory(new MidpointDisplacementStrategy());
        var origin = new Vec3d(0, 192, 0);
        var intra = factory.create(event(LightningKind.INTRACLOUD, origin), LightningLod.FULL, config);
        var inter = factory.create(event(LightningKind.INTERCLOUD, origin), LightningLod.FULL, config);
        assertNotEquals(intra.geometry(), inter.geometry());
        assertNotEquals(intra.timeline(), inter.timeline());
        var manager = new EffectManager(factory);
        config.general.profile = RealismProfile.CINEMATIC;
        manager.onContact(event(LightningKind.INTRACLOUD, origin), Vec3d.ZERO, config);
        assertTrue(manager.shockwaves().isEmpty());
    }
}
