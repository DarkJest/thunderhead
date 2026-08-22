package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.particle.FxParticle;
import dev.tempestfx.particle.FxParticleMaterial;
import java.util.List;

/**
 * Batched particle geometry.
 */
public final class ParticleRenderer {
    /** Streak length per block-per-tick of speed. */
    private static final double STREAK_LENGTH_SCALE = 0.75;

    /** Additive electrical streaks: sparks, micro arcs and water spray. */
    public void renderStreaks(List<FxParticle> particles, PoseStack.Pose pose, VertexConsumer consumer,
                              Vec3d camera, float partialTick) {
        for (int index = 0; index < particles.size(); index++) {
            FxParticle particle = particles.get(index);
            if (!particle.material.streak()) continue;
            streak(pose, consumer, particle, camera, partialTick);
        }
    }

    /**
     * Additive halo around anything that emits light.
     *
     * <p>Driven by {@code emissiveStrength}, so an ember becomes a glowing dot and a hot spark gets a
     * bloom-like corona around its streak without any post-processing.
     */
    public void renderGlowing(List<FxParticle> particles, PoseStack.Pose pose, VertexConsumer consumer,
                              Vec3d camera, float partialTick) {
        for (int index = 0; index < particles.size(); index++) {
            FxParticle particle = particles.get(index);
            if (particle.emissiveStrength <= 0.01f) continue;
            billboard(pose, consumer, particle, camera, partialTick,
                1f + 2.2f * particle.emissiveStrength, 0.4f * particle.emissiveStrength);
        }
    }

    /** Translucent soft particulate: dust and ash. */
    public void renderSoft(List<FxParticle> particles, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick) {
        billboards(particles, pose, consumer, camera, partialTick, FxParticleMaterial.DUST, FxParticleMaterial.ASH);
    }

    /** Translucent noise puffs: smoke and steam. */
    public void renderSmoke(List<FxParticle> particles, PoseStack.Pose pose, VertexConsumer consumer,
                            Vec3d camera, float partialTick) {
        billboards(particles, pose, consumer, camera, partialTick, FxParticleMaterial.SMOKE, FxParticleMaterial.STEAM);
    }

    /** Untextured solid fragments. */
    public void renderDebris(List<FxParticle> particles, PoseStack.Pose pose, VertexConsumer consumer,
                             Vec3d camera, float partialTick) {
        for (int index = 0; index < particles.size(); index++) {
            FxParticle particle = particles.get(index);
            if (particle.material != FxParticleMaterial.DEBRIS) continue;
            float alpha = particle.interpolatedAlpha(partialTick);
            if (alpha <= 0.004f) continue;
            double x = particle.interpolatedX(partialTick);
            double y = particle.interpolatedY(partialTick);
            double z = particle.interpolatedZ(partialTick);
            double scale = particle.interpolatedScale(partialTick);
            double rightX = camera.z() - z, rightZ = -(camera.x() - x);
            double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
            if (rightLength < 1.0e-9) { rightX = 1; rightZ = 0; } else { rightX /= rightLength; rightZ /= rightLength; }
            rightX *= scale; rightZ *= scale;
            RibbonRenderer.plainVertex(pose, consumer, x - rightX, y - scale, z - rightZ,
                particle.red, particle.green, particle.blue, alpha);
            RibbonRenderer.plainVertex(pose, consumer, x + rightX, y - scale, z + rightZ,
                particle.red, particle.green, particle.blue, alpha);
            RibbonRenderer.plainVertex(pose, consumer, x + rightX, y + scale, z + rightZ,
                particle.red, particle.green, particle.blue, alpha);
            RibbonRenderer.plainVertex(pose, consumer, x - rightX, y + scale, z - rightZ,
                particle.red, particle.green, particle.blue, alpha);
        }
    }

    private void billboards(List<FxParticle> particles, PoseStack.Pose pose, VertexConsumer consumer,
                            Vec3d camera, float partialTick, FxParticleMaterial... materials) {
        for (int index = 0; index < particles.size(); index++) {
            FxParticle particle = particles.get(index);
            if (!matches(particle.material, materials)) continue;
            billboard(pose, consumer, particle, camera, partialTick, 1f, 1f);
        }
    }

    private static boolean matches(FxParticleMaterial material, FxParticleMaterial[] accepted) {
        for (FxParticleMaterial candidate : accepted) if (candidate == material) return true;
        return false;
    }

    /** Camera-facing quad with a rotation about the view axis, so puffs do not look cloned. */
    private static void billboard(PoseStack.Pose pose, VertexConsumer consumer, FxParticle particle,
                                  Vec3d camera, float partialTick, float scaleFactor, float alphaFactor) {
        float alpha = particle.interpolatedAlpha(partialTick) * alphaFactor;
        if (alpha <= 0.004f) return;
        double x = particle.interpolatedX(partialTick);
        double y = particle.interpolatedY(partialTick);
        double z = particle.interpolatedZ(partialTick);
        double scale = particle.interpolatedScale(partialTick) * scaleFactor;
        if (scale <= 0) return;

        double viewX = camera.x() - x, viewY = camera.y() - y, viewZ = camera.z() - z;
        double viewLength = Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
        if (viewLength > 1.0e-9) { viewX /= viewLength; viewY /= viewLength; viewZ /= viewLength; }

        double rightX = -viewZ, rightY = 0, rightZ = viewX;
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLength < 1.0e-9) { rightX = 1; rightZ = 0; } else { rightX /= rightLength; rightZ /= rightLength; }
        double upX = rightY * viewZ - rightZ * viewY;
        double upY = rightZ * viewX - rightX * viewZ;
        double upZ = rightX * viewY - rightY * viewX;

        float rotation = particle.previousRotation + (particle.rotation - particle.previousRotation) * partialTick;
        // A non-unit aspect is what stops smoke and dust reading as identical circles.
        double halfWidth = scale * Math.max(0.05f, particle.aspect);
        double halfHeight = scale;
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        double ax = (rightX * cos + upX * sin) * halfWidth;
        double ay = (upY * sin) * halfWidth;
        double az = (rightZ * cos + upZ * sin) * halfWidth;
        double bx = (-rightX * sin + upX * cos) * halfHeight;
        double by = (upY * cos) * halfHeight;
        double bz = (-rightZ * sin + upZ * cos) * halfHeight;

        RibbonRenderer.vertex(pose, consumer, x - ax - bx, y - ay - by, z - az - bz, 0f, 0f,
            particle.red, particle.green, particle.blue, alpha);
        RibbonRenderer.vertex(pose, consumer, x + ax - bx, y + ay - by, z + az - bz, 1f, 0f,
            particle.red, particle.green, particle.blue, alpha);
        RibbonRenderer.vertex(pose, consumer, x + ax + bx, y + ay + by, z + az + bz, 1f, 1f,
            particle.red, particle.green, particle.blue, alpha);
        RibbonRenderer.vertex(pose, consumer, x - ax + bx, y - ay + by, z - az + bz, 0f, 1f,
            particle.red, particle.green, particle.blue, alpha);
    }

    /** Velocity-aligned ribbon whose length grows with speed, so fast sparks read as energy streaks. */
    private static void streak(PoseStack.Pose pose, VertexConsumer consumer, FxParticle particle,
                               Vec3d camera, float partialTick) {
        float alpha = particle.interpolatedAlpha(partialTick);
        if (alpha <= 0.004f) return;
        double x = particle.interpolatedX(partialTick);
        double y = particle.interpolatedY(partialTick);
        double z = particle.interpolatedZ(partialTick);
        double scale = particle.interpolatedScale(partialTick);
        if (scale <= 0) return;

        double speed = Math.sqrt(particle.velocityX * particle.velocityX
            + particle.velocityY * particle.velocityY + particle.velocityZ * particle.velocityZ);
        double dx, dy, dz;
        if (speed < 1.0e-9) { dx = 0; dy = 1; dz = 0; } else {
            dx = particle.velocityX / speed; dy = particle.velocityY / speed; dz = particle.velocityZ / speed;
        }
        double half = Math.max(scale * 1.5, speed * STREAK_LENGTH_SCALE);
        RibbonRenderer.renderRibbon(pose, consumer,
            x - dx * half, y - dy * half, z - dz * half,
            x + dx * half, y + dy * half, z + dz * half,
            camera.x(), camera.y(), camera.z(),
            scale, scale * 0.35,
            particle.red, particle.green, particle.blue, alpha);
    }
}
