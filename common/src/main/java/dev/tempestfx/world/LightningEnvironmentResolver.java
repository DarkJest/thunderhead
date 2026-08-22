package dev.tempestfx.world;

import dev.tempestfx.api.LightningEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Samples the impact surface once per strike.
 */
public final class LightningEnvironmentResolver {
    private static final int SURFACE_PROBE_DEPTH = 16;
    private static final int FOLIAGE_RADIUS = 3;
    private static final int FOLIAGE_HEIGHT = 5;

    public LightningEnvironment resolve(Level level, Vec3 strikePosition) {
        BlockPos origin = BlockPos.containing(strikePosition);
        BlockPos surface = findSurface(level, origin);
        BlockState state = level.getBlockState(surface);
        boolean water = level.getFluidState(surface).is(FluidTags.WATER)
            || level.getFluidState(surface.above()).is(FluidTags.WATER);
        boolean foliage = nearLeaves(level, surface);

        LightningEnvironment.Type type;
        if (water) type = LightningEnvironment.Type.WATER;
        else if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            type = LightningEnvironment.Type.SNOW;
        } else if (state.is(BlockTags.SAND)) type = LightningEnvironment.Type.SAND;
        else if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)) {
            type = LightningEnvironment.Type.STONE;
        } else if (foliage) type = LightningEnvironment.Type.FOREST;
        else type = LightningEnvironment.Type.LAND;

        int color = state.getMapColor(level, surface).col;
        boolean raining = level.isRaining();
        float moisture = water ? 1f : raining ? 0.85f : type == LightningEnvironment.Type.SNOW ? 0.5f : 0f;
        double surfaceY = Math.min(strikePosition.y, surface.getY() + topFaceHeight(level, surface));
        // Sky darkening has to be subtracted by hand: raw sky light stays 15 all night.
        float brightness = level.getRawBrightness(surface.above(), level.getSkyDarken()) / 15f;
        return new LightningEnvironment(type, color, raining, moisture, surfaceY, foliage, brightness);
    }

    /** Height of the top face of a block, in the range {@code 0..1}, honouring fluids and slabs. */
    private static double topFaceHeight(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.isEmpty()) return fluid.getHeight(level, pos);
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
    }

    private BlockPos findSurface(Level level, BlockPos position) {
        BlockPos.MutableBlockPos cursor = position.mutable();
        for (int step = 0; step < SURFACE_PROBE_DEPTH && cursor.getY() > level.getMinBuildHeight(); step++) {
            if (stopsTheProbe(level, cursor)) return cursor.immutable();
            cursor.move(0, -1, 0);
        }
        return position;
    }

    /** Anything solid enough to stand on, or any fluid. Decorations are walked through. */
    private static boolean stopsTheProbe(Level level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) return true;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean nearLeaves(Level level, BlockPos center) {
        BlockPos min = center.offset(-FOLIAGE_RADIUS, 0, -FOLIAGE_RADIUS);
        BlockPos max = center.offset(FOLIAGE_RADIUS, FOLIAGE_HEIGHT, FOLIAGE_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) return true;
        }
        return false;
    }
}
