package dev.tempestfx.audio;

/**
 * One scheduled component of a thunder event.
 *
 * @param profile        clip to play
 * @param extraDelayTicks delay on top of the physical propagation delay
 * @param gain           perceived loudness relative to the event loudness, {@code 0..1}
 * @param pitch          playback pitch multiplier
 */
public record ThunderLayer(ThunderProfile profile, int extraDelayTicks, float gain, float pitch) {
    public ThunderLayer {
        if (extraDelayTicks < 0) throw new IllegalArgumentException("delay must not be negative");
        if (gain <= 0) throw new IllegalArgumentException("gain must be positive");
        if (pitch <= 0) throw new IllegalArgumentException("pitch must be positive");
    }
}
