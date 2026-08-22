package dev.tempestfx.effect;

import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.StrikeTarget;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeSequenceSystemTest {
    /** Seed chosen because it plans a multi-stroke flash. */
    private static final long MULTI_STROKE_SEED = seedWithReturnStrokes();

    @Test
    void returnStrokesAreReleasedOnScheduleAndOnlyOnce() {
        TempestConfig config = new TempestConfig().validate();
        StrikeSequenceSystem system = new StrikeSequenceSystem();
        system.onStrike(primary(MULTI_STROKE_SEED), config);
        assertTrue(system.pendingCount() > 0);

        List<Integer> released = new ArrayList<>();
        for (int tick = 0; tick < 60; tick++) {
            system.tick((position, seed, intensity, stroke) -> released.add(stroke));
        }
        assertEquals(0, system.pendingCount());
        assertTrue(released.size() >= 1);
        for (int index = 0; index < released.size(); index++) {
            assertEquals(index + 1, released.get(index), "strokes must arrive in order");
        }
    }

    @Test
    void aReleasedStrokeCannotStartAFlashOfItsOwn() {
        TempestConfig config = new TempestConfig().validate();
        StrikeSequenceSystem system = new StrikeSequenceSystem();
        LightningStrikeFxEvent returnStroke = new LightningStrikeFxEvent(Vec3d.ZERO, MULTI_STROKE_SEED, 0.6f,
            LightningEnvironment.land(0x777777, true), StrikeTarget.none(), 1);
        system.onStrike(returnStroke, config);
        assertEquals(0, system.pendingCount(), "expanding a return stroke would chain forever");
    }

    @Test
    void reducedFlashingRemovesTheWholeSequence() {
        TempestConfig config = new TempestConfig();
        config.general.reducedFlashing = true;
        config.validate();
        StrikeSequenceSystem system = new StrikeSequenceSystem();
        system.onStrike(primary(MULTI_STROKE_SEED), config);
        assertEquals(0, system.pendingCount());
    }

    @Test
    void pendingStrokesStayBoundedUnderAStorm() {
        TempestConfig config = new TempestConfig().validate();
        StrikeSequenceSystem system = new StrikeSequenceSystem();
        for (int index = 0; index < 500; index++) {
            system.onStrike(primary(MULTI_STROKE_SEED + index), config);
        }
        assertTrue(system.pendingCount() <= 64, "queued " + system.pendingCount());
        system.clear();
        assertEquals(0, system.pendingCount());
    }

    @Test
    void strokesLandNearTheOriginalStrike() {
        TempestConfig config = new TempestConfig().validate();
        StrikeSequenceSystem system = new StrikeSequenceSystem();
        Vec3d origin = new Vec3d(100, 70, -40);
        system.onStrike(new LightningStrikeFxEvent(origin, MULTI_STROKE_SEED, 1f,
            LightningEnvironment.land(0x777777, true)), config);
        for (int tick = 0; tick < 60; tick++) {
            system.tick((position, seed, intensity, stroke) -> {
                assertTrue(position.distanceTo(origin) <= 2.61, "stroke landed " + position.distanceTo(origin));
                assertEquals(origin.y(), position.y(), 1e-9, "height is resolved by the caller, not here");
            });
        }
    }

    private static LightningStrikeFxEvent primary(long seed) {
        return new LightningStrikeFxEvent(Vec3d.ZERO, seed, 1f, LightningEnvironment.land(0x777777, true));
    }

    private static long seedWithReturnStrokes() {
        for (long seed = 1; seed < 1000; seed++) {
            if (!dev.tempestfx.lightning.StrikeSequence.plan(seed, 1f, 3).isEmpty()) return seed;
        }
        throw new IllegalStateException("no multi-stroke seed found");
    }
}
