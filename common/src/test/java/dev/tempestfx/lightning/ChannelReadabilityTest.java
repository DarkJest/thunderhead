package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every archetype has to come out looking like the same mod's lightning.
 *
 * <p>The property under test is the one that decides that: a segment must stay several times longer
 * than the ribbon drawn along it is wide. {@link dev.tempestfx.render.RibbonRenderer} turns each
 * segment into a camera-facing quad, and once the quads are as wide as they are long they overlap at
 * sharp angles rather than joining end to end - which an additive pass renders as torn lumps instead
 * of a channel.
 *
 * <p>This was not hypothetical: the first cut of the positive profile widened the channel by 2.35
 * without touching its subdivision, dropping the ratio from 5.0 to 2.9, and it looked broken.
 */
class ChannelReadabilityTest {
    /** The ratio an ordinary negative flash produces, and therefore the shape that is known to read. */
    private static final double REFERENCE_RATIO = 5.0;
    /** Nothing may fall below this. Measured, not guessed: below about 4 the joints come apart. */
    private static final double MINIMUM_RATIO = 4.0;
    /** Channel height a cloud-to-ground bolt is tuned against, matching the effect factory. */
    private static final double REFERENCE_HEIGHT = 110;

    @Test
    void everyArchetypeDrawsSegmentsLongerThanTheRibbonIsWide() {
        for (DischargeType type : DischargeType.values()) {
            double ratio = ratioFor(type);
            assertTrue(ratio >= MINIMUM_RATIO,
                type + " draws segments only " + String.format("%.2f", ratio)
                    + " times its own ribbon width; it will read as torn quads, not a channel");
        }
    }

    @Test
    void theOrdinaryFlashIsStillTheShapeEverythingElseIsMeasuredAgainst() {
        double ratio = ratioFor(DischargeType.NEGATIVE_CLOUD_TO_GROUND);
        assertTrue(Math.abs(ratio - REFERENCE_RATIO) < 2.0,
            "the tuned bolt moved to " + ratio + "; the other archetypes are calibrated against it");
    }

    /**
     * The seam a joint leaves, in blocks.
     *
     * <p>Each segment is drawn as its own camera-facing quad, so two segments meeting at an angle do
     * not share an edge: the outer corner opens by roughly {@code halfWidth * tan(kink / 2)}. On an
     * ordinary bolt that is lost among the forks. On a bare, wide, bright trunk it is what a player
     * calls "видно стыки".
     */
    private static double meanSeamFor(DischargeType type) {
        DischargeProfile profile = DischargeProfiles.of(type);
        LightningGeometry geometry = type.reachesGround() ? groundChannel(profile) : aerialChannel(profile);

        Vec3d previous = null;
        double total = 0;
        int count = 0;
        for (LightningSegment segment : geometry.segments()) {
            if (segment.branchDepth() != 0) continue;
            Vec3d direction = segment.end().subtract(segment.start()).normalize();
            if (previous != null) {
                double cosine = Math.max(-1, Math.min(1, previous.dot(direction)));
                double halfWidth = segment.startWidth() * profile.widthScale();
                total += halfWidth * Math.tan(Math.acos(cosine) / 2);
                count++;
            }
            previous = direction;
        }
        return count == 0 ? 0 : total / count;
    }

    @Test
    void aBareBrightTrunkDoesNotShowItsJoints() {
        // Mitring since removed the geometric seam outright - both segments at a joint are handed the
        // same side vector, so their vertices coincide - and this measurement no longer corresponds to
        // anything visible. It is kept because the property it pins is independently true: a
        // high-current channel is smoother than an ordinary one, and a bare trunk has no forks to hide
        // behind whatever the renderer does.
        //
        // The negative flash is the reference again: it has always looked right, and it hides its
        // joints among dense forks. Anything with the forks stripped away has to do better than it,
        // not merely as well.
        double reference = meanSeamFor(DischargeType.NEGATIVE_CLOUD_TO_GROUND);
        double positive = meanSeamFor(DischargeType.POSITIVE_CLOUD_TO_GROUND);
        assertTrue(positive < reference,
            "the positive trunk leaves a " + String.format("%.4f", positive)
                + " block seam against the ordinary flash's " + String.format("%.4f", reference)
                + "; with no branches to hide it, that reads as visible joints");
    }

    @Test
    void aerialChannelsSubdivideByLengthRatherThanByLegCount() {
        // A long channel broken into many legs must not end up with finer segments than a short one.
        int coarse = AerialChannelStrategy.generationsFor(20, 7);
        int fine = AerialChannelStrategy.generationsFor(300, 7);
        assertTrue(coarse < fine, "a 20-block leg should not be subdivided like a 300-block one");
        assertTrue(AerialChannelStrategy.generationsFor(4000, 7) <= 7, "the archetype's cap still holds");
        assertTrue(AerialChannelStrategy.generationsFor(1, 7) >= 3, "a leg still needs some shape");
    }

    /** Mean segment length over mean rendered half-width, for the trunk of a real generated channel. */
    private static double ratioFor(DischargeType type) {
        DischargeProfile profile = DischargeProfiles.of(type);
        LightningGeometry geometry = type.reachesGround() ? groundChannel(profile) : aerialChannel(profile);

        double length = 0;
        double width = 0;
        int count = 0;
        for (LightningSegment segment : geometry.segments()) {
            if (segment.branchDepth() != 0) continue;
            length += segment.length();
            width += (segment.startWidth() + segment.endWidth()) * 0.5;
            count++;
        }
        assertTrue(count > 4, type + " generated no trunk to measure");
        // widthScale is applied by the renderer rather than by the geometry, so it belongs here.
        return (length / count) / (width / count * profile.widthScale());
    }

    /** The same construction {@code LightningEffectFactory} performs for a strike. */
    private static LightningGeometry groundChannel(DischargeProfile profile) {
        double height = 160 * (profile.type() == DischargeType.POSITIVE_CLOUD_TO_GROUND ? 1.35 : 1.0);
        LightningGenerationConfig base = LightningGenerationConfig.high();
        LightningGenerationConfig selected = profile.geometry(base
            .withGenerations(7)
            .withDisplacement(base.displacement() * height / REFERENCE_HEIGHT), 1.0);
        return new MidpointDisplacementStrategy().generate(LightningBolt.builder()
            .start(new Vec3d(0, 64 + height, 0))
            .end(new Vec3d(0, 64, 0))
            .seed(0x9001)
            .config(selected)
            .build());
    }

    /** The same construction {@code SkyDischargeSystem} performs for an ambient discharge. */
    private static LightningGeometry aerialChannel(DischargeProfile profile) {
        double span = switch (profile.type()) {
            case INTRACLOUD -> 50;
            case MEGAFLASH -> 700;
            default -> 220;
        };
        LightningGenerationConfig selected = profile.geometry(LightningGenerationConfig.high()
            .withGenerations(7)
            .withDisplacement(Math.max(1.5, span * 0.055))
            .withMaxSegments(1900), 0);
        return new AerialChannelStrategy().generate(LightningBolt.builder()
            .start(new Vec3d(-span / 2, 190, 0))
            .end(new Vec3d(span / 2, 190, 0))
            .seed(0x9002)
            .config(selected)
            .build());
    }
}
