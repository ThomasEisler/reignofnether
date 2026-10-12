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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Traces a 3-wide road on the terrain surface between two buildings, going around water, other buildings,
// cliffs and (when cheap enough) trees, using an A* search on the x/z grid.
public class RoadPathTracer {

    public static final int MAX_LENGTH = 256; // max straight distance between the two buildings
    public static final int MAX_PATH_LENGTH = 384; // max length of the winding road itself
    public static final int WIDTH_RADIUS = 1; // 1 = 3 blocks wide
    private static final int MAX_SURFACE_SEARCH = 16;
    private static final int SEARCH_MARGIN = 48; // how far outside the box around both buildings a detour can go
    private static final int MAX_NODES = 60000; // search budget, to keep it fast
    private static final int MAX_STEP_HEIGHT = 1; // roads can't climb more than this per block
    private static final double SLOPE_COST = 1.5;
    private static final double TREE_COST = 3;
    private static final double DIAGONAL_COST = Math.sqrt(2);

    public static class Result {
        public final List<BlockPos> blocks; // road surface blocks, in order from the first building to the second
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

    private record Node(long key, double f) {}

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int keyX(long key) { return (int) (key >> 32); }
    private static int keyZ(long key) { return (int) key; }

    // cached ground info for a column: surface pos (null if water/no ground), whether another building is there
    private static class Column {
        @Nullable final BlockPos surface;
        final boolean blocked;
        final boolean tree;
        Column(@Nullable BlockPos surface, boolean blocked, boolean tree) {
            this.surface = surface;
            this.blocked = blocked;
            this.tree = tree;
        }
    }

    public static Result trace(Level level, BuildingPlacement a, BuildingPlacement b) {
        int x0 = a.centrePos.getX(), z0 = a.centrePos.getZ();
        int x1 = b.centrePos.getX(), z1 = b.centrePos.getZ();
        if (Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) > MAX_LENGTH)
            return Result.error("hud.reignofnether.road_too_long");

        int minX = Math.min(a.minCorner.getX(), b.minCorner.getX()) - SEARCH_MARGIN;
        int maxX = Math.max(a.maxCorner.getX(), b.maxCorner.getX()) + SEARCH_MARGIN;
        int minZ = Math.min(a.minCorner.getZ(), b.minCorner.getZ()) - SEARCH_MARGIN;
        int maxZ = Math.max(a.maxCorner.getZ(), b.maxCorner.getZ()) + SEARCH_MARGIN;

        Map<Long, Column> columns = new HashMap<>();
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        long startKey = key(x0, z0), goalKey = key(x1, z1);
        gScore.put(startKey, 0d);
        open.add(new Node(startKey, heuristic(x0, z0, x1, z1)));

        boolean found = false;
        int expanded = 0;
        while (!open.isEmpty() && expanded < MAX_NODES) {
            Node node = open.poll();
            long current = node.key();
            int cx = keyX(current), cz = keyZ(current);
            double g = gScore.get(current);
            if (node.f() > g + heuristic(cx, cz, x1, z1) + 1e-6)
                continue; // outdated queue entry
            if (current == goalKey) {
                found = true;
                break;
            }
            expanded++;
            boolean currentInEnds = isInFootprint(a, cx, cz) || isInFootprint(b, cx, cz);
            Column currentCol = currentInEnds ? null : getColumn(level, columns, cx, cz);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0)
                        continue;
                    int nx = cx + dx, nz = cz + dz;
                    if (nx < minX || nx > maxX || nz < minZ || nz > maxZ)
                        continue;
                    double stepCost = (dx != 0 && dz != 0) ? DIAGONAL_COST : 1;
                    boolean nextInEnds = isInFootprint(a, nx, nz) || isInFootprint(b, nx, nz);
                    if (!nextInEnds) {
                        Column col = getColumn(level, columns, nx, nz);
                        if (col.surface == null || col.blocked)
                            continue;
                        if (currentCol != null && currentCol.surface != null) {
                            int climb = Math.abs(col.surface.getY() - currentCol.surface.getY());
                            if (climb > MAX_STEP_HEIGHT)
                                continue;
                            stepCost += climb * SLOPE_COST;
                        }
                        if (col.tree)
                            stepCost += TREE_COST;
                    }
                    long next = key(nx, nz);
                    double newG = g + stepCost;
                    Double oldG = gScore.get(next);
                    if (oldG == null || newG < oldG) {
                        gScore.put(next, newG);
                        cameFrom.put(next, current);
                        open.add(new Node(next, newG + heuristic(nx, nz, x1, z1)));
                    }
                }
            }
        }
        if (!found)
            return Result.error("hud.reignofnether.road_no_path");

        // walk back from the goal to get the centre line in order from a to b
        List<Long> centreLine = new ArrayList<>();
        for (Long k = goalKey; k != null; k = cameFrom.get(k))
            centreLine.add(k);
        Collections.reverse(centreLine);

        Set<BlockPos> blocks = new LinkedHashSet<>();
        int length = 0;
        for (long k : centreLine) {
            int x = keyX(k), z = keyZ(k);
            if (isInFootprint(a, x, z) || isInFootprint(b, x, z))
                continue;
            length++;
            // a square brush keeps the road 3 wide whatever its direction
            for (int wx = x - WIDTH_RADIUS; wx <= x + WIDTH_RADIUS; wx++) {
                for (int wz = z - WIDTH_RADIUS; wz <= z + WIDTH_RADIUS; wz++) {
                    if (isInFootprint(a, wx, wz) || isInFootprint(b, wx, wz))
                        continue;
                    Column col = getColumn(level, columns, wx, wz);
                    if (col.surface != null && !col.blocked)
                        blocks.add(col.surface);
                }
            }
        }
        if (length == 0)
            return Result.error("hud.reignofnether.road_too_short");
        if (length > MAX_PATH_LENGTH)
            return Result.error("hud.reignofnether.road_too_long");
        return new Result(new ArrayList<>(blocks), length, null);
    }

    // octile distance: admissible for 8-way moves costing 1 straight and sqrt(2) diagonally
    private static double heuristic(int x, int z, int gx, int gz) {
        int dx = Math.abs(gx - x), dz = Math.abs(gz - z);
        return Math.max(dx, dz) + (DIAGONAL_COST - 1) * Math.min(dx, dz);
    }

    private static Column getColumn(Level level, Map<Long, Column> cache, int x, int z) {
        return cache.computeIfAbsent(key(x, z), k -> {
            BlockPos surface = getSurface(level, x, z);
            if (surface == null)
                return new Column(null, true, false);
            boolean blocked = BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), surface.above());
            boolean tree = level.getBlockState(surface.above()).is(BlockTags.LOGS);
            return new Column(surface, blocked, tree);
        });
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
