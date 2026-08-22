package dev.tempestfx.lightning;

import dev.tempestfx.math.Vec3d;

/**
 * One camera-facing ribbon quad of a channel.
 *
 * @param start          world position of the upper end
 * @param end            world position of the lower end
 * @param branchDepth    0 for the main channel, 1..n for forks, {@link #MICRO_DEPTH} for stubs
 * @param startWidth     half-width at {@code start}, in blocks, before layer and config scaling
 * @param endWidth       half-width at {@code end}
 * @param intensity      per-segment brightness multiplier, already carrying branch decay
 * @param alongStart     normalised distance travelled from the cloud at {@code start}, {@code 0..1}
 * @param alongEnd       normalised distance travelled from the cloud at {@code end}, {@code 0..1}
 * @param visibilityMask deterministic bit pattern driving per-segment electrical instability
 */
public record LightningSegment(Vec3d start, Vec3d end, int branchDepth, double startWidth, double endWidth,
                               double intensity, double alongStart, double alongEnd, long visibilityMask) {
    /** Depth marker for the short stubs that texture the main channel. */
    public static final int MICRO_DEPTH = 15;

    public LightningSegment {
        if (!start.finite() || !end.finite()) throw new IllegalArgumentException("segment coordinates must be finite");
        if (startWidth <= 0 || endWidth < 0 || intensity < 0) {
            throw new IllegalArgumentException("invalid segment appearance");
        }
        if (start.distanceSquaredTo(end) < 1.0e-12) {
            throw new IllegalArgumentException("segment must have non-zero length");
        }
    }

    public double length() { return start.distanceTo(end); }

    public boolean micro() { return branchDepth == MICRO_DEPTH; }
}
