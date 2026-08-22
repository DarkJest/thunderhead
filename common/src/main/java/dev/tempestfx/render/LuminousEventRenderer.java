package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.ActiveLuminousEvent;
import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.sky.LuminousProfile;
import dev.tempestfx.sky.LuminousStructure;
import java.util.List;

/**
 * Draws sprites and jets.
 *
 * <p>Separate from {@link LightningRenderer} rather than a parameterisation of it. A bolt is a
 * white-hot core inside a cold sheath, drawn in three layers of one colour; a sprite is the opposite
 * animal - no core, colour that changes along its length, and most of its apparent brightness coming
 * from diffuse halos rather than from the filaments at all. Forcing both through one method would
 * mean a method that does neither well.
 *
 * <p>Two layers per filament, not three: a soft outer bloom and a brighter inner one. There is no
 * conducting channel to put a white core down the middle of, and adding one is exactly what makes a
 * procedural sprite look like red lightning instead of like a sprite.
 */
public final class LuminousEventRenderer {
    /** Outer bloom, as a multiple of the filament's own width. */
    private static final double OUTER_WIDTH = 5.5;
    private static final double INNER_WIDTH = 1.8;
    private static final float OUTER_ALPHA = 0.1f;
    private static final float INNER_ALPHA = 0.42f;
    /** Blocks of extra half-width per block of distance; these are looked at from a long way off. */
    private static final double MIN_WIDTH_PER_BLOCK = 0.0022;
    private static final float CUTOFF = 0.003f;

    /** The filaments and their bloom, into the additive electricity pass. */
    public void renderFilaments(List<ActiveLuminousEvent> events, PoseStack.Pose pose, VertexConsumer consumer,
                                Vec3d camera, float partialTick, boolean reducedFlashing,
                                ShaderPackProfile shaderProfile) {
        for (ActiveLuminousEvent event : events) {
            float brightness = event.brightness(partialTick, reducedFlashing);
            if (brightness <= CUTOFF) continue;
            LuminousProfile profile = event.profile();
            LuminousStructure structure = event.structure();
            double minWidth = camera.distanceTo(event.anchor()) * MIN_WIDTH_PER_BLOCK
                * shaderProfile.minWidthScale();

            layer(structure.filaments(), event, pose, consumer, camera, partialTick, brightness,
                OUTER_WIDTH, minWidth, OUTER_ALPHA, profile, false, shaderProfile);
            layer(structure.filaments(), event, pose, consumer, camera, partialTick, brightness,
                INNER_WIDTH, minWidth, INNER_ALPHA, profile, false, shaderProfile);
            // The cool fringe above a sprite: faint, and never brighter than the body it sits on.
            layer(structure.wisps(), event, pose, consumer, camera, partialTick, brightness,
                INNER_WIDTH, minWidth, INNER_ALPHA * 0.4f, profile, true, shaderProfile);
        }
    }

    private void layer(List<LightningSegment> segments, ActiveLuminousEvent event, PoseStack.Pose pose,
                       VertexConsumer consumer, Vec3d camera, float partialTick, float brightness,
                       double widthScale, double minWidth, float alphaScale, LuminousProfile profile,
                       boolean wisp, ShaderPackProfile shaderProfile) {
        for (LightningSegment segment : segments) {
            if (!event.visible(segment, partialTick)) continue;
            float alpha = Math.min(1f, shaderProfile.liftIntensity(brightness) * alphaScale);
            if (alpha <= CUTOFF) continue;
            // Colour runs along the structure, not across it: that gradient is the single strongest
            // cue that this is a sprite and not a red bolt.
            int packed = wisp ? profile.wispColor()
                : rampAt(profile, (segment.alongStart() + segment.alongEnd()) * 0.5);
            RibbonRenderer.renderRibbon(pose, consumer,
                segment.start().x(), segment.start().y(), segment.start().z(),
                segment.end().x(), segment.end().y(), segment.end().z(),
                camera.x(), camera.y(), camera.z(),
                Math.max(segment.startWidth() * widthScale, minWidth),
                Math.max(segment.endWidth() * widthScale, minWidth),
                red(packed), green(packed), blue(packed), alpha);
        }
    }

    /** The diffuse halos, into the wide atmospheric pass where the other soft glow lives. */
    public void renderGlow(List<ActiveLuminousEvent> events, PoseStack.Pose pose, VertexConsumer consumer,
                           Vec3d camera, float partialTick, boolean reducedFlashing) {
        for (ActiveLuminousEvent event : events) {
            float brightness = event.brightness(partialTick, reducedFlashing);
            if (brightness <= CUTOFF) continue;
            LuminousProfile profile = event.profile();
            for (LuminousStructure.LuminousGlow glow : event.structure().glows()) {
                if (!event.visible(glow, partialTick)) continue;
                float alpha = Math.min(0.85f, brightness * glow.strength() * profile.glowStrength() * 0.36f);
                if (alpha <= CUTOFF) continue;
                int packed = rampAt(profile, glow.along());
                RibbonRenderer.cameraQuad(pose, consumer,
                    glow.position().x(), glow.position().y(), glow.position().z(),
                    camera.x(), camera.y(), camera.z(), glow.radius(),
                    red(packed), green(packed), blue(packed), alpha);
            }
        }
    }

    /**
     * Three-stop colour ramp along the structure: head, body, tip.
     *
     * <p>Packed integers rather than float triples so the profile stays a value type with sane
     * equality, and unpacking is a shift and a divide in a loop that is already doing trigonometry.
     */
    private static int rampAt(LuminousProfile profile, double along) {
        double t = Math.max(0, Math.min(1, along));
        if (t < 0.35) return blend(profile.headColor(), profile.bodyColor(), t / 0.35);
        return blend(profile.bodyColor(), profile.tipColor(), (t - 0.35) / 0.65);
    }

    private static int blend(int from, int to, double t) {
        int red = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int green = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (red << 16) | (green << 8) | blue;
    }

    private static float red(int packed) { return ((packed >> 16) & 0xFF) / 255f; }

    private static float green(int packed) { return ((packed >> 8) & 0xFF) / 255f; }

    private static float blue(int packed) { return (packed & 0xFF) / 255f; }
}
