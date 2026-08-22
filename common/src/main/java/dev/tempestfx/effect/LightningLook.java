package dev.tempestfx.effect;

import dev.tempestfx.api.LightningStyle;
import dev.tempestfx.config.TempestConfig;

/**
 * The four look values one strike is drawn with, and where they came from.
 *
 * <p>Accessibility is on purpose not in here. Reduced flashing, flicker and the camera settings
 * are read straight from the configuration wherever they are needed, so no style and no integration
 * can route around them - see {@link dev.tempestfx.lightning.LightningEnvelope#brightness}.
 *
 * @param thickness   channel width multiplier
 * @param branchCount fork budget, in the same units as {@code config.lightning.branchCount}
 * @param scale       how far the channel reaches into the sky
 * @param coldTint    how blue the glow layers are
 */
public record LightningLook(float thickness, float branchCount, float scale, float coldTint) {
    /**
     * The mod's own defaults, captured once.
     */
    private static final LightningLook STOCK = fromConfig(new TempestConfig().validate());

    /** @param style the strike's own look, or {@code null} to use the player's configuration */
    public static LightningLook resolve(TempestConfig config, LightningStyle style) {
        if (style == null) return fromConfig(config);
        return new LightningLook(
            STOCK.thickness * style.thickness(),
            STOCK.branchCount * style.branchiness(),
            STOCK.scale * style.scale(),
            STOCK.coldTint * style.coldTint());
    }

    private static LightningLook fromConfig(TempestConfig config) {
        return new LightningLook(config.lightning.thickness, config.lightning.branchCount,
            config.lightning.scale, config.lightning.coldTint);
    }
}
