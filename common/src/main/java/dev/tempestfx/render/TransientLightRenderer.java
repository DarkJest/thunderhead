package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.effect.TransientLightSystem.TransientPointLight;
import java.util.List;

/**
 * Draws the additive light pool of a transient impact light.
 */
public final class TransientLightRenderer {
    public void render(List<TransientPointLight> lights, PoseStack.Pose pose, VertexConsumer consumer,
                       float partialTick) {
        for (int index = 0; index < lights.size(); index++) {
            TransientPointLight light = lights.get(index);
            float intensity = light.intensity(partialTick);
            if (intensity <= 0.004f) continue;
            float alpha = Math.min(0.75f, intensity * 0.42f);
            ShockwaveRenderer.groundQuad(pose, consumer,
                light.position().x(), light.position().y() + 0.02, light.position().z(),
                light.radius(partialTick), 0f,
                0.72f, 0.82f, 1f, alpha);
        }
    }
}
