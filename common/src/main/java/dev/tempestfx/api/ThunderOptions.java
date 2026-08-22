package dev.tempestfx.api;

import dev.tempestfx.math.FxMath;

/**
 * What a strike should sound like.
 *
 * @param voice      which clip, or {@link ThunderVoice#AUTO} to choose by distance
 * @param volume     factor on the strike's own loudness, {@code 0..2}
 * @param delayTicks ticks between the flash and the sound, or {@code -1} for the honest one -
 *                   distance divided by the speed of sound, which is what the mod does itself
 */
public record ThunderOptions(ThunderVoice voice, float volume, int delayTicks) {
    /** Everything decided by the mod, exactly as an ordinary strike. */
    public static final ThunderOptions DEFAULT = new ThunderOptions(ThunderVoice.AUTO, 1f, -1);
    /** Ticks after which a delay is nonsense rather than atmosphere. */
    public static final int MAX_DELAY_TICKS = 400;

    public ThunderOptions {
        if (voice == null) voice = ThunderVoice.AUTO;
        volume = Float.isFinite(volume) ? (float) FxMath.clamp(volume, 0f, 2f) : 1f;
        delayTicks = delayTicks < 0 ? -1 : Math.min(delayTicks, MAX_DELAY_TICKS);
    }

    /** Whether the caller wants the arrival time computed from distance, as physics would. */
    public boolean delayFromDistance() { return delayTicks < 0; }

    public static ThunderOptions of(ThunderVoice voice) { return new ThunderOptions(voice, 1f, -1); }

    public static ThunderOptions silent() { return new ThunderOptions(ThunderVoice.SILENT, 0f, -1); }
}
