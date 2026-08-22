package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Buffer source owned by Thunderhead.
 *
 * <p>Using the loader's shared buffer source is unsafe here: a render callback that ends a batch
 * mid-effect would flush half a bolt. Owning a private {@link ByteBufferBuilder} keeps the effect
 * batches independent.
 */
public final class FxBufferSource implements AutoCloseable {
    private static final int INITIAL_BYTES = 2 * 1024 * 1024;
    private static final int IDLE_TICKS_BEFORE_RELEASE = 600;

    private ByteBufferBuilder builder;
    private MultiBufferSource.BufferSource source;
    private int idleTicks;

    /** Allocates on demand and marks the source as in use for this frame. */
    public MultiBufferSource.BufferSource acquire() {
        if (source == null) {
            builder = new ByteBufferBuilder(INITIAL_BYTES);
            source = MultiBufferSource.immediate(builder);
        }
        idleTicks = 0;
        return source;
    }

    /** Frees the native buffer after a long idle period. Call once per client tick. */
    public void tick(boolean busy) {
        if (busy) {
            idleTicks = 0;
            return;
        }
        if (source != null && ++idleTicks > IDLE_TICKS_BEFORE_RELEASE) close();
    }

    public boolean allocated() { return source != null; }

    @Override
    public void close() {
        if (builder != null) {
            builder.close();
            builder = null;
        }
        source = null;
        idleTicks = 0;
    }
}
