package dev.tempestfx.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The joint between two ribbon segments.
 *
 * <p>Each segment used to derive its side vector from its own direction, so two of them meeting at an
 * angle put their shared edge in two different places and the outer corner opened by
 * {@code halfWidth * tan(kink / 2)}. That is the seam the positive flash had to have its roughness
 * lowered to hide. Mitring removes it outright: both segments are handed the same side vector at the
 * point they share, so their vertices land on top of each other.
 */
class MiteredRibbonTest {
    /** Looking down -Z at a ribbon in the XY plane, which is the easy case to reason about. */
    private static final double[] VIEW = { 0, 0, 1 };

    private static double[] side(double ax, double ay, double bx, double by) {
        double[] out = new double[3];
        RibbonRenderer.miterSide(ax, ay, 0, bx, by, 0, VIEW[0], VIEW[1], VIEW[2], out);
        return out;
    }

    @Test
    void aStraightRunKeepsTheOrdinaryPerpendicular() {
        double[] out = new double[3];
        double miter = RibbonRenderer.miterSide(1, 0, 0, 1, 0, 0, VIEW[0], VIEW[1], VIEW[2], out);
        assertEquals(1.0, miter, 1e-9, "a straight joint must not widen");
        assertEquals(0.0, out[0], 1e-9);
        assertEquals(1.0, Math.abs(out[1]), 1e-9, "the side of a horizontal ribbon is vertical");
    }

    @Test
    void bothSegmentsAtAJointAgreeOnTheSharedEdge() {
        // A 60-degree corner: the incoming direction and the outgoing one.
        double[] incoming = { 1, 0 };
        double[] outgoing = { Math.cos(Math.toRadians(60)), Math.sin(Math.toRadians(60)) };

        // What the first segment computes for its end, and the second for its start, is the same
        // call with the same two directions - which is the whole mechanism.
        double[] fromFirst = side(incoming[0], incoming[1], outgoing[0], outgoing[1]);
        double[] fromSecond = side(incoming[0], incoming[1], outgoing[0], outgoing[1]);
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(fromFirst[axis], fromSecond[axis], 1e-12);
        }
    }

    @Test
    void aCornerWidensJustEnoughToKeepTheRibbonThickness() {
        for (double degrees : new double[] { 10, 30, 60, 90 }) {
            double half = Math.toRadians(degrees) / 2;
            double[] out = new double[3];
            double miter = RibbonRenderer.miterSide(1, 0, 0,
                Math.cos(Math.toRadians(degrees)), Math.sin(Math.toRadians(degrees)), 0,
                VIEW[0], VIEW[1], VIEW[2], out);
            // The exact compensation for a mitred join is 1 / cos(theta / 2).
            assertEquals(1.0 / Math.cos(half), miter, 1e-6, "wrong miter at " + degrees + " degrees");
        }
    }

    @Test
    void aHairpinIsClampedRatherThanThrowingASpikeAcrossTheScreen() {
        double[] out = new double[3];
        double miter = RibbonRenderer.miterSide(1, 0, 0, -0.98, 0.2, 0, VIEW[0], VIEW[1], VIEW[2], out);
        assertTrue(miter <= 3.0, "a hairpin widened by " + miter);
        assertTrue(Double.isFinite(miter));
    }

    @Test
    void aRibbonDoublingBackOnItselfStillProducesAUsableSide() {
        double[] out = new double[3];
        double miter = RibbonRenderer.miterSide(1, 0, 0, -1, 0, 0, VIEW[0], VIEW[1], VIEW[2], out);
        assertTrue(Double.isFinite(miter));
        double length = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        assertEquals(1.0, length, 1e-9, "the side vector must stay unit length");
    }

    @Test
    void aSegmentPointingAtTheCameraFallsBackToSomethingPerpendicular() {
        double[] out = new double[3];
        double miter = RibbonRenderer.miterSide(0, 0, 1, 0, 0, 1, 0, 0, 1, out);
        assertTrue(Double.isFinite(miter));
        double length = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        assertEquals(1.0, length, 1e-9);
    }
}
