package dev.tempestfx.strike;

import dev.tempestfx.math.Vec3d;
import java.util.List;
import java.util.Objects;

/**
 * How one strike met the ground.
 *
 * <p>Immutable and resolved once, before any geometry exists: the channel is then built to end at
 * {@link #point()} rather than at whatever height the bolt entity happened to be reported at. That
 * is what lets a bolt terminate on the tip of a lightning rod instead of beside it.
 *
 * @param point     where the channel terminates and the return stroke begins
 * @param streamers every upward streamer that reached for this leader, winner included
 * @param onRod     whether the winning streamer came from a lightning rod
 */
public record StrikeAttachment(Vec3d point, List<Streamer> streamers, boolean onRod) {
    /** The bolt simply reached the surface; nothing rose to meet it. */
    public static StrikeAttachment toGround(Vec3d point) {
        return new StrikeAttachment(point, List.of(), false);
    }

    public StrikeAttachment {
        Objects.requireNonNull(point, "point");
        streamers = List.copyOf(streamers);
        if (!point.finite()) throw new IllegalArgumentException("attachment point must be finite");
    }

    public boolean contested() { return !streamers.isEmpty(); }
}
