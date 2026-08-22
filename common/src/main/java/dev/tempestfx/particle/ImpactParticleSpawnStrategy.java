package dev.tempestfx.particle;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.SplittableRandom;

/**
 * Impact emission for a terrain strike.
 */
public final class ImpactParticleSpawnStrategy implements ParticleSpawnStrategy {
    private static final long SEED_SALT = 0x6a09e667f3bcc909L;

    @Override
    public void spawn(LightningStrikeFxEvent event, int budget, FxParticleSink sink) {
        SplittableRandom random = new SplittableRandom(StrikeSeed.derive(event.seed(), SEED_SALT));
        LightningEnvironment environment = event.environment();
        Vec3d origin = event.position();
        double floor = environment.surfaceY(origin.y());

        // Sparks and arcs read as "electricity" and are emitted first so a tight budget keeps them.
        emitSparks(sink, random, origin, floor, share(budget, 0.28));
        emitMicroArcs(sink, random, origin, floor, share(budget, 0.06));
        if (environment.water()) {
            emitSpray(sink, random, origin, floor, share(budget, 0.34));
            emitSteam(sink, random, origin, floor, share(budget, 0.20));
            emitSmoke(sink, random, environment, origin, floor, share(budget, 0.10));
        } else {
            emitDust(sink, random, environment, origin, floor, share(budget, 0.18));
            emitSmoke(sink, random, environment, origin, floor, share(budget, 0.22));
            emitDebris(sink, random, environment, origin, floor, share(budget, 0.12));
            emitEmbers(sink, random, origin, floor, share(budget, 0.07));
            emitAsh(sink, random, environment, origin, floor, share(budget, 0.06));
            if (environment.raining() || environment.moisture() > 0.4f) {
                emitSteam(sink, random, origin, floor, share(budget, 0.10));
            }
        }
    }

    private static int share(int budget, double fraction) { return Math.max(1, (int) Math.round(budget * fraction)); }

    private void emitSparks(FxParticleSink sink, SplittableRandom random, Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.SPARK);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.22, 0.95);
            // Sparks leave the surface, not the channel end: those differ whenever the bolt
            // terminates on a partial block or slightly above the ground.
            particle.setPosition(origin.x(), floor + random.nextDouble(0.02, 0.35), origin.z());
            particle.setVelocity(Math.cos(angle) * speed, random.nextDouble(0.2, 0.9), Math.sin(angle) * speed);
            particle.gravity = 0.055f;
            particle.drag = 0.975f;
            particle.floorY = floor;
            particle.setScale(0.055f, 0.012f);
            particle.setColor(0.86f, 0.93f, 1f);
            particle.fadeToColor(1f, 0.72f, 0.35f);
            particle.setAlpha(1f);
            particle.emissiveStrength = 1f;
            particle.lifetime = random.nextInt(10, 28);
        }
    }

    private void emitMicroArcs(FxParticleSink sink, SplittableRandom random, Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.MICRO_ARC);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double radius = random.nextDouble(0.1, 1.4);
            particle.setPosition(origin.x() + Math.cos(angle) * radius,
                floor + random.nextDouble(0.05, 0.9),
                origin.z() + Math.sin(angle) * radius);
            particle.setVelocity(random.nextDouble(-0.12, 0.12), random.nextDouble(-0.04, 0.12),
                random.nextDouble(-0.12, 0.12));
            particle.drag = 0.9f;
            particle.floorY = floor;
            particle.setScale(0.04f, 0.008f);
            particle.setColor(0.78f, 0.88f, 1f);
            particle.setAlpha(1f);
            particle.emissiveStrength = 1f;
            particle.lifetime = random.nextInt(3, 9);
        }
    }

    /**
     * Impact smoke is pulverised surface and steam, lit by the flash that made it. It starts pale
     * and only darkens as it cools, which is the opposite of the charcoal-coloured puff that reads
     * as a black ball against lit terrain.
     */
    private void emitSmoke(FxParticleSink sink, SplittableRandom random, LightningEnvironment environment,
                           Vec3d origin, double floor, int count) {
        float lit = environment.litScale();
        float wetLift = environment.raining() ? 0.08f : 0;
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.SMOKE);
            if (particle == null) return;
            particle.setPosition(origin.x() + random.nextDouble(-0.85, 0.85),
                floor + random.nextDouble(0, 0.6),
                origin.z() + random.nextDouble(-0.85, 0.85));
            particle.setVelocity(random.nextDouble(-0.028, 0.028), random.nextDouble(0.025, 0.08),
                random.nextDouble(-0.028, 0.028));
            particle.setAcceleration(random.nextDouble(-0.0016, 0.0016), 0.0008, random.nextDouble(-0.0016, 0.0016));
            particle.drag = 0.985f;
            particle.setScale(random.nextFloat(0.22f, 0.42f), random.nextFloat(0.85f, 1.45f));
            // Wider than tall while it billows out, so the cloud is not a row of identical circles.
            particle.aspect = random.nextFloat(1.15f, 1.75f);
            float tone = random.nextFloat(0.62f, 0.82f) + wetLift;
            particle.setColor(tone * lit, tone * lit, Math.min(1f, (tone + 0.03f) * lit));
            particle.fadeToColor(tone * 0.34f * lit, tone * 0.34f * lit, tone * 0.37f * lit);
            particle.setAlpha(random.nextFloat(0.3f, 0.45f));
            particle.rotation = random.nextFloat((float) Math.PI * 2);
            particle.angularVelocity = random.nextFloat(-0.03f, 0.03f);
            particle.lifetime = random.nextInt(52, 104);
        }
    }

    private void emitSteam(FxParticleSink sink, SplittableRandom random, Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.STEAM);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double radius = random.nextDouble(0.2, 1.8);
            particle.setPosition(origin.x() + Math.cos(angle) * radius, floor + 0.05,
                origin.z() + Math.sin(angle) * radius);
            particle.setVelocity(Math.cos(angle) * 0.02, random.nextDouble(0.05, 0.12), Math.sin(angle) * 0.02);
            particle.drag = 0.96f;
            particle.setScale(random.nextFloat(0.18f, 0.34f), random.nextFloat(0.6f, 1.0f));
            particle.aspect = random.nextFloat(1.1f, 1.6f);
            particle.setColor(0.9f, 0.93f, 0.97f);
            particle.setAlpha(0.32f);
            particle.rotation = random.nextFloat((float) Math.PI * 2);
            particle.angularVelocity = random.nextFloat(-0.02f, 0.02f);
            particle.lifetime = random.nextInt(26, 58);
        }
    }

    private void emitDebris(FxParticleSink sink, SplittableRandom random, LightningEnvironment environment,
                            Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.DEBRIS);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.08, 0.36);
            particle.setPosition(origin.x(), floor + 0.06, origin.z());
            particle.setVelocity(Math.cos(angle) * speed, random.nextDouble(0.12, 0.46), Math.sin(angle) * speed);
            particle.gravity = 0.035f;
            particle.drag = 0.97f;
            particle.floorY = floor;
            float size = random.nextFloat(0.045f, 0.14f);
            particle.setScale(size, size);
            applySurfaceColor(particle, environment, random, 1f);
            particle.setAlpha(0.9f);
            particle.rotation = random.nextFloat((float) Math.PI * 2);
            particle.angularVelocity = random.nextFloat(-0.22f, 0.22f);
            particle.lifetime = random.nextInt(22, 56);
        }
    }

    private void emitDust(FxParticleSink sink, SplittableRandom random, LightningEnvironment environment,
                          Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.DUST);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.12, 0.4);
            particle.setPosition(origin.x() + Math.cos(angle) * random.nextDouble(0.2, 1.2), floor + 0.04,
                origin.z() + Math.sin(angle) * random.nextDouble(0.2, 1.2));
            particle.setVelocity(Math.cos(angle) * speed, random.nextDouble(0.015, 0.095), Math.sin(angle) * speed);
            particle.drag = 0.91f;
            particle.setScale(random.nextFloat(0.07f, 0.16f), random.nextFloat(0.28f, 0.55f));
            particle.aspect = random.nextFloat(1.2f, 2.0f);
            applySurfaceColor(particle, environment, random, 1.35f);
            particle.setAlpha(0.4f);
            particle.lifetime = random.nextInt(20, 42);
        }
    }

    private void emitAsh(FxParticleSink sink, SplittableRandom random, LightningEnvironment environment,
                         Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.ASH);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            particle.setPosition(origin.x() + Math.cos(angle) * random.nextDouble(0, 1.1),
                floor + random.nextDouble(0.2, 1.6), origin.z() + Math.sin(angle) * random.nextDouble(0, 1.1));
            particle.setVelocity(random.nextDouble(-0.03, 0.03), random.nextDouble(0.005, 0.05),
                random.nextDouble(-0.03, 0.03));
            particle.gravity = 0.004f;
            particle.drag = 0.97f;
            particle.setScale(random.nextFloat(0.05f, 0.11f), random.nextFloat(0.04f, 0.09f));
            particle.aspect = random.nextFloat(0.45f, 2.2f);
            float tone = random.nextFloat(0.34f, 0.52f) * environment.litScale();
            particle.setColor(tone, tone * 0.97f, tone * 0.95f);
            particle.setAlpha(0.55f);
            particle.rotation = random.nextFloat((float) Math.PI * 2);
            particle.angularVelocity = random.nextFloat(-0.08f, 0.08f);
            particle.lifetime = random.nextInt(90, 190);
        }
    }

    private void emitEmbers(FxParticleSink sink, SplittableRandom random, Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.EMBER);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.05, 0.22);
            particle.setPosition(origin.x() + Math.cos(angle) * random.nextDouble(0, 0.7), floor + 0.05,
                origin.z() + Math.sin(angle) * random.nextDouble(0, 0.7));
            particle.setVelocity(Math.cos(angle) * speed, random.nextDouble(0.02, 0.18), Math.sin(angle) * speed);
            particle.gravity = 0.018f;
            particle.drag = 0.95f;
            particle.floorY = floor;
            particle.setScale(random.nextFloat(0.03f, 0.075f), 0.01f);
            particle.setColor(1f, 0.66f, 0.28f);
            particle.fadeToColor(0.35f, 0.09f, 0.03f);
            particle.setAlpha(0.95f);
            particle.emissiveStrength = 0.8f;
            particle.lifetime = random.nextInt(30, 74);
        }
    }

    private void emitSpray(FxParticleSink sink, SplittableRandom random, Vec3d origin, double floor, int count) {
        for (int index = 0; index < count; index++) {
            FxParticle particle = sink.acquire(FxParticleMaterial.WATER);
            if (particle == null) return;
            double angle = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.16, 0.7);
            particle.setPosition(origin.x(), floor + 0.05, origin.z());
            particle.setVelocity(Math.cos(angle) * speed, random.nextDouble(0.25, 0.95), Math.sin(angle) * speed);
            particle.gravity = 0.05f;
            particle.drag = 0.98f;
            particle.setScale(0.055f, 0.03f);
            particle.setColor(0.62f, 0.8f, 0.95f);
            particle.setAlpha(0.85f);
            particle.emissiveStrength = 0.25f;
            particle.lifetime = random.nextInt(18, 44);
        }
    }

    /** Tints a particle from the surface map colour, so modded blocks are handled without a switch. */
    private static void applySurfaceColor(FxParticle particle, LightningEnvironment environment,
                                          SplittableRandom random, float lift) {
        int color = environment.groundColor();
        // Scene light matters: these passes are unlit, so raw map colours look black in daylight.
        float variation = (float) random.nextDouble(0.82, 1.18) * lift * environment.litScale();
        particle.setColor(
            Math.min(1f, ((color >> 16) & 255) / 255f * variation),
            Math.min(1f, ((color >> 8) & 255) / 255f * variation),
            Math.min(1f, (color & 255) / 255f * variation));
    }
}
