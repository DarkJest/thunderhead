package dev.tempestfx.strike;

import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which upward streamer wins, and builds every streamer's geometry.
 *
 * <p>Pure: candidates in, an attachment out, and the same seed always gives the same answer, so two
 * players watching one bolt see the same rod win. The world scan that produced the candidates is the
 * only part that touches Minecraft, and it happens once per strike.
 */
public final class AttachmentPlanner {
    /** Most streamers drawn for one strike. Beyond a handful they stop reading as individual reaches. */
    private static final int MAX_STREAMERS = 5;
    /** Segments per streamer. Short filaments; they are metres long, not hundreds. */
    private static final int SEGMENTS = 5;
    /** Half-width of a streamer at its root, in blocks, before the kind's own brightness. */
    private static final double ROOT_HALF_WIDTH = 0.055;
    /** Earliest and latest leader progress at which a streamer starts to rise. */
    private static final double EARLIEST_START = 0.55;
    private static final double LATEST_START = 0.82;

    private AttachmentPlanner() {}

    /**
     * @param candidates everything the scan found, in any order
     * @param fallback   where the channel ends if nothing competes: the sampled surface
     * @param seed       the strike seed, so the winner and every wobble are reproducible
     */
    public static StrikeAttachment plan(List<StreamerCandidate> candidates, Vec3d fallback, long seed) {
        if (candidates.isEmpty()) return StrikeAttachment.toGround(fallback);

        // Strongest first, ties broken on position so iteration order cannot decide a strike.
        List<StreamerCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
            .comparingDouble((StreamerCandidate candidate) -> -candidate.weight())
            .thenComparingDouble(candidate -> candidate.tip().x())
            .thenComparingDouble(candidate -> candidate.tip().z()));

        StreamerCandidate winner = ranked.getFirst();
        // The tallest, most conductive thing usually wins, but not always: a real leader is already
        // committed to an approach when the streamers go up, so the second-best sometimes gets there
        // first. Rare enough to be a surprise rather than a coin toss.
        if (ranked.size() > 1 && StrikeSeed.unit(seed, 0x5712) < 0.18) winner = ranked.get(1);

        List<Streamer> streamers = new ArrayList<>();
        int count = Math.min(MAX_STREAMERS, ranked.size());
        for (int index = 0; index < count; index++) {
            StreamerCandidate candidate = ranked.get(index);
            boolean wins = candidate == winner;
            long streamerSeed = StrikeSeed.derive(seed, 0x5720 + index);
            // The winner commits first; the others hesitate, which is why they lose.
            double startsAt = wins
                ? EARLIEST_START
                : EARLIEST_START + StrikeSeed.unit(streamerSeed, 0x1) * (LATEST_START - EARLIEST_START);
            streamers.add(new Streamer(
                build(candidate, wins, streamerSeed), startsAt, wins, candidate.kind()));
        }

        Vec3d point = winner.tip().add(0, winner.kind().reach() * ATTACHMENT_HEIGHT_RATIO, 0);
        return new StrikeAttachment(point, winner.tip(), streamers, winner.kind() == StreamerKind.ROD);
    }

    /**
     * Where the channel actually meets the streamer, as a fraction of the streamer's reach.
     *
     * <p>Near the top of the streamer rather than at the object itself, so the filament visibly
     * bridges the last of the gap. Any lower and the channel simply lands on the block; much higher
     * and there is nothing left for the streamer to be.
     */
    private static final double ATTACHMENT_HEIGHT_RATIO = 0.88;

    /** A short filament climbing from the object's tip, wandering and tapering as it goes. */
    private static List<LightningSegment> build(StreamerCandidate candidate, boolean winner, long seed) {
        double reach = candidate.kind().reach() * (winner ? 1.0 : 0.55 + StrikeSeed.unit(seed, 0x2) * 0.35);
        double width = ROOT_HALF_WIDTH * (candidate.kind() == StreamerKind.ROD ? 1.35 : 1.0);
        // A streamer leans toward the leader rather than climbing plumb, and a losing one leans more:
        // it is reaching for a channel that is not coming to it.
        double leanX = StrikeSeed.signed(seed, 0x3) * reach * (winner ? 0.12 : 0.3);
        double leanZ = StrikeSeed.signed(seed, 0x4) * reach * (winner ? 0.12 : 0.3);

        List<LightningSegment> segments = new ArrayList<>(SEGMENTS);
        Vec3d current = candidate.tip();
        for (int step = 0; step < SEGMENTS; step++) {
            double t0 = step / (double) SEGMENTS;
            double t1 = (step + 1) / (double) SEGMENTS;
            Vec3d next = candidate.tip().add(
                leanX * t1 * t1 + StrikeSeed.signed(seed, 0x10 + step) * reach * 0.06,
                reach * t1,
                leanZ * t1 * t1 + StrikeSeed.signed(seed, 0x20 + step) * reach * 0.06);
            if (next.distanceSquaredTo(current) < 1.0e-8) continue;
            segments.add(new LightningSegment(current, next, 0,
                Math.max(0.004, width * (1 - t0 * 0.75)),
                Math.max(0.003, width * (1 - t1 * 0.75)),
                1, t0, t1, StrikeSeed.derive(seed, 0x30 + step)));
            current = next;
        }
        return segments;
    }
}
