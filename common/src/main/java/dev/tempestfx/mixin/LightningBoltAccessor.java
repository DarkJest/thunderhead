package dev.tempestfx.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads vanilla's {@code visualOnly} flag, which has a setter but no getter.
 */
@Mixin(LightningBolt.class)
public interface LightningBoltAccessor {
    @Accessor("visualOnly")
    boolean tempestfx$isVisualOnly();
}
