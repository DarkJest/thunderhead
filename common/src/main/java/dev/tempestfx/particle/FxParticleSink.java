package dev.tempestfx.particle;

/**
 * Hands out pooled particles under a budget.
 *
 * <p>Emitters never construct particles: they ask the sink, which draws from the pool, applies the
 * per-family enable predicate and enforces the global cap. A {@code null} result means "stop
 * emitting this family", either because the budget ran out or because the family is disabled.
 * The caller must fully configure whatever it receives.
 */
public interface FxParticleSink {
    FxParticle acquire(FxParticleMaterial material);
}
