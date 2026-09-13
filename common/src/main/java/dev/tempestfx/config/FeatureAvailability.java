package dev.tempestfx.config;

import java.util.Set;

/** UI explains effective dependencies instead of displaying enabled switches that cannot work. */
public final class FeatureAvailability {
    private static final Set<String> CINEMATIC = Set.of("shockwave", "air_distortion", "surface_ripple", "entity_discharge", "ash_imprint", "giant_roll");
    private static final Set<String> REALISTIC = Set.of("radiance", "channel_thunder", "surface_lighting", "shelter_attenuation");
    private static final Set<String> FLASHING = Set.of("return_strokes", "distant_bolts", "world_flash");
    private FeatureAvailability() {}
    public static String reason(String key, TempestConfig config) {
        if (CINEMATIC.contains(key) && config.realistic()) return "option.tempestfx.requires_cinematic";
        if (REALISTIC.contains(key) && !config.realistic()) return "option.tempestfx.requires_realistic";
        if (FLASHING.contains(key) && config.general.reducedFlashing) return "option.tempestfx.reduced_override";
        if ((key.equals("air_distortion") || key.equals("surface_ripple")) && !config.impact.shockwave) return "option.tempestfx.requires_shockwave";
        if (key.equals("surface_lighting") && !config.lighting.dynamicLighting) return "option.tempestfx.requires_lighting";
        return null;
    }
}
