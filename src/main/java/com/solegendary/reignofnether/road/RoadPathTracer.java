package com.solegendary.reignofnether.road;

import net.minecraft.util.Mth;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Traces a 3-wide road on the terrain surface along a straight line between two buildings.
public class RoadPathTracer {

    public static final int MAX_LENGTH = 256;
    public static final int WIDTH_RADIUS = 1; // 1 = 3 blocks wide
    private static final int MAX_SURFACE_SEARCH = 16;

    public static class Result {
        public final List<BlockPos> blocks; // road surface blocks
        public final int length; // number of centre line steps, used for cost
        @Nullable public final String errorKey;

        private Result(List<BlockPos> blocks, int length, @Nullable String errorKey) {
            this.blocks = blocks;
            this.length = length;
            this.errorKey = errorKey;
        }

        static Result error(String errorKey) {
            return new Result(List.of(), 0, errorKey);
        }
    }

    public static Result trace(Level level, BuildingPlacement a, BuildingPlacement b) {
        int x0 = a.centrePos.getX(), z0 = a.centrePos.getZ();
        int x1 = b.centrePos.getX(), z1 = b.centrePos.getZ();
        int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        if (Math.max(dx, dz) > MAX_LENGTH)
            return Result.error("hud.reignofnether.road_too_long");
        boolean alongX = dx >= dz; // widen perpendicular to the main direction

        Set<BlockPos> blocks = new LinkedHashSet<>();
        int length = 0;
        // Bresenham line between the two building centres
        int sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int x = x0, z = z0;
        while (true) {
            if (!isInFootprint(a, x, z) && !isInFootprint(b, x, z)) {
                BlockPos centre = getSurface(level, x, z);
                if (centre == null)
                    return Result.error("hud.reignofnether.road_blocked_water");
                if (BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), centre.above()))
                    return Result.error("hud.reignofnether.road_blocked_building");
                length++;
                for (int w = -WIDTH_RADIUS; w <= WIDTH_RADIUS; w++) {
                    int wx = alongX ? x : x + w;
                    int wz = alongX ? z + w : z;
                    if (isInFootprint(a, wx, wz) || isInFootprint(b, wx, wz))
                        continue;
                    BlockPos side = getSurface(level, wx, wz);
                    if (side != null && !BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), side.above()))
                        blocks.add(side);
                }
            }
            if (x == x1 && z == z1)
                break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
        }
        if (length == 0)
            return Result.error("hud.reignofnether.road_too_short");
        return new Result(new ArrayList<>(blocks), length, null);
    }

    public static final int APRON_MIN_WIDTH = 0;
    public static final int APRON_MAX_WIDTH = 2;

    // ground blocks forming an irregular ring of road around a building's footprint, so roads arriving from any
    // direction join up with it; the shape is deterministic per position so it is the same each time it's traced
    public static List<BlockPos> traceApron(Level level, BuildingPlacement placement) {
        List<BlockPos> apron = new ArrayList<>();
        int minX = placement.minCorner.getX(), maxX = placement.maxCorner.getX();
        int minZ = placement.minCorner.getZ(), maxZ = placement.maxCorner.getZ();
        for (int x = minX - APRON_MAX_WIDTH; x <= maxX + APRON_MAX_WIDTH; x++) {
            for (int z = minZ - APRON_MAX_WIDTH; z <= maxZ + APRON_MAX_WIDTH; z++) {
                if (isInFootprint(placement, x, z))
                    continue;
                // distance from the footprint rectangle, compared against a noisy width to look less square
                int dx = Math.max(0, Math.max(minX - x, x - maxX));
                int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
                double dist = Math.sqrt(dx * dx + dz * dz);
                long hash = Mth.getSeed(x, 0, z);
                double width = APRON_MIN_WIDTH + ((hash >> 16) & 0xFF) / 255d * (APRON_MAX_WIDTH - APRON_MIN_WIDTH + 0.5);
                if (dist > width)
                    continue;
                BlockPos surface = getSurface(level, x, z);
                if (surface != null && !BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), surface.above()))
                    apron.add(surface);
            }
        }
        return apron;
    }

    private static boolean isInFootprint(BuildingPlacement placement, int x, int z) {
        return x >= placement.minCorner.getX() && x <= placement.maxCorner.getX() &&
               z >= placement.minCorner.getZ() && z <= placement.maxCorner.getZ();
    }

    // highest natural ground block at x,z, skipping trees and plants; null if the surface is liquid
    @Nullable
    private static BlockPos getSurface(Level level, int x, int z) {
        BlockPos bp = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        for (int i = 0; i < MAX_SURFACE_SEARCH; i++) {
            BlockState bs = level.getBlockState(bp);
            if (!bs.getFluidState().isEmpty())
                return null;
            if (isGround(bs))
                return bp;
            bp = bp.below();
        }
        return null;
    }

    public static boolean isGround(BlockState bs) {
        if (bs.is(BlockTags.LOGS) || bs.is(BlockTags.LEAVES) || bs.canBeReplaced())
            return false;
        return bs.is(BlockTags.DIRT) || bs.is(BlockTags.SAND) || bs.is(BlockTags.BASE_STONE_OVERWORLD) ||
               bs.is(BlockTags.TERRACOTTA) || bs.is(Blocks.GRAVEL) || bs.is(Blocks.DIRT_PATH) ||
               bs.is(Blocks.SNOW_BLOCK) || bs.is(Blocks.CLAY) || RoadType.isRoadBlock(bs);
    }
}
