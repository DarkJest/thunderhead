package dev.tempestfx.world;

import dev.tempestfx.math.Vec3d;
import dev.tempestfx.strike.StreamerCandidate;
import dev.tempestfx.strike.StreamerKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds what could answer a descending leader, in a strictly bounded scan.
 *
 * <p>Runs once per strike on the game thread, never from a render callback, and reads the heightmap
 * rather than walking columns: one lookup per column over a small square, plus one block state at
 * each column top. That is roughly the same budget the foliage probe already spends, and the result
 * is cached on the effect for as long as the bolt is alive.
 *
 * <p>What it is looking for, in order of how much it matters: lightning rods, exposed metal, and
 * anything simply tall enough to compete.
 */
public final class StreamerScanner {
    /** Columns are sampled this far out from the strike, in blocks. */
    private static final int RADIUS = 7;
    /** Columns closer together than this are treated as one candidate, so a hillside is not a fence. */
    private static final double MERGE_DISTANCE = 2.5;
    /** A column has to stand this far above the strike surface to compete at all. */
    private static final double MIN_PROMINENCE = 1.5;
    /** Upper bound on candidates handed on, before the planner picks its own few. */
    private static final int MAX_CANDIDATES = 12;

    /**
     * @param surfaceY the height the strike would otherwise have terminated at
     * @return candidates in no particular order; empty when the ground is flat and bare
     */
    public List<StreamerCandidate> scan(Level level, Vec3d strike, double surfaceY) {
        List<StreamerCandidate> candidates = new ArrayList<>();
        BlockPos centre = BlockPos.containing(strike.x(), surfaceY, strike.z());

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                BlockPos column = centre.offset(dx, 0, dz);
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                double prominence = top.getY() - surfaceY;
                BlockState state = level.getBlockState(top.below());
                StreamerKind kind = classify(state);

                // A rod competes from anywhere in range; everything else has to stand up first.
                if (kind != StreamerKind.ROD && prominence < MIN_PROMINENCE) continue;
                if (candidates.size() >= MAX_CANDIDATES) continue;

                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double weight = (1.0 + Math.max(0, prominence)) * kind.baseWeight()
                    // Nearer objects are inside a stronger part of the field.
                    * (1.0 - 0.045 * horizontal);
                if (weight <= 0) continue;

                Vec3d tip = new Vec3d(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                if (tooClose(candidates, tip, kind)) continue;
                candidates.add(new StreamerCandidate(tip, weight, kind));
            }
        }
        return candidates;
    }

    /**
     * A rod is never merged away: it is the one thing whose whole purpose is to be the candidate.
     */
    private static boolean tooClose(List<StreamerCandidate> candidates, Vec3d tip, StreamerKind kind) {
        if (kind == StreamerKind.ROD) return false;
        for (StreamerCandidate existing : candidates) {
            if (existing.kind() != StreamerKind.ROD && existing.tip().distanceTo(tip) < MERGE_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static StreamerKind classify(BlockState state) {
        if (state.getBlock() instanceof LightningRodBlock) return StreamerKind.ROD;
        if (state.is(BlockTags.BEACON_BASE_BLOCKS) || state.is(Blocks.IRON_BARS)
            || state.is(Blocks.CHAIN) || state.is(BlockTags.COPPER_ORES)
            || state.getBlock().getDescriptionId().contains("copper")) {
            return StreamerKind.METAL;
        }
        return StreamerKind.TERRAIN;
    }
}
