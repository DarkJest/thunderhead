package dev.tempestfx.api;

import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.math.FxMath;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The server half of the API: ball lightning, and nothing else.
 *
 * <p>Separate from {@link TempestFxApi} because a sphere is a replicated entity the server owns and
 * ticks, not a client-side visual. Call it from server-side code with a {@link ServerLevel}.
 */
public final class TempestFxServerApi {
    public static final float MIN_RADIUS = 0.1f;
    public static final float MAX_RADIUS = 3f;
    /** Two minutes, so a sphere nobody removes still cleans itself up. */
    public static final int MAX_LIFETIME_TICKS = 2400;

    private TempestFxServerApi() {}

    /**
     * Spawns a ball lightning sphere.
     *
     * @param level     the level to spawn in
     * @param position  where to put it; it will settle to hovering height on its own
     * @param seed      decides its wobble, sparks and drift; replicated, so every client agrees
     * @param radius    nominal radius in blocks, clamped to {@value #MIN_RADIUS}..{@value #MAX_RADIUS}
     * @param lifetime  ticks before it bursts, clamped to 1..{@value #MAX_LIFETIME_TICKS}
     * @return the spawned entity, or {@code null} if the entity type is not registered
     */
    public static BallLightning spawnBallLightning(ServerLevel level, Vec3 position, long seed,
                                                   float radius, int lifetime) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        if (!TempestEntities.available()) return null;
        return BallLightning.spawn(level, position, seed,
            (float) FxMath.clamp(radius, MIN_RADIUS, MAX_RADIUS),
            Math.max(1, Math.min(lifetime, MAX_LIFETIME_TICKS)));
    }
}
