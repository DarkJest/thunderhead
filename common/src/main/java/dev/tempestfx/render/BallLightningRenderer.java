package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.lightning.ArcGeometry;
import dev.tempestfx.math.StrikeSeed;

/**
 * Draws ball lightning as a layered plasma sphere rather than a glowing billboard.
 *
 * <pre>
 *   ground pool     faint additive disc that tracks the surface underneath
 *   plasma shell    three counter-rotating turbulent puffs, cold violet-white
 *   corona          soft halo just outside the core
 *   core            small, almost white, hot centre
 *   surface arcs    short discharges crawling over the sphere
 * </pre>
 *
 * <p>Geometry only, and split by render type rather than by sphere, because the layers belong to three
 * different batches. Every method takes the origin it should draw around, so the same code serves the
 * mod's world pass, where the pose is the camera and spheres are at world coordinates, and the entity
 * dispatcher's fallback, where the pose is already at the entity and the origin is zero.
 */
public final class BallLightningRenderer {
    private static final int SHELL_LAYERS = 3;
    private static final int ARCS = 6;
    private static final int ARC_GENERATIONS = 2;
    private static final int ARC_POINTS = ArcGeometry.pointCount(ARC_GENERATIONS);
    /** Arc shapes are re-rolled this many times per tick. */
    private static final double ARC_BUCKETS_PER_TICK = 0.5;

    private final double[] arcPoints = new double[ARCS * ARC_POINTS * 3];
    private long cachedArcKey = Long.MIN_VALUE;

    /**
     * Faint additive disc on the surface below, which is what sells the sphere as a light source.
     */
    public void renderGroundPool(BallLightningDraw sphere, PoseStack.Pose pose, VertexConsumer consumer,
                                 double originX, double originY, double originZ) {
        double surfaceOffset = sphere.surfaceBelow() - sphere.y();
        if (surfaceOffset < -6 || surfaceOffset > 0.5) return;
        float fade = (float) (1.0 - Math.min(1.0, -surfaceOffset / 6.0));
        ShockwaveRenderer.groundQuad(pose, consumer, originX, originY + surfaceOffset + 0.02, originZ,
            sphere.radius() * 4.5, 0f, 0.62f, 0.72f, 1f, sphere.output() * fade * 0.3f);
    }

    /** Counter-rotating noise puffs: the turbulence that makes it look like contained plasma. */
    public void renderShell(BallLightningDraw sphere, PoseStack.Pose pose, VertexConsumer consumer,
                            double originX, double originY, double originZ) {
        for (int layer = 0; layer < SHELL_LAYERS; layer++) {
            float direction = layer % 2 == 0 ? 1f : -1f;
            float spin = (float) (sphere.age() * 0.06 * direction
                + StrikeSeed.unit(sphere.seed(), 0x9000 + layer) * Math.PI * 2);
            float scale = sphere.radius() * (1.55f + layer * 0.42f);
            float alpha = sphere.output() * (0.34f - layer * 0.08f);
            if (alpha <= 0.002f) continue;
            // Slightly violet outside, colder blue inside.
            float red = 0.44f + layer * 0.06f;
            float green = 0.52f + layer * 0.04f;
            billboardXY(pose, consumer, originX, originY, originZ, scale, spin, red, green, 1f, alpha);
            billboardXZ(pose, consumer, originX, originY, originZ, scale, spin * 0.7f, red, green, 1f,
                alpha * 0.75f);
        }
    }

    /** Two crossed quads for the hot centre; near white and deliberately overdriven. */
    public void renderCore(BallLightningDraw sphere, PoseStack.Pose pose, VertexConsumer consumer,
                           double originX, double originY, double originZ,
                           double cameraX, double cameraY, double cameraZ) {
        float radius = sphere.radius();
        float output = sphere.output();
        billboardXY(pose, consumer, originX, originY, originZ, radius * 2.3f, 0f, 0.68f, 0.8f, 1f, output * 0.5f);
        billboardXY(pose, consumer, originX, originY, originZ, radius * 0.95f, 0f, 0.97f, 0.99f, 1f, output);
        billboardXZ(pose, consumer, originX, originY, originZ, radius * 0.95f, 0f, 0.97f, 0.99f, 1f, output * 0.8f);
        // A short vertical wisp, so the sphere reads as connected to the air rather than pasted on.
        RibbonRenderer.renderRibbon(pose, consumer,
            originX, originY + radius * 0.4, originZ,
            originX, originY + radius * 2.4, originZ,
            cameraX, cameraY, cameraZ,
            radius * 0.28, 0.01, 0.7f, 0.82f, 1f, output * 0.35f);
    }

    /** Short discharges crawling over the surface of the sphere. */
    public void renderArcs(BallLightningDraw sphere, PoseStack.Pose pose, VertexConsumer consumer,
                           double originX, double originY, double originZ,
                           double cameraX, double cameraY, double cameraZ, ShaderPackProfile profile) {
        float radius = sphere.radius();
        float output = sphere.output();
        long bucket = (long) (sphere.age() * ARC_BUCKETS_PER_TICK);
        // Keyed on the sphere as well as the bucket: two spheres alive at once must not share a shape.
        long key = sphere.seed() * 31 + bucket;
        if (key != cachedArcKey) {
            cachedArcKey = key;
            rebuildArcs(StrikeSeed.derive(sphere.seed(), bucket), radius);
        }
        // With the quads gone, the arcs are the sphere: wider and brighter so it still reads as one.
        double width = radius * (profile.drawsWideGlow() ? 1.0 : profile.widthScale());
        float glow = profile.drawsWideGlow() ? 1f : profile.emissiveScale() * 0.55f;
        for (int arc = 0; arc < ARCS; arc++) {
            int base = arc * ARC_POINTS * 3;
            for (int point = 0; point < ARC_POINTS - 1; point++) {
                int a = base + point * 3;
                int b = a + 3;
                RibbonRenderer.renderRibbon(pose, consumer,
                    originX + arcPoints[a], originY + arcPoints[a + 1], originZ + arcPoints[a + 2],
                    originX + arcPoints[b], originY + arcPoints[b + 1], originZ + arcPoints[b + 2],
                    cameraX, cameraY, cameraZ,
                    width * 0.09, width * 0.09, 0.6f, 0.76f, 1f, Math.min(1f, output * 0.5f * glow));
                RibbonRenderer.renderRibbon(pose, consumer,
                    originX + arcPoints[a], originY + arcPoints[a + 1], originZ + arcPoints[a + 2],
                    originX + arcPoints[b], originY + arcPoints[b + 1], originZ + arcPoints[b + 2],
                    cameraX, cameraY, cameraZ,
                    width * 0.025, width * 0.025, 0.95f, 0.98f, 1f, Math.min(1f, output * glow));
            }
        }
    }

    /** Arcs run between two points on the sphere, bulging just outside its surface. */
    private void rebuildArcs(long seed, float radius) {
        double shell = radius * 1.08;
        for (int arc = 0; arc < ARCS; arc++) {
            long arcSeed = StrikeSeed.derive(seed, arc);
            double theta0 = StrikeSeed.unit(arcSeed, 1) * Math.PI * 2;
            double phi0 = Math.acos(StrikeSeed.signed(arcSeed, 2));
            double theta1 = theta0 + (0.6 + StrikeSeed.unit(arcSeed, 3) * 1.6);
            double phi1 = Math.acos(StrikeSeed.signed(arcSeed, 4));
            ArcGeometry.generate(arcSeed,
                Math.sin(phi0) * Math.cos(theta0) * shell, Math.cos(phi0) * shell, Math.sin(phi0) * Math.sin(theta0) * shell,
                Math.sin(phi1) * Math.cos(theta1) * shell, Math.cos(phi1) * shell, Math.sin(phi1) * Math.sin(theta1) * shell,
                radius * 0.55, ARC_GENERATIONS, arcPoints, arc * ARC_POINTS * 3);
        }
    }

    /** Quad in the sphere's local XY plane, rotated about Z. */
    private static void billboardXY(PoseStack.Pose pose, VertexConsumer consumer,
                                    double originX, double originY, double originZ,
                                    float size, float spin,
                                    float red, float green, float blue, float alpha) {
        if (alpha <= 0.002f) return;
        double cos = Math.cos(spin) * size;
        double sin = Math.sin(spin) * size;
        RibbonRenderer.vertex(pose, consumer, originX - cos + sin, originY - sin - cos, originZ,
            0f, 0f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, originX - cos - sin, originY - sin + cos, originZ,
            0f, 1f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, originX + cos - sin, originY + sin + cos, originZ,
            1f, 1f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, originX + cos + sin, originY + sin - cos, originZ,
            1f, 0f, red, green, blue, alpha);
    }

    /** Quad in the sphere's local XZ plane, rotated about Y. */
    private static void billboardXZ(PoseStack.Pose pose, VertexConsumer consumer,
                                    double originX, double originY, double originZ,
                                    float size, float spin,
                                    float red, float green, float blue, float alpha) {
        if (alpha <= 0.002f) return;
        double cos = Math.cos(spin) * size;
        double sin = Math.sin(spin) * size;
        RibbonRenderer.vertex(pose, consumer, originX - cos + sin, originY, originZ - sin - cos,
            0f, 0f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, originX - cos - sin, originY, originZ - sin + cos,
            0f, 1f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, originX + cos - sin, originY, originZ + sin + cos,
            1f, 1f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, originX + cos + sin, originY, originZ + sin - cos,
            1f, 0f, red, green, blue, alpha);
    }
}
