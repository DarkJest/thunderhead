package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.api.LightningStyle;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.effect.LightningLook;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.lightning.LightningBranch;
import dev.tempestfx.lightning.LightningSegment;
import java.util.List;
import dev.tempestfx.math.FxMath;
import dev.tempestfx.math.Vec3d;

/**
 * Draws the channel as three stacked additive ribbon layers.
 *
 * <pre>
 *        outer halo   7x core width, faint, cold
 *        inner glow   2.8x core width, strong, slightly cold
 *        core         1x, near white, fully opaque
 * </pre>
 */
public final class LightningRenderer {
    private static final double OUTER_WIDTH = 7.0;
    private static final double INNER_WIDTH = 2.8;
    private static final double CORE_WIDTH = 1.0;
    /**
     * Narrowest a ribbon is allowed to be drawn, in pixels.
     *
     * <p>Below about a pixel a line cannot be rasterised honestly, so it is drawn at this width and
     * dimmed by however much it had to be widened. Energy is conserved: a branch far enough away
     * fades out instead of staying a bright thread, which is what stops a storm front of hundreds of
     * them from shimmering as the camera turns.
     */
    private static final double MIN_PIXELS = 1.5;
    /**
     * Floor on the sub-pixel dimming.
     *
     * <p>The measurement this rides on comes from the frame's own projection matrix, and a rendering
     * feature is not allowed to be the reason lightning cannot be seen. Whatever the arithmetic
     * decides, a channel keeps at least this much of its alpha.
     */
    private static final float MIN_COVERAGE = 0.3f;
    /** Absolute half-width floor used only under a shader pack, in blocks. */
    private static final double NEAR_WIDTH_FLOOR = 0.03;
    /**
     * The violet-white end of the channel palette, reached at {@code warmth = 1}.
     *
     * <p>A positive flash is not a brighter blue one. Its channel is hotter and the halo around it
     * reads violet rather than cyan, which is what lets a player tell the two apart in one frame.
     */
    private static final float[] WARM_OUTER = { 0.72f, 0.34f, 0.86f };
    private static final float[] WARM_INNER = { 0.99f, 0.74f, 0.88f };
    private static final float[] WARM_CORE = { 1f, 0.97f, 0.93f };
    /** Two points closer than this are the same joint; anything further is a break in the polyline. */
    private static final double JOINT_EPSILON = 1.0e-8;

    // Scratch for the mitring, reused on every segment: the render thread is the only caller, and a
    // hot path may not allocate.
    private final double[] own = new double[3];
    private final double[] incoming = new double[3];
    private final double[] outgoing = new double[3];
    private final double[] viewDirection = new double[3];
    private final double[] sideStart = new double[3];
    private final double[] sideEnd = new double[3];

    public void render(ActiveLightningEffect effect, PoseStack stack, VertexConsumer consumer,
                       Vec3d camera, float partialTick, TempestConfig config, float emissiveBoost,
                       ShaderPackProfile profile, double pixelScale) {
        float brightness = effect.brightness(partialTick, config.lightning.flicker, config.general.reducedFlashing);
        if (brightness <= 0) return;

        PoseStack.Pose pose = stack.last();
        double distance = camera.distanceTo(effect.event().position());
        // The width one pixel covers out there. Anything thinner is drawn at this and dimmed.
        double pixelFloor = distance * pixelScale * MIN_PIXELS * 0.5 * profile.minWidthScale();
        // Under a shader pack there is no analytic cross-section to carry the thin end of the branch
        // ladder, so an absolute floor stays - and is deliberately not dimmed, because a fallback
        // that fades its own branches out would simply be missing them.
        double hardFloor = profile.drawsWideGlow() ? 0 : NEAR_WIDTH_FLOOR;
        // The player's settings for a strike of the mod's own; an integration's style for one it
        // asked for. Brightness and flicker are not in here - those are accessibility, and they are
        // read from the configuration above whatever any style says.
        LightningLook look = LightningLook.resolve(config, effect.event().style());
        DischargeProfile discharge = effect.profile();
        double thickness = look.thickness() * profile.widthScale() * discharge.widthScale();
        float glow = config.lightning.glowStrength * emissiveBoost;
        float tint = look.coldTint();
        // How much of the channel is exposed at all. An intracloud event is nearly all cloud and
        // almost no visible strand, which is the whole point of it.
        float exposure = discharge.channelOpacity();
        if (exposure <= 0.001f) return;
        float warmth = discharge.warmth();

        // Cold outer halo, then a brighter inner sheath, then the near-white conducting core. A
        // style may name the two colours outright; otherwise they come off the cold-tint ramp, which
        // is what an ordinary bolt uses, warmed toward violet for the positive archetypes.
        LightningStyle style = effect.event().style();
        float[] outer = style != null && style.hasGlowColor()
            ? rgb(style.glowColor(), 1f)
            : warm(mix(0.30f, tint), mix(0.48f, tint), 1f, WARM_OUTER, warmth);
        float[] inner = style != null && style.hasGlowColor()
            ? rgb(style.glowColor(), 1.45f)
            : warm(mix(0.58f, tint), mix(0.76f, tint), 1f, WARM_INNER, warmth);
        float[] core = style != null && style.hasCoreColor()
            ? rgb(style.coreColor(), 1f)
            : warm(0.97f, 0.99f, 1f, WARM_CORE, warmth);

        renderLayer(effect, pose, consumer, camera, partialTick, brightness, OUTER_WIDTH * thickness,
            pixelFloor, hardFloor, outer[0], outer[1], outer[2], 0.085f * glow * exposure, profile);
        renderLayer(effect, pose, consumer, camera, partialTick, brightness, INNER_WIDTH * thickness,
            pixelFloor, hardFloor, inner[0], inner[1], inner[2], 0.24f * glow * exposure, profile);
        renderLayer(effect, pose, consumer, camera, partialTick, brightness, CORE_WIDTH * thickness,
            pixelFloor, hardFloor, core[0], core[1], core[2], exposure, profile);
    }

    /**
     * One layer of every branch.
     *
     * <p>Walked branch by branch rather than over the flat segment list, because a branch is a
     * polyline and its segments have to share the vertices where they meet. Consecutive segments
     * already agree about width at a shared point - the width profile is a continuous function of
     * position along the channel - so giving them the same mitred side vector puts their vertices in
     * exactly the same place, and the joint has no seam left to show.
     */
    private void renderLayer(ActiveLightningEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                             Vec3d camera, float partialTick, float brightness, double layerWidth,
                             double pixelFloor, double hardFloor, float red, float green, float blue,
                             float alphaScale, ShaderPackProfile profile) {
        if (alphaScale <= 0) return;
        for (LightningBranch branch : effect.geometry().branches()) {
            List<LightningSegment> segments = branch.segments();
            for (int index = 0; index < segments.size(); index++) {
                LightningSegment segment = segments.get(index);
                if (!effect.segmentVisible(segment, partialTick)) continue;

                float strength = brightness * profile.liftIntensity((float) segment.intensity())
                    * effect.returnStrokeBoost(segment, partialTick);
                double geometricStart = segment.startWidth() * layerWidth;
                double geometricEnd = segment.endWidth() * layerWidth;
                double geometric = (geometricStart + geometricEnd) * 0.5;
                // However much the ribbon had to be widened to survive rasterisation, it loses in alpha.
                float coverage = pixelFloor > 1.0e-9
                    ? (float) Math.max(MIN_COVERAGE, Math.min(1.0, geometric / pixelFloor))
                    : 1f;
                float alpha = Math.min(1f, strength * alphaScale * coverage);
                if (alpha <= 0.002f) continue;

                emitMitred(segments, index, segment, pose, consumer, camera,
                    Math.max(Math.max(geometricStart, pixelFloor), hardFloor),
                    Math.max(Math.max(geometricEnd, pixelFloor), hardFloor),
                    red, green, blue, alpha);
            }
        }
    }

    /**
     * Emits one segment with its ends mitred against whichever neighbours it has in the branch.
     *
     * <p>A neighbour that is currently hidden is still used. The visible run then ends on the same
     * geometry it would have had if the whole branch were drawn, so a fork blinking on and off cannot
     * make the trunk it hangs from twitch.
     */
    private void emitMitred(List<LightningSegment> segments, int index, LightningSegment segment,
                            PoseStack.Pose pose, VertexConsumer consumer, Vec3d camera,
                            double startWidth, double endWidth,
                            float red, float green, float blue, float alpha) {
        direction(segment.start(), segment.end(), own);
        LightningSegment previous = index > 0 ? segments.get(index - 1) : null;
        LightningSegment next = index + 1 < segments.size() ? segments.get(index + 1) : null;

        if (previous != null && previous.end().distanceSquaredTo(segment.start()) < JOINT_EPSILON) {
            direction(previous.start(), previous.end(), incoming);
        } else {
            incoming[0] = own[0]; incoming[1] = own[1]; incoming[2] = own[2];
        }
        if (next != null && next.start().distanceSquaredTo(segment.end()) < JOINT_EPSILON) {
            direction(next.start(), next.end(), outgoing);
        } else {
            outgoing[0] = own[0]; outgoing[1] = own[1]; outgoing[2] = own[2];
        }

        view(camera, segment.start(), segment.end(), viewDirection);
        double startMiter = RibbonRenderer.miterSide(incoming[0], incoming[1], incoming[2],
            own[0], own[1], own[2], viewDirection[0], viewDirection[1], viewDirection[2], sideStart);
        double endMiter = RibbonRenderer.miterSide(own[0], own[1], own[2],
            outgoing[0], outgoing[1], outgoing[2], viewDirection[0], viewDirection[1], viewDirection[2],
            sideEnd);

        RibbonRenderer.renderRibbon(pose, consumer,
            segment.start().x(), segment.start().y(), segment.start().z(),
            segment.end().x(), segment.end().y(), segment.end().z(),
            sideStart, sideEnd, startWidth * startMiter, endWidth * endMiter,
            red, green, blue, alpha);
    }

    private static void direction(Vec3d from, Vec3d to, double[] out) {
        double dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-9) { out[0] = 1; out[1] = 0; out[2] = 0; return; }
        out[0] = dx / length; out[1] = dy / length; out[2] = dz / length;
    }

    /** Unit vector from the middle of a segment back to the camera. */
    private static void view(Vec3d camera, Vec3d start, Vec3d end, double[] out) {
        double vx = camera.x() - (start.x() + end.x()) * 0.5;
        double vy = camera.y() - (start.y() + end.y()) * 0.5;
        double vz = camera.z() - (start.z() + end.z()) * 0.5;
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length < 1.0e-9) { out[0] = 0; out[1] = 0; out[2] = 1; return; }
        out[0] = vx / length; out[1] = vy / length; out[2] = vz / length;
    }

    /**
     * A single wide quad behind the top of a channel: the cloud it emerged from lighting up.
     */
    public void renderCloudGlow(ActiveLightningEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                                Vec3d camera, float partialTick) {
        float brightness = effect.brightness(partialTick, true, false);
        if (brightness <= 0.01f) return;
        var segments = effect.segments();
        if (segments.isEmpty()) return;
        Vec3d top = segments.getFirst().start();
        double height = Math.max(8, top.y() - segments.getLast().end().y());
        RibbonRenderer.cameraQuad(pose, consumer, top.x(), top.y(), top.z(),
            camera.x(), camera.y(), camera.z(), height * 0.55,
            0.6f, 0.72f, 1f, Math.min(0.5f, brightness * 0.3f));
    }

    /**
     * Unpacks {@code 0xRRGGBB} and brightens it.
     */
    private static float[] rgb(int packed, float gain) {
        return new float[] {
            (float) FxMath.clamp(((packed >> 16) & 0xFF) / 255f * gain, 0f, 1f),
            (float) FxMath.clamp(((packed >> 8) & 0xFF) / 255f * gain, 0f, 1f),
            (float) FxMath.clamp((packed & 0xFF) / 255f * gain, 0f, 1f) };
    }

    /** Blends the cold channel colour toward the warm palette of a positive discharge. */
    private static float[] warm(float red, float green, float blue, float[] target, float warmth) {
        float amount = (float) FxMath.clamp(warmth, 0f, 1f);
        if (amount <= 0) return new float[] { red, green, blue };
        return new float[] {
            red + (target[0] - red) * amount,
            green + (target[1] - green) * amount,
            blue + (target[2] - blue) * amount };
    }

    /** Blends a cold channel value toward white as the user reduces the cold tint. */
    private static float mix(float cold, float tint) {
        return (float) FxMath.clamp(1f - (1f - cold) * FxMath.clamp(tint, 0f, 2f), 0f, 1f);
    }
}
