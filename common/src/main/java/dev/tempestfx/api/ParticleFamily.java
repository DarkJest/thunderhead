package dev.tempestfx.api;

import dev.tempestfx.particle.FxParticleMaterial;

/**
 * The kinds of debris a strike throws up, selectable per strike.
 *
 * <p>A selection is a filter, not an instruction: asking for {@link #WATER_SPRAY} on dry ground
 * still produces nothing, because what the ground is made of decides what can come off it. The
 * player's own particle toggles and budget apply on top, so this can narrow what is emitted and
 * never widen it past what they allowed.
 */
public enum ParticleFamily {
    SPARKS(FxParticleMaterial.SPARK),
    MICRO_ARCS(FxParticleMaterial.MICRO_ARC),
    SMOKE(FxParticleMaterial.SMOKE),
    STEAM(FxParticleMaterial.STEAM),
    DUST(FxParticleMaterial.DUST),
    DEBRIS(FxParticleMaterial.DEBRIS),
    ASH(FxParticleMaterial.ASH),
    EMBERS(FxParticleMaterial.EMBER),
    WATER_SPRAY(FxParticleMaterial.WATER);

    private final FxParticleMaterial material;

    ParticleFamily(FxParticleMaterial material) { this.material = material; }

    public FxParticleMaterial material() { return material; }
}
