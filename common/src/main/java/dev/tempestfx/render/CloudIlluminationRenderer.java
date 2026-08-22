package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.CloudLightSource;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.List;

/**
 * Draws a cloud lit from the inside.
 *
 * <p>Three or four warped billboards per region, not a volume. The pass runs through the curl-warped
 * puff program, so each quad is a torn irregular blob rather than a radial gradient, and the
 * per-quad offset is smuggled in as a hair of variation in the red and blue channels - which the
 * program folds into its noise lookup, and which is far too small to see as a colour shift. That is
 * what stops a lit cloud from looking like a row of identical soft circles, at the cost of nothing:
 * no extra attachment, no read-back, no second pass.
 */
public final class CloudIlluminationRenderer {
    /** Billboards per region. Enough to break the silhouette, few enough to stay cheap. */
    private static final int LOBES = 4;
    /** Base colour of lit cloud: pale, cold and slightly blue. */
    private static final float[] COLD = { 0.66f, 0.79f, 1f };
    /** Where the palette goes for a positive discharge. */
    private static final float[] WARM = { 1f, 0.72f, 0.86f };
    /** Peak alpha of one lobe. Deliberately low: this accumulates additively across the lobes. */
    private static final float LOBE_ALPHA = 0.3f;
    /** Beyond this the region is too faint to be worth the quads. */
    private static final float CUTOFF = 0.004f;

    public void render(List<CloudLightSource> sources, PoseStack.Pose pose, VertexConsumer consumer,
                       Vec3d camera, float partialTick) {
        for (CloudLightSource source : sources) {
            float intensity = source.intensity(partialTick);
            if (intensity <= CUTOFF) continue;
            float radius = source.radius(partialTick);
            float warmth = source.warmth();
            float red = COLD[0] + (WARM[0] - COLD[0]) * warmth;
            float green = COLD[1] + (WARM[1] - COLD[1]) * warmth;
            float blue = COLD[2] + (WARM[2] - COLD[2]) * warmth;

            for (int lobe = 0; lobe < LOBES; lobe++) {
                long lobeSeed = StrikeSeed.derive(source.seed(), lobe);
                double offsetX = StrikeSeed.signed(lobeSeed, 0x1) * radius * 0.55;
                double offsetY = StrikeSeed.signed(lobeSeed, 0x2) * radius * 0.3;
                double offsetZ = StrikeSeed.signed(lobeSeed, 0x3) * radius * 0.55;
                double size = radius * (0.55 + StrikeSeed.unit(lobeSeed, 0x4) * 0.7);
                float alpha = Math.min(0.85f, intensity * LOBE_ALPHA);
                if (alpha <= CUTOFF) continue;
                // The noise decorrelation, hidden in the low bits of two channels.
                float jitter = (float) StrikeSeed.unit(lobeSeed, 0x5) * 0.03f;
                RibbonRenderer.cameraQuad(pose, consumer,
                    source.position().x() + offsetX,
                    source.position().y() + offsetY,
                    source.position().z() + offsetZ,
                    camera.x(), camera.y(), camera.z(), size,
                    Math.min(1f, red + jitter), green, Math.max(0f, blue - jitter), alpha);
            }
        }
    }
}
