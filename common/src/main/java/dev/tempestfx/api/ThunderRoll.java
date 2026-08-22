package dev.tempestfx.api;

import dev.tempestfx.math.Vec3d;
import java.util.Objects;

/**
 * A rolling thunder event raised on its own, with no lightning in front of it.
 *
 * @param position          where the storm is, used for bearing and distance
 * @param seed              decides the layout of layers and channels; the same seed rolls the same event
 * @param durationTicks     {@code 0} to let the seed choose, otherwise 80..320 ticks (4 to 16 seconds)
 * @param flashesPerSecond  {@code 0} to let the seed choose, otherwise 15..100 distant channels a second
 */
public record ThunderRoll(Vec3d position, long seed, int durationTicks, int flashesPerSecond) {
    public ThunderRoll {
        Objects.requireNonNull(position, "position");
        if (!position.finite()) throw new IllegalArgumentException("roll position must be finite");
        durationTicks = Math.max(0, Math.min(durationTicks, 320));
        flashesPerSecond = Math.max(0, Math.min(flashesPerSecond, 100));
    }

    /** Everything about the event decided by its seed. */
    public static ThunderRoll at(Vec3d position, long seed) { return new ThunderRoll(position, seed, 0, 0); }
}
