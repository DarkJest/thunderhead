package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.strike.Streamer;
import dev.tempestfx.strike.StrikeAttachment;
import java.util.List;

/**
 * Draws the upward streamers and the flash where one of them connects.
 *
 * <p>Emitted into the passes that already exist — the filaments into the shared additive electricity
 * batch, the connection flash into the glow batch — so this costs no extra draw call, no GL state of
 * its own and nothing for the compositor to learn about. It is drawn into the mod's own framebuffer
 * and applied to the finished frame exactly like every other effect, which is why it behaves the
 * same with a shader pack as without one.
 */
public final class StreamerRenderer {
    /** Two layers, like the channel but without a white core: a streamer is dimmer than what it meets. */
    private static final double OUTER_WIDTH = 4.5;
    private static final double CORE_WIDTH = 1.0;
    private static final float OUTER_ALPHA = 0.16f;
    /** Streamers are violet-white rather than the channel's blue-white; positive charge, warmer. */
    private static final float[] OUTER_COLOR = { 0.72f, 0.58f, 1f };
    private static final float[] CORE_COLOR = { 0.95f, 0.9f, 1f };
    /** Blocks of extra half-width per block of distance, so a distant streamer is still a few pixels. */
    private static final double MIN_WIDTH_PER_BLOCK = 0.0016;
    /** Ticks the connection flash lasts. It is an instant, not an event. */
    private static final float FLASH_TICKS = 2.4f;
    private static final float CUTOFF = 0.004f;

    /** The filaments themselves, into the additive electricity pass. */
    public void renderStreamers(List<ActiveLightningEffect> effects, PoseStack.Pose pose,
                                VertexConsumer consumer, Vec3d camera, float partialTick,
                                ShaderPackProfile profile) {
        for (ActiveLightningEffect effect : effects) {
            StrikeAttachment attachment = effect.attachment();
            if (attachment == null || !attachment.contested()) continue;

            float leader = effect.propagation(partialTick);
            float time = effect.time(partialTick);
            double minWidth = camera.distanceTo(attachment.point()) * MIN_WIDTH_PER_BLOCK
                * profile.minWidthScale();

            for (Streamer streamer : attachment.streamers()) {
                float growth = streamer.growth(leader);
                if (growth <= 0) continue;
                float output = output(streamer, leader, time, effect);
                if (output <= CUTOFF) continue;

                float kindBrightness = streamer.kind().brightness();
                layer(streamer, pose, consumer, camera, growth, minWidth, OUTER_WIDTH,
                    OUTER_COLOR, output * kindBrightness * OUTER_ALPHA, profile);
                layer(streamer, pose, consumer, camera, growth, minWidth, CORE_WIDTH,
                    CORE_COLOR, output * kindBrightness, profile);
            }
        }
    }

    /**
     * How brightly a streamer is glowing right now.
     *
     * <p>All of them brighten as the leader closes. At the moment of attachment the winner is
     * swallowed by the return stroke — the channel is drawn over it and far brighter — while the
     * losers, whose field has just collapsed, snap dark over a few ticks. That contrast is the whole
     * story of the attachment phase in one frame.
     */
    private static float output(Streamer streamer, float leader, float time, ActiveLightningEffect effect) {
        if (leader < 1) return streamer.growth(leader) * 0.85f;
        float since = time - effect.profile().envelope().propagationTicks();
        if (streamer.winner()) {
            // The winner is now part of the channel: it stays lit while the return stroke passes.
            return (float) Math.max(0, 1.0 - since / (FLASH_TICKS * 1.6));
        }
        return (float) Math.max(0, 0.85 - since / (FLASH_TICKS * 0.7));
    }

    private void layer(Streamer streamer, PoseStack.Pose pose, VertexConsumer consumer, Vec3d camera,
                       float growth, double minWidth, double widthScale, float[] color, float alphaScale,
                       ShaderPackProfile profile) {
        float alpha = Math.min(1f, profile.liftIntensity(alphaScale));
        if (alpha <= CUTOFF) return;
        for (LightningSegment segment : streamer.segments()) {
            if (segment.alongStart() > growth) continue;
            RibbonRenderer.renderRibbon(pose, consumer,
                segment.start().x(), segment.start().y(), segment.start().z(),
                segment.end().x(), segment.end().y(), segment.end().z(),
                camera.x(), camera.y(), camera.z(),
                Math.max(segment.startWidth() * widthScale, minWidth),
                Math.max(segment.endWidth() * widthScale, minWidth),
                color[0], color[1], color[2], alpha);
        }
    }

    /**
     * The flash at the junction, into the glow pass.
     *
     * <p>Small and short: this is the instant two things touched, and the return stroke that follows
     * it is what lights the world. Making it large would compete with the strike itself.
     */
    public void renderAttachmentFlash(List<ActiveLightningEffect> effects, PoseStack.Pose pose,
                                      VertexConsumer consumer, Vec3d camera, float partialTick) {
        for (ActiveLightningEffect effect : effects) {
            StrikeAttachment attachment = effect.attachment();
            if (attachment == null || !attachment.contested()) continue;
            float time = effect.time(partialTick);
            float since = time - effect.profile().envelope().propagationTicks();
            if (since < 0 || since > FLASH_TICKS) continue;

            float fade = 1f - since / FLASH_TICKS;
            float alpha = Math.min(0.9f, fade * fade * 0.85f);
            if (alpha <= CUTOFF) continue;
            double radius = 0.45 + (1 - fade) * 1.5;
            // A rod earns a slightly bigger, warmer flash: it is the showcase of the whole feature.
            if (attachment.onRod()) radius *= 1.35;
            RibbonRenderer.cameraQuad(pose, consumer,
                attachment.point().x(), attachment.point().y(), attachment.point().z(),
                camera.x(), camera.y(), camera.z(), radius,
                1f, attachment.onRod() ? 0.93f : 0.88f, 1f, alpha);
        }
    }

    /** Whether anything at all would be drawn, so the caller can skip the batch. */
    public static boolean any(List<ActiveLightningEffect> effects) {
        for (ActiveLightningEffect effect : effects) {
            StrikeAttachment attachment = effect.attachment();
            if (attachment != null && attachment.contested()) return true;
        }
        return false;
    }
}
