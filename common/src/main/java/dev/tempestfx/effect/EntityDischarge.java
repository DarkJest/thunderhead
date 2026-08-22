package dev.tempestfx.effect;

import dev.tempestfx.lightning.ArcGeometry;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;

/**
 * Residual charge crawling over one entity.
 */
public final class EntityDischarge {
    public static final int ARCS = 5;
    public static final int ARC_GENERATIONS = 3;
    public static final int POINTS_PER_ARC = ArcGeometry.pointCount(ARC_GENERATIONS);
    /** Arc shapes are re-rolled this many times per second. */
    private static final double BUCKETS_PER_TICK = 0.4;

    private final int entityId;
    private final long seed;
    private final boolean player;
    private final double[] arcPoints = new double[ARCS * POINTS_PER_ARC * 3];

    private float charge;
    private float previousCharge;
    private int ticksRemaining;
    private long bucket = Long.MIN_VALUE;
    private float width = 0.6f;
    private float height = 1.8f;
    private Vec3d anchor = Vec3d.ZERO;
    private Vec3d previousAnchor = Vec3d.ZERO;

    public EntityDischarge(int entityId, boolean player, long strikeSeed) {
        this.entityId = entityId;
        this.player = player;
        this.seed = StrikeSeed.derive(strikeSeed, 0x0d15c8a6L ^ entityId);
    }

    public int entityId() { return entityId; }

    public boolean player() { return player; }

    public float charge() { return charge; }

    public float charge(float partialTick) {
        float t = Math.max(0, Math.min(1, partialTick));
        return previousCharge + (charge - previousCharge) * t;
    }

    public Vec3d anchor(float partialTick) { return previousAnchor.lerp(anchor, Math.max(0, Math.min(1, partialTick))); }

    public double[] arcPoints() { return arcPoints; }

    public float width() { return width; }

    public float height() { return height; }

    public boolean expired() { return ticksRemaining <= 0 || charge < 0.02f; }

    public void reinforce(DischargeTarget target, float amount, int ticks) {
        charge = Math.max(charge, Math.min(1f, amount));
        previousCharge = Math.max(previousCharge, charge);
        ticksRemaining = Math.max(ticksRemaining, ticks);
        // Seed the anchor and arc shapes now, so the first frame after the strike already draws
        // arcs on the entity instead of waiting for the next tick.
        width = Math.max(0.2f, target.width());
        height = Math.max(0.5f, target.height());
        if (bucket == Long.MIN_VALUE) {
            anchor = previousAnchor = target.position();
            bucket = 0;
            rebuildArcs(0);
        }
    }

    /**
     * Advances one tick against the latest replicated snapshot of the entity.
     */
    public void tick(DischargeTarget target, float minimumSpeed, long gameTime) {
        previousCharge = charge;
        previousAnchor = anchor;
        anchor = target.position();
        width = Math.max(0.2f, target.width());
        height = Math.max(0.5f, target.height());
        ticksRemaining--;

        double speed = target.speed();
        if (speed >= minimumSpeed) {
            charge = Math.min(1f, charge * 0.965f + (float) Math.min(0.06, speed * 0.22));
        } else {
            charge *= 0.8f;
        }

        long nextBucket = (long) (gameTime * BUCKETS_PER_TICK);
        if (nextBucket != bucket) {
            bucket = nextBucket;
            rebuildArcs(nextBucket);
        }
    }

    private void rebuildArcs(long bucketIndex) {
        long bucketSeed = StrikeSeed.derive(seed, bucketIndex);
        double radius = width * 0.55;
        for (int arc = 0; arc < ARCS; arc++) {
            long arcSeed = StrikeSeed.derive(bucketSeed, arc);
            double a0 = StrikeSeed.unit(arcSeed, 1) * Math.PI * 2;
            double a1 = a0 + Math.PI * (0.35 + StrikeSeed.unit(arcSeed, 2) * 1.1);
            double y0 = height * (0.08 + StrikeSeed.unit(arcSeed, 3) * 0.85);
            double y1 = height * (0.08 + StrikeSeed.unit(arcSeed, 4) * 0.85);
            ArcGeometry.generate(arcSeed,
                Math.cos(a0) * radius, y0, Math.sin(a0) * radius,
                Math.cos(a1) * radius, y1, Math.sin(a1) * radius,
                radius * 0.9, ARC_GENERATIONS, arcPoints, arc * POINTS_PER_ARC * 3);
        }
    }
}
