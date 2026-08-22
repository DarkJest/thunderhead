package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Where a pass's geometry goes.
 *
 * <p>The seam between the effect and the machinery that draws it. Every renderer in this package
 * asks for a {@link VertexConsumer} and emits vertices into it; none of them knows whether the
 * vertices end up in the mod's own program and framebuffer or in Minecraft's shader objects and
 * whatever the game had bound. That is the whole reason the same geometry survives any shader loader.
 */
public interface FxBatchTarget extends AutoCloseable {
    /** Opens a pass and returns the consumer to emit into. */
    VertexConsumer begin(FxPass pass);

    /** Flushes the pass opened by {@link #begin}. Must be called before the next {@code begin}. */
    void end(FxPass pass);

    /** Whether this target is usable this frame. */
    boolean available();

    /** Frees buffers after a long idle period. Call once per client tick. */
    void tick(boolean busy);

    @Override
    void close();
}
