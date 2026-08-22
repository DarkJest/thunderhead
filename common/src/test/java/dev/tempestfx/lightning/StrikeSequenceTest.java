package dev.tempestfx.lightning;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeSequenceTest {
    @Test
    void sameSeedAlwaysPlansTheSameFlash() {
        assertEquals(StrikeSequence.plan(0xfeed, 1f, 3), StrikeSequence.plan(0xfeed, 1f, 3));
    }

    @Test
    void disablingTheFeatureLeavesASingleStroke() {
        for (long seed = 0; seed < 50; seed++) {
            assertTrue(StrikeSequence.plan(seed, 1f, 0).isEmpty());
        }
    }

    @Test
    void strokesAreOrderedWeakeningAndCloseToTheFirst() {
        int flashesWithReturnStrokes = 0;
        for (long seed = 0; seed < 400; seed++) {
            List<StrikeSequence.ReturnStroke> strokes = StrikeSequence.plan(seed, 1f, 3);
            if (!strokes.isEmpty()) flashesWithReturnStrokes++;

            int previousDelay = 0;
            float previousIntensity = 1f;
            int expectedIndex = 1;
            for (StrikeSequence.ReturnStroke stroke : strokes) {
                assertEquals(expectedIndex++, stroke.index());
                assertTrue(stroke.delayTicks() > previousDelay, "strokes must not land on the same tick");
                assertTrue(stroke.intensity() < previousIntensity, "each stroke must be weaker");
                assertTrue(stroke.intensity() >= 0.12f);
                double offset = Math.hypot(stroke.offsetX(), stroke.offsetZ());
                assertTrue(offset <= 2.6001, "stroke wandered " + offset + " blocks");
                previousDelay = stroke.delayTicks();
                previousIntensity = stroke.intensity();
            }
            assertTrue(strokes.size() <= 3);
        }
        // Roughly half of natural flashes carry more than one stroke; the distribution should be
        // somewhere near that rather than "always" or "never".
        assertTrue(flashesWithReturnStrokes > 120 && flashesWithReturnStrokes < 320,
            "multi-stroke flashes: " + flashesWithReturnStrokes + "/400");
    }

    @Test
    void theConfiguredCapIsRespected() {
        for (long seed = 0; seed < 200; seed++) {
            assertTrue(StrikeSequence.plan(seed, 1f, 1).size() <= 1);
            assertTrue(StrikeSequence.plan(seed, 1f, 2).size() <= 2);
            assertTrue(StrikeSequence.plan(seed, 1f, 99).size() <= 4, "hard limit must still apply");
        }
    }

    @Test
    void weakPrimaryStrokesDoNotProduceInvisibleFollowUps() {
        for (long seed = 0; seed < 200; seed++) {
            for (StrikeSequence.ReturnStroke stroke : StrikeSequence.plan(seed, 0.2f, 4)) {
                assertTrue(stroke.intensity() >= 0.12f, "invisible stroke planned: " + stroke.intensity());
            }
        }
    }
}
