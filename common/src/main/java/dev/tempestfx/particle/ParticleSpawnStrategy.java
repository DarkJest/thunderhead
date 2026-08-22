package dev.tempestfx.particle;

import dev.tempestfx.api.LightningStrikeFxEvent;

/** Decides which particle families a strike produces. Swappable per environment or per pack. */
public interface ParticleSpawnStrategy {
    /**
     * @param budget particles the LOD allows for this strike; families should be sized as fractions
     *               of it so a distant strike thins out evenly instead of losing whole families
     * @param sink   pooled allocator that also enforces the budget and the per-family config toggles
     */
    void spawn(LightningStrikeFxEvent event, int budget, FxParticleSink sink);
}
