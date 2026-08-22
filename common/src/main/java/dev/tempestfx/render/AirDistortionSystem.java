package dev.tempestfx.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ShockwaveEffect;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.render.composite.DistortionField;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Turns the strongest active wavefront into the six numbers that describe it on screen.
 *
 * <p>Only measurement lives here: the projection happens during the world pass, where the pose stack
 * still maps world coordinates into view space, and the result is handed to the compositor to apply
 * later. Nothing in this class owns a framebuffer, a program or a render target, which is why the
 * refraction no longer has a pipeline it only works under.
 *
 * <p>Analytic rather than a screen-sized vector texture on purpose. A wavefront is a circle, six
 * floats describe it exactly, and the composite shader can evaluate it per pixel for free - where a
 * distortion buffer would cost an attachment, a clear, geometry, and the bandwidth of reading it back
 * every frame, for a field that has a closed form.
 */
public final class AirDistortionSystem {
    /** Below this screen radius the ripple is smaller than the noise it would add. */
    private static final float MIN_SCREEN_RADIUS = 0.02f;

    private final Vector4f scratch = new Vector4f();
    private final float[] center = new float[2];
    private final float[] edge = new float[2];

    private DistortionField field = DistortionField.NONE;

    /** The field measured for this frame, or {@link DistortionField#NONE}. */
    public DistortionField field() {
        return field;
    }

    /** Forgets the current field, so a frame that draws nothing refracts nothing. */
    public void clear() {
        field = DistortionField.NONE;
    }

    /**
     * Measures the strongest active wavefront. Called from the world pass.
     */
    public void capture(List<ShockwaveEffect> shockwaves, PoseStack stack, Vec3d camera, float partialTick,
                        TempestConfig config) {
        field = DistortionField.NONE;
        if (!config.impact.airDistortion || config.impact.airDistortionStrength <= 0) return;
        if (shockwaves.isEmpty()) return;

        ShockwaveEffect best = null;
        float bestStrength = 0;
        for (ShockwaveEffect effect : shockwaves) {
            float candidate = ShockwaveRenderer.ringStrength(effect, partialTick)
                / (float) (1.0 + camera.distanceTo(effect.event().position()) * 0.02);
            if (candidate > bestStrength) { bestStrength = candidate; best = effect; }
        }
        if (best == null || bestStrength <= 0.01f) return;

        Minecraft minecraft = Minecraft.getInstance();
        float windowAspect = ScreenProjection.aspect(minecraft.getWindow().getWidth(),
            minecraft.getWindow().getHeight());
        Matrix4f pose = stack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        Vec3d position = best.event().position();

        scratch.set((float) position.x(), (float) best.surfaceY(), (float) position.z(), 1f);
        pose.transform(scratch);
        float viewX = scratch.x(), viewY = scratch.y(), viewZ = scratch.z(), viewW = scratch.w();

        float worldRadius = (float) best.radius(partialTick) * config.impact.shockwaveStrength;
        if (!ScreenProjection.toScreen(projection, scratch, viewX, viewY, viewZ, viewW, center)) return;
        if (!ScreenProjection.toScreen(projection, scratch, viewX + worldRadius, viewY, viewZ, viewW, edge)) return;

        float screenRadius = Math.abs(edge[0] - center[0]) * windowAspect;
        if (screenRadius < MIN_SCREEN_RADIUS) return;

        field = new DistortionField(center[0], center[1], screenRadius,
            bestStrength * config.impact.airDistortionStrength, windowAspect, best.age() + partialTick);
    }
}
