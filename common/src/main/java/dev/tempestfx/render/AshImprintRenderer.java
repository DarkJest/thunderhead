package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.AshImprint;
import java.util.List;

/**
 * Draws the ash mark left by a direct hit.
 */
public final class AshImprintRenderer {
    /** Translucent charcoal pass. */
    public void renderDecal(List<AshImprint> imprints, PoseStack.Pose pose, VertexConsumer consumer,
                            float partialTick) {
        for (int index = 0; index < imprints.size(); index++) {
            AshImprint imprint = imprints.get(index);
            float alpha = imprint.ashOpacity(partialTick) * 0.86f;
            if (alpha <= 0.004f) continue;
            ShockwaveRenderer.groundQuad(pose, consumer,
                imprint.position().x(), imprint.position().y() + 0.015, imprint.position().z(),
                imprint.radius() * imprint.spread(partialTick), imprint.rotation(),
                0.09f, 0.085f, 0.08f, alpha);
        }
    }

    /** Additive ember pass, cooling from orange to a dull red. */
    public void renderEmbers(List<AshImprint> imprints, PoseStack.Pose pose, VertexConsumer consumer,
                             float partialTick) {
        for (int index = 0; index < imprints.size(); index++) {
            AshImprint imprint = imprints.get(index);
            float glow = imprint.emberGlow(partialTick);
            if (glow <= 0.004f) continue;
            float red = 1f;
            float green = 0.34f + 0.3f * glow;
            float blue = 0.1f + 0.16f * glow;
            ShockwaveRenderer.groundQuad(pose, consumer,
                imprint.position().x(), imprint.position().y() + 0.02, imprint.position().z(),
                imprint.radius() * imprint.spread(partialTick) * 0.94, imprint.rotation(),
                red, green, blue, glow * 0.7f);
        }
    }
}
