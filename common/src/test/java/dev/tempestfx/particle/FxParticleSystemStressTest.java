package dev.tempestfx.particle;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.math.Vec3d;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxParticleSystemStressTest {
    @Test
    void twentyStrikesStayBoundedAndFullyExpire() {
        FxParticleSystem system = new FxParticleSystem(512, new ImpactParticleSpawnStrategy());
        for (int index = 0; index < 20; index++) system.emit(strike(index), 220);

        assertTrue(system.activeCount() <= 512, "cap exceeded: " + system.activeCount());
        for (int tick = 0; tick < 400; tick++) system.tick();
        assertEquals(0, system.activeCount());
    }

    @Test
    void repeatedStormsReuseParticlesInsteadOfAllocating() {
        FxParticleSystem system = new FxParticleSystem(256, new ImpactParticleSpawnStrategy());
        Map<FxParticle, Boolean> firstWave = new IdentityHashMap<>();
        system.emit(strike(1), 200);
        system.active().forEach(particle -> firstWave.put(particle, Boolean.TRUE));
        assertTrue(firstWave.size() > 32, "expected a substantial first wave");

        for (int tick = 0; tick < 400; tick++) system.tick();
        assertEquals(0, system.activeCount());

        system.emit(strike(2), 200);
        long reused = system.active().stream().filter(firstWave::containsKey).count();
        assertEquals(system.activeCount(), reused, "every particle should come from the pool");
    }

    @Test
    void disabledFamiliesFreeTheirBudgetForTheRest() {
        FxParticleSystem system = new FxParticleSystem(512, new ImpactParticleSpawnStrategy());
        system.emit(strike(5), 200, material -> material == FxParticleMaterial.SPARK);
        assertTrue(system.activeCount() > 0);
        assertTrue(system.active().stream().allMatch(particle -> particle.material == FxParticleMaterial.SPARK));
    }

    @Test
    void waterStrikesProduceSprayAndNoDryDebris() {
        FxParticleSystem system = new FxParticleSystem(512, new ImpactParticleSpawnStrategy());
        LightningEnvironment water = new LightningEnvironment(LightningEnvironment.Type.WATER, 0x3f76e4,
            true, 1f, 62, false, 1f);
        system.emit(new LightningStrikeFxEvent(new Vec3d(0, 62, 0), 9, 1, water), 200);
        assertTrue(system.active().stream().anyMatch(particle -> particle.material == FxParticleMaterial.WATER));
        assertTrue(system.active().stream().noneMatch(particle -> particle.material == FxParticleMaterial.DEBRIS));
        assertTrue(system.active().stream().noneMatch(particle -> particle.material == FxParticleMaterial.DUST));
    }

    @Test
    void emissionNeverExceedsTheBudget() {
        FxParticleSystem system = new FxParticleSystem(4096, new ImpactParticleSpawnStrategy());
        system.emit(strike(11), 40);
        assertTrue(system.activeCount() <= 40, "budget exceeded: " + system.activeCount());
    }

    @Test
    void interpolatedStateIsSmoothFromTheFirstTick() {
        FxParticleSystem system = new FxParticleSystem(64, new ImpactParticleSpawnStrategy());
        system.emit(strike(3), 40);
        FxParticle particle = system.active().getFirst();
        assertEquals(particle.interpolatedX(0f), particle.interpolatedX(1f), 1e-9,
            "a freshly spawned particle must not jump on its first frame");
        assertTrue(particle.interpolatedAlpha(0.5f) >= 0);
    }

    @Test
    void unlitParticlesFollowTheSceneLightInsteadOfPaintingBlackBlobs() {
        FxParticleSystem lit = new FxParticleSystem(512, new ImpactParticleSpawnStrategy());
        FxParticleSystem dark = new FxParticleSystem(512, new ImpactParticleSpawnStrategy());
        lit.emit(strikeAtBrightness(1f), 200, material -> material == FxParticleMaterial.SMOKE);
        dark.emit(strikeAtBrightness(0f), 200, material -> material == FxParticleMaterial.SMOKE);

        float litTone = lit.active().getFirst().red;
        float darkTone = dark.active().getFirst().red;
        assertTrue(litTone > darkTone, "daylight smoke should be brighter than midnight smoke");
        assertTrue(litTone > 0.5f, "daylight smoke must not be near-black, was " + litTone);
        assertTrue(darkTone > 0.15f, "night smoke must still be visible, was " + darkTone);
    }

    @Test
    void softParticlesAreNotAllPerfectCircles() {
        FxParticleSystem system = new FxParticleSystem(512, new ImpactParticleSpawnStrategy());
        system.emit(strikeAtBrightness(1f), 300);
        long stretched = system.active().stream()
            .filter(particle -> particle.material == FxParticleMaterial.SMOKE
                || particle.material == FxParticleMaterial.DUST
                || particle.material == FxParticleMaterial.ASH)
            .filter(particle -> Math.abs(particle.aspect - 1f) > 0.05f)
            .count();
        assertTrue(stretched > 0, "every soft particle was a circle");
    }

    private static LightningStrikeFxEvent strikeAtBrightness(float brightness) {
        return new LightningStrikeFxEvent(new Vec3d(0, 64, 0), 4242, 1,
            new LightningEnvironment(LightningEnvironment.Type.LAND, 0x735a40, false, 0f, 64, false, brightness));
    }

    private static LightningStrikeFxEvent strike(int index) {
        return new LightningStrikeFxEvent(new Vec3d(index, 64, 0), index, 1,
            LightningEnvironment.land(0x735a40, true));
    }
}
