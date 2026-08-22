package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws the mod's passes through Minecraft's own render types and shader objects.
 *
 * <p>The fallback, for the case where the mod's programs could not be compiled. It is the path the mod
 * used before it owned its programs, with the compromises that come with it: under a shader pack the
 * bundled programs cannot be used, so the wide glow passes are skipped and the channel is widened and
 * brightened instead. Correct, and visibly poorer — which is why it is the fallback.
 */
public final class VanillaFxBatchTarget implements FxBatchTarget {
    private final FxBufferSource buffers = new FxBufferSource();

    private MultiBufferSource.BufferSource source;

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public VertexConsumer begin(FxPass pass) {
        if (source == null) source = buffers.acquire();
        return source.getBuffer(TempestRenderTypes.of(pass));
    }

    @Override
    public void end(FxPass pass) {
        if (source != null) source.endBatch(TempestRenderTypes.of(pass));
    }

    @Override
    public void tick(boolean busy) {
        buffers.tick(busy);
        if (!buffers.allocated()) source = null;
    }

    @Override
    public void close() {
        buffers.close();
        source = null;
    }
}
