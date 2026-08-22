package dev.tempestfx.math;

/**
 * Allocation-free deterministic noise used by geometry, envelopes and decals.
 */
public final class Noise {
    private Noise() {}

    /** Smooth 1D value noise with {@code C1} continuity, period 1 per integer step. */
    public static double value(long seed, double x) {
        double floor = Math.floor(x);
        long cell = (long) floor;
        double t = x - floor;
        double smooth = t * t * (3.0 - 2.0 * t);
        double a = StrikeSeed.signed(seed, cell);
        double b = StrikeSeed.signed(seed, cell + 1);
        return a + (b - a) * smooth;
    }

    /**
     * Noise that is exactly periodic over {@code 2*PI}, so rings built from it never show a seam at
     * {@code theta = 0}. Built from integer harmonics with seeded phases.
     */
    public static double ring(long seed, double theta, int harmonics) {
        double sum = 0;
        double amplitude = 1;
        double norm = 0;
        for (int harmonic = 1; harmonic <= Math.max(1, harmonics); harmonic++) {
            double phase = StrikeSeed.unit(seed, harmonic) * Math.PI * 2.0;
            sum += Math.sin(theta * harmonic + phase) * amplitude;
            norm += amplitude;
            amplitude *= 0.55;
        }
        return sum / norm;
    }

    /**
     * High-frequency flicker in {@code [0,1]} sampled on a continuous timeline. Interpolating between
     * integer samples keeps the result frame-rate independent instead of stepping at 20 Hz.
     */
    public static double flicker(long seed, double time, double rate) {
        return value(seed, time * rate) * 0.5 + 0.5;
    }
}
