package dev.tempestfx.render.composite;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.tempestfx.render.gl.FxProgram;
import dev.tempestfx.render.gl.FxPrograms;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Turns the effect attachment into the glow that is added on top of it.
 *
 * <p>The whole chain runs on the mod's <em>own</em> half-float attachment and never touches the
 * scene, the frame's depth or anybody else's target. That is what makes it safe: there is no
 * pipeline it has to be right about, and a shader pack cannot interfere with a pass that only reads
 * a texture the mod allocated.
 *
 * <pre>
 *   effect (full)  --extract-->  bright (1/2)  --blur X-->  ping (1/4)
 *                                                --blur Y-->  pong (1/4)
 *                                                --shafts-->  ping (1/4)
 * </pre>
 *
 * <p>Four small draws at a quarter of the pixels. The bloom is deliberately produced at low
 * resolution and magnified by the hardware on the way back: a wide, soft bleed is exactly what
 * bilinear upscaling of a blurred quarter-res image looks like, and paying full resolution for it
 * would buy nothing visible.
 */
final class BloomChain implements AutoCloseable {
    /** Values above this in the effect attachment are what bleeds. */
    private static final float THRESHOLD = 0.85f;
    /** Soft knee, so a decaying bolt fades out of the bloom instead of popping out of it. */
    private static final float KNEE = 0.45f;
    /** Blur radius, in quarter-resolution texels. */
    private static final float RADIUS = 2.4f;

    private final FxPrograms programs;
    private final FullscreenQuad quad = new FullscreenQuad();

    private final Buffer bright = new Buffer();
    private final Buffer ping = new Buffer();
    private final Buffer pong = new Buffer();

    private int resultTexture = -1;

    BloomChain(FxPrograms programs) {
        this.programs = programs;
    }

    /** Whether the programs this needs are present. They are optional, and may simply not be. */
    boolean available() {
        return programs.get(FxPrograms.Kind.BLOOM_EXTRACT) != null
            && programs.get(FxPrograms.Kind.BLOOM_BLUR) != null
            && programs.get(FxPrograms.Kind.BLOOM_SHAFTS) != null;
    }

    /**
     * Runs the chain.
     *
     * @param shafts where the channel is on screen and how hard to smear toward it; may be
     *               {@link LightShaftField#NONE}
     * @return the texture holding the finished glow, or {@code -1} if nothing could be produced
     */
    int run(int effectTexture, int width, int height, float strength, LightShaftField shafts) {
        resultTexture = -1;
        if (!available() || strength <= 0 || width <= 0 || height <= 0) return -1;

        int halfWidth = Math.max(1, width / 2);
        int halfHeight = Math.max(1, height / 2);
        int quarterWidth = Math.max(1, width / 4);
        int quarterHeight = Math.max(1, height / 4);
        if (!bright.ensure(halfWidth, halfHeight)
            || !ping.ensure(quarterWidth, quarterHeight)
            || !pong.ensure(quarterWidth, quarterHeight)) {
            return -1;
        }

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);

        FxProgram extract = programs.get(FxPrograms.Kind.BLOOM_EXTRACT);
        bright.bind();
        extract.bind();
        extract.setVector4("TempestBloom", THRESHOLD, KNEE, 1f / width, 1f / height);
        bindSource(extract, effectTexture);
        quad.draw();

        FxProgram blur = programs.get(FxPrograms.Kind.BLOOM_BLUR);
        ping.bind();
        blur.bind();
        blur.setVector4("TempestBlur", 1f / quarterWidth, 0f, RADIUS, 0f);
        bindSource(blur, bright.texture);
        quad.draw();

        pong.bind();
        blur.bind();
        blur.setVector4("TempestBlur", 0f, 1f / quarterHeight, RADIUS, 0f);
        bindSource(blur, ping.texture);
        quad.draw();

        FxProgram shaftProgram = programs.get(FxPrograms.Kind.BLOOM_SHAFTS);
        ping.bind();
        shaftProgram.bind();
        shaftProgram.setVector4("TempestShaft", shafts.centerX(), shafts.centerY(),
            shafts.strength(), shafts.aspect());
        bindSource(shaftProgram, pong.texture);
        quad.draw();

        FxProgram.unbind();
        resultTexture = ping.texture;
        return resultTexture;
    }

    /** The texture the last {@link #run} produced, or {@code -1}. */
    int resultTexture() {
        return resultTexture;
    }

    private static void bindSource(FxProgram program, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        program.setSampler("Sampler0", 0);
    }

    /** Releases every buffer. The next storm allocates them again. */
    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        bright.close();
        ping.close();
        pong.close();
        quad.close();
        resultTexture = -1;
    }

    /** One half-float colour target, sized on demand. */
    private static final class Buffer {
        private int framebuffer = -1;
        private int texture = -1;
        private int width;
        private int height;

        boolean ensure(int targetWidth, int targetHeight) {
            if (framebuffer >= 0 && width == targetWidth && height == targetHeight) return true;
            close();
            width = targetWidth;
            height = targetHeight;
            framebuffer = GL30.glGenFramebuffers();
            texture = TextureUtil.generateTextureId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            // Linear, because the whole point of the low resolution is that it is magnified back.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        }

        void bind() {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, width, height);
        }

        void close() {
            if (texture > -1) {
                TextureUtil.releaseTextureId(texture);
                texture = -1;
            }
            if (framebuffer > -1) {
                GL30.glDeleteFramebuffers(framebuffer);
                framebuffer = -1;
            }
            width = height = 0;
        }
    }
}
