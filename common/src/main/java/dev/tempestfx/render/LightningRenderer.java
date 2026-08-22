package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.api.LightningStyle;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.effect.LightningLook;
import dev.tempestfx.lightning.DischargeProfile;
import dev.tempestfx.lightning.LightningSegment;
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
    /** Blocks of extra half-width per block of distance, keeping distant bolts a few pixels wide. */
    private static final double MIN_WIDTH_PER_BLOCK = 0.0016;
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

    public void render(ActiveLightningEffect effect, PoseStack stack, VertexConsumer consumer,
                       Vec3d camera, float partialTick, TempestConfig config, float emissiveBoost,
                       ShaderPackProfile profile) {
        float brightness = effect.brightness(partialTick, config.lightning.flicker, config.general.reducedFlashing);
        if (brightness <= 0) return;

        PoseStack.Pose pose = stack.last();
        double distance = camera.distanceTo(effect.event().position());
        // Under a shader pack the floor carries the thin end of the branch ladder, so it is raised
        // there and given an absolute minimum for branches close enough that distance alone is small.
        double minWidth = Math.max(distance * MIN_WIDTH_PER_BLOCK * profile.minWidthScale(),
            profile.drawsWideGlow() ? 0 : NEAR_WIDTH_FLOOR);
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

        renderLayer(effect, pose, consumer, camera, partialTick, brightness,
            OUTER_WIDTH * thickness, minWidth, outer[0], outer[1], outer[2], 0.085f * glow * exposure, profile);
        renderLayer(effect, pose, consumer, camera, partialTick, brightness,
            INNER_WIDTH * thickness, minWidth, inner[0], inner[1], inner[2], 0.24f * glow * exposure, profile);
        renderLayer(effect, pose, consumer, camera, partialTick, brightness,
            CORE_WIDTH * thickness, minWidth, core[0], core[1], core[2], exposure, profile);
    }

    private void renderLayer(ActiveLightningEffect effect, PoseStack.Pose pose, VertexConsumer consumer,
                             Vec3d camera, float partialTick, float brightness, double layerWidth,
                             double minWidth, float red, float green, float blue, float alphaScale,
                             ShaderPackProfile profile) {
        if (alphaScale <= 0) return;
        for (LightningSegment segment : effect.segments()) {
            if (!effect.segmentVisible(segment, partialTick)) continue;
            float strength = brightness * profile.liftIntensity((float) segment.intensity())
                * effect.returnStrokeBoost(segment, partialTick);
            float alpha = Math.min(1f, strength * alphaScale);
            if (alpha <= 0.002f) continue;
            double startWidth = Math.max(segment.startWidth() * layerWidth, minWidth);
            double endWidth = Math.max(segment.endWidth() * layerWidth, minWidth);
            RibbonRenderer.renderRibbon(pose, consumer,
                segment.start().x(), segment.start().y(), segment.start().z(),
                segment.end().x(), segment.end().y(), segment.end().z(),
                camera.x(), camera.y(), camera.z(),
                startWidth, endWidth, red, green, blue, alpha);
        }
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
