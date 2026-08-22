package dev.tempestfx.render.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Everything the mod's own passes are allowed to touch, remembered and put back.
 *
 * <p>A pass that binds its own framebuffer and program is only safe if the frame it interrupted cannot
 * tell. That is not a property of any one renderer, so it lives here rather than being repeated in
 * each of them: capture once on the way in, restore once on the way out, and no effect renderer
 * contains a line of GPU state management.
 *
 * <p>Both directions go through raw GL rather than {@code GlStateManager}, and that is the whole point.
 * The values are read from the driver because the frame belongs to whoever is rendering it, and a
 * shader pack may legitimately have left blending, depth writes or the draw framebuffer somewhere
 * else — or have locked the game's state cache so that setting them through it does nothing at all.
 * They are written back the same way, so what the game believes about the state is exactly what the
 * state is when the pass ends. Nothing is assumed and nothing is left to a cache.
 */
public final class FxStateGuard {
    /** The texture units the mod's passes bind samplers to. */
    private static final int UNITS = 2;

    private final ByteBuffer booleans = BufferUtils.createByteBuffer(4);
    private final int[] textures = new int[UNITS];

    private boolean held;
    private boolean stateHeld;

    private int drawFramebuffer;
    private int readFramebuffer;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private boolean blend;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private boolean depthTest;
    private int depthFunc;
    private boolean depthWrite;
    private boolean cull;
    private boolean scissor;
    private boolean maskRed;
    private boolean maskGreen;
    private boolean maskBlue;
    private boolean maskAlpha;
    private int activeTexture;
    private int program;
    private int vertexArray;

    /** Whether a capture is currently open, so an unbalanced call cannot corrupt the snapshot. */
    public boolean held() {
        return held;
    }

    /**
     * Remembers where drawing goes, and optionally the pipeline state as well.
     *
     * @param withState {@code true} for a pass that sets GL state with raw calls, which is then the
     *     only thing that can put it back. A pass that goes through {@code GlStateManager} must pass
     *     {@code false} and restore through it too, or the game's cache and the driver disagree.
     */
    public void capture(boolean withState) {
        RenderSystem.assertOnRenderThread();
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        viewportX = GlStateManager.Viewport.x();
        viewportY = GlStateManager.Viewport.y();
        viewportWidth = GlStateManager.Viewport.width();
        viewportHeight = GlStateManager.Viewport.height();
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        held = true;
        stateHeld = withState;
        if (!withState) return;
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        booleans.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, booleans);
        maskRed = booleans.get(0) != 0;
        maskGreen = booleans.get(1) != 0;
        maskBlue = booleans.get(2) != 0;
        maskAlpha = booleans.get(3) != 0;
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        for (int unit = 0; unit < UNITS; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(activeTexture);
    }

    /**
     * Puts every captured value back. Safe to call without a matching capture, and safe to call
     * twice: the second call does nothing.
     */
    public void restore() {
        if (!held) return;
        held = false;
        GL30.glBindVertexArray(vertexArray);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        if (!stateHeld) return;
        stateHeld = false;
        for (int unit = 0; unit < UNITS; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
        }
        GL13.glActiveTexture(activeTexture);
        GL20.glUseProgram(program);
        GL11.glColorMask(maskRed, maskGreen, maskBlue, maskAlpha);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        toggle(GL11.GL_BLEND, blend);
        GL11.glDepthFunc(depthFunc);
        GL11.glDepthMask(depthWrite);
        toggle(GL11.GL_DEPTH_TEST, depthTest);
        toggle(GL11.GL_CULL_FACE, cull);
        toggle(GL11.GL_SCISSOR_TEST, scissor);
    }

    private static void toggle(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
