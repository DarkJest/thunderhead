package dev.tempestfx.render;

import dev.tempestfx.entity.BallLightning;
import net.minecraft.util.Mth;

/**
 * Everything the sphere's geometry needs, read off the entity once per frame.
 *
 * <p>Ball lightning is a real entity, so it is offered to the client by the entity dispatcher rather
 * than by the effect manager. Snapshotting it into plain numbers is what lets it be drawn in the
 * mod's own world pass, batched with every other effect and composited the same way, instead of going
 * through the shared entity buffer into a frame the mod does not own.
 */
public record BallLightningDraw(double x, double y, double z, double surfaceBelow,
                                float radius, float output, long seed, float age) {
    public static BallLightningDraw of(BallLightning entity, float partialTick) {
        return new BallLightningDraw(
            Mth.lerp(partialTick, entity.xOld, entity.getX()),
            Mth.lerp(partialTick, entity.yOld, entity.getY()),
            Mth.lerp(partialTick, entity.zOld, entity.getZ()),
            entity.surfaceBelow(),
            entity.renderRadius(partialTick),
            entity.output(partialTick),
            entity.visualSeed(),
            entity.tickCount + partialTick);
    }
}
