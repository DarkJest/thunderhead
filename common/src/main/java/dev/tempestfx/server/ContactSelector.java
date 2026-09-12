package dev.tempestfx.server;

import dev.tempestfx.math.Vec3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded 17x17 loaded-column search for opt-in extra ground strikes. Vanilla choices are untouched. */
public final class ContactSelector {
    private ContactSelector() {}
    public static Vec3d select(ServerLevel level, BlockPos around) {
        BlockPos best = null; double score = -Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            BlockPos column = around.offset(x, 0, z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (!level.isRainingAt(top)) continue;
            var state = level.getBlockState(top.below());
            double candidate = top.getY() - .35 * (x*x + z*z) + (state.is(Blocks.LIGHTNING_ROD) ? 10_000 : 0);
            if (candidate > score) { score = candidate; best = top; }
        }
        return best == null ? null : new Vec3d(best.getX()+.5, best.getY(), best.getZ()+.5);
    }
}
