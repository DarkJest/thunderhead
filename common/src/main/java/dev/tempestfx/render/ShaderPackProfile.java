package dev.tempestfx.render;

/**
 * What to draw when a third-party shader pack owns the frame.
 *
 * <p>Two things go wrong under a pack, and neither is a bug in the pack. The wide soft-glow quads are
 * shaped by a bundled program that a pack will not run, so drawn through a vanilla program they show
 * their mask as a visible disc. And additive geometry that reads correctly against vanilla's output
 * gets flattened by the pack's own exposure and tonemapping.
 *
 * <p>So under a pack the mod draws less, brighter and wider: the channel, the sparks and the debris,
 * with the wide halo work left to the pack, which is doing bloom anyway.
 *
 * <p>Wider because the bundled channel program spreads a soft cross-section past the edges of the
 * ribbon it is given. Without it the visible width collapses to the geometry, and the thin end of the
 * branch ladder - eleven per cent of trunk width - falls below a pixel and disappears. The geometry
 * is generated once and shared, so the compensation belongs here rather than in the generator.
 */
public enum ShaderPackProfile {
    /** Everything, shaped by the bundled programs. */
    FULL(1f, true, 1f, 1f, 0f),
    /** Channel and particulate only, brightened and widened to survive a pack. */
    CONSERVATIVE(2.1f, false, 1.75f, 3.4f, 0.55f);

    private final float emissiveScale;
    private final boolean wideGlow;
    private final float widthScale;
    private final float minWidthScale;
    private final float intensityLift;

    ShaderPackProfile(float emissiveScale, boolean wideGlow, float widthScale, float minWidthScale,
                      float intensityLift) {
        this.emissiveScale = emissiveScale;
        this.wideGlow = wideGlow;
        this.widthScale = widthScale;
        this.minWidthScale = minWidthScale;
        this.intensityLift = intensityLift;
    }

    public static ShaderPackProfile of(boolean shaderPackActive) {
        return shaderPackActive ? CONSERVATIVE : FULL;
    }

    /** Extra gain on the emissive channel layers. */
    public float emissiveScale() { return emissiveScale; }

    /** Whether the atmosphere, flash and glow passes are drawn at all. */
    public boolean drawsWideGlow() { return wideGlow; }

    /** Gain on every channel layer's width, replacing the bundled program's soft spread. */
    public float widthScale() { return widthScale; }

    /**
     * Gain on the distance-dependent width floor.
     *
     * <p>Applied to the floor rather than the width, so it lifts the thinnest segments most: a trunk
     * is already far above it and a fourth-level twig is not.
     */
    public float minWidthScale() { return minWidthScale; }

    /**
     * Compresses per-segment intensity toward full.
     *
     * <p>Width was only half the reason branches vanished under a pack. A fourth-level fork also
     * carries about a fifth of the trunk's intensity, and a fifth of an additive contribution is what
     * a tonemapper throws away first. Lifting the range costs the trunk almost nothing, because it is
     * already near one, and rescues the thin end.
     */
    public float liftIntensity(float intensity) {
        return intensityLift <= 0 ? intensity : intensity + (1f - intensity) * intensityLift;
    }
}
