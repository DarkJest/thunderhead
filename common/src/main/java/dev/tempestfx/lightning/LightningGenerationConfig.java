package dev.tempestfx.lightning;

/**
 * Tuning for one procedural bolt.
 *
 * @param generations           midpoint subdivisions of the main channel; {@code 2^generations} segments
 * @param displacement          initial lateral offset amplitude in blocks
 * @param roughness             per-generation amplitude decay, {@code 0.35..0.85}
 * @param branchProbability     chance a candidate node spawns a fork
 * @param directionBias         how strongly a fork keeps the parent's direction
 * @param branchAngleRadians    nominal fork angle
 * @param branchLength          fork length as a fraction of the remaining channel
 * @param branchDecay           per-depth displacement and brightness decay
 * @param branchJitter          lateral wander applied to a fork tip
 * @param maxBranchDepth        recursion limit for forks
 * @param microBranchProbability chance a node grows a short stub that textures the channel
 * @param canopyBranches        near-horizontal channels spread across the cloud base
 * @param canopySpread          canopy length as a fraction of the channel height
 * @param forkBiasY             vertical bias added to every fork direction; negative hangs the fork
 *                              downward the way a cloud-to-ground leader branches, zero lets an
 *                              aerial channel spread flat
 * @param maxSegments           hard cap on generated segments, protecting the render budget
 */
public record LightningGenerationConfig(
    int generations,
    double displacement,
    double roughness,
    double branchProbability,
    double directionBias,
    double branchAngleRadians,
    double branchLength,
    double branchDecay,
    double branchJitter,
    int maxBranchDepth,
    double microBranchProbability,
    int canopyBranches,
    double canopySpread,
    double forkBiasY,
    int maxSegments
) {
    public LightningGenerationConfig {
        if (generations < 1 || generations > 10) throw new IllegalArgumentException("generations must be 1..10");
        if (!(displacement > 0)) throw new IllegalArgumentException("displacement must be positive");
        if (roughness < 0.35 || roughness > 0.85) throw new IllegalArgumentException("roughness must be 0.35..0.85");
        if (branchProbability < 0 || branchProbability > 1) throw new IllegalArgumentException("branchProbability must be 0..1");
        if (microBranchProbability < 0 || microBranchProbability > 1) {
            throw new IllegalArgumentException("microBranchProbability must be 0..1");
        }
        if (branchLength <= 0 || branchDecay <= 0 || branchDecay >= 1) throw new IllegalArgumentException("invalid branch decay");
        if (maxBranchDepth < 0 || maxBranchDepth > 4) throw new IllegalArgumentException("maxBranchDepth must be 0..4");
        if (canopyBranches < 0 || canopyBranches > 12) throw new IllegalArgumentException("canopyBranches must be 0..12");
        if (canopySpread < 0) throw new IllegalArgumentException("canopySpread must not be negative");
        if (forkBiasY < -1 || forkBiasY > 1) throw new IllegalArgumentException("forkBiasY must be -1..1");
        if (maxSegments < 32) throw new IllegalArgumentException("maxSegments must leave room for a channel");
    }

    public static LightningGenerationConfig high() {
        return new LightningGenerationConfig(7, 6.5, 0.56, 0.34, 0.64, Math.toRadians(38),
            0.34, 0.6, 0.36, 3, 0.2, 4, 0.55, -0.18, 3600);
    }

    /** Distance-scaled variant. Fewer generations, forks and stubs the further the strike is. */
    public LightningGenerationConfig forLod(LightningLod lod) {
        return switch (lod) {
            case FULL -> this;
            case MEDIUM -> withDetail(Math.min(7, generations), branchProbability * 0.7,
                microBranchProbability * 0.6, Math.min(2, maxBranchDepth), Math.min(3, canopyBranches), 3000);
            case DISTANT -> withDetail(Math.min(6, generations), branchProbability * 0.3,
                0, Math.min(1, maxBranchDepth), Math.min(2, canopyBranches), 1100);
            case ATMOSPHERIC -> withDetail(Math.min(4, generations), 0, 0, 0, 0, 128);
        };
    }

    private LightningGenerationConfig withDetail(int newGenerations, double forkProbability, double stubProbability,
                                                 int depth, int canopy, int segmentCap) {
        return new LightningGenerationConfig(newGenerations, displacement, roughness, forkProbability,
            directionBias, branchAngleRadians, branchLength, branchDecay, branchJitter, depth,
            stubProbability, canopy, canopySpread, forkBiasY, Math.max(32, Math.min(maxSegments, segmentCap)));
    }

    public LightningGenerationConfig withGenerations(int value) {
        return new LightningGenerationConfig(value, displacement, roughness, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopyBranches, canopySpread, forkBiasY, maxSegments);
    }

    public LightningGenerationConfig withBranchProbability(double value) {
        return new LightningGenerationConfig(generations, displacement, roughness, value, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopyBranches, canopySpread, forkBiasY, maxSegments);
    }

    /**
     * How fast displacement amplitude decays per generation.
     *
     * <p>This is the fine-scale knob rather than the overall one: the early generations set the
     * shape of the channel, the late ones set how sharply it kinks from segment to segment - and the
     * kink angle is what decides whether the joints between ribbon quads are visible.
     */
    public LightningGenerationConfig withRoughness(double value) {
        return new LightningGenerationConfig(generations, displacement, value, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopyBranches, canopySpread, forkBiasY, maxSegments);
    }

    public LightningGenerationConfig withDisplacement(double value) {
        return new LightningGenerationConfig(generations, value, roughness, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopyBranches, canopySpread, forkBiasY, maxSegments);
    }

    public LightningGenerationConfig withMaxBranchDepth(int value) {
        return new LightningGenerationConfig(generations, displacement, roughness, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, value, microBranchProbability,
            canopyBranches, canopySpread, forkBiasY, maxSegments);
    }

    /** How forks lean out of their parent: down for a descending leader, flat for an aerial channel. */
    public LightningGenerationConfig withForkBiasY(double value) {
        return new LightningGenerationConfig(generations, displacement, roughness, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopyBranches, canopySpread, value, maxSegments);
    }

    public LightningGenerationConfig withMaxSegments(int value) {
        return new LightningGenerationConfig(generations, displacement, roughness, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopyBranches, canopySpread, forkBiasY, value);
    }

    /** Scales the sky-spanning canopy, from none at 0 to a full cloud-base web. */
    public LightningGenerationConfig withSkySpread(double scale) {
        int canopy = (int) Math.round(Math.min(12, canopyBranches * scale));
        return new LightningGenerationConfig(generations, displacement, roughness, branchProbability, directionBias,
            branchAngleRadians, branchLength, branchDecay, branchJitter, maxBranchDepth, microBranchProbability,
            canopy, canopySpread * Math.max(0.2, scale), forkBiasY, maxSegments);
    }
}
