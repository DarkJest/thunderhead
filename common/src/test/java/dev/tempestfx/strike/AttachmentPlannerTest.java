package dev.tempestfx.strike;

import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentPlannerTest {
    private static final Vec3d GROUND = new Vec3d(0, 64, 0);

    private static StreamerCandidate rod(double x, double height) {
        return new StreamerCandidate(new Vec3d(x, 64 + height, 0),
            (1 + height) * StreamerKind.ROD.baseWeight(), StreamerKind.ROD);
    }

    private static StreamerCandidate terrain(double x, double height) {
        return new StreamerCandidate(new Vec3d(x, 64 + height, 0),
            (1 + height) * StreamerKind.TERRAIN.baseWeight(), StreamerKind.TERRAIN);
    }

    @Test
    void nothingToReachUpMeansTheBoltJustLands() {
        StrikeAttachment attachment = AttachmentPlanner.plan(List.of(), GROUND, 1);
        assertEquals(GROUND, attachment.point());
        assertFalse(attachment.contested());
        assertFalse(attachment.onRod());
    }

    @Test
    void aRodBeatsATallerHillAlmostEveryTime() {
        // The rod is shorter than the terrain on purpose: conductivity is supposed to outweigh height.
        List<StreamerCandidate> candidates = List.of(terrain(3, 6), rod(-2, 3));
        int rodWins = 0;
        for (long seed = 0; seed < 400; seed++) {
            if (AttachmentPlanner.plan(candidates, GROUND, seed).onRod()) rodWins++;
        }
        assertTrue(rodWins > 300, "the rod won only " + rodWins + " times in 400");
        assertTrue(rodWins < 400, "the leader should occasionally be committed elsewhere");
    }

    @Test
    void theAttachmentSitsAboveTheObjectRatherThanOnIt() {
        StrikeAttachment attachment = AttachmentPlanner.plan(List.of(rod(0, 4)), GROUND, 7);
        assertTrue(attachment.point().y() > 64 + 4, "the channel must meet the streamer, not the block");
        assertTrue(attachment.point().y() < 64 + 4 + StreamerKind.ROD.reach(),
            "and it must not meet it past the end of the streamer");
    }

    @Test
    void everyCandidateGetsAStreamerAndExactlyOneWins() {
        List<StreamerCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) candidates.add(terrain(index, 2 + index * 0.3));
        StrikeAttachment attachment = AttachmentPlanner.plan(candidates, GROUND, 3);

        assertTrue(attachment.contested());
        assertTrue(attachment.streamers().size() <= 5, "too many streamers to read individually");
        assertEquals(1, attachment.streamers().stream().filter(Streamer::winner).count());
    }

    @Test
    void theWinnerCommitsBeforeTheOthersDo() {
        List<StreamerCandidate> candidates = List.of(terrain(0, 5), terrain(4, 4), terrain(-4, 3));
        StrikeAttachment attachment = AttachmentPlanner.plan(candidates, GROUND, 11);
        Streamer winner = attachment.streamers().stream().filter(Streamer::winner).findFirst().orElseThrow();
        for (Streamer streamer : attachment.streamers()) {
            if (streamer.winner()) continue;
            assertTrue(streamer.startsAt() >= winner.startsAt(),
                "a losing streamer started before the winner did");
        }
    }

    @Test
    void streamersClimbAndTaper() {
        StrikeAttachment attachment = AttachmentPlanner.plan(List.of(rod(0, 5)), GROUND, 13);
        Streamer streamer = attachment.streamers().getFirst();
        assertTrue(!streamer.segments().isEmpty());
        for (LightningSegment segment : streamer.segments()) {
            assertTrue(segment.end().y() > segment.start().y(), "a streamer climbs");
            assertTrue(segment.endWidth() < segment.startWidth(), "and thins as it goes");
        }
    }

    @Test
    void growthFollowsTheLeaderAndStopsAtFull() {
        Streamer streamer = AttachmentPlanner.plan(List.of(rod(0, 4)), GROUND, 17)
            .streamers().getFirst();
        assertEquals(0f, streamer.growth(0f));
        // Float-cast of the double start makes the comparison land a hair past it; still nothing.
        assertEquals(0f, streamer.growth((float) streamer.startsAt()), 1e-5f);
        assertTrue(streamer.growth((float) (streamer.startsAt() + 1) / 2f) > 0);
        assertEquals(1f, streamer.growth(1f), 1e-6);
        assertEquals(1f, streamer.growth(2f), 1e-6, "growth must not run past the attachment");
    }

    @Test
    void theSameStrikePicksTheSameWinner() {
        List<StreamerCandidate> candidates = List.of(terrain(0, 5), rod(6, 2), terrain(-3, 4));
        for (long seed = 0; seed < 50; seed++) {
            StrikeAttachment first = AttachmentPlanner.plan(candidates, GROUND, seed);
            StrikeAttachment second = AttachmentPlanner.plan(candidates, GROUND, seed);
            assertEquals(first.point(), second.point(), "two clients must agree on where the bolt landed");
            assertEquals(first.onRod(), second.onRod());
        }
    }

    @Test
    void theOrderCandidatesArriveInCannotDecideAStrike() {
        // A level does not list blocks in the same order on every client, so the planner may not
        // depend on it: equal weights are broken on position instead.
        StreamerCandidate a = terrain(2, 4);
        StreamerCandidate b = terrain(-2, 4);
        StrikeAttachment forward = AttachmentPlanner.plan(List.of(a, b), GROUND, 5);
        StrikeAttachment reversed = AttachmentPlanner.plan(List.of(b, a), GROUND, 5);
        assertEquals(forward.point(), reversed.point());
    }
}
