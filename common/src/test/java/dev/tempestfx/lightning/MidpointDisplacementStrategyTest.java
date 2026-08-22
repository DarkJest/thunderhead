package dev.tempestfx.lightning;

import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidpointDisplacementStrategyTest {
    private final MidpointDisplacementStrategy strategy = new MidpointDisplacementStrategy();

    private LightningBolt bolt(long seed) {
        return LightningBolt.builder().start(new Vec3d(2, 112, -3)).end(new Vec3d(0, 64, 0)).seed(seed)
            .generations(7).branchProbability(0.35).displacement(5).build();
    }

    @Test
    void midpointGenerationProducesExpectedMainChannelResolution() {
        LightningGeometry geometry = strategy.generate(bolt(42));
        assertEquals(128, geometry.branches().getFirst().segments().size());
        assertTrue(geometry.segmentCount() >= 128);
    }

    @Test
    void channelStartsAtTheCloudAndTerminatesExactlyOnTheImpactPoint() {
        // Displacement must never move the endpoints: the bolt has to land on the strike position.
        LightningGeometry geometry = strategy.generate(bolt(4242));
        var channel = geometry.branches().getFirst().segments();
        assertEquals(new Vec3d(2, 112, -3), channel.getFirst().start());
        assertEquals(new Vec3d(0, 64, 0), channel.getLast().end());
    }

    @Test
    void channelIsContinuousWithNoGapsBetweenSegments() {
        var channel = strategy.generate(bolt(555)).branches().getFirst().segments();
        for (int index = 0; index < channel.size() - 1; index++) {
            assertEquals(channel.get(index).end(), channel.get(index + 1).start(),
                "segment " + index + " does not join the next one");
        }
    }

    @Test
    void sameSeedProducesBitIdenticalGeometry() {
        LightningGeometry first = strategy.generate(bolt(0x5eed));
        LightningGeometry second = strategy.generate(bolt(0x5eed));
        assertEquals(first.branches(), second.branches());
        assertEquals(first.bounds(), second.bounds());
        assertEquals(first.segments(), second.segments());
    }

    @Test
    void differentSeedsChangeGeometry() {
        assertNotEquals(strategy.generate(bolt(1)).branches(), strategy.generate(bolt(2)).branches());
    }

    @Test
    void branchesAreGeneratedAndTaperedBelowTheMainChannel() {
        LightningGeometry geometry = strategy.generate(bolt(99887766));
        assertTrue(geometry.branches().size() > 1, "seed should produce secondary branches");
        assertTrue(geometry.branches().stream().skip(1).allMatch(branch -> branch.depth() > 0));
        double mainWidth = geometry.branches().getFirst().segments().getFirst().startWidth();
        assertTrue(geometry.branches().stream().skip(1)
            .flatMap(branch -> branch.segments().stream())
            .allMatch(segment -> segment.startWidth() < mainWidth));
    }

    @Test
    void widthStepsDownVisiblyWithEveryLevelOfBranching() {
        // Trunk, limb, twig: each rung has to be obviously thinner than the one above it, otherwise
        // the tree reads as a tangle of identical strands.
        LightningGeometry geometry = strategy.generate(bolt(0x7712ab));
        java.util.Map<Integer, Double> widestPerDepth = new java.util.HashMap<>();
        for (LightningBranch branch : geometry.branches()) {
            if (branch.depth() == LightningSegment.MICRO_DEPTH) continue;
            for (LightningSegment segment : branch.segments()) {
                widestPerDepth.merge(branch.depth(), segment.startWidth(), Math::max);
            }
        }
        assertTrue(widestPerDepth.containsKey(2), "seed should reach a third level of branching");
        for (int depth = 1; widestPerDepth.containsKey(depth); depth++) {
            double parent = widestPerDepth.get(depth - 1);
            double child = widestPerDepth.get(depth);
            assertTrue(child < parent * 0.75,
                "depth " + depth + " at " + child + " is not visibly thinner than " + parent);
        }
    }

    @Test
    void microStubsAreMarkedAndStayShort() {
        LightningGeometry geometry = strategy.generate(bolt(0xbeef));
        assertTrue(geometry.segments().stream().anyMatch(LightningSegment::micro), "expected micro stubs");
        assertTrue(geometry.segments().stream().filter(LightningSegment::micro)
            .allMatch(segment -> segment.length() < 4.0));
    }

    @Test
    void boundsContainEveryGeneratedEndpoint() {
        LightningGeometry geometry = strategy.generate(bolt(77));
        geometry.segments().forEach(segment -> {
            assertTrue(geometry.bounds().contains(segment.start()));
            assertTrue(geometry.bounds().contains(segment.end()));
            assertTrue(segment.length() > 0);
        });
    }

    @Test
    void propagationParametersAreOrderedAndNormalised() {
        LightningGeometry geometry = strategy.generate(bolt(0x1234));
        geometry.segments().forEach(segment -> {
            assertTrue(segment.alongStart() >= 0 && segment.alongStart() <= 1,
                "alongStart out of range: " + segment.alongStart());
            assertTrue(segment.alongEnd() >= 0 && segment.alongEnd() <= 1,
                "alongEnd out of range: " + segment.alongEnd());
            assertTrue(segment.alongEnd() >= segment.alongStart());
        });
        assertEquals(0.0, geometry.branches().getFirst().segments().getFirst().alongStart(), 1e-9);
    }

    @Test
    void segmentBudgetIsNeverExceeded() {
        LightningGenerationConfig capped = new LightningGenerationConfig(7, 5.0, 0.56, 0.9, 0.64,
            Math.toRadians(38), 0.32, 0.58, 0.36, 2, 0.9, 6, 0.8, -0.18, 64);
        LightningGeometry geometry = strategy.generate(LightningBolt.builder()
            .start(new Vec3d(0, 120, 0)).end(new Vec3d(0, 64, 0)).seed(7).config(capped).build());
        assertTrue(geometry.segmentCount() <= 64, "generated " + geometry.segmentCount() + " segments");
    }

    @Test
    void canopyBranchesSpreadTheFlashAcrossTheSky() {
        // The intracloud half of a flash: without it a bolt is a thread hanging in an empty sky.
        LightningGeometry geometry = strategy.generate(bolt(0xc0ffee));
        double channelTop = geometry.branches().getFirst().segments().getFirst().start().y();
        double horizontalReach = 0;
        for (LightningSegment segment : geometry.segments()) {
            if (segment.start().y() < channelTop - 40) continue;
            horizontalReach = Math.max(horizontalReach,
                Math.hypot(segment.start().x() - 2, segment.start().z() + 3));
        }
        assertTrue(horizontalReach > 12, "flash only reached " + horizontalReach + " blocks sideways");
    }

    @Test
    void canopyCanBeTurnedOffEntirely() {
        LightningGenerationConfig flat = LightningGenerationConfig.high().withSkySpread(0f);
        LightningGeometry geometry = strategy.generate(LightningBolt.builder()
            .start(new Vec3d(0, 180, 0)).end(new Vec3d(0, 64, 0)).seed(5).config(flat).build());
        double reach = geometry.segments().stream()
            .mapToDouble(segment -> Math.hypot(segment.start().x(), segment.start().z()))
            .max().orElse(0);
        assertTrue(reach < 40, "canopy should be gone, reach was " + reach);
    }

    @Test
    void atmosphericLodDropsForksEntirely() {
        LightningGenerationConfig distant = LightningGenerationConfig.high().forLod(LightningLod.ATMOSPHERIC);
        LightningGeometry geometry = strategy.generate(LightningBolt.builder()
            .start(new Vec3d(0, 150, 0)).end(new Vec3d(0, 64, 0)).seed(3).config(distant).build());
        assertEquals(1, geometry.branches().size());
        assertEquals(16, geometry.branches().getFirst().segments().size());
    }
}
