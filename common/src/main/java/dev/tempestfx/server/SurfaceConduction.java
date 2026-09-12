package dev.tempestfx.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class SurfaceConduction {
    public static final TagKey<Block> CONDUCTIVE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("tempestfx", "conductive"));
    private SurfaceConduction() {}
    public static float at(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) return 0;
        var state = level.getBlockState(position);
        if (state.getFluidState().is(FluidTags.WATER)) return 1.2f;
        if (state.isAir()) return 0;
        if (state.is(CONDUCTIVE)) return 1.5f;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return .2f;
        return state.isSolid() ? .65f : .25f;
    }
    public static float path(ServerLevel level, Vec3 strike, Vec3 feet) {
        if (Math.abs(strike.y - feet.y) > 2) return 0;
        int steps = Math.min(64, Math.max(1, (int) Math.ceil(strike.distanceTo(feet) * 2)));
        float factor = 1.5f;
        for (int i=0; i<=steps; i++) {
            Vec3 p = strike.lerp(feet, i/(double)steps).add(0,-.2,0);
            factor = Math.min(factor, at(level, BlockPos.containing(p)));
            if (factor == 0) return 0;
        }
        return factor;
    }
}
