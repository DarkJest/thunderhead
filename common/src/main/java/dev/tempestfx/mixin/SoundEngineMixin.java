package dev.tempestfx.mixin;

import dev.tempestfx.client.TempestFxHooks;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    /**
     * Drops exactly the two vanilla lightning clips Thunderhead replaces, and only while custom
     * thunder is enabled. Rain and every other weather sound keep playing untouched.
     *
     * <p>Both clips are started client-side by {@code LightningBolt#tick}, so suppressing them here
     * costs nothing in audible range: Thunderhead reacts to the same entity.
     */
    @Inject(method = "play", at = @At("HEAD"), cancellable = true, require = 0)
    private void tempestfx$suppressVanillaLightningAudio(SoundInstance sound, CallbackInfo callbackInfo) {
        if (TempestFxHooks.suppressVanillaSound(sound.getLocation())) callbackInfo.cancel();
    }
}
