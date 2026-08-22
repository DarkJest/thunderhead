package dev.tempestfx.api;

import dev.tempestfx.math.Vec3d;
import java.util.Objects;

/**
 * Builder-friendly description of a strike another mod wants Thunderhead to visualise.
 *
 * <p>A {@code null} style means "draw it the way this player has their lightning configured", which
 * is what an integration that only wants a bolt somewhere should leave it as.
 */
public record LightningEffect(Vec3d position, long seed, float intensity,
                              LightningEnvironment environment, StrikeTarget target,
                              StrikeOptions options) {
    /** Upper bound on the intensity an integration may ask for. */
    public static final float MAX_INTENSITY = 4f;

    public LightningEffect {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(target, "target");
        intensity = Float.isFinite(intensity) ? Math.max(0.01f, Math.min(intensity, MAX_INTENSITY)) : 1f;
        if (options == null) options = StrikeOptions.DEFAULT;
    }

    public LightningEffect(Vec3d position, long seed, float intensity,
                           LightningEnvironment environment, StrikeTarget target) {
        this(position, seed, intensity, environment, target, StrikeOptions.DEFAULT);
    }

    public LightningStyle style() { return options.style(); }

    public Vec3d origin() { return options.origin(); }

    /** Whether this effect brings its own look rather than following the player's settings. */
    public boolean styled() { return options.styled(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Vec3d position;
        private long seed;
        private float intensity = 1;
        private LightningEnvironment environment = LightningEnvironment.land(0x777777, false);
        private StrikeTarget target = StrikeTarget.none();
        private final StrikeOptions.Builder options = StrikeOptions.builder();

        public Builder position(Vec3d value) { position = value; return this; }
        public Builder seed(long value) { seed = value; return this; }
        public Builder intensity(float value) { intensity = value; return this; }
        public Builder environment(LightningEnvironment value) { environment = value; return this; }
        public Builder target(StrikeTarget value) { target = value; return this; }
        public Builder style(LightningStyle value) { options.style(value); return this; }

        /** Where the channel starts. Fixes the bolt's angle and length outright. */
        public Builder origin(Vec3d value) { options.origin(value); return this; }

        /** What the strike sounds like: which clip, how loud, how long after the flash. */
        public Builder thunder(ThunderOptions value) { options.thunder(value); return this; }

        /** Restricts which debris families may be emitted. Absent means all of them. */
        public Builder particles(ParticleFamily... value) { options.particles(value); return this; }

        public LightningEffect build() {
            return new LightningEffect(position, seed, intensity, environment, target, options.build());
        }
    }
}
