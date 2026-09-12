package dev.tempestfx.api;

import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.math.FxMath;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Server-thread API for synchronized visual flashes, real strikes and experimental ball lightning.
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

    /** Server-thread, visual-only flash to clients with the optional Thunderhead protocol. */
    public static long flash(ServerLevel level, LightningKind kind, dev.tempestfx.math.Vec3d origin,
                             dev.tempestfx.math.Vec3d target, long seed) {
        Objects.requireNonNull(level); Objects.requireNonNull(kind);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server API must run on the server thread");
        return dev.tempestfx.server.StormServer.visual(level, kind, origin, target, seed);
    }

    /** Real vanilla strike with ordinary gameplay; refuses unloaded targets. Server thread only. */
    public static net.minecraft.world.entity.LightningBolt strike(ServerLevel level, net.minecraft.core.BlockPos target) {
        Objects.requireNonNull(level); Objects.requireNonNull(target);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server API must run on the server thread");
        if (!level.hasChunkAt(target)) return null;
        var bolt = new net.minecraft.world.entity.LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(target.getX()+.5, target.getY(), target.getZ()+.5);
        return level.addFreshEntity(bolt) ? bolt : null;
    }

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
