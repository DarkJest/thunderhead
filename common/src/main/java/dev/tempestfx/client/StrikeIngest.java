package dev.tempestfx.client;

import dev.tempestfx.api.DischargeType;
import dev.tempestfx.api.LightningEnvironment;
import dev.tempestfx.api.LightningStrikeFxEvent;
import dev.tempestfx.api.StrikeOptions;
import dev.tempestfx.api.StrikeTarget;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.DischargeTarget;
import dev.tempestfx.lightning.DischargeSelector;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.world.LightningEnvironmentResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a vanilla bolt spawn into a Thunderhead event.
 *
 * <p>First, the seed. {@code LightningBolt.seed} is assigned from the entity's own client-side
 * random, so it differs on every client and on every rejoin; using it would make each player see a
 * different bolt for the same strike. Thunderhead derives the seed from the replicated spawn position
 * and the bolt's entity id instead, both of which arrive verbatim in the spawn packet and are known
 * to the server as well, so the client visuals and the server-side gameplay agree on one flash.
 */
public final class StrikeIngest {
    /** Horizontal tolerance for calling a strike a direct hit, in blocks. */
    private static final double DIRECT_HIT_HORIZONTAL = 1.6;
    private static final double DIRECT_HIT_BELOW = 1.5;
    private static final double DIRECT_HIT_ABOVE = 2.5;
    /** Upper bound on entities collected for the discharge effect. */
    private static final int MAX_DISCHARGE_TARGETS = 12;

    private final LightningEnvironmentResolver environments = new LightningEnvironmentResolver();
    private final LightningObservationTracker observations = new LightningObservationTracker();

    /** @return the event to publish, or {@code null} if this bolt was already handled. */
    public LightningStrikeFxEvent ingest(ClientLevel level, LightningBolt bolt, TempestConfig config) {
        if (!observations.firstObservation(bolt.getId())) return null;
        Vec3 position = bolt.position();
        Vec3d point = new Vec3d(position.x, position.y, position.z);
        long seed = StrikeSeed.of(position.x, position.y, position.z, bolt.getId());
        LightningEnvironment environment = environments.resolve(level, position);
        // Which archetype this is, decided once and carried: every subsystem downstream reads it off
        // the event instead of rolling for it again, and the roll is seeded so every client agrees.
        DischargeType type = DischargeSelector.forGroundStrike(seed, config.lightning.superboltChance);
        StrikeOptions options = StrikeOptions.builder().type(type).build();
        return new LightningStrikeFxEvent(point, seed, 1f, environment, resolveTarget(level, point), 0, options);
    }

    public void tick() { observations.tick(); }

    /** Shared surface sampler, so return strokes and debug strikes classify the ground the same way. */
    public LightningEnvironmentResolver environments() { return environments; }

    public void clear() { observations.clear(); }

    /** Finds the entity the bolt came down on, if any. */
    private StrikeTarget resolveTarget(ClientLevel level, Vec3d point) {
        AABB box = new AABB(
            point.x() - DIRECT_HIT_HORIZONTAL, point.y() - DIRECT_HIT_BELOW, point.z() - DIRECT_HIT_HORIZONTAL,
            point.x() + DIRECT_HIT_HORIZONTAL, point.y() + DIRECT_HIT_ABOVE, point.z() + DIRECT_HIT_HORIZONTAL);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean bestIsPlayer = false;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            boolean isPlayer = entity instanceof Player;
            double distance = entity.position().distanceToSqr(point.x(), point.y(), point.z());
            // Players outrank other mobs regardless of distance: the imprint effect is about them.
            // Exact ties fall back to the entity id, because the order a level hands back its
            // entities is not the same on every client and two players must not disagree about who
            // was hit.
            boolean better = best == null
                || (isPlayer && !bestIsPlayer)
                || (isPlayer == bestIsPlayer && (distance < bestDistance
                    || (distance == bestDistance && entity.getId() < best.getId())));
            if (better) { best = entity; bestDistance = distance; bestIsPlayer = isPlayer; }
        }
        if (best == null) return StrikeTarget.none();
        return new StrikeTarget(best.getId(), bestIsPlayer,
            new Vec3d(best.getX(), best.getY(), best.getZ()), best.getBbWidth(), best.getBbHeight());
    }

    /**
     * Collects entities close enough to pick up residual charge from the strike.
     */
    public List<DischargeTarget> collectDischargeTargets(ClientLevel level, Vec3d point, double radius) {
        List<DischargeTarget> targets = new ArrayList<>();
        if (radius <= 0) return targets;
        AABB box = new AABB(point.x() - radius, point.y() - radius, point.z() - radius,
            point.x() + radius, point.y() + radius, point.z() + radius);
        List<LivingEntity> candidates =
            new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive));
        candidates.sort(Comparator
            .comparingDouble((LivingEntity entity) -> entity.position().distanceToSqr(point.x(), point.y(), point.z()))
            .thenComparingInt(Entity::getId));
        for (LivingEntity entity : candidates) {
            if (targets.size() >= MAX_DISCHARGE_TARGETS) break;
            targets.add(snapshot(entity));
        }
        return targets;
    }

    /** Latest replicated snapshot of an entity, or {@code null} once it is gone. */
    public static DischargeTarget snapshot(ClientLevel level, int entityId) {
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return null;
        return snapshot(living);
    }

    private static DischargeTarget snapshot(LivingEntity entity) {
        return new DischargeTarget(entity.getId(), entity instanceof Player,
            new Vec3d(entity.getX(), entity.getY(), entity.getZ()),
            new Vec3d(entity.xOld, entity.yOld, entity.zOld),
            entity.getBbWidth(), entity.getBbHeight());
    }
}
