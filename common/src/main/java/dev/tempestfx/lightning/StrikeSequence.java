package dev.tempestfx.lightning;

import dev.tempestfx.math.StrikeSeed;
import java.util.ArrayList;
import java.util.List;

/**
 * The return strokes of one flash.
 */
public final class StrikeSequence {
    private static final int MAX_RETURN_STROKES = 4;
    /** Return strokes land within this radius of the first one, in blocks. */
    private static final double MAX_OFFSET = 2.6;

    private StrikeSequence() {}

    /**
     * @param seed      primary stroke seed
     * @param intensity intensity of the primary stroke
     * @param maximum   user-configured cap on return strokes; 0 disables the feature
     * @return return strokes in ascending delay order, empty for a single-stroke flash
     */
    public static List<ReturnStroke> plan(long seed, float intensity, int maximum) {
        int cap = Math.min(MAX_RETURN_STROKES, Math.max(0, maximum));
        if (cap == 0) return List.of();

        // Roughly half of natural flashes are single-stroke; the rest carry two to five strokes.
        double roll = StrikeSeed.unit(seed, 0x5e01);
        if (roll < 0.45) return List.of();
        int count = 1 + (int) (StrikeSeed.unit(seed, 0x5e02) * cap);

        List<ReturnStroke> strokes = new ArrayList<>(count);
        int delay = 0;
        float strength = intensity;
        for (int index = 1; index <= count; index++) {
            // 30-90 ms between strokes, quantised to whole ticks but never zero.
            delay += 1 + (int) Math.round(StrikeSeed.unit(seed, 0x5e10 + index) * 2.0);
            strength *= (float) (0.62 + StrikeSeed.unit(seed, 0x5e20 + index) * 0.24);
            if (strength < 0.12f) break;
            double angle = StrikeSeed.unit(seed, 0x5e30 + index) * Math.PI * 2;
            double distance = StrikeSeed.unit(seed, 0x5e40 + index) * MAX_OFFSET;
            strokes.add(new ReturnStroke(index, delay,
                Math.cos(angle) * distance, Math.sin(angle) * distance,
                strength, StrikeSeed.derive(seed, 0x5e50 + index)));
        }
        return List.copyOf(strokes);
    }

    /**
     * @param index      1-based stroke number within the flash
     * @param delayTicks delay after the primary stroke
     * @param offsetX    horizontal offset from the primary strike point
     * @param offsetZ    horizontal offset from the primary strike point
     * @param intensity  intensity of this stroke
     * @param seed       seed for this stroke's geometry and thunder
     */
    public record ReturnStroke(int index, int delayTicks, double offsetX, double offsetZ,
                               float intensity, long seed) {}
}
