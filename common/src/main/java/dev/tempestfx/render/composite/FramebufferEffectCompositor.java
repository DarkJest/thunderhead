package dev.tempestfx.render.composite;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.tempestfx.TempestFx;
import dev.tempestfx.render.gl.FxProgram;
import dev.tempestfx.render.gl.FxPrograms;
import dev.tempestfx.render.gl.FxStateGuard;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Draws the effect into a framebuffer of the mod's own, then applies it to the finished frame.
 *
 * <p>This is the whole shader-pack compatibility story, and it contains no shader-pack code. The
 * world pass writes emissive colour and coverage into one private half-float attachment, depth-tested
 * against whatever the frame's own depth buffer happens to be; the composite pass reads that
 * attachment back after Minecraft — and any pack — has finished producing a scene image, and applies
 * it with premultiplied blending. Neither half asks who rendered the scene.
 *
 * <p>The accumulation is arranged so that the result is the same image direct rendering would have
 * produced. Additive layers contribute {@code colour × alpha} and no coverage; translucent layers
 * contribute premultiplied colour and accumulate coverage, which also attenuates the additive light
 * already underneath them. The composite then evaluates {@code scene × (1 − coverage) + colour},
 * which is exactly the "over" operator the world pass would have applied one layer at a time. In
 * vanilla the pixels are unchanged; under a pack they are the vanilla pixels rather than the pack's
 * idea of them.
 *
 * <p>Every path out of this class is survivable. If the depth buffer cannot be borrowed, the programs
 * failed to compile, or anything at all throws, the world pass is told to draw straight into the scene
 * the way it did before — degraded, never missing.
 */
public final class FramebufferEffectCompositor implements EffectCompositor {
    private static final int IDLE_TICKS_BEFORE_RELEASE = 400;
    /**
     * How many isolated world passes may go uncomposited before the isolation is abandoned.
     *
     * <p>If the composite hook is never reached — an unexpected render pipeline, a mixin that did not
     * apply — an isolated effect would be drawn into a framebuffer nobody ever reads, which is worse
     * than not isolating it at all. A handful of frames is enough to tell, and the answer cannot
     * change later in the session.
     */
    private static final int MISSED_COMPOSITES_BEFORE_GIVING_UP = 3;

    private final FxPrograms programs;
    private final EffectRenderTarget target = new EffectRenderTarget();
    private final SceneColorCopy sceneCopy = new SceneColorCopy();
    private final FxStateGuard guard = new FxStateGuard();
    private final BloomChain bloom;
    private final FullscreenQuad quad = new FullscreenQuad();

    private boolean disabled;
    private boolean worldPassOpen;
    private boolean pendingComposite;
    private int missedComposites;
    private int idleTicks;

    /**
     * How much of a quarter-resolution blurred image one unit of user strength is worth.
     *
     * <p>A gaussian spreads the energy it is given, so the peak of the blurred bloom is a fraction of
     * what went into it. Adding it back at face value is barely perceptible, which is precisely the
     * report this constant exists to answer.
     */
    private static final float GLOW_GAIN = 2.2f;

    /** Strength the glow is added at; 0 skips the chain outright. */
    private float glowStrength = 1f;

    public FramebufferEffectCompositor(FxPrograms programs) {
        this.programs = programs;
        this.bloom = new BloomChain(programs);
    }

    /**
     * Sets how hard the glow is applied.
     *
     * <p>Pushed in rather than read from the config here, because this class owns GPU resources and
     * nothing else, and a compositor that reads user settings would be two things.
     */
    public void setGlowStrength(float value) {
        glowStrength = Math.max(0, value);
    }

    @Override
    public boolean available() {
        return !disabled;
    }

    @Override
    public boolean beginWorldPass() {
        if (disabled || worldPassOpen || !RenderSystem.isOnRenderThread()) return false;
        if (pendingComposite && ++missedComposites > MISSED_COMPOSITES_BEFORE_GIVING_UP) {
            degrade("the composite pass is never reached in this pipeline", null);
            return false;
        }
        // Without the program there is nothing that could bring the effect back onto the screen.
        if (programs.get(FxPrograms.Kind.COMPOSITE) == null) return false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return false;
        // Rendering straight to the window leaves no depth attachment to borrow, and nothing this
        // pass could be isolated from either.
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0) return false;
        // The effect attachment is sized to the buffer the composite will write into, so the world
        // pass has to be running at that resolution too. It always is - every target in the level
        // render is sized to the main one - but a pipeline rendering the world at some other scale
        // would stretch the effect across the frame, and drawing directly is the better answer.
        if (GlStateManager.Viewport.width() != main.width
            || GlStateManager.Viewport.height() != main.height) {
            return false;
        }

        try {
            if (!target.prepare(main.width, main.height)) return false;
        } catch (Throwable failure) {
            degrade("the effect framebuffer could not be prepared", failure);
            return false;
        }
        worldPassOpen = true;
        pendingComposite = true;
        idleTicks = 0;
        return true;
    }

    @Override
    public void endWorldPass() {
        if (!worldPassOpen) return;
        worldPassOpen = false;
        try {
            target.detachDepth();
        } catch (Throwable failure) {
            degrade("the borrowed depth buffer could not be released", failure);
        }
    }

    @Override
    public void composite(DistortionField distortion, LightShaftField shafts) {
        if (!pendingComposite) return;
        pendingComposite = false;
        missedComposites = 0;
        if (disabled || !RenderSystem.isOnRenderThread()) return;
        FxProgram program = programs.get(FxPrograms.Kind.COMPOSITE);
        if (program == null) return;

        guard.capture(true);
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            DistortionField field = distortion == null ? DistortionField.NONE : distortion;
            boolean refract = field.active()
                && sceneCopy.capture(main.frameBufferId, main.width, main.height);
            // The glow is produced first, from the mod's own attachment and nothing else, while the
            // scene image is still untouched.
            int glow = bloom.run(target.colorTextureId(), main.width, main.height, glowStrength,
                shafts == null ? LightShaftField.NONE : shafts);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
            GL11.glViewport(0, 0, main.viewWidth, main.viewHeight);
            drawFullscreen(program, refract ? sceneCopy.textureId() : target.colorTextureId(), refract,
                field, glow);
        } catch (Throwable failure) {
            degrade("the composite pass failed", failure);
        } finally {
            guard.restore();
        }
    }

    @Override
    public void tick(boolean busy) {
        if (busy) {
            idleTicks = 0;
            return;
        }
        if (target.allocated() && ++idleTicks > IDLE_TICKS_BEFORE_RELEASE) release();
    }

    @Override
    public void close() {
        release();
    }

    /** One quad, one program, one blend mode; the only pass that ever touches the scene image. */
    private void drawFullscreen(FxProgram program, int sceneTexture, boolean refract, DistortionField field,
                                int glowTexture) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        // Premultiplied over: scene x (1 - coverage) + colour.
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // The scene's alpha channel belongs to whoever presents the frame; leave it alone.
        GL11.glColorMask(true, true, true, false);

        program.bind();
        program.setVector4("TempestRipple", field.centerX(), field.centerY(), field.radius(),
            refract ? field.strength() : 0f);
        program.setVector4("TempestRippleShape", field.aspect(), field.phase(), 0f, 0f);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
        program.setSampler("Sampler0", 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.colorTextureId());
        program.setSampler("Sampler1", 1);
        // A glow the chain could not produce is simply absent: the shader is told zero strength and
        // never reads the sampler, so there is nothing to bind and nothing to go wrong.
        program.setVector4("TempestGlow", glowTexture >= 0 ? glowStrength * GLOW_GAIN : 0f, 0f, 0f, 0f);
        if (glowTexture >= 0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glowTexture);
            program.setSampler("Sampler2", 2);
        }

        quad.draw();
        FxProgram.unbind();
    }

    /**
     * Turns the isolated path off for the rest of the session.
     *
     * <p>Permanent on purpose: a pipeline that cannot support the pass at frame one will not start
     * supporting it at frame two, and retrying every frame would trade a working degraded effect for
     * a flickering one.
     */
    private void degrade(String reason, Throwable failure) {
        if (disabled) return;
        disabled = true;
        worldPassOpen = false;
        pendingComposite = false;
        if (failure == null) {
            TempestFx.log().warn("Effect compositor off: {}; drawing straight into the scene instead", reason);
        } else {
            TempestFx.log().warn("Effect compositor off: {}; drawing straight into the scene instead",
                reason, failure);
        }
        release();
    }

    private void release() {
        if (!RenderSystem.isOnRenderThread()) return;
        if (guard.held()) guard.restore();
        worldPassOpen = false;
        pendingComposite = false;
        idleTicks = 0;
        target.close();
        sceneCopy.close();
        bloom.close();
        quad.close();
    }
}
