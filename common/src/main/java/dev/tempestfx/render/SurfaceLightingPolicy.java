package dev.tempestfx.render;

import dev.tempestfx.config.TempestConfig;

/** Scene shading is an optional replacement; direct fallback retains the legacy light pool. */
public final class SurfaceLightingPolicy {
    private SurfaceLightingPolicy() {}
    public static boolean useSurfacePath(TempestConfig config, boolean isolated, boolean hideFlashes) {
        return config.realistic() && config.lighting.dynamicLighting && config.lighting.surfaceLighting
            && config.compatibility.customShaders && config.compatibility.effectCompositor && isolated && !hideFlashes;
    }

    /** The fallback pulse remains stored while surface lighting consumes a frame. */
    public static int skyFlashTicks(int pending, TempestConfig config, boolean isolated) {
        return useSurfacePath(config, isolated, false) ? 0 : pending;
    }
}
