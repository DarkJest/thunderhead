package dev.tempestfx.compat;

/**
 * Optional glow enhancement around an effect batch.
 */
public interface BloomBackend {
    boolean isAvailable();

    /** Opens the enhanced pass. Implementations must be tolerant of unbalanced calls. */
    void begin();

    void end();

    /**
     * Multiplier applied to the additive glow layers while this backend is active.
     *
     * @return {@code 1} for no change
     */
    default float emissiveBoost() { return 1f; }
}
