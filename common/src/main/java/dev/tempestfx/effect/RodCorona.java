package dev.tempestfx.effect;

import dev.tempestfx.lightning.ArcGeometry;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * The corona on one lightning rod standing in a charged field.
 *
 * <p>Not a warning: nothing here knows a strike is coming, and nothing could — vanilla decides
 * lightning on the tick it spawns the bolt, so there is no lead time to predict from. This is driven
 * by the storm's own accumulated charge instead, which is both what actually makes a rod hiss and
 * crackle and the only honest thing available. When a bolt then does take the rod, the corona reads
 * as having been the warning it never technically was.
 *
 * <p>Arc geometry lives in a preallocated array and is regenerated a few times a second rather than
 * per frame, the same arrangement the entity discharge uses, so a field of rods allocates nothing.
 */
public final class RodCorona {
    /** Filaments at the tip. Few and short: this is a hiss, not a discharge. */
    public static final int ARCS = 4;
    private static final int GENERATIONS = 2;
    public static final int POINTS_PER_ARC = ArcGeometry.pointCount(GENERATIONS);
    /** How often the arcs are redrawn, in ticks. Faster than the eye tracks, slower than a frame. */
    private static final int REGENERATE_EVERY = 3;
    /** Length of one filament, in blocks. */
    private static final double ARC_LENGTH = 0.34;
    /** Below this charge a rod is simply a rod. */
    public static final float THRESHOLD = 0.45f;

    private final Vec3d tip;
    private final long seed;
    private final double[] points = new double[ARCS * POINTS_PER_ARC * 3];
    private float charge;
    private float previousCharge;
    private int age;

    public RodCorona(Vec3d tip, long seed) {
        this.tip = tip;
        this.seed = seed;
        regenerate();
    }

    /**
     * @param stormCharge the storm's electrical activity, {@code 0..1}
     */
    public void tick(float stormCharge) {
        previousCharge = charge;
        // Ramps in over the top half of the storm's charge, so an ordinary shower does nothing.
        float target = stormCharge <= THRESHOLD ? 0
            : (stormCharge - THRESHOLD) / (1 - THRESHOLD);
        // Approached rather than set: a rod brightens and dims over seconds, it does not flick.
        charge += (target - charge) * 0.06f;
        if (charge < 1.0e-3f) charge = 0;
        age++;
        if (age % REGENERATE_EVERY == 0) regenerate();
    }

    /** Interpolated output, {@code 0..1}. */
    public float charge(float partialTick) {
        return previousCharge + (charge - previousCharge) * Math.max(0, Math.min(1, partialTick));
    }

    public boolean visible() { return charge > 0.01f; }

    public Vec3d tip() { return tip; }

    /** Flattened xyz triples relative to the tip; {@link #ARCS} arcs of {@link #POINTS_PER_ARC}. */
    public double[] arcPoints() { return points; }

    /**
     * Filaments climbing off the tip, splayed outward.
     *
     * <p>They point up rather than in every direction because that is where the field gradient is:
     * a rod discharges toward the cloud, not toward the roof it is bolted to.
     */
    private void regenerate() {
        long frameSeed = StrikeSeed.derive(seed, age);
        for (int arc = 0; arc < ARCS; arc++) {
            long arcSeed = StrikeSeed.derive(frameSeed, arc);
            double angle = StrikeSeed.unit(arcSeed, 0x1) * Math.PI * 2;
            double spread = 0.25 + StrikeSeed.unit(arcSeed, 0x2) * 0.5;
            double length = ARC_LENGTH * (0.6 + StrikeSeed.unit(arcSeed, 0x3) * 0.8);
            ArcGeometry.generate(arcSeed, 0, 0, 0,
                Math.cos(angle) * length * spread,
                length,
                Math.sin(angle) * length * spread,
                length * 0.45, GENERATIONS, points, arc * POINTS_PER_ARC * 3);
        }
    }
}
