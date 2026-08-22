package dev.tempestfx.audio;

import dev.tempestfx.math.StrikeSeed;
import java.util.List;

/**
 * Layers thunder by distance so a near strike reads as {@code CRACK -> body -> rolling tail} while a
 * far one reads as {@code soft transient -> long roll}. Selection is a pure function of the
 * replicated strike seed, so all clients build the same event.
 */
public final class DistanceThunderSoundStrategy implements ThunderSoundStrategy {
    private static final double NEAR_BLOCKS = 30;
    private static final double MID_BLOCKS = 110;
    private static final double FAR_BLOCKS = 280;

    @Override
    public List<ThunderLayer> select(double distance, long seed, float intensity) {
        long variant = Math.floorMod(seed, 3);
        float pitch = 0.94f + (float) StrikeSeed.unit(seed, 0x7ea1) * 0.12f;
        if (distance < NEAR_BLOCKS) {
            ThunderProfile head = variant == 0 ? ThunderProfile.CLOSE_CRACK : ThunderProfile.CLOSE_HEAVY;
            return List.of(
                new ThunderLayer(head, 0, 1f, pitch),
                new ThunderLayer(ThunderProfile.IMPACT_THUMP, 0, 0.85f, pitch * 0.96f),
                // A three-second body, not the seven-second tail: close thunder is short and violent.
                new ThunderLayer(ThunderProfile.ROLL_BODY, 6 + (int) (variant * 2), 0.5f, pitch * 0.9f));
        }
        if (distance < MID_BLOCKS) {
            return List.of(
                new ThunderLayer(variant == 0 ? ThunderProfile.CLOSE_HEAVY : ThunderProfile.MEDIUM_RUMBLE, 0, 1f, pitch),
                new ThunderLayer(ThunderProfile.ROLL_FAR, 11 + (int) variant * 3, 0.45f, pitch * 0.9f));
        }
        if (distance < FAR_BLOCKS) {
            return List.of(
                new ThunderLayer(ThunderProfile.MEDIUM_RUMBLE, 0, 0.7f, pitch * 0.9f),
                new ThunderLayer(ThunderProfile.LONG_ROLLING_THUNDER, 18 + (int) variant * 4, 0.85f, pitch * 0.86f));
        }
        return List.of(
            new ThunderLayer(ThunderProfile.DISTANT_THUNDER, 0, 0.8f, pitch * 0.86f),
            new ThunderLayer(ThunderProfile.LONG_ROLLING_THUNDER, 24, 0.7f, pitch * 0.82f));
    }
}
