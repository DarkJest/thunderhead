package dev.tempestfx.effect;

import dev.tempestfx.api.*;
import dev.tempestfx.config.*;
import dev.tempestfx.lightning.*;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashSimulationTest {
    @Test void arrivingFlashStartsAtZeroAndRetainsOneTreeThroughoutItsPulses() {
        var effects = new EffectManager(new LightningEffectFactory(new MidpointDisplacementStrategy()));
        var sim = new FlashSimulation(effects);
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        var event = new LightningStrikeFxEvent(Vec3d.ZERO, 12345, 1, LightningEnvironment.land(0, false));
        int[] accepted = {0}, contacts = {0};
        sim.enqueue(event);
        sim.tick(Vec3d.ZERO, config, e -> contacts[0]++, e -> accepted[0]++);
        var effect = effects.lightning().getFirst();
        var tree = effect.geometry();
        assertEquals(0, effect.age(), "a new flash must not age before its first frame");
        effect.prepareFrame(.8f, 20f / 144);
        assertEquals(effect.timeline().average(0, .8f), effect.brightness(.8f, true, false), 1e-6);
        for (int i = 0; i < 30; i++) {
            for (var live : effects.lightning()) assertSame(tree, live.geometry());
            assertTrue(effects.lightning().size() <= 1);
            sim.tick(Vec3d.ZERO, config, e -> contacts[0]++, e -> accepted[0]++);
        }
        assertEquals(1, accepted[0]);
        assertEquals(effect.timeline().pulses().size(), contacts[0]);
    }

    @Test void reducedModeLivesThroughItsEntireFade() {
        var config = new TempestConfig(); config.general.profile = RealismProfile.REALISTIC;
        config.general.reducedFlashing = true;
        var event = new LightningStrikeFxEvent(Vec3d.ZERO, 3, 1, LightningEnvironment.land(0, false));
        var effect = new LightningEffectFactory(new MidpointDisplacementStrategy()).create(event, LightningLod.FULL, config);
        while (effect.alive()) {
            float before = effect.brightness(0, false, true);
            effect.tick();
            if (!effect.alive()) assertEquals(0, before, "must be dark before removal");
        }
    }
}
