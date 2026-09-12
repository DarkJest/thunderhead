package dev.tempestfx.api;

import dev.tempestfx.math.Vec3d;
import java.util.EnumSet;
import java.util.Set;

/**
 * Everything about one strike an integration may override, in one place.
 *
 * <p>Grouped rather than spread across the event, because these are all the same kind of thing -
 * optional, per-strike, and absent for every strike the mod raises itself. {@code null} anywhere
 * here means "decide it the way you normally would", which is what {@link #DEFAULT} is.
 *
 * @param style     look, or {@code null} to follow the player's lightning settings
 * @param origin    where the channel starts, or {@code null} to hang it from the cloud base
 * @param thunder   sound, or {@code null} for the mod's own choice by distance
 * @param particles which debris families may be emitted, or {@code null} for all of them
 */
public record StrikeOptions(LightningStyle style, Vec3d origin, ThunderOptions thunder,
                            Set<ParticleFamily> particles) {
    /** Nothing overridden. Every strike the mod raises itself carries this. */
    public static final StrikeOptions DEFAULT = new StrikeOptions(null, null, null, null);

    public StrikeOptions {
        if (origin != null && !origin.finite()) origin = null;
        // Defensive copy: a caller must not be able to change what a strike does after raising it,
        // and the renderer reads this every frame the bolt is alive.
        particles = particles == null || particles.isEmpty() ? null
            : Set.copyOf(EnumSet.copyOf(particles));
    }

    public boolean styled() { return style != null; }

    /** @return whether the given family may be emitted for this strike */
    public boolean allows(ParticleFamily family) { return particles == null || particles.contains(family); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private LightningStyle style;
        private Vec3d origin;
        private ThunderOptions thunder;
        private Set<ParticleFamily> particles;

        public Builder style(LightningStyle value) { style = value; return this; }
        public Builder origin(Vec3d value) { origin = value; return this; }
        public Builder thunder(ThunderOptions value) { thunder = value; return this; }
        public Builder particles(Set<ParticleFamily> value) { particles = value; return this; }

        public Builder particles(ParticleFamily... value) {
            particles = value == null || value.length == 0 ? null : Set.of(value);
            return this;
        }

        public StrikeOptions build() { return new StrikeOptions(style, origin, thunder, particles); }
    }
}
