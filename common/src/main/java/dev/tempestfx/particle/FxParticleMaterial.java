package dev.tempestfx.particle;

/** Simulation and render family of a particle. Drives fade curve, geometry and render pass. */
public enum FxParticleMaterial {
    /** Ballistic electrical spark, drawn as a velocity-stretched streak. */
    SPARK,
    /** Short-lived arc fragment near the impact or on a charged entity. */
    MICRO_ARC,
    /** Soft, expanding, noise-textured smoke. */
    SMOKE,
    /** Ground dust lifted by the pressure wave. */
    DUST,
    /** Solid fragment tinted from the surface map colour. */
    DEBRIS,
    /** Water droplet or spray. */
    WATER,
    /** Vapour produced when a hot impact meets a wet surface. */
    STEAM,
    /** Slow, drifting, cooling ash flake. */
    ASH,
    /** Glowing ember that cools from orange to dark. */
    EMBER;

    public boolean streak() { return this == SPARK || this == MICRO_ARC || this == WATER; }

    public boolean textured() { return this == SMOKE || this == STEAM || this == ASH; }

    public boolean bounces() { return this == SPARK || this == DEBRIS || this == EMBER; }
}
