package dev.tempestfx.server;

import dev.tempestfx.api.LightningKind;
import dev.tempestfx.math.StrikeSeed;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.storm.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;

/** Server-owned cells, optional ground strikes and a small replay window. Never requests new chunks. */
public final class StormServer {
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
    private static long sequence;
    private static boolean spawningOwn;
    private StormServer() {}
    private static final class State {
        final Map<Long, StormCell> cells = new TreeMap<>();
        final Set<UUID> announced = new HashSet<>();
        final ArrayDeque<StormEvent> recent = new ArrayDeque<>();
        final List<Ground> ground = new ArrayList<>();
        long budgetTick = -1; int published;
    }
    private record Ground(long at, LightningBolt bolt) {}
    public static void clear() { STATES.clear(); spawningOwn = false; }
    /** Purely visual, authoritative flash for integrations; returns -1 when the bounded budget rejects it. */
    public static long visual(ServerLevel level, LightningKind kind, Vec3d origin, Vec3d target, long seed) {
        long id = ++sequence;
        var event = new StormEvent(id, seed, level.getGameTime()+4, level.dimension().location().toString(), kind.ordinal(), origin, target, 1, 3);
        return publish(level, STATES.computeIfAbsent(level, key -> new State()), event) ? id : -1;
    }

    public static void tick(ServerLevel level) {
        var state = STATES.computeIfAbsent(level, key -> new State());
        long now = level.getGameTime();
        var config = TempestFxServer.config().storm;
        while (!state.recent.isEmpty() && state.recent.getFirst().startTick() < now - 160) state.recent.removeFirst();
        state.announced.retainAll(level.players().stream().map(p -> p.getUUID()).toList());
        for (var player : level.players()) {
            boolean fresh = state.announced.add(player.getUUID());
            if (fresh || now % 200 == 0) {
                StormNetwork.send(player, new StormEvent(0, 0, now, level.dimension().location().toString(), -1, Vec3d.ZERO, Vec3d.ZERO, 0, 0));
                if (fresh) for (var event : state.recent) if (player.position().distanceToSqr(event.target().x(), event.target().y(), event.target().z())
                    <= config.broadcastDistance * config.broadcastDistance) StormNetwork.send(player, event);
            }
        }
        for (int i = state.ground.size() - 1; i >= 0; i--) {
            var pending = state.ground.get(i);
            if (pending.at() > now) continue;
            state.ground.remove(i);
            if (!config.enabled || !config.groundStrikes || !level.hasChunkAt(pending.bolt().blockPosition())) continue;
            spawningOwn = true;
            try { level.addFreshEntity(pending.bolt()); } finally { spawningOwn = false; }
        }
        if (!config.enabled || !level.dimensionType().hasSkyLight() || !level.isThundering()) { state.cells.clear(); return; }
        Set<Long> wanted = new TreeSet<>();
        for (var player : level.players()) {
            int x = Math.floorDiv(player.getBlockX(), 256), z = Math.floorDiv(player.getBlockZ(), 256);
            wanted.add(((long) x << 32) | (z & 0xffffffffL));
        }
        state.cells.keySet().retainAll(wanted);
        while (state.cells.size() > config.maxCells) {
            Long last = state.cells.keySet().stream().reduce((a,b)->b).orElseThrow(); state.cells.remove(last);
        }
        for (long key : wanted) {
            if (state.cells.size() >= config.maxCells) break;
            if (!state.cells.containsKey(key)) {
                int x = (int) (key >> 32), z = (int) key;
                long seed = StrikeSeed.of(x, config.cloudBaseY, z, level.getSeed() ^ now);
                state.cells.put(key, new StormCell(seed, now, new Vec3d(x * 256.0 + 128, config.cloudBaseY, z * 256.0 + 128)));
            }
        }
        state.cells.values().removeIf(cell -> cell.expired(now));
        for (var cell : state.cells.values()) {
            var discharge = cell.tick(now, level.getThunderLevel(1), config.groundStrikes, config.flashesPerSecond);
            if (discharge == null) continue;
            Vec3d target = discharge.target(), origin = discharge.origin();
            long seed = discharge.seed(); LightningBolt bolt = null;
            if (discharge.kind().contactsGround()) {
                BlockPos column = BlockPos.containing(origin.x(), 0, origin.z());
                if (!level.hasChunkAt(column) || state.ground.size() >= 16) continue;
                target = ContactSelector.select(level, column);
                if (target == null) continue;
                origin = new Vec3d(origin.x(), Math.max(origin.y(), target.y() + 32), origin.z());
                bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.moveTo(target.x(), target.y(), target.z());
                seed = StrikeSeed.of(target.x(), target.y(), target.z(), bolt.getId());
            }
            if (!StormEvent.valid(origin) || !StormEvent.valid(target)) continue;
            var event = new StormEvent(++sequence, seed, now + 4, level.dimension().location().toString(),
                discharge.kind().ordinal(), origin, target, 1, 3, bolt == null ? -1 : bolt.getId());
            if (publish(level, state, event) && bolt != null) state.ground.add(new Ground(now + 5, bolt));
        }
    }

    public static void observe(ServerLevel level, LightningBolt bolt) {
        if (spawningOwn) return;
        var at = bolt.position();
        long seed = StrikeSeed.of(at.x, at.y, at.z, bolt.getId());
        double height = Math.max(32, TempestFxServer.config().storm.cloudBaseY - at.y);
        Vec3d target = new Vec3d(at.x, at.y, at.z);
        Vec3d origin = target.add(StrikeSeed.signed(seed, 11) * height * .25, height, StrikeSeed.signed(seed, 12) * height * .25);
        if (!StormEvent.valid(origin) || !StormEvent.valid(target)) return;
        publish(level, STATES.computeIfAbsent(level, key -> new State()), new StormEvent(++sequence, seed,
            level.getGameTime(), level.dimension().location().toString(), LightningKind.NEGATIVE_GROUND.ordinal(), origin, target, 1, 3, bolt.getId()));
    }

    private static boolean publish(ServerLevel level, State state, StormEvent event) {
        long now = level.getGameTime();
        if (state.budgetTick != now) { state.budgetTick = now; state.published = 0; }
        if (state.published++ >= 16) return false;
        state.recent.addLast(event);
        while (state.recent.size() > 64) state.recent.removeFirst();
        double radius = TempestFxServer.config().storm.broadcastDistance;
        for (var player : level.players()) if (player.position().distanceToSqr(event.target().x(), event.target().y(), event.target().z()) <= radius * radius)
            StormNetwork.send(player, event);
        return true;
    }
}
