package dev.tempestfx.compat;

/**
 * Framebuffer-free glow enhancement.
 *
 * <p>Calls are idempotent; an unbalanced {@link #begin()} or {@link #end()} can never throw out of a
 * render callback.
 */
public final class SafeBloomBackend implements BloomBackend {
    private static final float EMISSIVE_BOOST = 1.3f;

    private boolean active;

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public void begin() { active = true; }

    @Override
    public void end() { active = false; }

    @Override
    public float emissiveBoost() { return active ? EMISSIVE_BOOST : 1f; }
}
