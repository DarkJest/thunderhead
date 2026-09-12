package dev.tempestfx.render;

import dev.tempestfx.api.*;
import dev.tempestfx.config.*;
import dev.tempestfx.effect.*;
import dev.tempestfx.lightning.*;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.render.composite.SceneLightField;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneLightFieldTest {
    @Test void lightSamplesAreBoundedAndUseTheCurrentChannelExposure() {
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        var factory = new LightningEffectFactory(new MidpointDisplacementStrategy());
        var effects = new ArrayList<ActiveLightningEffect>();
        for (int i = 0; i < 12; i++) effects.add(factory.create(new LightningStrikeFxEvent(
            new Vec3d(i * 2, 64, -40), i, 1, LightningEnvironment.land(0, false)), LightningLod.FULL, config));
        var projection = new Matrix4f().perspective((float) Math.toRadians(70), 1.5f, .05f, 1024);
        var field = SceneLightField.from(effects, new Matrix4f().translation(0, -70, 0), projection, .6f, config);
        assertEquals(4, field.lights().size());
        assertEquals(config.lighting.illuminationRadius, field.radius());
        for (var light : field.lights()) {
            assertTrue(Float.isFinite(light.x()) && Float.isFinite(light.y()) && Float.isFinite(light.z()));
            assertTrue(light.power() > 0);
        }
        var point = new Vector4f(12, 4, -80, 1);
        var clip = field.projection().transform(new Vector4f(point));
        clip.div(clip.w);
        var restored = field.inverseProjection().transform(clip); restored.div(restored.w);
        assertEquals(point.x, restored.x, .002);
        assertEquals(point.z, restored.z, .02);
        config.lighting.illuminationRadius = 60;
        assertEquals(60, SceneLightField.from(effects, new Matrix4f(), projection, .6f, config).radius());
        config.lighting.illuminationRadius = 0;
        assertFalse(SceneLightField.from(effects, new Matrix4f(), projection, .6f, config).active());
        config.lighting.dynamicLighting = false;
        assertFalse(SceneLightField.from(effects, new Matrix4f(), projection, .6f, config).active());
    }

    @Test void failedIsolationAndDisabledProgramsRetainFallback() {
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        assertTrue(SurfaceLightingPolicy.useSurfacePath(config, true, false));
        assertFalse(SurfaceLightingPolicy.useSurfacePath(config, false, false));
        config.compatibility.customShaders = false;
        assertFalse(SurfaceLightingPolicy.useSurfacePath(config, true, false));
    }

    @Test void firstUnexpectedFailureRetainsSkyFlashEvenWhenPackCannotDrawGlow() {
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        var fallback = new WorldFlashSystem();
        fallback.onStrike(new LightningStrikeFxEvent(Vec3d.ZERO, 1, 1, LightningEnvironment.land(0, false)), Vec3d.ZERO, config);
        assertEquals(0, SurfaceLightingPolicy.skyFlashTicks(fallback.flashTicks(), config, true));
        assertFalse(ShaderPackProfile.of(true).drawsWideGlow());
        assertTrue(SurfaceLightingPolicy.skyFlashTicks(fallback.flashTicks(), config, false) > 0);
        fallback.tick();
        assertTrue(SurfaceLightingPolicy.skyFlashTicks(fallback.flashTicks(), config, false) > 0);
    }
}
