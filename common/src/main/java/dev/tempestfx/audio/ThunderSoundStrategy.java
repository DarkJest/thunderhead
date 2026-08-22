package dev.tempestfx.audio;

import java.util.List;

/** Chooses which clips a strike is built from. Replaceable so packs can restyle the storm. */
public interface ThunderSoundStrategy {
    /**
     * @param distance listener distance in blocks
     * @param seed     replicated strike seed, so every client picks the same clips
     * @param intensity strike intensity, {@code 1} for a natural bolt
     * @return ordered layers, never empty
     */
    List<ThunderLayer> select(double distance, long seed, float intensity);
}
