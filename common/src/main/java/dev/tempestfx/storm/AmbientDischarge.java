package dev.tempestfx.storm;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.math.Vec3d;
import java.util.Objects;

/**
 * One discharge the storm decided to produce, described and then handed on.
 *
 * <p>Immutable and free of geometry: the planner says what happened and where, the effect system
 * decides what that looks like and the audio decides what it sounds like. All three read the same
 * record, so a channel is generated once no matter how many subsystems care about it.
 *
 * @param type    the archetype
 * @param origin  where the channel starts
 * @param target  where it ends; for an aerial discharge this is another point in the cloud layer
 * @param energy  relative output, {@code 0..2}, before the archetype's own scaling
 * @param seed    the single number every subsystem branches from
 */
public record AmbientDischarge(DischargeType type, Vec3d origin, Vec3d target, float energy, long seed) {
    public AmbientDischarge {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        if (!origin.finite() || !target.finite()) throw new IllegalArgumentException("endpoints must be finite");
        if (!(energy > 0)) throw new IllegalArgumentException("energy must be positive");
    }

    /** Straight-line extent of the channel, in blocks. */
    public double span() { return origin.distanceTo(target); }

    /** The middle of the channel, which is where a distant observer perceives it to be. */
    public Vec3d midpoint() { return origin.lerp(target, 0.5); }
}
