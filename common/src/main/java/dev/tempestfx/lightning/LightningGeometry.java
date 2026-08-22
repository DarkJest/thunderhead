package dev.tempestfx.lightning;

import dev.tempestfx.math.Bounds3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully generated, immutable bolt topology.
 */
public final class LightningGeometry {
    private final List<LightningBranch> branches;
    private final List<LightningSegment> segments;
    private final Bounds3d bounds;
    private final long seed;

    public LightningGeometry(List<LightningBranch> branches, Bounds3d bounds, long seed) {
        this.branches = List.copyOf(branches);
        this.bounds = bounds;
        this.seed = seed;
        List<LightningSegment> flattened = new ArrayList<>();
        for (LightningBranch branch : this.branches) flattened.addAll(branch.segments());
        this.segments = List.copyOf(flattened);
    }

    public List<LightningBranch> branches() { return branches; }

    /** Pre-flattened render order: main channel first, then forks in generation order. */
    public List<LightningSegment> segments() { return segments; }

    public Bounds3d bounds() { return bounds; }

    public long seed() { return seed; }

    public int segmentCount() { return segments.size(); }

    @Override
    public boolean equals(Object other) {
        return other instanceof LightningGeometry geometry
            && seed == geometry.seed && bounds.equals(geometry.bounds) && branches.equals(geometry.branches);
    }

    @Override
    public int hashCode() { return branches.hashCode() * 31 + Long.hashCode(seed); }
}
