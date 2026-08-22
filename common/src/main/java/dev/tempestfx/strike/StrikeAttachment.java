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
 * @param anchor    the object that won: the rod's own tip, rather than the point above it where the
 *                  channel met its streamer. This is where the <em>strike</em> is, and therefore
 *                  where its sparks, its light and its sound belong
 * @param streamers every upward streamer that reached for this leader, winner included
 * @param onRod     whether the winning streamer came from a lightning rod
 */
public record StrikeAttachment(Vec3d point, Vec3d anchor, List<Streamer> streamers, boolean onRod) {
    /** The bolt simply reached the surface; nothing rose to meet it. */
    public static StrikeAttachment toGround(Vec3d point) {
        return new StrikeAttachment(point, point, List.of(), false);
    }

    public StrikeAttachment {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(anchor, "anchor");
        streamers = List.copyOf(streamers);
        if (!point.finite() || !anchor.finite()) {
            throw new IllegalArgumentException("attachment points must be finite");
        }
    }

    public boolean contested() { return !streamers.isEmpty(); }
}
