package dev.tempestfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tempestfx.client.TempestFxHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /**
     * Adds the damped pressure impulse to the world pose. {@code bobHurt} already owns the
     * shake-the-view role and runs before the level render.
     */
    @Inject(method = "bobHurt", at = @At("HEAD"), require = 0)
    private void tempestfx$applyPressureImpulse(PoseStack stack, float partialTick, CallbackInfo callbackInfo) {
        TempestFxHooks.applyCameraImpulse(stack, partialTick);
    }

    /**
     * Runs the effect composite right after the level render.
     *
     * <p>This is the one point in the frame that is the same for every rendering pipeline: the level
     * is finished, and so is anything a shader pack does to it, because a pack's own composite and
     * final passes happen while the level is being rendered and end by writing the scene into
     * Minecraft's main render target. It is also the window vanilla uses for its own post effects.
     *
     * <p>Optional by design: a pipeline where this is never reached loses the composite, not the
     * frame - the compositor notices and puts the effect back into the scene directly.
     */
    @Inject(
        method = "render",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"),
        require = 0)
    private void tempestfx$processPostLevel(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        TempestFxHooks.processPostLevel();
    }
}
