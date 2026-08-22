package dev.tempestfx.lightning;

import dev.tempestfx.math.Vec3d;
import java.util.Objects;

/** Immutable input to a {@link LightningGeometryStrategy}. */
public record LightningBolt(Vec3d start, Vec3d end, long seed, float intensity, LightningGenerationConfig config) {
    public LightningBolt {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(config, "config");
        if (!start.finite() || !end.finite()) throw new IllegalArgumentException("bolt endpoints must be finite");
        if (start.distanceTo(end) < 0.01) throw new IllegalArgumentException("bolt must have length");
        if (intensity <= 0) throw new IllegalArgumentException("intensity must be positive");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Vec3d start;
        private Vec3d end;
        private long seed;
        private float intensity = 1.0f;
        private LightningGenerationConfig config = LightningGenerationConfig.high();

        public Builder start(Vec3d value) { start = value; return this; }
        public Builder end(Vec3d value) { end = value; return this; }
        public Builder seed(long value) { seed = value; return this; }
        public Builder intensity(float value) { intensity = value; return this; }
        public Builder config(LightningGenerationConfig value) { config = value; return this; }
        public Builder generations(int value) { config = config.withGenerations(value); return this; }
        public Builder branchProbability(double value) { config = config.withBranchProbability(value); return this; }
        public Builder displacement(double value) { config = config.withDisplacement(value); return this; }

        public LightningBolt build() { return new LightningBolt(start, end, seed, intensity, config); }
    }
}
