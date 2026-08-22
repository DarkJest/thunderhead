package dev.tempestfx.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.render.composite.LightShaftField;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Finds the brightest channel on screen and reports where it is.
 *
 * <p>Measurement only, exactly like {@link AirDistortionSystem}: the projection has to happen during
 * the world pass while the pose stack still maps world to view, and the shafts themselves are drawn
 * later by the bloom chain, which owns no matrices. Nothing here allocates a target or a program.
 *
 * <p>The channel is sampled at its midpoint rather than at its impact, because a shaft fans out from
 * the length of the thing throwing it and the middle of a bolt is the best single point to stand in
 * for that.
 */
public final class LightShaftSystem {
    /** Below this the channel is not bright enough for a shaft to be anything but noise. */
    private static final float MIN_OUTPUT = 0.25f;
    /** A shaft from behind the camera is a smear from a point that is not there. */
    private static final float MAX_STRENGTH = 0.85f;

    private final Vector4f scratch = new Vector4f();
    private final float[] screen = new float[2];

    private LightShaftField field = LightShaftField.NONE;

    public LightShaftField field() { return field; }

    public void clear() { field = LightShaftField.NONE; }

    /**
     * Measures the strongest channel this frame. Called from the world pass.
     *
     * @param channels every live bolt, ground and aerial alike; the brightest wins
     */
    public void capture(List<ActiveLightningEffect> channels, List<ActiveLightningEffect> aerial,
                        PoseStack stack, Vec3d camera, float partialTick, TempestConfig config) {
        field = LightShaftField.NONE;
        if (!config.lighting.lightShafts || config.lighting.bloomStrength <= 0) return;

        ActiveLightningEffect best = null;
        float bestOutput = 0;
        for (List<ActiveLightningEffect> list : List.of(channels, aerial)) {
            for (ActiveLightningEffect effect : list) {
                float output = effect.brightness(partialTick, config.lightning.flicker,
                    config.general.reducedFlashing);
                // Distance thins a shaft the way it thins everything else, and keeps a bolt on the
                // horizon from smearing the whole frame.
                float weighted = output / (float) (1.0 + camera.distanceTo(effect.event().position()) * 0.012);
                if (weighted > bestOutput) { bestOutput = weighted; best = effect; }
            }
        }
        if (best == null || bestOutput <= MIN_OUTPUT) return;

        Vec3d anchor = midpoint(best);
        Matrix4f pose = stack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        scratch.set((float) anchor.x(), (float) anchor.y(), (float) anchor.z(), 1f);
        pose.transform(scratch);
        if (!ScreenProjection.toScreen(projection, scratch, scratch.x(), scratch.y(), scratch.z(),
            scratch.w(), screen)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        float aspect = ScreenProjection.aspect(minecraft.getWindow().getWidth(),
            minecraft.getWindow().getHeight());
        float strength = Math.min(MAX_STRENGTH, bestOutput * config.lighting.lightShaftStrength);
        field = new LightShaftField(screen[0], screen[1], strength, aspect);
    }

    /** The middle of the channel, which is the point a shaft appears to radiate from. */
    private static Vec3d midpoint(ActiveLightningEffect effect) {
        var segments = effect.segments();
        if (segments.isEmpty()) return effect.event().position();
        return segments.get(segments.size() / 2).start();
    }
}
