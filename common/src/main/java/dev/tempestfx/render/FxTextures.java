package dev.tempestfx.render;

import net.minecraft.resources.ResourceLocation;

/**
 * Original grayscale masks bundled with the mod.
 */
public final class FxTextures {
    private static final String NAMESPACE = "tempestfx";

    /** Cross-section profile shared by every channel, spark and arc ribbon. */
    public static final ResourceLocation RIBBON = fx("spark_gradient");
    /** Radial falloff used for glows, dust and embers. */
    public static final ResourceLocation SOFT_GLOW = fx("soft_glow");
    /** Noise-modulated puff used for smoke and steam. */
    public static final ResourceLocation SMOKE = fx("smoke_noise");
    /** Annulus with an inner wake, used for the shockwave and the surface ripple. */
    public static final ResourceLocation RIPPLE = fx("ripple_ring");
    /** Branching burn scar left by a direct hit. */
    public static final ResourceLocation SCORCH = fx("scorch_mask");
    /** Curl map: {@code rg} is an offset vector, {@code b} is density. Tileable. */
    public static final ResourceLocation CURL = fx("curl_noise");
    /** Overexposed point light with anisotropic rays, used for the impact flash. */
    public static final ResourceLocation FLASH = fx("flash_burst");
    /** Very wide, very soft falloff for atmospheric haze. */
    public static final ResourceLocation ATMOSPHERE = fx("atmos_glow");
    /** Fully transparent pixel that replaces the vanilla bolt texture. */
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/empty.png");

    private FxTextures() {}

    private static ResourceLocation fx(String name) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/fx/" + name + ".png");
    }
}
