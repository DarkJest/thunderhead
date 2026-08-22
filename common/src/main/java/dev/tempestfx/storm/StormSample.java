package dev.tempestfx.storm;

/**
 * What the client can see of the weather this tick, with no Minecraft types attached.
 *
 * <p>Sampled once per tick by the client and handed to the storm model, which is what keeps the
 * model itself testable and free of a world reference.
 *
 * @param thundering   whether the level is in a thunderstorm at all
 * @param rainLevel    interpolated rain strength, {@code 0..1}
 * @param thunderLevel interpolated thunder strength, {@code 0..1}
 * @param gameTime     replicated world time, the only clock every client shares
 * @param cloudBaseY   world height the cloud layer sits at
 * @param viewDistance how far the player can actually see, in blocks
 */
public record StormSample(boolean thundering, float rainLevel, float thunderLevel,
                          long gameTime, double cloudBaseY, double viewDistance) {
    /** No storm: what a clear sky, a loading screen or a disabled feature all look like. */
    public static StormSample calm(long gameTime, double cloudBaseY, double viewDistance) {
        return new StormSample(false, 0f, 0f, gameTime, cloudBaseY, viewDistance);
    }
}
