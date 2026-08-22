package dev.tempestfx.sky;

import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuminousStructuresTest {
    private static final Vec3d ANCHOR = new Vec3d(0, 450, 0);

    private static LuminousStructure sprite(long seed) {
        return LuminousStructures.create(LuminousProfiles.of(TransientLuminousEvent.RED_SPRITE),
            ANCHOR, seed, 1.0);
    }

    private static LuminousStructure jet(long seed) {
        return LuminousStructures.create(LuminousProfiles.of(TransientLuminousEvent.BLUE_JET),
            ANCHOR, seed, 1.0);
    }

    @Test
    void aSpriteHangsBelowItsAnchorAndAJetClimbsAboveIts() {
        LuminousStructure sprite = sprite(0x5b817e);
        for (LightningSegment segment : sprite.filaments()) {
            assertTrue(segment.start().y() <= ANCHOR.y() + 1e-6,
                "a sprite filament rose above the anchor: " + segment.start().y());
        }
        assertTrue(sprite.filaments().stream().anyMatch(s -> s.end().y() < ANCHOR.y() - sprite.height() * 0.4),
            "the tendrils must actually reach down");

        LuminousStructure jet = jet(0x81e7);
        for (LightningSegment segment : jet.filaments()) {
            assertTrue(segment.end().y() >= ANCHOR.y() - 1e-6,
                "a jet filament fell below the cloud top: " + segment.end().y());
        }
    }

    @Test
    void aSpriteHasTheCoolFringeAboveItAndAJetDoesNot() {
        assertTrue(!sprite(1).wisps().isEmpty(), "a sprite without its upward wisps is half the picture");
        for (LightningSegment wisp : sprite(1).wisps()) {
            assertTrue(wisp.end().y() > wisp.start().y(), "wisps reach up");
        }
        assertEquals(0, jet(1).wisps().size());
    }

    @Test
    void everyStructureIsRevealedFromItsAnchorOutward() {
        // The reveal runs 0..1 from the anchor, so the structure has to span that range: something
        // must be present as soon as the event starts, and something must still be arriving at the
        // end of it. Elements are staggered slightly, so "at the anchor" is a small band, not zero.
        for (LuminousStructure structure : new LuminousStructure[] { sprite(7), jet(7) }) {
            double earliest = 1;
            double latest = 0;
            for (LightningSegment segment : structure.filaments()) {
                assertTrue(segment.alongStart() >= 0 && segment.alongStart() <= 1,
                    "along out of range: " + segment.alongStart());
                assertTrue(segment.alongEnd() >= 0 && segment.alongEnd() <= 1);
                earliest = Math.min(earliest, segment.alongStart());
                latest = Math.max(latest, segment.alongEnd());
            }
            assertTrue(earliest < 0.2, "the structure only begins at " + earliest + " of its own reveal");
            assertTrue(latest > 0.75, "the structure stops developing at " + latest);
        }
    }

    @Test
    void aSpriteIsAClusterRatherThanOneShape() {
        LuminousStructure structure = sprite(0xc1);
        double spread = structure.filaments().stream()
            .mapToDouble(segment -> Math.abs(segment.start().x() - ANCHOR.x())).max().orElse(0);
        assertTrue(spread > structure.width() * 0.2,
            "the elements are stacked on one axis; a sprite is a curtain, not a column");
        assertTrue(structure.glows().size() >= 3, "each element needs its head, plus the cluster halo");
    }

    @Test
    void bothStructuresStayCheap() {
        // These are drawn at extreme distance; the whole point is that they cost far less than a bolt.
        assertTrue(sprite(3).segmentCount() < 500, "sprite used " + sprite(3).segmentCount() + " segments");
        assertTrue(jet(3).segmentCount() < 200, "jet used " + jet(3).segmentCount() + " segments");
    }

    @Test
    void theSameSeedGivesTheSameStructure() {
        LuminousStructure first = sprite(0xabcdef);
        LuminousStructure second = sprite(0xabcdef);
        assertEquals(first.segmentCount(), second.segmentCount());
        assertEquals(first.filaments().getFirst().end(), second.filaments().getFirst().end());
        assertTrue(sprite(1).filaments().size() != sprite(2).filaments().size()
            || !sprite(1).filaments().getFirst().end().equals(sprite(2).filaments().getFirst().end()),
            "different seeds must give different sprites");
    }

    @Test
    void tendrilsTaperToAPointRatherThanEndingBluntly() {
        LuminousStructure structure = sprite(0x77);
        double thinnest = structure.filaments().stream()
            .mapToDouble(LightningSegment::endWidth).min().orElse(1);
        double thickest = structure.filaments().stream()
            .mapToDouble(LightningSegment::startWidth).max().orElse(0);
        assertTrue(thinnest < thickest * 0.15, "nothing tapered: " + thinnest + " against " + thickest);
    }

    @Test
    void aJetWidensAsItClimbs() {
        LuminousStructure structure = jet(0x99);
        LightningSegment first = structure.filaments().getFirst();
        // The trunk is the first thing generated, and its base is the narrowest part of a jet.
        assertTrue(first.startWidth() < first.endWidth(),
            "a jet leaves the cloud narrow and opens out, not the other way round");
    }
}
