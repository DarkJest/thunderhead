package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leader has to be seen to build, and the return stroke has to be seen to climb.
 *
 * <p>Both are the difference between a channel that appears and a channel that happens, which is the
 * whole point of the strike lifecycle.
 */
class SteppedLeaderTest {
    private static final EnvelopeProfile STEPPED = EnvelopeProfile.DEFAULT;
    private static final float SAMPLE = 0.02f;

    @Test
    void theLeaderAdvancesAndPausesRatherThanSliding() {
        // The property that matters is the shape, not the ratio of samples: the channel should sit
        // at one length for a stretch, jump, and sit again — once per step.
        LightningEnvelope envelope = new LightningEnvelope(0x1, STEPPED);
        float span = STEPPED.propagationTicks();

        java.util.Set<Float> plateaus = new java.util.HashSet<>();
        int run = 0;
        int longestPause = 0;
        float previous = envelope.propagation(0);
        for (float time = SAMPLE; time < span; time += SAMPLE) {
            float value = envelope.propagation(time);
            if (value - previous < 1e-5f) {
                run++;
                longestPause = Math.max(longestPause, run);
                plateaus.add(Math.round(value * 10000f) / 10000f);
            } else {
                run = 0;
            }
            previous = value;
        }
        assertEquals(STEPPED.leaderSteps(), plateaus.size(),
            "the leader should rest at one length per step, not " + plateaus.size() + " of them");
        assertTrue(longestPause >= 4,
            "the longest pause was " + longestPause + " samples; that reads as a smooth slide");
    }

    @Test
    void theLeaderNeverRetreatsAndAlwaysArrives() {
        LightningEnvelope envelope = new LightningEnvelope(0x2, STEPPED);
        float previous = -1;
        for (float time = 0; time <= STEPPED.propagationTicks() + 1; time += SAMPLE) {
            float value = envelope.propagation(time);
            assertTrue(value >= previous - 1e-6f, "the leader went backwards at " + time);
            assertTrue(value >= 0 && value <= 1, "propagation out of range: " + value);
            previous = value;
        }
        assertEquals(1f, envelope.propagation(STEPPED.propagationTicks()), 1e-6);
        assertEquals(1f, envelope.propagation(STEPPED.propagationTicks() * 2), 1e-6);
    }

    @Test
    void everyStepIsBigEnoughToSee() {
        // A step that moves less than a few percent of the channel is indistinguishable from a
        // smooth reveal, which would make the whole feature invisible.
        LightningEnvelope envelope = new LightningEnvelope(0x3, STEPPED);
        assertTrue(1.0 / STEPPED.leaderSteps() > 0.04,
            STEPPED.leaderSteps() + " steps is too fine to read as stepping");
        assertTrue(envelope.propagation(STEPPED.propagationTicks() / STEPPED.leaderSteps() * 0.9f) > 0.05f,
            "the first step should have landed by the end of its own slot");
    }

    @Test
    void turningItOffRestoresTheSmoothReveal() {
        LightningEnvelope smooth = new LightningEnvelope(0x4, STEPPED.withoutSteps());
        float previous = smooth.propagation(0);
        for (float time = SAMPLE; time < STEPPED.propagationTicks(); time += SAMPLE) {
            float value = smooth.propagation(time);
            assertTrue(value > previous, "a smooth leader must advance on every sample");
            previous = value;
        }
        assertEquals(0, STEPPED.withoutSteps().leaderSteps());
        assertFalse(STEPPED.withoutSteps().stepped());
    }

    // ------------------------------------------------------------------ return stroke

    @Test
    void theReturnStrokeClimbsFromTheGroundToTheCloud() {
        LightningEnvelope envelope = new LightningEnvelope(0x5, STEPPED);
        float start = STEPPED.propagationTicks();
        float span = STEPPED.returnStrokeTicks();

        // Just after attachment the ground end is the bright one.
        assertTrue(envelope.returnStrokeBoost(1.0, start) > envelope.returnStrokeBoost(0.0, start),
            "the front starts at the ground");
        // Most of the way through, the cloud end is.
        float late = start + span * 0.95f;
        assertTrue(envelope.returnStrokeBoost(0.0, late) > envelope.returnStrokeBoost(1.0, late),
            "the front finishes at the cloud");
    }

    @Test
    void thereIsNoReturnStrokeBeforeTheLeaderLands() {
        LightningEnvelope envelope = new LightningEnvelope(0x6, STEPPED);
        for (float time = 0; time < STEPPED.propagationTicks() - SAMPLE; time += SAMPLE) {
            for (double along = 0; along <= 1; along += 0.1) {
                assertEquals(1f, envelope.returnStrokeBoost(along, time), 1e-6,
                    "the channel brightened before it had attached, at " + time);
            }
        }
    }

    @Test
    void theBoostIsBoundedAndSettlesBack() {
        LightningEnvelope envelope = new LightningEnvelope(0x7, STEPPED);
        for (float time = 0; time < STEPPED.durationTicks(); time += SAMPLE) {
            for (double along = 0; along <= 1; along += 0.25) {
                float boost = envelope.returnStrokeBoost(along, time);
                assertTrue(boost >= 1f && boost <= 3.6f, "boost out of range: " + boost);
            }
        }
        assertEquals(1f, envelope.returnStrokeBoost(0.5, STEPPED.durationTicks()), 1e-6,
            "the channel must be uniform again long before it fades");
    }

    @Test
    void onlyChannelsWithALeaderGetAReturnStroke() {
        // An aerial discharge has no attachment and no ground end, so a front climbing "up" it would
        // be describing something that did not happen.
        for (DischargeType type : DischargeType.values()) {
            EnvelopeProfile profile = DischargeProfiles.of(type).envelope();
            assertEquals(type.reachesGround(), profile.stepped(),
                type + ": stepping and ground contact must agree");
            LightningEnvelope envelope = new LightningEnvelope(1, profile);
            if (!profile.stepped()) {
                assertEquals(1f, envelope.returnStrokeBoost(0.5, profile.propagationTicks() + 0.1f), 1e-6);
            }
        }
    }

    @Test
    void aPositiveFlashStepsMoreCoarselyThanANegativeOne() {
        int negative = DischargeProfiles.of(DischargeType.NEGATIVE_CLOUD_TO_GROUND).envelope().leaderSteps();
        int positive = DischargeProfiles.of(DischargeType.POSITIVE_CLOUD_TO_GROUND).envelope().leaderSteps();
        assertTrue(positive < negative, positive + " against " + negative);
        assertTrue(positive > 0);
    }
}
