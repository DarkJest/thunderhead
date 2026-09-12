package dev.tempestfx.api;

import dev.tempestfx.math.Vec3d;
import java.util.Objects;

/**
 * Immutable description of one stroke, published once and consumed by independent subsystems.
 *
 * <p>{@code seed} must be derived from replicated data (see
 * {@link dev.tempestfx.math.StrikeSeed}) so that every client in a session generates identical
 * geometry, thunder and particles for the same bolt.
 *
 * @param stroke 0 for the stroke the server actually spawned, 1..n for the visual return strokes of
 *               the same flash. Only stroke 0 may start a new sequence, which is what keeps the
 *               expansion from chaining forever.
 * @param options per-strike overrides an integration asked for; {@link StrikeOptions#DEFAULT} for
 *                every strike the mod raises itself
 */
public record LightningStrikeFxEvent(Vec3d position, long seed, float intensity,
                                     LightningEnvironment environment, StrikeTarget target, int stroke,
                                     StrikeOptions options) {
    public LightningStrikeFxEvent {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(target, "target");
        if (!position.finite()) throw new IllegalArgumentException("strike position must be finite");
        if (!(intensity > 0) || !Float.isFinite(intensity)) {
            throw new IllegalArgumentException("strike intensity must be positive and finite");
        }
        if (stroke < 0) throw new IllegalArgumentException("stroke index must not be negative");
        if (options == null) options = StrikeOptions.DEFAULT;
    }

    public LightningStrikeFxEvent(Vec3d position, long seed, float intensity,
                                  LightningEnvironment environment, StrikeTarget target, int stroke) {
        this(position, seed, intensity, environment, target, stroke, StrikeOptions.DEFAULT);
    }

    public LightningStrikeFxEvent(Vec3d position, long seed, float intensity,
                                  LightningEnvironment environment, StrikeTarget target) {
        this(position, seed, intensity, environment, target, 0, StrikeOptions.DEFAULT);
    }

    public LightningStrikeFxEvent(Vec3d position, long seed, float intensity, LightningEnvironment environment) {
        this(position, seed, intensity, environment, StrikeTarget.none(), 0, StrikeOptions.DEFAULT);
    }

    public LightningStyle style() { return options.style(); }

    public Vec3d origin() { return options.origin(); }

    /** True when the bolt came down on a player rather than terrain. */
    public boolean directPlayerHit() { return target.present() && target.player(); }

    /** True for the stroke that corresponds to a real, server-spawned bolt. */
    public boolean primary() { return stroke == 0; }

    /** Copy of this stroke at a new position, seed and intensity, used to build return strokes. */
    public LightningStrikeFxEvent asStroke(Vec3d newPosition, long newSeed, float newIntensity,
                                           LightningEnvironment newEnvironment, int strokeIndex) {
        return new LightningStrikeFxEvent(newPosition, newSeed, newIntensity, newEnvironment,
            StrikeTarget.none(), strokeIndex);
    }
}
