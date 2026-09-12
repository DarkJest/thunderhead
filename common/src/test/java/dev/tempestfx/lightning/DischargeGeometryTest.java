package dev.tempestfx.lightning;

import dev.tempestfx.api.*;
import dev.tempestfx.math.Vec3d;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DischargeGeometryTest {
    @Test void coarseBackbonesRetainCanonicalVerticesAndAttachedForks() {
        var generator = new DischargeGeometryStrategy();
        var start = new Vec3d(10, 190, -10); var end = new Vec3d(0, 64, 0);
        var bolt = new LightningBolt(start, end, 12345, 1, LightningGenerationConfig.high());
        var full = generator.generate(bolt, LightningKind.NEGATIVE_GROUND, LightningLod.FULL);
        var vertices = new HashSet<Vec3d>();
        for (var segment : full.branches().getFirst().segments()) { vertices.add(segment.start()); vertices.add(segment.end()); }
        for (var lod : LightningLod.values()) {
            var geometry = generator.generate(bolt, LightningKind.NEGATIVE_GROUND, lod);
            assertEquals(geometry, generator.generate(bolt, LightningKind.NEGATIVE_GROUND, lod));
            var trunk = geometry.branches().getFirst().segments();
            assertEquals(start, trunk.getFirst().start()); assertEquals(end, trunk.getLast().end());
            var retained = new HashSet<Vec3d>();
            for (int i = 0; i < trunk.size(); i++) {
                assertTrue(vertices.contains(trunk.get(i).start()));
                retained.add(trunk.get(i).start());
                if (i > 0) assertEquals(trunk.get(i - 1).end(), trunk.get(i).start());
            }
            for (var branch : geometry.branches().subList(1, geometry.branches().size())) {
                assertTrue(retained.contains(branch.segments().getFirst().start()), "disconnected fork at " + lod);
            }
        }
    }

    @Test void lowBudgetRetainsBothEndpoints() {
        var base = LightningGenerationConfig.high();
        var config = new LightningGenerationConfig(9, 6, .6, 1, .7, .5, .3, .6, .1, 3, 0, 0, 0, 32);
        var bolt = new LightningBolt(new Vec3d(0, 200, 0), Vec3d.ZERO, 5, 1, config);
        var geometry = new DischargeGeometryStrategy().generate(bolt, LightningKind.NEGATIVE_GROUND, LightningLod.FULL);
        assertTrue(geometry.segmentCount() <= 32);
        assertEquals(Vec3d.ZERO, geometry.branches().getFirst().segments().getLast().end());
    }

    @Test void cloudKindsHaveDistinctTimeProfilesWithoutGroundContact() {
        var ground = FlashTimeline.plan(12345, 4, true, LightningKind.NEGATIVE_GROUND);
        var cloud = FlashTimeline.plan(12345, 4, true, LightningKind.INTRACLOUD);
        var positive = FlashTimeline.plan(12345, 4, true, LightningKind.POSITIVE_GROUND);
        assertFalse(LightningKind.INTRACLOUD.contactsGround());
        assertFalse(LightningKind.INTERCLOUD.contactsGround());
        assertTrue(cloud.leaderTicks() > ground.leaderTicks());
        assertTrue(positive.decayTicks() > ground.decayTicks());
        assertTrue(positive.pulses().size() <= 2);
        assertNotEquals(cloud, FlashTimeline.plan(12345, 4, true, LightningKind.INTERCLOUD));
        var bolt = new LightningBolt(new Vec3d(0, 192, 0), new Vec3d(100, 192, 0), 12345, 1, LightningGenerationConfig.high());
        var strategy = new DischargeGeometryStrategy();
        assertNotEquals(strategy.generate(bolt, LightningKind.INTRACLOUD, LightningLod.FULL),
            strategy.generate(bolt, LightningKind.INTERCLOUD, LightningLod.FULL));
    }
}
