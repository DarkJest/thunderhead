package dev.tempestfx.strike;

import dev.tempestfx.math.Vec3d;
import java.util.Objects;

/**
 * Something on the ground that could answer a descending leader.
 *
 * <p>A downward leader does not choose where it lands from the cloud. It gets close, the field under
 * it concentrates on whatever is tallest and most conductive, and those objects throw upward
 * streamers of their own; the one that connects first decides the strike point. This is one such
 * object, found by a bounded scan around where the bolt is going to land.
 *
 * @param tip    where the streamer would leave the object
 * @param weight how strongly it competes, from its height and what it is made of
 * @param kind   what it is, which decides how far and how brightly its streamer reaches
 */
public record StreamerCandidate(Vec3d tip, double weight, StreamerKind kind) {
    public StreamerCandidate {
        Objects.requireNonNull(tip, "tip");
        Objects.requireNonNull(kind, "kind");
        if (!tip.finite()) throw new IllegalArgumentException("candidate tip must be finite");
        if (!(weight > 0)) throw new IllegalArgumentException("weight must be positive");
    }
}
