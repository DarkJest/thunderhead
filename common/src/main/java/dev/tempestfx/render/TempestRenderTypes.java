package dev.tempestfx.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

/**
 * Render types owned by Thunderhead.
 *
 * <p>Types are on purpose few and shared: everything electrical goes through {@link #BOLT}, so the
 * channel, its forks, sparks, micro arcs, water spray and entity discharges all resolve to a single
 * additive draw call. Additive passes skip quad sorting because additive blending is order
 * independent; the translucent passes keep it.
 *
 * <p>Blending is written as separate colour and alpha functions so that one set of types works both
 * ways: drawn straight into the scene it behaves exactly as it always did, and drawn into the
 * compositor's own attachment it accumulates a premultiplied image the composite pass can apply in
 * one operation. The alpha channel is the difference. An additive layer contributes light but no
 * coverage, so it leaves alpha alone; a translucent layer contributes both, and its coverage then
 * attenuates whatever light was already accumulated underneath it, which is what occluding a glow
 * with a puff of smoke means. Nothing reads the alpha the passes write into the scene itself, so the
 * direct path is unaffected.
 */
public final class TempestRenderTypes extends RenderType {
    private static final int BUFFER_BYTES = 4096;
    /** {@code GL_LEQUAL}, the depth function the rest of the world pass uses. */
    private static final int LEQUAL = 515;

    /** Additive electricity: channels, forks, sparks, arcs, spray. */
    public static final RenderType BOLT = additive("tempestfx_bolt", FxTextures.RIBBON, true);
    /** Additive radial glow: impact flash, transient light pools, embers. */
    public static final RenderType GLOW = additive("tempestfx_glow", FxTextures.SOFT_GLOW, false);
    /** Additive procedural ring: shockwave front and surface ripple. */
    public static final RenderType RIPPLE = twoSampler("tempestfx_ripple", FxTextures.RIPPLE,
        TempestShaders::shockwaveShader, false);
    /** Additive overexposed burst at the impact point. */
    public static final RenderType FLASH = additive("tempestfx_flash", FxTextures.FLASH, false);
    /** Wide atmospheric haze around a bright event. */
    public static final RenderType ATMOSPHERE = additive("tempestfx_atmosphere", FxTextures.ATMOSPHERE, false);
    /** Additive curl-warped puff: a cloud region lit from the inside. */
    public static final RenderType CLOUD_LIGHT = twoSampler("tempestfx_cloud_light", FxTextures.SMOKE,
        TempestShaders::smokeShader, false);
    /** Additive turbulent puff: the plasma shell of ball lightning. */
    public static final RenderType PLASMA = additive("tempestfx_plasma", FxTextures.SMOKE, false);
    /** Translucent soft particulate: dust and ash flakes. */
    public static final RenderType SOFT = translucent("tempestfx_soft", FxTextures.SOFT_GLOW);
    /** Translucent procedural puffs: smoke and steam. */
    public static final RenderType SMOKE = twoSampler("tempestfx_smoke", FxTextures.SMOKE,
        TempestShaders::smokeShader, true);
    /** Translucent ground scar left by a direct hit. */
    public static final RenderType DECAL = translucent("tempestfx_decal", FxTextures.SCORCH);
    /** Additive pass over the same scar mask, used while the burn is still glowing. */
    public static final RenderType DECAL_EMBER = additive("tempestfx_decal_ember", FxTextures.SCORCH, false);
    /** Untextured solid fragments. */
    public static final RenderType DEBRIS = solid("tempestfx_debris");

    /**
     * The render type equivalent to a pass, for the fallback path and for the one effect the entity
     * dispatcher draws itself.
     */
    public static RenderType of(FxPass pass) {
        return switch (pass) {
            case DECAL -> DECAL;
            case DECAL_EMBER -> DECAL_EMBER;
            case RIPPLE -> RIPPLE;
            case ATMOSPHERE -> ATMOSPHERE;
            case CLOUD_LIGHT -> CLOUD_LIGHT;
            case FLASH -> FLASH;
            case GLOW -> GLOW;
            case BOLT -> BOLT;
            case PLASMA -> PLASMA;
            case DEBRIS -> DEBRIS;
            case SOFT -> SOFT;
            case SMOKE -> SMOKE;
        };
    }

    private TempestRenderTypes(String name, VertexFormat format, boolean sortOnUpload,
                               Runnable setup, Runnable clear) {
        super(name, format, VertexFormat.Mode.QUADS, BUFFER_BYTES, false, sortOnUpload, setup, clear);
    }

    private static RenderType additive(String name, ResourceLocation texture, boolean boltProfile) {
        return new TempestRenderTypes(name, DefaultVertexFormat.POSITION_TEX_COLOR, false, () -> {
            RenderSystem.setShader(boltProfile ? TempestShaders::boltShader : TempestShaders::particleShader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend();
            additiveBlend();
            depthTestedNoWrite();
            RenderSystem.disableCull();
        }, TempestRenderTypes::restoreRenderState);
    }

    /**
     * Type whose program reads a shape mask on {@code Sampler0} and the curl map on
     * {@code Sampler1}. Binding the mask as well as the noise is what keeps the vanilla fallback
     * correct: the stock program samples only {@code Sampler0} and still gets the right silhouette.
     */
    private static RenderType twoSampler(String name, ResourceLocation mask,
                                         Supplier<ShaderInstance> shader, boolean alphaBlend) {
        return new TempestRenderTypes(name, DefaultVertexFormat.POSITION_TEX_COLOR, alphaBlend, () -> {
            RenderSystem.setShader(shader);
            RenderSystem.setShaderTexture(0, mask);
            RenderSystem.setShaderTexture(1, FxTextures.CURL);
            RenderSystem.enableBlend();
            if (alphaBlend) {
                translucentBlend();
            } else {
                additiveBlend();
            }
            depthTestedNoWrite();
            RenderSystem.disableCull();
        }, TempestRenderTypes::restoreRenderState);
    }

    private static RenderType translucent(String name, ResourceLocation texture) {
        return new TempestRenderTypes(name, DefaultVertexFormat.POSITION_TEX_COLOR, true, () -> {
            RenderSystem.setShader(TempestShaders::particleShader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend();
            translucentBlend();
            depthTestedNoWrite();
            RenderSystem.disableCull();
        }, TempestRenderTypes::restoreRenderState);
    }

    private static RenderType solid(String name) {
        return new TempestRenderTypes(name, DefaultVertexFormat.POSITION_COLOR, true, () -> {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            translucentBlend();
            depthTestedNoWrite();
            RenderSystem.disableCull();
        }, TempestRenderTypes::restoreRenderState);
    }

    /** Light with no coverage: adds to the image, leaves the accumulated alpha untouched. */
    private static void additiveBlend() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    /** Coverage as well as colour, so the layer both occludes and accumulates. */
    private static void translucentBlend() {
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Depth state every world pass needs: tested against the scene, but not written.
     */
    private static void depthTestedNoWrite() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(LEQUAL);
        RenderSystem.depthMask(false);
    }

    /** Returns the pipeline to the state the rest of the frame expects. */
    public static void restoreRenderState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
