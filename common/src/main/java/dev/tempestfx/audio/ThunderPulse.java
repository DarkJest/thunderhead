package dev.tempestfx.audio;

import dev.tempestfx.math.Vec3d;

/**
 * One scheduled component of a rolling thunder event.
 *
 * @param delayTicks ticks after the event started
 * @param profile    which component layer to play
 * @param position   where the pulse is heard from
 * @param gain       perceived loudness at the listener, {@code 0..1}
 * @param pitch      playback pitch, also the layer's effective filter shift
 * @param impact     how much of a physical punch this pulse carries, {@code 0..1}; drives the camera
 */
public record ThunderPulse(int delayTicks, ThunderProfile profile, Vec3d position,
                           float gain, float pitch, float impact) {
    public ThunderPulse {
        if (delayTicks < 0) throw new IllegalArgumentException("delay must not be negative");
        if (!(gain > 0)) throw new IllegalArgumentException("gain must be positive");
        if (!(pitch > 0)) throw new IllegalArgumentException("pitch must be positive");
    }
}
