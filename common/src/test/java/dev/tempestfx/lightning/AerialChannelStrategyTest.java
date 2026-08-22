package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialChannelStrategyTest {
    private final AerialChannelStrategy strategy = new AerialChannelStrategy();

    private static LightningBolt horizontal(long seed, double span, int segmentBudget) {
        LightningGenerationConfig config = DischargeProfiles.of(DischargeType.CLOUD_TO_CLOUD)
            .geometry(LightningGenerationConfig.high().withMaxSegments(segmentBudget), 0);
        return LightningBolt.builder()
            .start(new Vec3d(-span / 2, 190, 0))
            .end(new Vec3d(span / 2, 190, 0))
            .seed(seed)
            .config(config)
            .build();
    }

    @Test
    void theChannelTravelsTheWholeSpanRatherThanRestartingAtEveryJoint() {
        LightningGeometry geometry = strategy.generate(horizontal(0xc10d, 240, 900));
        double earliest = 1;
        double latest = 0;
        for (LightningSegment segment : geometry.segments()) {
            assertTrue(segment.alongStart() >= 0 && segment.alongStart() <= 1,
                "along out of range: " + segment.alongStart());
            assertTrue(segment.alongEnd() >= segment.alongStart());
            earliest = Math.min(earliest, segment.alongStart());
            latest = Math.max(latest, segment.alongEnd());
        }
        assertTrue(earliest < 0.05, "the leader must start at the beginning, not at " + earliest);
        assertTrue(latest > 0.9, "the leader must reach the far end, not stop at " + latest);
    }

    @Test
    void theChannelStaysHorizontalAndInsideItsOwnCorridor() {
        double span = 300;
        LightningGeometry geometry = strategy.generate(horizontal(0x5eed, span, 900));
        for (LightningSegment segment : geometry.segments()) {
            double drop = Math.abs(segment.start().y() - 190);
            assertTrue(drop < span * 0.35, "an aerial channel must not fall out of the cloud layer: " + drop);
        }
    }

    @Test
    void theSegmentBudgetIsSharedAcrossTheLegsRatherThanSpentByEachOfThem() {
        LightningGeometry geometry = strategy.generate(horizontal(0x11, 400, 200));
        assertTrue(geometry.segmentCount() <= 200 + 32,
            "generated " + geometry.segmentCount() + " segments for a 200 budget");
    }

    @Test
    void theSameSeedGivesTheSameChannel() {
        LightningGeometry first = strategy.generate(horizontal(0xabc, 260, 700));
        LightningGeometry second = strategy.generate(horizontal(0xabc, 260, 700));
        assertEquals(first.segmentCount(), second.segmentCount());
        for (int index = 0; index < first.segmentCount(); index++) {
            assertEquals(first.segments().get(index).start(), second.segments().get(index).start());
        }
    }

    @Test
    void differentSeedsGiveDifferentChannels() {
        LightningGeometry first = strategy.generate(horizontal(1, 260, 700));
        LightningGeometry second = strategy.generate(horizontal(2, 260, 700));
        assertTrue(!first.segments().getFirst().end().equals(second.segments().getFirst().end())
            || first.segmentCount() != second.segmentCount());
    }
}
