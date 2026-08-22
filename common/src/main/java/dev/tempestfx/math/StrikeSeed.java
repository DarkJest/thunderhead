package dev.tempestfx.math;

/**
 * Derives every random stream used by a strike from data that the server replicates identically to
 * all clients and knows itself, so one bolt looks, sounds and behaves the same everywhere.
 *
 * <p>{@code LightningBolt.seed} on purpose is <em>not</em> used: vanilla assigns it from the
 * entity's client-side {@link java.util.Random}, so it differs per client and per join.
 */
public final class StrikeSeed {
    /** Positions are quantised to 1/16 of a block before hashing to absorb float round-trips. */
    private static final double POSITION_QUANTISATION = 16.0;

    private StrikeSeed() {}

    /**
     * Seed for a strike at a replicated world position.
     *
     * @param salt server-assigned discriminator, normally the bolt's entity id
     */
    public static long of(double x, double y, double z, long salt) {
        long hash = 0x9e3779b97f4a7c15L;
        hash = mix(hash ^ quantise(x));
        hash = mix(hash ^ quantise(y));
        hash = mix(hash ^ quantise(z));
        hash = mix(hash ^ salt);
        return hash == 0 ? 0x5deece66dL : hash;
    }

    /** Independent sub-stream of a strike seed, so subsystems never consume each other's randomness. */
    public static long derive(long seed, long salt) {
        return mix(seed ^ mix(salt));
    }

    /** Uniform value in {@code [0,1)} for an indexed sample of a seed. Free of allocation and state. */
    public static double unit(long seed, long index) {
        return (derive(seed, index) >>> 11) * 0x1.0p-53;
    }

    /** Uniform value in {@code [-1,1]} for an indexed sample of a seed. */
    public static double signed(long seed, long index) {
        return unit(seed, index) * 2.0 - 1.0;
    }

    private static long quantise(double coordinate) {
        double scaled = coordinate * POSITION_QUANTISATION;
        return Double.isFinite(scaled) ? Math.round(Math.max(-1.0e15, Math.min(1.0e15, scaled))) : 0L;
    }

    /** SplitMix64 finaliser: strong avalanche, no allocation, identical on every JVM. */
    private static long mix(long value) {
        long z = value + 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
