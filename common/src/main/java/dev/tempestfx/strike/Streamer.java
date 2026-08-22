package dev.tempestfx.strike;

import dev.tempestfx.lightning.LightningSegment;
import java.util.List;
import java.util.Objects;

/**
 * One upward streamer, and when in the leader's descent it appears.
 *
 * <p>Segments carry their own {@code along} from the object's tip to the streamer's end, so the
 * renderer grows one exactly the way it grows the channel — no second mechanism, and nothing is
 * rebuilt while it is on screen.
 *
 * @param segments the filament, tip outward
 * @param startsAt leader progress at which this streamer begins to rise, {@code 0..1}
 * @param winner   whether this is the one the leader connected to
 * @param kind     what threw it
 */
public record Streamer(List<LightningSegment> segments, double startsAt, boolean winner, StreamerKind kind) {
    public Streamer {
        segments = List.copyOf(segments);
        Objects.requireNonNull(kind, "kind");
        if (startsAt < 0 || startsAt > 1) throw new IllegalArgumentException("startsAt must be 0..1");
    }

    /**
     * How far this streamer has grown, {@code 0..1}, for a leader that has travelled {@code leader}.
     *
     * <p>A streamer that loses stops growing at the moment of attachment rather than continuing:
     * once another one has connected, the field collapses and there is nothing left to climb toward.
     */
    public float growth(float leader) {
        if (leader <= startsAt) return 0;
        double span = Math.max(1.0e-3, 1.0 - startsAt);
        return (float) Math.min(1.0, (leader - startsAt) / span);
    }
}
