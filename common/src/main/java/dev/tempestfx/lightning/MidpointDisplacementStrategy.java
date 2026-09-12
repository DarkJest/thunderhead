package dev.tempestfx.lightning;

import dev.tempestfx.math.Bounds3d;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Recursive midpoint displacement with direction-aware forking.
 *
 * <p>A channel starts as one straight segment. Every generation inserts a midpoint offset inside the
 * local orthonormal frame of its parent segment, and the offset amplitude decays by
 * {@code roughness}. Offsets are additionally clamped against the parent segment length so the
 * channel stays a channel instead of knotting into itself.
 */
public final class MidpointDisplacementStrategy implements LightningGeometryStrategy {
    /** Half-width of the main channel core, in blocks, before layer and config scaling. */
    private static final double BASE_HALF_WIDTH = 0.25;
    /**
     * Half-width of a fork relative to the main channel, per level of depth. An explicit ladder,
     * not a power of {@code branchDecay}, which is tuned for displacement and brightness.
     */
    private static final double[] BRANCH_WIDTH_TIER = { 1.0, 0.52, 0.3, 0.18, 0.11 };
    /** Displacement is never allowed past this fraction of the segment it subdivides. */
    private static final double MAX_OFFSET_RATIO = 0.55;
    /** Interior nodes are sampled with this stride so fork cost stays proportional to channel length. */
    private static final int FORK_CANDIDATE_STRIDE = 8;

    @Override
    public LightningGeometry generate(LightningBolt bolt) {
        List<LightningBranch> branches = new ArrayList<>();
        int[] budget = { bolt.config().maxSegments() };
        generateBranch(branches, budget, bolt.start(), bolt.end(), bolt.seed(), bolt.config(),
            0, bolt.intensity(), 0, 1);
        growCanopy(branches, budget, bolt);

        Bounds3d bounds = Bounds3d.empty();
        for (LightningBranch branch : branches) {
            for (LightningSegment segment : branch.segments()) {
                bounds = bounds.include(segment.start()).include(segment.end());
            }
        }
        return new LightningGeometry(branches, bounds, bolt.seed());
    }

    /** Near-horizontal intracloud channels spreading out from the cloud base. */
    private void growCanopy(List<LightningBranch> output, int[] budget, LightningBolt bolt) {
        LightningGenerationConfig config = bolt.config();
        if (config.canopyBranches() <= 0 || config.canopySpread() <= 0) return;
        double height = Math.abs(bolt.start().y() - bolt.end().y());
        if (height < 8) return;

        SplittableRandom random = new SplittableRandom(StrikeSeed.derive(bolt.seed(), 0xca7095));
        // Canopy channels are wide but thin, so they need their own generation budget rather than
        // the fork rules, which taper aggressively toward a tip.
        LightningGenerationConfig canopyConfig = config
            .withGenerations(Math.max(4, config.generations() - 1))
            .withBranchProbability(config.branchProbability() * 0.8);

        for (int index = 0; index < config.canopyBranches() && budget[0] > 0; index++) {
            double angle = random.nextDouble(Math.PI * 2);
            double length = height * config.canopySpread() * (0.55 + random.nextDouble() * 0.9);
            // Anchored just under the cloud base and drifting slightly downward as it travels.
            Vec3d origin = bolt.start().add(
                random.nextDouble(-6, 6), random.nextDouble(-10, 4), random.nextDouble(-6, 6));
            Vec3d tip = origin.add(
                Math.cos(angle) * length,
                -length * random.nextDouble(0.05, 0.28),
                Math.sin(angle) * length);
            if (tip.distanceTo(origin) < 1) continue;
            generateBranch(output, budget, origin, tip, random.nextLong(), canopyConfig,
                1, bolt.intensity() * 0.85, 0, 0.35);
        }
    }

    private void generateBranch(List<LightningBranch> output, int[] budget, Vec3d start, Vec3d end, long seed,
                                LightningGenerationConfig config, int depth, double intensity,
                                double alongStart, double alongEnd) {
        if (budget[0] <= 0) return;
        SplittableRandom random = new SplittableRandom(seed);

        int generations = Math.max(1, config.generations() - depth);
        int pointCount = (1 << generations) + 1;
        if (pointCount - 1 > budget[0]) {
            generations = Math.max(1, 31 - Integer.numberOfLeadingZeros(Math.max(2, budget[0])));
            pointCount = (1 << generations) + 1;
        }

        double[] px = new double[pointCount];
        double[] py = new double[pointCount];
        double[] pz = new double[pointCount];
        px[0] = start.x(); py[0] = start.y(); pz[0] = start.z();
        px[1] = end.x(); py[1] = end.y(); pz[1] = end.z();
        int count = 2;

        double amplitude = config.displacement() * Math.pow(config.branchDecay(), depth);
        for (int generation = 0; generation < generations; generation++) {
            count = subdivide(px, py, pz, count, amplitude, random);
            amplitude *= config.roughness();
        }

        int segmentCount = Math.min(count - 1, budget[0]);
        List<LightningSegment> segments = new ArrayList<>(segmentCount);
        double depthScale = Math.pow(config.branchDecay(), depth);
        double alongSpan = alongEnd - alongStart;
        for (int index = 0; index < segmentCount; index++) {
            double t0 = index / (double) (count - 1);
            double t1 = (index + 1) / (double) (count - 1);
            segments.add(new LightningSegment(
                new Vec3d(px[index], py[index], pz[index]),
                new Vec3d(px[index + 1], py[index + 1], pz[index + 1]),
                depth,
                halfWidth(depth, t0),
                halfWidth(depth, t1),
                intensity * depthScale,
                alongStart + alongSpan * t0,
                alongStart + alongSpan * t1,
                random.nextLong()));
        }
        budget[0] -= segments.size();
        output.add(new LightningBranch(depth, seed, segments));

        if (segments.isEmpty()) return;
        growMicroBranches(output, budget, px, py, pz, count, seed, config, depth, intensity * depthScale,
            alongStart, alongSpan, random);
        if (depth >= config.maxBranchDepth()) return;
        growForks(output, budget, px, py, pz, count, start.distanceTo(end), config, depth, intensity,
            alongStart, alongSpan, random);
    }

    /** Inserts one displaced midpoint between every pair of consecutive points. */
    private static int subdivide(double[] px, double[] py, double[] pz, int count, double amplitude,
                                 SplittableRandom random) {
        int target = count * 2 - 1;
        // Walk backwards so the expansion happens in place without a second array.
        px[target - 1] = px[count - 1];
        py[target - 1] = py[count - 1];
        pz[target - 1] = pz[count - 1];
        for (int index = count - 2; index >= 0; index--) {
            int write = index * 2;
            double ax = px[index], ay = py[index], az = pz[index];
            double bx = px[write + 2], by = py[write + 2], bz = pz[write + 2];

            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double limit = length * MAX_OFFSET_RATIO;
            if (length > 1.0e-9) { dx /= length; dy /= length; dz /= length; }

            // Local orthonormal frame: side and up are perpendicular to the segment direction.
            double rx = Math.abs(dy) < 0.9 ? 0 : 1, ry = Math.abs(dy) < 0.9 ? 1 : 0, rz = 0;
            double sx = dy * rz - dz * ry, sy = dz * rx - dx * rz, sz = dx * ry - dy * rx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0e-9) { sx = 1; sy = 0; sz = 0; sl = 1; }
            sx /= sl; sy /= sl; sz /= sl;
            double ux = sy * dz - sz * dy, uy = sz * dx - sx * dz, uz = sx * dy - sy * dx;

            double lateral = clampOffset(gaussian(random) * amplitude, limit);
            double vertical = clampOffset(gaussian(random) * amplitude * 0.65, limit);
            px[write + 1] = (ax + bx) * 0.5 + sx * lateral + ux * vertical;
            py[write + 1] = (ay + by) * 0.5 + sy * lateral + uy * vertical;
            pz[write + 1] = (az + bz) * 0.5 + sz * lateral + uz * vertical;
            px[write] = ax; py[write] = ay; pz[write] = az;
        }
        return target;
    }

    /** Short stubs that break the silhouette of the main channel without spawning full forks. */
    private void growMicroBranches(List<LightningBranch> output, int[] budget, double[] px, double[] py, double[] pz,
                                   int count, long seed, LightningGenerationConfig config, int depth,
                                   double intensity, double alongStart, double alongSpan, SplittableRandom random) {
        if (config.microBranchProbability() <= 0 || depth > 1) return;
        for (int index = 3; index < count - 2 && budget[0] > 0; index += 3) {
            if (random.nextDouble() > config.microBranchProbability()) continue;
            double along = index / (double) (count - 1);
            Vec3d origin = new Vec3d(px[index], py[index], pz[index]);
            Vec3d direction = new Vec3d(px[index + 1] - px[index - 1], py[index + 1] - py[index - 1],
                pz[index + 1] - pz[index - 1]).normalize();
            if (direction.lengthSquared() < 0.5) continue;
            Vec3d radial = randomPerpendicular(direction, random);
            double length = 0.55 + random.nextDouble() * 1.7;
            Vec3d tip = origin.add(radial.scale(length)).add(direction.scale(length * 0.35));
            if (tip.distanceSquaredTo(origin) < 1.0e-6) continue;
            double stubIntensity = intensity * 0.55;
            // Two rungs below their parent: stubs are texture on a channel, not limbs of it.
            double width = BASE_HALF_WIDTH * widthTier(depth + 2);
            List<LightningSegment> stub = List.of(new LightningSegment(origin, tip, LightningSegment.MICRO_DEPTH,
                width, width * 0.12, stubIntensity, alongStart + alongSpan * along,
                Math.min(1, alongStart + alongSpan * along + 0.02), random.nextLong()));
            budget[0] -= 1;
            output.add(new LightningBranch(LightningSegment.MICRO_DEPTH, StrikeSeed.derive(seed, index), stub));
        }
    }

    /** Full recursive forks, weighted toward the middle and lower part of the parent channel. */
    private void growForks(List<LightningBranch> output, int[] budget, double[] px, double[] py, double[] pz,
                           int count, double parentLength, LightningGenerationConfig config, int depth,
                           double intensity, double alongStart, double alongSpan, SplittableRandom random) {
        for (int index = FORK_CANDIDATE_STRIDE; index < count - 1 && budget[0] > 0; index += FORK_CANDIDATE_STRIDE) {
            double along = index / (double) (count - 1);
            double weight = (0.3 + 0.95 * Math.sin(Math.PI * along)) * (0.6 + 0.6 * along);
            if (random.nextDouble() > config.branchProbability() * weight) continue;

            Vec3d origin = new Vec3d(px[index], py[index], pz[index]);
            Vec3d mainDirection = new Vec3d(px[index + 1] - px[index - 1], py[index + 1] - py[index - 1],
                pz[index + 1] - pz[index - 1]).normalize();
            if (mainDirection.lengthSquared() < 0.5) continue;

            Vec3d radial = randomPerpendicular(mainDirection, random);
            double angle = config.branchAngleRadians() * (0.65 + random.nextDouble() * 0.7);
            Vec3d branchDirection = mainDirection.scale(Math.cos(angle) * config.directionBias())
                .add(radial.scale(Math.sin(angle)))
                .add(0, -0.18, 0)
                .normalize();
            if (branchDirection.lengthSquared() < 0.5) continue;

            double remaining = parentLength * (1.0 - along);
            double length = Math.max(1.1, remaining * config.branchLength() * (0.65 + random.nextDouble() * 0.7));
            double jitter = config.branchJitter() * length;
            Vec3d tip = origin.add(branchDirection.scale(length))
                .add(random.nextDouble(-jitter, jitter), 0, random.nextDouble(-jitter, jitter));
            if (tip.distanceTo(origin) < 0.5) continue;

            double branchAlongStart = Math.min(1 - 1.0e-4, alongStart + alongSpan * along);
            double branchAlongEnd = Math.min(1, Math.max(branchAlongStart + 1.0e-4,
                branchAlongStart + alongSpan * (1 - along) * 0.6));
            generateBranch(output, budget, origin, tip, random.nextLong(), config, depth + 1,
                intensity, branchAlongStart, branchAlongEnd);
        }
    }

    /** Main channel stays near-uniform and swells toward the ground; forks taper to a point. */
    private static double halfWidth(int depth, double along) {
        double profile = depth == 0 ? 0.82 + 0.28 * along : 1.0 - 0.85 * along;
        return Math.max(0.004, BASE_HALF_WIDTH * widthTier(depth) * profile);
    }

    /** Width of a given fork depth relative to the trunk, saturating at the thinnest rung. */
    private static double widthTier(int depth) {
        return BRANCH_WIDTH_TIER[Math.min(Math.max(depth, 0), BRANCH_WIDTH_TIER.length - 1)];
    }

    private static double clampOffset(double offset, double limit) {
        return Math.max(-limit, Math.min(limit, offset));
    }

    private static Vec3d randomPerpendicular(Vec3d direction, SplittableRandom random) {
        for (int attempt = 0; attempt < 4; attempt++) {
            Vec3d candidate = new Vec3d(random.nextDouble(-1, 1), random.nextDouble(-0.35, 0.35), random.nextDouble(-1, 1));
            Vec3d perpendicular = candidate.subtract(direction.scale(candidate.dot(direction)));
            if (perpendicular.lengthSquared() > 1.0e-6) return perpendicular.normalize();
        }
        return Math.abs(direction.y()) < 0.9 ? direction.cross(Vec3d.UP).normalize() : new Vec3d(1, 0, 0);
    }

    /** Box-Muller normal scaled so most samples land inside the displacement amplitude. */
    private static double gaussian(SplittableRandom random) {
        double u = Math.max(1.0e-12, random.nextDouble());
        return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * random.nextDouble()) * 0.42;
    }
}
