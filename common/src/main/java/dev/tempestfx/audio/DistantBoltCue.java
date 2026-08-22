package dev.tempestfx.audio;

import dev.tempestfx.math.Vec3d;

/**
 * One distant cloud-to-ground bolt scheduled inside a rolling thunder event.
 *
 * @param delayTicks  ticks after the event started
 * @param top         where the channel leaves the cloud
 * @param ground      where it terminates, offset horizontally from {@code top} to give it its lean
 * @param intensity   brightness, {@code 0..1}
 * @param seed        geometry and flicker seed for this channel
 */
public record DistantBoltCue(int delayTicks, Vec3d top, Vec3d ground, float intensity, long seed) {
    public DistantBoltCue {
        if (delayTicks < 0) throw new IllegalArgumentException("delay must not be negative");
        if (!(intensity > 0)) throw new IllegalArgumentException("intensity must be positive");
    }

    /** Horizontal lean of the channel, in blocks. */
    public double lean() { return Math.hypot(ground.x() - top.x(), ground.z() - top.z()); }

    public double height() { return top.y() - ground.y(); }
}
