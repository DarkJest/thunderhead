package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tempestfx.client.TempestFxHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LightningBolt;

/**
 * Hides the vanilla bolt visual while Thunderhead draws its own, and defers to vanilla when it does
 * not.
 *
 * <p>The entity is untouched: it still ticks, damages, starts fires and powers lightning rods on the
 * server. Only the client geometry is replaced. The fallback matters because this renderer is
 * registered for {@code minecraft:lightning_bolt} for the whole session, so switching the mod off in
 * the config must not leave the world with invisible lightning.
 */
public final class EmptyLightningRenderer extends EntityRenderer<LightningBolt> {
    private final LightningBoltRenderer vanilla;

    public EmptyLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.vanilla = new LightningBoltRenderer(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LightningBolt entity) { return FxTextures.EMPTY; }

    @Override
    public boolean shouldRender(LightningBolt entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return !TempestFxHooks.drawsOwnLightning()
            && vanilla.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
    }

    @Override
    public void render(LightningBolt entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int packedLight) {
        if (TempestFxHooks.drawsOwnLightning()) return;
        vanilla.render(entity, yaw, partialTick, stack, buffers, packedLight);
    }
}
