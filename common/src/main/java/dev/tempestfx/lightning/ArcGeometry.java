package dev.tempestfx.lightning;

import java.util.SplittableRandom;

/**
 * Tiny midpoint-displacement generator for surface arcs.
 *
 * <p>Writes into caller-owned arrays so the crawling discharge effect never allocates. Uses the same
 * subdivision idea as {@link MidpointDisplacementStrategy}, but sized for centimetre-scale arcs that
 * hug an entity instead of a hundred-block channel.
 */
public final class ArcGeometry {
    private ArcGeometry() {}

    /** Number of points {@link #generate} writes for a given generation count. */
    public static int pointCount(int generations) { return (1 << Math.max(1, generations)) + 1; }

    /**
     * Fills {@code out} with {@code pointCount(generations)} xyz triples describing one arc.
     *
     * @param out flattened destination, at least {@code offset + pointCount(generations) * 3} long
     * @return number of points written
     */
    public static int generate(long seed, double startX, double startY, double startZ,
                               double endX, double endY, double endZ,
                               double amplitude, int generations, double[] out, int offset) {
        int generationCount = Math.max(1, generations);
        int points = pointCount(generationCount);
        SplittableRandom random = new SplittableRandom(seed);

        out[offset] = startX; out[offset + 1] = startY; out[offset + 2] = startZ;
        out[offset + 3] = endX; out[offset + 4] = endY; out[offset + 5] = endZ;
        int count = 2;

        double displacement = amplitude;
        for (int generation = 0; generation < generationCount; generation++) {
            int target = count * 2 - 1;
            copy(out, offset, count - 1, target - 1);
            for (int index = count - 2; index >= 0; index--) {
                int write = index * 2;
                int a = offset + index * 3;
                int b = offset + (write + 2) * 3;
                double ax = out[a], ay = out[a + 1], az = out[a + 2];
                double bx = out[b], by = out[b + 1], bz = out[b + 2];
                int mid = offset + (write + 1) * 3;
                out[mid] = (ax + bx) * 0.5 + (random.nextDouble() - 0.5) * displacement;
                out[mid + 1] = (ay + by) * 0.5 + (random.nextDouble() - 0.5) * displacement;
                out[mid + 2] = (az + bz) * 0.5 + (random.nextDouble() - 0.5) * displacement;
                int write3 = offset + write * 3;
                out[write3] = ax; out[write3 + 1] = ay; out[write3 + 2] = az;
            }
            count = target;
            displacement *= 0.55;
        }
        return points;
    }

    private static void copy(double[] array, int offset, int from, int to) {
        int source = offset + from * 3;
        int destination = offset + to * 3;
        array[destination] = array[source];
        array[destination + 1] = array[source + 1];
        array[destination + 2] = array[source + 2];
    }
}
