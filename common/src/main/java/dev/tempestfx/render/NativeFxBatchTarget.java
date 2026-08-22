package dev.tempestfx.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tempestfx.render.gl.FxProgram;
import dev.tempestfx.render.gl.FxPrograms;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/**
 * Draws the mod's passes with the mod's own programs, buffers and GL state.
 *
 * <p>Nothing here goes through Minecraft's shader objects or its shader state, which is what makes the
 * effect independent of whichever pipeline owns the frame: there is no {@code ShaderInstance} for a
 * shader loader to recognise, override, skip or redirect, and no cached render state for it to lock.
 * Geometry is still emitted through Minecraft's {@code BufferBuilder} and uploaded through its
 * {@code VertexBuffer} — those are plain buffer plumbing, and reusing them keeps the vertex layout
 * exactly what the rest of the mod already produces.
 *
 * <p>GL state is set with raw calls rather than through {@code GlStateManager}, for the same reason:
 * a pipeline that has locked the game's blend or depth-mask state would otherwise silently swallow
 * them. Everything set here is captured and put back by the guard that wraps the whole pass, so the
 * game's own cache of the state stays true.
 */
public final class NativeFxBatchTarget implements FxBatchTarget {
    private static final int INITIAL_BYTES = 2 * 1024 * 1024;
    private static final int IDLE_TICKS_BEFORE_RELEASE = 600;

    private final FxPrograms programs;

    private ByteBufferBuilder buffer;
    private VertexBuffer vertexBuffer;
    private BufferBuilder open;
    private int idleTicks;

    public NativeFxBatchTarget(FxPrograms programs) {
        this.programs = programs;
    }

    @Override
    public boolean available() {
        return RenderSystem.isOnRenderThread() && programs.available();
    }

    @Override
    public VertexConsumer begin(FxPass pass) {
        if (open != null) throw new IllegalStateException("A pass is already open");
        if (buffer == null) buffer = new ByteBufferBuilder(INITIAL_BYTES);
        if (vertexBuffer == null) vertexBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        idleTicks = 0;
        open = new BufferBuilder(buffer, VertexFormat.Mode.QUADS, pass.format());
        return open;
    }

    @Override
    public void end(FxPass pass) {
        BufferBuilder builder = open;
        open = null;
        if (builder == null) return;
        MeshData mesh = builder.build();
        if (mesh == null) return;
        boolean uploaded = false;
        try {
            if (pass.sorted()) mesh.sortQuads(buffer, RenderSystem.getVertexSorting());
            FxProgram program = programs.get(pass.program());
            if (program == null) return;
            apply(pass, program);
            vertexBuffer.bind();
            // Takes ownership of the mesh and closes it.
            vertexBuffer.upload(mesh);
            uploaded = true;
            vertexBuffer.draw();
        } finally {
            if (!uploaded) mesh.close();
            VertexBuffer.unbind();
            FxProgram.unbind();
        }
    }

    @Override
    public void tick(boolean busy) {
        if (busy) {
            idleTicks = 0;
            return;
        }
        if (buffer != null && ++idleTicks > IDLE_TICKS_BEFORE_RELEASE) close();
    }

    @Override
    public void close() {
        open = null;
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        idleTicks = 0;
    }

    /** Depth tested against the scene but never written, and blended per the pass description. */
    private void apply(FxPass pass, FxProgram program) {
        GL11.glEnable(GL11.GL_BLEND);
        if (pass.blend() == FxPass.Blend.ADDITIVE) {
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        } else {
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColorMask(true, true, true, true);

        program.bind();
        program.setMatrix("ModelViewMat", RenderSystem.getModelViewMatrix());
        program.setMatrix("ProjMat", RenderSystem.getProjectionMatrix());
        bind(program, "Sampler0", 0, pass.texture0());
        bind(program, "Sampler1", 1, pass.texture1());
    }

    private static void bind(FxProgram program, String sampler, int unit, ResourceLocation texture) {
        if (texture == null) return;
        int id = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        program.setSampler(sampler, unit);
    }
}
