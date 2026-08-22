package dev.tempestfx.render.composite;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * A readable copy of the finished scene, taken only when something actually displaces it.
 *
 * <p>The composite writes into the same colour buffer it would have to read for refraction, and a
 * pass cannot sample its own render target. The usual answer is a ping-pong pair of framebuffers and
 * two fullscreen passes, which is what the mod's old post chain did: blit the scene out, shade it
 * back in. This is the cheaper half of that. A single {@code glCopyTexSubImage2D} moves the pixels
 * inside the driver with no program, no vertex work and no second framebuffer, and it only happens on
 * the frames where a shockwave is actually bending the image. With no refraction on screen the effect
 * composites with plain premultiplied blending and the scene is never read at all.
 */
final class SceneColorCopy implements AutoCloseable {
    private int texture = -1;
    private int width;
    private int height;

    int textureId() {
        return texture;
    }

    /**
     * Copies the colour attachment of {@code readFramebuffer} into this texture.
     *
     * @return {@code false} when the copy could not be sized, in which case refraction is skipped
     */
    boolean capture(int readFramebuffer, int targetWidth, int targetHeight) {
        RenderSystem.assertOnRenderThread();
        if (!ensure(targetWidth, targetHeight)) return false;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return true;
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        if (texture > -1) {
            TextureUtil.releaseTextureId(texture);
            texture = -1;
        }
        width = height = 0;
    }

    private boolean ensure(int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) return false;
        if (texture > -1 && width == targetWidth && height == targetHeight) return true;
        close();
        width = targetWidth;
        height = targetHeight;
        texture = TextureUtil.generateTextureId();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        // Eight bit unsigned, matching the colour buffer the game presents; a copy has no headroom
        // to gain and half the bandwidth to lose.
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return true;
    }
}
