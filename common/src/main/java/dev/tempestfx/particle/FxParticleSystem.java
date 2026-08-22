package dev.tempestfx.particle;

import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.util.ObjectPool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bounded, pooled particle simulation.
 *
 * <p>The active list never exceeds the configured cap and every particle comes from an
 * {@link ObjectPool}, so a storm of strikes allocates nothing after warm-up. Emission goes through a
 * single reusable {@link FxParticleSink}, which is why emitters cannot allocate either.
 */
public final class FxParticleSystem {
    private final List<FxParticle> active = new ArrayList<>();
    private final List<FxParticle> activeView = Collections.unmodifiableList(active);
    private final ObjectPool<FxParticle> pool;
    private final ParticleSpawnStrategy strategy;
    private final BudgetedSink sink = new BudgetedSink();
    private final int maximum;

    public FxParticleSystem(int maximum, ParticleSpawnStrategy strategy) {
        this.maximum = Math.max(1, maximum);
        this.strategy = strategy;
        this.pool = new ObjectPool<>(Math.min(256, this.maximum), this.maximum, FxParticle::new, FxParticle::reset);
    }

    public void emit(LightningStrikeFxEvent event, int budget) { emit(event, budget, material -> true); }

    public void emit(LightningStrikeFxEvent event, int budget, Predicate<FxParticleMaterial> enabled) {
        if (budget <= 0) return;
        sink.open(budget, enabled);
        try {
            strategy.spawn(event, budget, sink);
        } finally {
            sink.close();
        }
    }

    /**
     * Emission entry point for subsystems that own their own geometry, such as the entity discharge
     * and ash imprint effects.
     */
    public void emit(int budget, Predicate<FxParticleMaterial> enabled, ParticleEmitter emitter) {
        if (budget <= 0) return;
        sink.open(budget, enabled);
        try {
            emitter.emit(sink);
        } finally {
            sink.close();
        }
    }

    public void tick() {
        for (int index = active.size() - 1; index >= 0; index--) {
            FxParticle particle = active.get(index);
            if (!particle.tick()) {
                active.remove(index);
                pool.release(particle);
            }
        }
    }

    public List<FxParticle> active() { return activeView; }

    public int activeCount() { return active.size(); }

    public int capacity() { return maximum; }

    public void clear() {
        for (FxParticle particle : active) pool.release(particle);
        active.clear();
    }

    /** Callback form used by subsystems that emit outside the strike strategy. */
    @FunctionalInterface
    public interface ParticleEmitter {
        void emit(FxParticleSink sink);
    }

    private final class BudgetedSink implements FxParticleSink {
        private int remaining;
        private Predicate<FxParticleMaterial> enabled;
        private boolean open;

        void open(int budget, Predicate<FxParticleMaterial> predicate) {
            remaining = budget;
            enabled = predicate;
            open = true;
        }

        void close() {
            open = false;
            enabled = null;
            remaining = 0;
        }

        @Override
        public FxParticle acquire(FxParticleMaterial material) {
            if (!open || remaining <= 0 || active.size() >= maximum) return null;
            if (enabled != null && !enabled.test(material)) return null;
            FxParticle particle = pool.acquire();
            particle.material = material;
            remaining--;
            active.add(particle);
            return particle;
        }
    }
}
