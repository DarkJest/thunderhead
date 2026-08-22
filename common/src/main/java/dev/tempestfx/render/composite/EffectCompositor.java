package dev.tempestfx.render.composite;

/**
 * Where an effect's geometry ends up, and how it reaches the screen.
 *
 * <p>The point of the abstraction is that no effect renderer has to know. A renderer emits geometry
 * into a {@code VertexConsumer} and never asks which framebuffer is bound, which rendering pipeline
 * is installed or which shader pack is active. The compositor answers all three questions on its
 * own, once per frame, in one place.
 *
 * <p>The contract is two windows in the frame:
 *
 * <pre>
 *   beginWorldPass()   the effect target is bound and depth-tested against the world
 *   ...world pass...   effect geometry is drawn
 *   endWorldPass()     every piece of GPU state is put back
 *   ...the game and any shader pack finish the scene image...
 *   composite()        the effect is applied to that image
 * </pre>
 *
 * <p>{@link #beginWorldPass()} returning {@code false} is not an error: it means the effect is not
 * isolated this frame and the world pass will draw straight into whatever the game has bound, which
 * is what the mod did before there was a compositor. {@link #composite} is then a no-op. Every
 * implementation has to survive that path, because it is also the failure path.
 */
public interface EffectCompositor extends AutoCloseable {
    /**
     * Opens the world pass.
     *
     * @return {@code true} when the effect target is bound and the pass is isolated from the
     *     pipeline that owns the frame; {@code false} when the caller should draw directly
     */
    boolean beginWorldPass();

    /** Closes the world pass and restores every piece of GPU state it captured. */
    void endWorldPass();

    /**
     * Applies the accumulated effect to the finished scene image.
     *
     * @param distortion screen-space refraction to apply while compositing, or
     *     {@link DistortionField#NONE}
     * @param shafts     where the brightest channel is on screen, for the light shafts, or
     *     {@link LightShaftField#NONE}
     */
    void composite(DistortionField distortion, LightShaftField shafts);

    /** Whether an isolated world pass is possible at all; for the debug overlay. */
    boolean available();

    /** Releases GPU resources after a long idle period. Call once per client tick. */
    void tick(boolean busy);

    @Override
    void close();
}
