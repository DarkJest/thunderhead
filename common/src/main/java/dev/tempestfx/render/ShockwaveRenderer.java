package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ShockwaveEffect;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Noise;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * Impact rendering: the pressure ring, the surface ripple decal and the core flash.
 */
public final class ShockwaveRenderer {
    private static final int NEAR_SEGMENTS = 96;
    private static final int MID_SEGMENTS = 64;
    private static final int FAR_SEGMENTS = 32;
    private static final int NOISE_HARMONICS = 5;
    private static final float PROFILE_U = 0.5f;

    /** Additive ground ring. Shares the electricity batch, so it costs no extra draw call. */
    public void renderRing(ShockwaveEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick, TempestConfig config) {
        float strength = config.impact.shockwaveStrength;
        if (strength <= 0) return;
        double radius = effect.radius(partialTick) * strength;
        if (radius <= 0.05) return;
        float alpha = effect.opacity(partialTick) * 0.55f * Math.min(2f, strength);
        if (alpha <= 0.004f) return;

        Vec3d center = effect.event().position();
        double cameraDistance = camera.distanceTo(center);
        int segments = cameraDistance < 32 ? NEAR_SEGMENTS : cameraDistance < 96 ? MID_SEGMENTS : FAR_SEGMENTS;

        boolean water = effect.event().environment().water();
        long seed = effect.event().seed();
        double band = 0.22 + radius * 0.02;
        double noiseAmount = Math.min(0.9, radius * 0.05);
        double wobble = water ? 0.16 : 0.05;
        double cx = center.x();
        double cy = effect.surfaceY() + 0.045;
        double cz = center.z();

        float outerRed = water ? 0.5f : 0.62f;
        float outerGreen = water ? 0.82f : 0.78f;

        double previousAngle = 0;
        double previousRadius = radius + Noise.ring(seed, 0, NOISE_HARMONICS) * noiseAmount;
        for (int index = 1; index <= segments; index++) {
            double angle = Math.PI * 2 * index / segments;
            double edgeRadius = radius + Noise.ring(seed, angle, NOISE_HARMONICS) * noiseAmount;

            double cos0 = Math.cos(previousAngle), sin0 = Math.sin(previousAngle);
            double cos1 = Math.cos(angle), sin1 = Math.sin(angle);
            double y0 = cy + Math.sin(previousAngle * 3) * wobble;
            double y1 = cy + Math.sin(angle * 3) * wobble;
            double inner0 = Math.max(0, previousRadius - band), outer0 = previousRadius + band;
            double inner1 = Math.max(0, edgeRadius - band), outer1 = edgeRadius + band;

            RibbonRenderer.vertex(pose, consumer, cx + cos0 * inner0, y0, cz + sin0 * inner0,
                PROFILE_U, 0f, 0.86f, 0.93f, 1f, alpha);
            RibbonRenderer.vertex(pose, consumer, cx + cos1 * inner1, y1, cz + sin1 * inner1,
                PROFILE_U, 0f, 0.86f, 0.93f, 1f, alpha);
            RibbonRenderer.vertex(pose, consumer, cx + cos1 * outer1, y1, cz + sin1 * outer1,
                PROFILE_U, 1f, outerRed, outerGreen, 1f, alpha * 0.55f);
            RibbonRenderer.vertex(pose, consumer, cx + cos0 * outer0, y0, cz + sin0 * outer0,
                PROFILE_U, 1f, outerRed, outerGreen, 1f, alpha * 0.55f);

            previousAngle = angle;
            previousRadius = edgeRadius;
        }
    }

    /**
     * Ground decal that sells the surface deformation: an annulus mask scaled with the wavefront,
     * drawn additively so it lifts the struck material rather than painting over it.
     */
    public void renderSurfaceRipple(ShockwaveEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                                    float partialTick, TempestConfig config) {
        if (!config.impact.surfaceRipple) return;
        double radius = effect.radius(partialTick) * config.impact.shockwaveStrength;
        if (radius <= 0.2) return;
        float alpha = effect.opacity(partialTick) * 0.38f;
        if (alpha <= 0.004f) return;

        Vec3d center = effect.event().position();
        boolean water = effect.event().environment().water();
        int color = effect.event().environment().groundColor();
        float red = water ? 0.55f : Math.min(1f, ((color >> 16) & 255) / 255f + 0.25f);
        float green = water ? 0.78f : Math.min(1f, ((color >> 8) & 255) / 255f + 0.25f);
        float blue = water ? 1f : Math.min(1f, (color & 255) / 255f + 0.3f);
        float rotation = (float) (StrikeSeed.unit(effect.event().seed(), 0x71) * Math.PI * 2);

        groundQuad(pose, consumer, center.x(), effect.surfaceY() + 0.03, center.z(),
            radius * 1.12, rotation, red, green, blue, alpha);
    }

    /** Additive core flash at the impact: a short vertical column plus a bright pool on the ground. */
    public void renderFlash(ShockwaveEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                            Vec3d camera, float partialTick) {
        float flash = (float) Math.max(0, 1.0 - (effect.age() + partialTick) / 3.5);
        if (flash <= 0) return;
        flash *= flash;

        Vec3d center = effect.event().position();
        double surface = effect.surfaceY();
        double size = 0.4 + flash * 2.6;

        RibbonRenderer.renderRibbon(pose, consumer,
            center.x(), surface + 0.02, center.z(),
            center.x(), surface + size * 2.4, center.z(),
            camera.x(), camera.y(), camera.z(),
            size * 0.55, size * 0.12,
            0.94f, 0.98f, 1f, flash * 0.9f);
        groundQuad(pose, consumer, center.x(), surface + 0.02, center.z(), size * 2.2, 0,
            0.85f, 0.93f, 1f, flash * 0.75f);
    }

    /**
     * The overexposed burst at the impact point: a camera-facing star with anisotropic rays, the
     * shape a point light takes when it blows out an exposure.
     */
    public void renderBurst(ShockwaveEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                            Vec3d camera, float partialTick) {
        float flash = (float) Math.max(0, 1.0 - (effect.age() + partialTick) / 4.5);
        if (flash <= 0) return;
        flash *= flash;
        Vec3d center = effect.event().position();
        RibbonRenderer.cameraQuad(pose, consumer, center.x(), effect.surfaceY() + 0.6, center.z(),
            camera.x(), camera.y(), camera.z(), 3.0 + flash * 9.0,
            0.95f, 0.98f, 1f, flash);
    }

    /** Wide, faint haze so the flash reads as lighting the air itself, not just the ground. */
    public void renderHaze(ShockwaveEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick) {
        float glow = (float) Math.max(0, 1.0 - (effect.age() + partialTick) / 9.0);
        if (glow <= 0) return;
        Vec3d center = effect.event().position();
        RibbonRenderer.cameraQuad(pose, consumer, center.x(), effect.surfaceY() + 5.0, center.z(),
            camera.x(), camera.y(), camera.z(), 14.0 + (1 - glow) * 26.0,
            0.55f, 0.7f, 1f, glow * glow * 0.3f);
    }

    /** Horizontal camera-independent quad lying on the surface, rotated around the vertical axis. */
    static void groundQuad(PoseStack.Pose pose, VertexConsumer consumer, double x, double y, double z,
                           double halfSize, float rotation, float red, float green, float blue, float alpha) {
        double cos = Math.cos(rotation) * halfSize;
        double sin = Math.sin(rotation) * halfSize;
        RibbonRenderer.vertex(pose, consumer, x - cos + sin, y, z - sin - cos, 0f, 0f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, x - cos - sin, y, z - sin + cos, 0f, 1f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, x + cos - sin, y, z + sin + cos, 1f, 1f, red, green, blue, alpha);
        RibbonRenderer.vertex(pose, consumer, x + cos + sin, y, z + sin - cos, 1f, 0f, red, green, blue, alpha);
    }

    /** Exposed for the distortion pass, which needs the same falloff the ring uses. */
    public static float ringStrength(ShockwaveEffect effect, float partialTick) {
        return (float) FxMath.clamp(effect.opacity(partialTick), 0, 1);
    }
}
