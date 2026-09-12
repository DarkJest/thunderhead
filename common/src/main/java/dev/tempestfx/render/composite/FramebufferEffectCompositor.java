package dev.tempestfx.render.composite;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tempestfx.TempestFx;
import dev.tempestfx.render.gl.FxProgram;
import dev.tempestfx.render.gl.FxPrograms;
import dev.tempestfx.render.gl.FxStateGuard;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;

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
    private final FxStateGuard preparationGuard = new FxStateGuard();
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
    private String status = "not yet drawn";
    private SceneLightField lighting = SceneLightField.NONE;
    private boolean capturedLightDepth;
    private int captureDiagnostics;

    private ByteBufferBuilder quadBuffer;
    private VertexBuffer quad;

    private boolean disabled;
    private boolean worldPassOpen;
    private boolean pendingComposite;
    private int missedComposites;
    private int idleTicks;

    public FramebufferEffectCompositor(FxPrograms programs) {
        this.programs = programs;
    }

    @Override
    public boolean available() {
        return !disabled;
    }

    @Override
    public String status() { return status; }

    @Override
    public void lighting(SceneLightField field) { lighting = field == null ? SceneLightField.NONE : field; }

    @Override
    public boolean beginWorldPass() {
        capturedLightDepth = false;
        status = disabled ? "disabled" : "direct";
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
        viewport.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (viewport.get(0) != 0 || viewport.get(1) != 0
            || viewport.get(2) != main.width || viewport.get(3) != main.height) {
            status = "direct: viewport mismatch";
            return false;
        }

        preparationGuard.capture(true);
        boolean prepared = false;
        try {
            prepared = target.prepare(main.width, main.height);
            if (!prepared) {
                status = "direct: unavailable depth target";
                return false;
            }
        } catch (Throwable failure) {
            degrade("the effect framebuffer could not be prepared", failure);
            return false;
        } finally {
            // A failed attachment may already have bound our private FBO. Direct fallback MUST
            // receive the original target and state, not a colour-only FBO nobody composites.
            if (!prepared) preparationGuard.restore();
        }
        worldPassOpen = true;
        pendingComposite = true;
        idleTicks = 0;
        status = "isolated";
        return true;
    }

    @Override
    public void endWorldPass() {
        if (!worldPassOpen) return;
        worldPassOpen = false;
        try {
            capturedLightDepth = lighting.active() && target.captureDepth();
            target.detachDepth();
        } catch (Throwable failure) {
            degrade("the borrowed depth buffer could not be released", failure);
        } finally {
            preparationGuard.restore();
        }
    }

    @Override
    public void composite(DistortionField distortion) {
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
            boolean copied = (field.active() || capturedLightDepth)
                && sceneCopy.capture(main.frameBufferId, main.width, main.height);
            boolean refract = copied && field.active();
            if (Boolean.getBoolean("tempestfx.capture") && capturedLightDepth && captureDiagnostics++ < 5) {
                TempestFx.log().info("QA surface field: copy={} radius={} lights={}", copied, lighting.radius(), lighting.lights());
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
            GL11.glViewport(0, 0, main.viewWidth, main.viewHeight);
            drawFullscreen(program, copied ? sceneCopy.textureId() : target.colorTextureId(), refract, field,
                copied && capturedLightDepth);
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
        disabled = false;
        missedComposites = 0;
        status = "not yet drawn";
    }

    /** One quad, one program, one blend mode; the only pass that ever touches the scene image. */
    private void drawFullscreen(FxProgram program, int sceneTexture, boolean refract, DistortionField field, boolean lit) {
        if (quadBuffer == null) quadBuffer = new ByteBufferBuilder(4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        if (quad == null) quad = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
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
        FxStateGuard.useTextureFiltering(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
        program.setSampler("Sampler0", 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        FxStateGuard.useTextureFiltering(1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.colorTextureId());
        program.setSampler("Sampler1", 1);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        FxStateGuard.useTextureFiltering(2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, lit ? target.depthTextureId() : target.colorTextureId());
        program.setSampler("SceneDepth", 2);
        program.setVector4("LightControl", lit ? lighting.lights().size() : 0, lit ? lighting.radius() : 0, 0, 0);
        if (lit) {
            program.setMatrix("LightProjection", lighting.projection());
            program.setMatrix("LightInverseProjection", lighting.inverseProjection());
            for (int index = 0; index < lighting.lights().size(); index++) {
                var light = lighting.lights().get(index);
                program.setVector4("ChannelLight[" + index + "]", light.x(), light.y(), light.z(), light.power());
            }
        }

        // Clip space directly: the program declares no matrices, so nothing has to be pushed, saved
        // or restored to draw it.
        BufferBuilder builder = new BufferBuilder(quadBuffer, VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(-1f, -1f, 0f).setUv(0f, 0f).setColor(255, 255, 255, 255);
        builder.addVertex(1f, -1f, 0f).setUv(1f, 0f).setColor(255, 255, 255, 255);
        builder.addVertex(1f, 1f, 0f).setUv(1f, 1f).setColor(255, 255, 255, 255);
        builder.addVertex(-1f, 1f, 0f).setUv(0f, 1f).setColor(255, 255, 255, 255);
        MeshData mesh = builder.build();
        if (mesh == null) return;
        quad.bind();
        quad.upload(mesh);
        quad.draw();
        VertexBuffer.unbind();
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
        status = "disabled: " + reason;
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
        worldPassOpen = false;
        pendingComposite = false;
        capturedLightDepth = false;
        lighting = SceneLightField.NONE;
        idleTicks = 0;
        target.close();
        sceneCopy.close();
        if (quad != null) {
            quad.close();
            quad = null;
        }
        if (quadBuffer != null) {
            quadBuffer.close();
            quadBuffer = null;
        }
    }
}
