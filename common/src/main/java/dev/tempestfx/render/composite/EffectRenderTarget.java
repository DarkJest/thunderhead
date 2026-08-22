package dev.tempestfx.render.composite;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/**
 * The framebuffer the effect is drawn into, and the one piece of the frame it borrows.
 *
 * <p>Colour is the mod's own: one {@code RGBA16F} attachment holding premultiplied emissive colour
 * in {@code RGB} and coverage in {@code A}. Nothing else writes to it, no pipeline knows it exists,
 * and it is never resolved, tonemapped or exposed by anybody but the mod's composite pass. That is
 * what makes the effect look the same under every shader loader.
 *
 * <p>Depth is <em>not</em> the mod's own, and cannot be. An effect has to be occluded by terrain,
 * water, entities and particles, which means it has to be depth-tested against the depth buffer of
 * whoever rendered them — and under a shader pack that buffer belongs to the pack, at an address no
 * public API reports. So instead of guessing, the target asks the driver: whatever depth attachment
 * the currently bound framebuffer has, this one borrows for the duration of a single pass, read-only
 * and with depth writes off. The borrow is completely blind to which pipeline created it, works the
 * same in vanilla, and needs no shader-pack API.
 *
 * <p>Two properties keep the borrow safe. The attachment is queried and re-attached every frame, so
 * a pipeline that recreates its buffers — a resize, a render-scale change, a shader pack being
 * switched on — is followed automatically rather than leaving a dangling reference. And it is
 * detached again at the end of the pass, so the mod never holds a foreign resource across frames.
 *
 * <p>Every call is raw GL. The mod's passes deliberately leave no trace in Minecraft's state cache,
 * so that restoring the driver state is enough to leave the frame exactly as it was found.
 */
final class EffectRenderTarget implements AutoCloseable {
    /** Both the depth-only and the packed depth-stencil attachment points are worth trying. */
    private static final int[] DEPTH_ATTACHMENTS = { GL30.GL_DEPTH_ATTACHMENT, GL30.GL_DEPTH_STENCIL_ATTACHMENT };

    private int framebuffer = -1;
    private int colorTexture = -1;
    private int width;
    private int height;

    /** Attachment point the borrowed depth buffer sits on, or {@code -1} when nothing is borrowed. */
    private int borrowedPoint = -1;
    private boolean borrowedRenderbuffer;

    int colorTextureId() {
        return colorTexture;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean allocated() {
        return framebuffer > -1;
    }

    /**
     * Sizes the target, borrows the bound framebuffer's depth buffer, binds for writing and clears
     * the colour attachment.
     *
     * <p>Depth is never cleared: it is somebody else's scene depth and the whole point of borrowing
     * it. Depth writes are switched off here as well, so no effect renderer can corrupt it even by
     * accident.
     *
     * @return {@code false} when the frame offers nothing to depth-test against, or the resulting
     *     framebuffer is not something this driver can render to
     */
    boolean prepare(int targetWidth, int targetHeight) {
        RenderSystem.assertOnRenderThread();
        int depthName = 0;
        int depthPoint = -1;
        boolean renderbuffer = false;
        for (int attachment : DEPTH_ATTACHMENTS) {
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE && type != GL30.GL_RENDERBUFFER) continue;
            int name = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (name <= 0) continue;
            depthName = name;
            depthPoint = attachment;
            renderbuffer = type == GL30.GL_RENDERBUFFER;
            break;
        }
        if (depthPoint == -1) return false;
        if (!ensureColor(targetWidth, targetHeight)) return false;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        if (renderbuffer) {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, depthPoint, GL30.GL_RENDERBUFFER, depthName);
        } else {
            // Target-agnostic on purpose: a pipeline is free to use a multisample or array texture,
            // and glFramebufferTexture attaches any of them without the mod having to ask which.
            GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, depthPoint, depthName, 0);
        }
        borrowedPoint = depthPoint;
        borrowedRenderbuffer = renderbuffer;

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            // Most likely a multisampled depth buffer against a single-sampled colour attachment.
            detachDepth();
            return false;
        }

        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        return true;
    }

    /** Gives the borrowed depth buffer back. The target stays complete: colour alone is enough. */
    void detachDepth() {
        if (borrowedPoint == -1 || framebuffer < 0) return;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        if (borrowedRenderbuffer) {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, borrowedPoint, GL30.GL_RENDERBUFFER, 0);
        } else {
            GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, borrowedPoint, 0, 0);
        }
        borrowedPoint = -1;
        borrowedRenderbuffer = false;
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        detachDepth();
        if (colorTexture > -1) {
            TextureUtil.releaseTextureId(colorTexture);
            colorTexture = -1;
        }
        if (framebuffer > -1) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = -1;
        }
        width = height = 0;
    }

    /**
     * Allocates or resizes the colour attachment.
     *
     * <p>Half-float rather than eight-bit because the pass accumulates: a dozen additive layers over
     * one pixel of a close strike saturate an eight-bit target long before the composite gets to see
     * them, and clamping twice is not the same as clamping once.
     */
    private boolean ensureColor(int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) return false;
        if (framebuffer >= 0 && width == targetWidth && height == targetHeight) return true;

        close();
        width = targetWidth;
        height = targetHeight;
        framebuffer = GL30.glGenFramebuffers();
        colorTexture = TextureUtil.generateTextureId();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0,
            GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, colorTexture, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return true;
    }
}
