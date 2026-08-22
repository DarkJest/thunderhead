package dev.tempestfx.world;

import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds the exposed lightning rods around the player, occasionally.
 *
 * <p>Vanilla gives a lightning rod no block entity, so there is no registry to ask and the world has
 * to be looked at. Looking at a volume is out of the question — a twenty-block radius is over a
 * hundred thousand blocks — so this reads the heightmap instead and checks only the top of each
 * column. That finds every rod that is actually exposed to the sky, which is exactly the set that
 * would corona, and misses ones tucked under an overhang, which would not.
 *
 * <p>Roughly seventeen hundred lookups, a few times a minute, on the game thread.
 */
public final class RodScanner {
    /** Columns are sampled this far out from the player, in blocks. */
    private static final int RADIUS = 20;
    /** Ticks between scans. A rod is not placed often enough to justify more. */
    public static final int INTERVAL_TICKS = 40;
    /** Rescan early if the player has moved this far since the last one. */
    private static final double MOVE_THRESHOLD = 8;
    /** Upper bound on what one scan reports. */
    private static final int MAX_RODS = 16;
    /** Where the corona sits above the rod's own block. */
    private static final double TIP_HEIGHT = 1.05;

    private Vec3d lastScanAt = new Vec3d(Double.NaN, Double.NaN, Double.NaN);
    private int cooldown;

    /** Whether a scan is worth running this tick. */
    public boolean due(Vec3d camera) {
        if (cooldown > 0) {
            cooldown--;
            return !lastScanAt.finite() || camera.distanceTo(lastScanAt) > MOVE_THRESHOLD;
        }
        return true;
    }

    /** @return rod tips near the player, closest first is not guaranteed and is not needed */
    public List<Vec3d> scan(Level level, Vec3d camera) {
        cooldown = INTERVAL_TICKS;
        lastScanAt = camera;

        List<Vec3d> tips = new ArrayList<>();
        BlockPos centre = BlockPos.containing(camera.x(), camera.y(), camera.z());
        for (int dx = -RADIUS; dx <= RADIUS && tips.size() < MAX_RODS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS && tips.size() < MAX_RODS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    centre.offset(dx, 0, dz));
                BlockPos rod = top.below();
                if (!(level.getBlockState(rod).getBlock() instanceof LightningRodBlock)) continue;
                tips.add(new Vec3d(rod.getX() + 0.5, rod.getY() + TIP_HEIGHT, rod.getZ() + 0.5));
            }
        }
        return tips;
    }

    public void clear() {
        lastScanAt = new Vec3d(Double.NaN, Double.NaN, Double.NaN);
        cooldown = 0;
    }
}
