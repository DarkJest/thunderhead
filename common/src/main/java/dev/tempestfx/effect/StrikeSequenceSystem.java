package dev.tempestfx.effect;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.StrikeSequence;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands a primary stroke into the rest of its flash and releases the strokes on schedule.
 */
public final class StrikeSequenceSystem {
    /** Bound so a burst of strikes cannot queue an unbounded number of follow-ups. */
    private static final int MAX_PENDING = 64;

    private final List<Pending> pending = new ArrayList<>();

    public void onStrike(LightningStrikeFxEvent event, TempestConfig config) {
        if (!event.primary() || config.lightning.returnStrokes <= 0) return;
        if (!event.dischargeType().reachesGround()) return;
        for (StrikeSequence.ReturnStroke stroke
            : StrikeSequence.plan(event.seed(), event.intensity(), config.lightning.returnStrokes)) {
            if (pending.size() >= MAX_PENDING) return;
            Vec3d position = event.position().add(stroke.offsetX(), 0, stroke.offsetZ());
            pending.add(new Pending(stroke.delayTicks(), position, stroke.seed(), stroke.intensity(),
                stroke.index(), event.dischargeType()));
        }
    }

    /** Releases strokes whose delay has elapsed. Call once per client tick. */
    public void tick(StrokeReleaser releaser) {
        for (int index = pending.size() - 1; index >= 0; index--) {
            Pending next = pending.get(index).next();
            if (next.ticks() <= 0) {
                pending.remove(index);
                releaser.release(next.position(), next.seed(), next.intensity(), next.stroke(), next.type());
            } else {
                pending.set(index, next);
            }
        }
    }

    public int pendingCount() { return pending.size(); }

    public void clear() { pending.clear(); }

    /** Builds and publishes the finished event, after sampling the surface under the stroke. */
    @FunctionalInterface
    public interface StrokeReleaser {
        void release(Vec3d position, long seed, float intensity, int stroke, DischargeType type);
    }

    private record Pending(int ticks, Vec3d position, long seed, float intensity, int stroke,
                           DischargeType type) {
        Pending next() { return new Pending(ticks - 1, position, seed, intensity, stroke, type); }
    }
}
