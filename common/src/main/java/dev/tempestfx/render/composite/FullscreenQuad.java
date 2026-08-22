package dev.tempestfx.render.composite;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * The one quad every post pass draws.
 *
 * <p>Emitted in clip space, so the programs declare no matrices at all and nothing has to be pushed,
 * saved or restored to draw one. Shared between the composite and the bloom chain because they are
 * literally the same four vertices, and because two of them would be two things to release.
 */
final class FullscreenQuad implements AutoCloseable {
    private ByteBufferBuilder builder;
    private VertexBuffer buffer;

    /** Uploads and draws. Silently does nothing if the mesh could not be built. */
    void draw() {
        if (builder == null) {
            builder = new ByteBufferBuilder(4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        }
        if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);

        BufferBuilder vertices = new BufferBuilder(builder, VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR);
        vertices.addVertex(-1f, -1f, 0f).setUv(0f, 0f).setColor(255, 255, 255, 255);
        vertices.addVertex(1f, -1f, 0f).setUv(1f, 0f).setColor(255, 255, 255, 255);
        vertices.addVertex(1f, 1f, 0f).setUv(1f, 1f).setColor(255, 255, 255, 255);
        vertices.addVertex(-1f, 1f, 0f).setUv(0f, 1f).setColor(255, 255, 255, 255);
        MeshData mesh = vertices.build();
        if (mesh == null) return;
        buffer.bind();
        buffer.upload(mesh);
        buffer.draw();
        VertexBuffer.unbind();
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        if (builder != null) {
            builder.close();
            builder = null;
        }
    }
}
