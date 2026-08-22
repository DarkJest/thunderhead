package dev.tempestfx.particle;

import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.SplittableRandom;

/**
 * Ambient particles shed by ball lightning: a slow trickle of sparks and embers falling out of the
 * sphere, plus the occasional arc fragment. Emitted a few per tick rather than in bursts, which is
 * what makes the sphere look like it is continuously burning instead of repeatedly exploding.
 */
public final class BallLightningEmitter {
    private BallLightningEmitter() {}

    public static void spawn(FxParticleSink sink, Vec3d center, float radius, float output, long seed, long tick) {
        SplittableRandom random = new SplittableRandom(StrikeSeed.derive(seed, tick));
        int sparks = output > 0.6f ? 2 : 1;

        for (int index = 0; index < sparks; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.SPARK);
            if (particle == null) break;
            double angle = random.nextDouble(Math.PI * 2);
            double pitch = random.nextDouble(-1, 1);
            particle.setPosition(center.x() + Math.cos(angle) * radius,
                center.y() + pitch * radius, center.z() + Math.sin(angle) * radius);
            particle.setVelocity(Math.cos(angle) * random.nextDouble(0.02, 0.12),
                random.nextDouble(-0.06, 0.06), Math.sin(angle) * random.nextDouble(0.02, 0.12));
            particle.gravity = 0.03f;
            particle.drag = 0.94f;
            particle.floorY = center.y() - 6;
            particle.setScale(0.045f, 0.01f);
            particle.setColor(0.8f, 0.9f, 1f);
            particle.fadeToColor(1f, 0.7f, 0.35f);
            particle.setAlpha(output);
            particle.emissiveStrength = 1f;
            particle.lifetime = random.nextInt(8, 22);
        }

        if (random.nextDouble() < 0.35) {
            FxParticle ember = sink.acquire(FxParticleMaterial.EMBER);
            if (ember != null) {
                double angle = random.nextDouble(Math.PI * 2);
                ember.setPosition(center.x() + Math.cos(angle) * radius * 0.8, center.y(),
                    center.z() + Math.sin(angle) * radius * 0.8);
                ember.setVelocity(Math.cos(angle) * 0.02, -random.nextDouble(0.01, 0.05), Math.sin(angle) * 0.02);
                ember.gravity = 0.012f;
                ember.drag = 0.96f;
                ember.floorY = center.y() - 6;
                ember.setScale(0.05f, 0.012f);
                ember.setColor(1f, 0.68f, 0.3f);
                ember.fadeToColor(0.3f, 0.07f, 0.02f);
                ember.setAlpha(output);
                ember.emissiveStrength = 0.9f;
                ember.lifetime = random.nextInt(20, 48);
            }
        }

        if (random.nextDouble() < 0.25) {
            FxParticle arc = sink.acquire(FxParticleMaterial.MICRO_ARC);
            if (arc != null) {
                double angle = random.nextDouble(Math.PI * 2);
                arc.setPosition(center.x() + Math.cos(angle) * radius * 1.3,
                    center.y() + random.nextDouble(-radius, radius),
                    center.z() + Math.sin(angle) * radius * 1.3);
                arc.setVelocity(random.nextDouble(-0.05, 0.05), random.nextDouble(-0.05, 0.05),
                    random.nextDouble(-0.05, 0.05));
                arc.drag = 0.88f;
                arc.setScale(0.035f, 0.006f);
                arc.setColor(0.72f, 0.85f, 1f);
                arc.setAlpha(output);
                arc.emissiveStrength = 1f;
                arc.lifetime = random.nextInt(2, 6);
            }
        }
    }
}
