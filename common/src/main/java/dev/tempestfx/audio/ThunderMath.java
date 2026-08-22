package dev.tempestfx.audio;

import dev.tempestfx.math.FxMath;

/**
 * Physical and engine-side sound maths.
 *
 * <p>The engine part matters as much as the physical part. Minecraft's sound engine derives the
 * OpenAL linear-attenuation range from the volume argument
 * ({@code range = max(volume, 1) * attenuationDistance}) and clamps the source gain to
 * {@code [0,1]}. A thunder clip played at "volume 1.0" is therefore inaudible beyond 16 blocks,
 * which is why vanilla passes {@code 10000} for its own thunder. Thunderhead instead solves for the
 * volume argument that reproduces a chosen perceived loudness at the listener's distance, keeping
 * both the direction cue and a believable falloff.
 */
public final class ThunderMath {
    public static final double SPEED_OF_SOUND_BLOCKS_PER_SECOND = 343.0;
    /** Default {@code attenuation_distance} of a sound definition, in blocks. */
    public static final double ATTENUATION_BLOCKS = 16.0;
    /** Distance at which thunder is considered to be at reference loudness. */
    private static final double LOUDNESS_REFERENCE_BLOCKS = 18.0;
    /** OpenAL cannot reproduce a gain of exactly 1 at a non-zero distance; stay just below. */
    private static final float MAX_GAIN = 0.98f;

    private ThunderMath() {}

    public static double delaySeconds(double distanceBlocks) {
        return Math.max(0, distanceBlocks) / SPEED_OF_SOUND_BLOCKS_PER_SECOND;
    }

    public static int delayTicks(double distanceBlocks) {
        return (int) Math.round(delaySeconds(distanceBlocks) * 20.0);
    }

    /** Perceptual loudness Thunderhead wants the listener to hear, before engine compensation. */
    public static float thunderGain(double distanceBlocks, float intensity, float configuredVolume) {
        if (configuredVolume <= 0 || intensity <= 0) return 0;
        double spread = Math.sqrt(LOUDNESS_REFERENCE_BLOCKS
            / (LOUDNESS_REFERENCE_BLOCKS + Math.max(0, distanceBlocks)));
        return (float) FxMath.clamp(spread * intensity * configuredVolume, 0.0, MAX_GAIN);
    }

    /**
     * Volume argument for {@code Level#playLocalSound} that makes the listener hear
     * {@code targetGain} at {@code distanceBlocks}.
     */
    public static float spatialVolume(double distanceBlocks, float targetGain) {
        float gain = FxMath.clamp(targetGain, 0f, MAX_GAIN);
        if (gain <= 0) return 0;
        double distance = Math.max(0, distanceBlocks);
        double gainAtUnitVolume = 1.0 - distance / ATTENUATION_BLOCKS;
        if (gainAtUnitVolume >= gain) {
            // Close enough that the default range already reaches the listener: attenuate the source.
            return (float) (gain / gainAtUnitVolume);
        }
        // Too far for the default range: widen it until the requested gain survives the distance.
        return (float) Math.max(1.0, distance / (ATTENUATION_BLOCKS * (1.0 - gain)));
    }

    /** Gain the engine will actually produce, mirroring {@code SoundEngine#play}. Used by tests. */
    public static float perceivedGain(double distanceBlocks, float volume) {
        if (volume <= 0) return 0;
        double range = Math.max(volume, 1.0f) * ATTENUATION_BLOCKS;
        double sourceGain = FxMath.clamp(volume, 0f, 1f);
        return (float) (sourceGain * FxMath.clamp(1.0 - Math.max(0, distanceBlocks) / range, 0.0, 1.0));
    }
}
