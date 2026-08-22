package dev.tempestfx.particle;

import dev.tempestfx.math.Vec3d;
import java.util.SplittableRandom;

/**
 * Particle burst that accompanies a direct hit on a player: a column of ash and embers thrown up
 * from the imprint, settling back into a drifting cloud.
 */
public final class AshImprintEmitter {
    private AshImprintEmitter() {}

    public static void spawn(FxParticleSink sink, Vec3d anchor, float radius, float height, long seed, int budget) {
        SplittableRandom random = new SplittableRandom(seed);
        int ashCount = Math.max(6, (int) (budget * 0.5));
        int emberCount = Math.max(3, (int) (budget * 0.2));
        int smokeCount = Math.max(3, (int) (budget * 0.3));

        for (int index = 0; index < ashCount; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.ASH);
            if (particle == null) break;
            double angle = random.nextDouble(Math.PI * 2);
            double offset = random.nextDouble(0, radius);
            particle.setPosition(anchor.x() + Math.cos(angle) * offset,
                anchor.y() + random.nextDouble(0.05, height),
                anchor.z() + Math.sin(angle) * offset);
            particle.setVelocity(Math.cos(angle) * random.nextDouble(0.01, 0.07),
                random.nextDouble(0.03, 0.16), Math.sin(angle) * random.nextDouble(0.01, 0.07));
            particle.gravity = 0.0045f;
            particle.drag = 0.965f;
            particle.setScale(random.nextFloat(0.05f, 0.13f), random.nextFloat(0.04f, 0.1f));
            float tone = random.nextFloat(0.13f, 0.3f);
            particle.setColor(tone, tone * 0.96f, tone * 0.93f);
            particle.setAlpha(0.8f);
            particle.rotation = random.nextFloat((float) Math.PI * 2);
            particle.angularVelocity = random.nextFloat(-0.09f, 0.09f);
            particle.lifetime = random.nextInt(140, 300);
        }

        for (int index = 0; index < emberCount; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.EMBER);
            if (particle == null) break;
            double angle = random.nextDouble(Math.PI * 2);
            particle.setPosition(anchor.x() + Math.cos(angle) * random.nextDouble(0, radius * 0.8),
                anchor.y() + 0.06, anchor.z() + Math.sin(angle) * random.nextDouble(0, radius * 0.8));
            particle.setVelocity(Math.cos(angle) * 0.03, random.nextDouble(0.04, 0.2), Math.sin(angle) * 0.03);
            particle.gravity = 0.016f;
            particle.drag = 0.95f;
            particle.floorY = anchor.y();
            particle.setScale(random.nextFloat(0.035f, 0.08f), 0.012f);
            particle.setColor(1f, 0.7f, 0.3f);
            particle.fadeToColor(0.28f, 0.06f, 0.02f);
            particle.setAlpha(1f);
            particle.emissiveStrength = 1f;
            particle.lifetime = random.nextInt(50, 110);
        }

        for (int index = 0; index < smokeCount; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.SMOKE);
            if (particle == null) break;
            particle.setPosition(anchor.x() + random.nextDouble(-radius, radius), anchor.y() + 0.15,
                anchor.z() + random.nextDouble(-radius, radius));
            particle.setVelocity(random.nextDouble(-0.015, 0.015), random.nextDouble(0.02, 0.06),
                random.nextDouble(-0.015, 0.015));
            particle.setAcceleration(0, 0.0006, 0);
            particle.drag = 0.985f;
            particle.setScale(random.nextFloat(0.2f, 0.38f), random.nextFloat(0.7f, 1.2f));
            particle.setColor(0.16f, 0.16f, 0.17f);
            particle.fadeToColor(0.34f, 0.34f, 0.36f);
            particle.setAlpha(0.6f);
            particle.rotation = random.nextFloat((float) Math.PI * 2);
            particle.angularVelocity = random.nextFloat(-0.025f, 0.025f);
            particle.lifetime = random.nextInt(80, 150);
        }
    }
}
