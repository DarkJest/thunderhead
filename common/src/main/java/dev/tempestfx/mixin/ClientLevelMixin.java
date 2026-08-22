package dev.tempestfx.mixin;

import dev.tempestfx.client.TempestFxHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    /**
     * Catches a replicated bolt at the exact moment it appears.
     */
    @Inject(method = "addEntity", at = @At("TAIL"))
    private void tempestfx$onEntityAdded(Entity entity, CallbackInfo callbackInfo) {
        TempestFxHooks.onEntityAdded((ClientLevel) (Object) this, entity);
    }

    /**
     * Extends the transient client-side sky flash.
     */
    @Inject(method = "getSkyFlashTime", at = @At("RETURN"), cancellable = true, require = 0)
    private void tempestfx$extendSkyFlash(CallbackInfoReturnable<Integer> callbackInfo) {
        int requested = TempestFxHooks.skyFlashTicks();
        if (requested > callbackInfo.getReturnValueI()) callbackInfo.setReturnValue(requested);
    }
}
