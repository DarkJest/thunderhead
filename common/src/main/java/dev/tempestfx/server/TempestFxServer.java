package dev.tempestfx.server;

import dev.tempestfx.TempestFx;
import dev.tempestfx.entity.BallLightning;
import dev.tempestfx.entity.BallLightningMotion;
import dev.tempestfx.entity.TempestEntities;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.mixin.LightningBoltAccessor;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gameplay side of Thunderhead.
 *
 * <p>Everything random here is derived from {@link StrikeSeed} using the bolt's position and entity
 * id - the same inputs the clients use - so the ball lightning a strike leaves behind matches the
 * flash the players just saw.
 */
public final class TempestFxServer {
    private static final ServerConfigManager CONFIGS = new ServerConfigManager();
    /** Written on the loading or server thread, read on the server thread; volatile for the handoff. */
    private static volatile ServerConfig config = new ServerConfig().validate();

    private TempestFxServer() {}

    /** Loads the gameplay config. Called once by the loader bootstrap. */
    public static void load(Path configDirectory) {
        config = CONFIGS.load(configDirectory);
        TempestFx.log().info("Server features: near-miss damage {}, ball lightning {}",
            config.nearMiss.enabled ? "on" : "off", config.ballLightning.enabled ? "on" : "off");
    }

    public static ServerConfig config() { return config; }

    /**
     * Called when a bolt is added to a server level.
     */
    public static void onLightningSpawn(ServerLevel level, LightningBolt bolt) {
        // A visual-only bolt is scenery: vanilla skips its entire damage block, which is how the
        // skeleton horse trap flashes without electrocuting whoever triggered it. Clients still draw
        // the flash - only the gameplay half is skipped, exactly as vanilla does.
        if (bolt instanceof LightningBoltAccessor accessor && accessor.tempestfx$isVisualOnly()) return;

        Vec3 position = bolt.position();
        long seed = StrikeSeed.of(position.x, position.y, position.z, bolt.getId());
        if (config.nearMiss.enabled) applyNearMissDamage(level, bolt, position);
        if (config.ballLightning.enabled && TempestEntities.available()) maybeSpawnBallLightning(level, position, seed);
    }

    private static void applyNearMissDamage(ServerLevel level, LightningBolt bolt, Vec3 position) {
        ServerConfig.NearMiss settings = config.nearMiss;
        if (settings.maxDamage <= 0) return;
        AABB box = new AABB(position, position).inflate(settings.radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!settings.affectMobs && !(target instanceof Player)) continue;
            Vec3 at = target.position();
            // Vanilla's own box, not a sphere approximating it: anything inside is vanilla's to hurt.
            if (NearMissDamage.insideVanillaBox(at.x - position.x, at.y - position.y, at.z - position.z)) {
                continue;
            }
            double distance = at.distanceTo(position);
            float damage = NearMissDamage.damageAt(distance, settings.radius, settings.maxDamage);
            if (damage <= 0) continue;
            target.hurt(bolt.damageSources().lightningBolt(), damage);
            if (settings.igniteSeconds > 0 && !target.fireImmune()
                && NearMissDamage.ignites(distance, settings.radius, settings.igniteFraction)) {
                target.igniteForSeconds(settings.igniteSeconds);
            }
        }
    }

    private static void maybeSpawnBallLightning(ServerLevel level, Vec3 position, long seed) {
        ServerConfig.BallLightning settings = config.ballLightning;
        if (StrikeSeed.unit(seed, 0xba11) >= settings.chancePerStrike) return;
        // Never materialise it on top of someone: that reads as a bug, not as a phenomenon.
        for (Player player : level.players()) {
            if (player.position().distanceTo(position) < settings.minimumSpawnDistance) return;
        }

        double angle = StrikeSeed.unit(seed, 0xba12) * Math.PI * 2;
        double offset = 1.0 + StrikeSeed.unit(seed, 0xba13) * 2.0;
        Vec3 spawn = new Vec3(
            position.x + Math.cos(angle) * offset,
            position.y + BallLightningMotion.HOVER_HEIGHT,
            position.z + Math.sin(angle) * offset);

        float radius = (float) (settings.minRadius
            + StrikeSeed.unit(seed, 0xba14) * (settings.maxRadius - settings.minRadius));
        int lifetime = (int) Math.round(20 * (settings.minSeconds
            + StrikeSeed.unit(seed, 0xba15) * (settings.maxSeconds - settings.minSeconds)));
        BallLightning.spawn(level, spawn, StrikeSeed.derive(seed, 0xba16), radius, lifetime);
    }
}
