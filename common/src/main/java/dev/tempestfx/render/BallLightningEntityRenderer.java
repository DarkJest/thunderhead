package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tempestfx.client.TempestFxHooks;
import dev.tempestfx.entity.BallLightning;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The entity dispatcher's view of ball lightning.
 *
 * <p>Its first job is to hand the sphere to the mod's own world pass, which draws it batched with
 * every other effect and composited the same way, so it survives a shader pack intact. Its second job
 * is to draw it here and now if that is not possible - the mod switched off, or the world pass out of
 * reach - because the entity exists on the server either way and a summoned sphere must not be
 * invisible.
 */
public final class BallLightningEntityRenderer extends EntityRenderer<BallLightning> {
    private final BallLightningRenderer geometry = new BallLightningRenderer();

    public BallLightningEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(BallLightning entity) { return FxTextures.SOFT_GLOW; }

    @Override
    public void render(BallLightning entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int packedLight) {
        float output = entity.output(partialTick);
        if (output <= 0.01f) return;

        BallLightningDraw sphere = BallLightningDraw.of(entity, partialTick);
        if (TempestFxHooks.deferBallLightning(sphere)) return;

        // The pose stack is already at the entity, so the camera has to be expressed in that space.
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        double cameraX = camera.x - sphere.x();
        double cameraY = camera.y - sphere.y();
        double cameraZ = camera.z - sphere.z();
        PoseStack.Pose pose = stack.last();

        // Straight into a frame the mod does not own, so the shader-pack compromises still apply:
        // only the arcs read correctly through a vanilla program, and the pack supplies the glow.
        ShaderPackProfile profile = TempestFxHooks.shaderPackProfile();
        if (profile.drawsWideGlow()) {
            geometry.renderGroundPool(sphere, pose, buffers.getBuffer(TempestRenderTypes.GLOW), 0, 0, 0);
            geometry.renderShell(sphere, pose, buffers.getBuffer(TempestRenderTypes.PLASMA), 0, 0, 0);
            geometry.renderCore(sphere, pose, buffers.getBuffer(TempestRenderTypes.GLOW), 0, 0, 0,
                cameraX, cameraY, cameraZ);
        }
        geometry.renderArcs(sphere, pose, buffers.getBuffer(TempestRenderTypes.BOLT), 0, 0, 0,
            cameraX, cameraY, cameraZ, profile);
    }
}
