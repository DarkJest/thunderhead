package dev.tempestfx.entity;

import dev.tempestfx.audio.TempestSounds;
import dev.tempestfx.audio.ThunderProfile;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.server.ServerConfig;
import dev.tempestfx.server.TempestFxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ball lightning: a slow, floating plasma sphere left behind by a strike.
 */
public class BallLightning extends Entity {
    private static final EntityDataAccessor<Float> DATA_RADIUS =
        SynchedEntityData.defineId(BallLightning.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
        SynchedEntityData.defineId(BallLightning.class, EntityDataSerializers.INT);
    /** The visual seed, replicated rather than rolled locally. */
    private static final EntityDataAccessor<Long> DATA_SEED =
        SynchedEntityData.defineId(BallLightning.class, EntityDataSerializers.LONG);
    /** Server age in ticks. {@code tickCount} restarts when the entity enters a client's view. */
    private static final EntityDataAccessor<Integer> DATA_AGE =
        SynchedEntityData.defineId(BallLightning.class, EntityDataSerializers.INT);

    private static final int SURFACE_PROBE_DEPTH = 6;
    /** How often the server restates its age. Cheap, and the client interpolates in between. */
    private static final int AGE_SYNC_INTERVAL = 20;

    private int ageOffset;
    private int contactCooldown;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private int lerpSteps;

    public BallLightning(EntityType<? extends BallLightning> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server-side factory; the seed comes from the strike so the visual matches the flash. */
    public static BallLightning spawn(ServerLevel level, Vec3 position, long seed, float radius, int lifetimeTicks) {
        BallLightning ball = TempestEntities.ballLightning().create(level);
        if (ball == null) return null;
        ball.setPos(position.x, position.y, position.z);
        ball.entityData.set(DATA_SEED, seed);
        ball.entityData.set(DATA_RADIUS, radius);
        ball.entityData.set(DATA_LIFETIME, lifetimeTicks);
        level.addFreshEntity(ball);
        return ball;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 0.45f);
        builder.define(DATA_LIFETIME, 120);
        builder.define(DATA_SEED, 0L);
        builder.define(DATA_AGE, 0);
    }

    public float nominalRadius() { return entityData.get(DATA_RADIUS); }

    public int lifetime() { return entityData.get(DATA_LIFETIME); }

    public long visualSeed() { return entityData.get(DATA_SEED); }

    /** Age in ticks as the server counts it: the local counter shifted onto the server's. */
    public float age(float partialTick) { return tickCount + ageOffset + partialTick; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_AGE.equals(key) && level().isClientSide) ageOffset = entityData.get(DATA_AGE) - tickCount;
    }

    /** Output curve shared by the renderer and the damage logic. */
    public float output(float partialTick) {
        return BallLightningMotion.output(age(partialTick), lifetime());
    }

    public float renderRadius(float partialTick) {
        return BallLightningMotion.radius(visualSeed(), age(partialTick), nominalRadius());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Clients only age and interpolate. Removal comes from the server.
            interpolate();
            return;
        }
        // A summoned sphere still needs a replicated seed; position and entity id both qualify.
        if (visualSeed() == 0) {
            entityData.set(DATA_SEED, StrikeSeed.of(getX(), getY(), getZ(), getId()));
        }
        if (tickCount % AGE_SYNC_INTERVAL == 0) entityData.set(DATA_AGE, tickCount);
        if (tickCount >= lifetime()) {
            burstAndDiscard((ServerLevel) level());
            return;
        }
        drift();
        if (contactCooldown > 0) contactCooldown--;
        else discharge((ServerLevel) level());
    }

    /**
     * Client-side smoothing between position updates.
     *
     * <p>{@code Entity#lerpTo} snaps, and the tracker only sends a position every other tick. Hold
     * the target and walk toward it over the remaining steps, the way living entities do.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, steps);
    }

    private void interpolate() {
        if (lerpSteps <= 0) return;
        setPos(
            getX() + (lerpX - getX()) / lerpSteps,
            getY() + (lerpY - getY()) / lerpSteps,
            getZ() + (lerpZ - getZ()) / lerpSteps);
        lerpSteps--;
    }

    private void drift() {
        double surfaceY = surfaceBelow();
        Vec3 velocity = getDeltaMovement();
        double vy = BallLightningMotion.stepVerticalVelocity(getY(), surfaceY, velocity.y);
        double vx = BallLightningMotion.driftX(visualSeed(), tickCount);
        double vz = BallLightningMotion.driftZ(visualSeed(), tickCount);
        setDeltaMovement(vx, vy, vz);
        setPos(getX() + vx, getY() + vy, getZ() + vz);
    }

    /** Highest solid or fluid surface just under the ball, so it follows the ground contour. */
    public double surfaceBelow() {
        BlockPos.MutableBlockPos cursor = blockPosition().mutable();
        for (int step = 0; step < SURFACE_PROBE_DEPTH && cursor.getY() > level().getMinBuildHeight(); step++) {
            if (!level().getFluidState(cursor).isEmpty()
                || !level().getBlockState(cursor).getCollisionShape(level(), cursor).isEmpty()) {
                return cursor.getY() + 1.0;
            }
            cursor.move(0, -1, 0);
        }
        return getY() - BallLightningMotion.HOVER_HEIGHT;
    }

    /** Discharges into anything it touches. Damage values and toggles come from the server config. */
    private void discharge(ServerLevel level) {
        ServerConfig config = TempestFxServer.config();
        if (!config.ballLightning.contactDamage) return;
        double reach = nominalRadius() + config.ballLightning.contactRadius;
        AABB box = getBoundingBox().inflate(reach);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (target.distanceToSqr(this) > reach * reach) continue;
            target.hurt(damageSources().lightningBolt(), config.ballLightning.contactDamage());
            if (config.ballLightning.igniteSeconds > 0 && !target.fireImmune()) {
                target.igniteForSeconds(config.ballLightning.igniteSeconds);
            }
            contactCooldown = config.ballLightning.contactCooldownTicks;
            burstAndDiscard(level);
            return;
        }
    }

    /**
     * The report at the end: a short crack, a scorch where it touched down, then gone.
     *
     * <p>The mod's own arc clip, not {@code entity.lightning_bolt.impact} - that id is one of the
     * two Thunderhead suppresses client-side, so every player running the mod would watch the sphere
     * burst in silence while vanilla clients on the same server heard it.
     */
    private void burstAndDiscard(ServerLevel level) {
        SoundEvent burst = TempestSounds.event(ThunderProfile.ELECTRIC_ARC);
        level.playSound(null, getX(), getY(), getZ(),
            burst != null ? burst : SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.4f, 1.6f);
        ServerConfig config = TempestFxServer.config();
        if (config.ballLightning.scorchGround) scorchBelow(level);
        discard();
    }

    /** Turns grass into dirt directly under the burst, and nothing else. Respects mobGriefing. */
    private void scorchBelow(ServerLevel level) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) return;
        BlockPos below = BlockPos.containing(getX(), surfaceBelow() - 0.5, getZ());
        if (level.getBlockState(below).is(Blocks.GRASS_BLOCK) && level.getFluidState(below).getType() == Fluids.EMPTY) {
            level.setBlockAndUpdate(below, Blocks.DIRT.defaultBlockState());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_SEED, tag.getLong("Seed"));
        entityData.set(DATA_RADIUS, tag.contains("Radius") ? tag.getFloat("Radius") : 0.45f);
        entityData.set(DATA_LIFETIME, tag.contains("Lifetime") ? tag.getInt("Lifetime") : 120);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Seed", visualSeed());
        tag.putFloat("Radius", nominalRadius());
        tag.putInt("Lifetime", lifetime());
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSquared) { return distanceSquared < 64 * 64; }
}
