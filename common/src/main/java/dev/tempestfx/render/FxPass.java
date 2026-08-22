package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tempestfx.render.gl.FxPrograms;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's render passes, described once.
 *
 * <p>Passes are on purpose few and shared: everything electrical goes through {@link #BOLT}, so the
 * channel, its forks, sparks, micro arcs, water spray and entity discharges all resolve to a single
 * additive draw. Additive passes skip quad sorting because additive blending is order independent;
 * the translucent passes keep it.
 *
 * <p>One description, two ways of executing it. The mod's own renderer reads the fields here directly;
 * the fallback maps each pass onto an equivalent Minecraft {@code RenderType}. Nothing that emits
 * geometry knows which is in use.
 */
public enum FxPass {
    /** Translucent ground scar left by a direct hit. */
    DECAL(FxPrograms.Kind.PARTICLE, FxTextures.SCORCH, null, Blend.TRANSLUCENT, true),
    /** Additive pass over the same scar mask, used while the burn is still glowing. */
    DECAL_EMBER(FxPrograms.Kind.PARTICLE, FxTextures.SCORCH, null, Blend.ADDITIVE, false),
    /** Additive procedural ring: shockwave front and surface ripple. */
    RIPPLE(FxPrograms.Kind.SHOCKWAVE, FxTextures.RIPPLE, FxTextures.CURL, Blend.ADDITIVE, false),
    /** Wide atmospheric haze around a bright event. */
    ATMOSPHERE(FxPrograms.Kind.PARTICLE, FxTextures.ATMOSPHERE, null, Blend.ADDITIVE, false),
    /**
     * Cloud lit from the inside. Additive, and drawn by the curl-warped puff program so a lit region
     * is a torn irregular volume rather than a soft circle.
     */
    CLOUD_LIGHT(FxPrograms.Kind.SMOKE, FxTextures.SMOKE, FxTextures.CURL, Blend.ADDITIVE, false),
    /** Additive overexposed burst at the impact point. */
    FLASH(FxPrograms.Kind.PARTICLE, FxTextures.FLASH, null, Blend.ADDITIVE, false),
    /** Additive radial glow: impact flash, transient light pools, embers. */
    GLOW(FxPrograms.Kind.PARTICLE, FxTextures.SOFT_GLOW, null, Blend.ADDITIVE, false),
    /** Additive electricity: channels, forks, sparks, arcs, spray. */
    BOLT(FxPrograms.Kind.BOLT, FxTextures.RIBBON, null, Blend.ADDITIVE, false),
    /** Additive turbulent puff: the plasma shell of ball lightning. */
    PLASMA(FxPrograms.Kind.PARTICLE, FxTextures.SMOKE, null, Blend.ADDITIVE, false),
    /** Untextured solid fragments. */
    DEBRIS(FxPrograms.Kind.SOLID, null, null, Blend.TRANSLUCENT, true),
    /** Translucent soft particulate: dust and ash flakes. */
    SOFT(FxPrograms.Kind.PARTICLE, FxTextures.SOFT_GLOW, null, Blend.TRANSLUCENT, true),
    /** Translucent procedural puffs: smoke and steam. */
    SMOKE(FxPrograms.Kind.SMOKE, FxTextures.SMOKE, FxTextures.CURL, Blend.TRANSLUCENT, true);

    /**
     * How a pass combines with what is already there.
     *
     * <p>Both are written as separate colour and alpha functions, so one description works whether the
     * pass is drawn straight into the scene or accumulated in the compositor's own attachment. The
     * alpha channel is the difference: an additive layer contributes light but no coverage, so it
     * leaves alpha alone; a translucent layer contributes both, and its coverage then attenuates
     * whatever light was accumulated underneath it — which is what occluding a glow with a puff of
     * smoke means. Nothing reads the alpha written into the scene itself, so drawing directly is
     * unaffected.
     */
    public enum Blend {
        ADDITIVE, TRANSLUCENT
    }

    private final FxPrograms.Kind program;
    private final ResourceLocation texture0;
    private final ResourceLocation texture1;
    private final Blend blend;
    private final boolean sorted;

    FxPass(FxPrograms.Kind program, ResourceLocation texture0, ResourceLocation texture1, Blend blend,
           boolean sorted) {
        this.program = program;
        this.texture0 = texture0;
        this.texture1 = texture1;
        this.blend = blend;
        this.sorted = sorted;
    }

    public FxPrograms.Kind program() {
        return program;
    }

    /** Shape mask, or {@code null} for an untextured pass. */
    public ResourceLocation texture0() {
        return texture0;
    }

    /**
     * Second sampler, or {@code null}.
     *
     * <p>The two-sampler programs read a shape mask on the first and the curl map on the second even
     * though they compute their own shape. Binding the mask as well as the noise is what keeps the
     * fallback correct: Minecraft's stock program samples only the first and still gets the right
     * silhouette.
     */
    public ResourceLocation texture1() {
        return texture1;
    }

    public Blend blend() {
        return blend;
    }

    public boolean sorted() {
        return sorted;
    }

    public VertexFormat format() {
        return program.format();
    }
}
