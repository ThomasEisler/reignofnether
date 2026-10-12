package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Buildings under construction look like real building sites: scaffolding towers stand inside and around the
// building's footprint and grow, each a bit unevenly, as construction progresses, rising ahead of the walls.
// They are taken down when the building is finished or destroyed. The layout is deterministic per position, so
// it is found again after a server restart.
public class ConstructionSiteServerEvents {

    private static final int CHECK_TICKS = 20;
    private static final int CELLS_PER_TOWER = 12; // roughly one tower per this many cells of footprint
    private static final int MIN_TOWERS = 2;
    private static final int MAX_TOWERS = 10;
    private static final int MAX_GROUND_OFFSET = 3; // skip towers whose ground is this far from the building's base

    private static class Tower {
        final int x, z;
        final int targetHeight; // height reached when the building is finished
        final float speed; // >1 grows faster than the construction, <1 slower
        final float delay; // progress at which it starts to grow
        int bottomY = Integer.MIN_VALUE; // found when the tower first grows
        int height = 0;

        Tower(int x, int z, int targetHeight, float speed, float delay) {
            this.x = x;
            this.z = z;
            this.targetHeight = targetHeight;
            this.speed = speed;
            this.delay = delay;
        }
    }

    // building origin -> its scaffolding towers
    private static final Map<BlockPos, List<Tower>> sites = new HashMap<>();
    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    private static boolean hasConstructionSite(BuildingPlacement placement) {
        return !(placement.getBuilding() instanceof AbstractBridge) && !placement.ownerName.isEmpty();
    }

    private static float random01(int x, int salt, int z) {
        return Math.floorMod(Mth.getSeed(x, salt, z) >> 16, 1000L) / 1000f;
    }

    // tower spots in the footprint and the ring just around it, chosen pseudo-randomly from the positions
    private static List<Tower> planTowers(BuildingPlacement placement) {
        int minX = placement.minCorner.getX() - 1, maxX = placement.maxCorner.getX() + 1;
        int minZ = placement.minCorner.getZ() - 1, maxZ = placement.maxCorner.getZ() + 1;
        int cells = (maxX - minX + 1) * (maxZ - minZ + 1);
        int wanted = Mth.clamp(cells / CELLS_PER_TOWER, MIN_TOWERS, MAX_TOWERS);
        int buildingHeight = placement.maxCorner.getY() - placement.originPos.getY() + 1;

        // rank every cell by a hash and keep the best ones that aren't right next to another tower
        List<long[]> ranked = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                ranked.add(new long[] { x, z, Mth.getSeed(x, 3, z) });
        ranked.sort((a, b) -> Long.compare(a[2], b[2]));

        List<Tower> towers = new ArrayList<>();
        for (long[] cell : ranked) {
            if (towers.size() >= wanted)
                break;
            int x = (int) cell[0], z = (int) cell[1];
            boolean tooClose = false;
            for (Tower t : towers)
                if (Math.abs(t.x - x) <= 1 && Math.abs(t.z - z) <= 1)
                    tooClose = true;
            if (tooClose)
                continue;
            int targetHeight = Math.max(2, Math.round(buildingHeight * (0.5f + 0.7f * random01(x, 5, z))));
            float speed = 0.8f + 0.6f * random01(x, 9, z);
            float delay = 0.25f * random01(x, 11, z);
            towers.add(new Tower(x, z, targetHeight, speed, delay));
        }
        return towers;
    }

    // lowest spot of the column where scaffolding can stand: free (or already scaffolding) above solid ground
    private static int findBottom(ServerLevel level, BuildingPlacement placement, int x, int z) {
        int baseY = placement.originPos.getY();
        for (int y = baseY - MAX_GROUND_OFFSET; y <= baseY + MAX_GROUND_OFFSET; y++) {
            BlockPos bp = new BlockPos(x, y, z);
            BlockState bs = level.getBlockState(bp);
            BlockState below = level.getBlockState(bp.below());
            if ((bs.canBeReplaced() || bs.is(Blocks.SCAFFOLDING)) && bs.getFluidState().isEmpty() &&
                !below.is(Blocks.SCAFFOLDING) && below.isFaceSturdy(level, bp.below(), Direction.UP))
                return y;
        }
        return Integer.MIN_VALUE;
    }

    private static void growTowers(ServerLevel level, BuildingPlacement placement, List<Tower> towers) {
        float progress = Math.min(1, placement.getBlocksPlacedPercent());
        for (Tower tower : towers) {
            int height = Math.round(Mth.clamp((progress - tower.delay) * tower.speed / (1 - tower.delay), 0, 1)
                * tower.targetHeight);
            if (height <= tower.height)
                continue; // towers never shrink, eg. when the site is attacked
            if (tower.bottomY == Integer.MIN_VALUE) {
                tower.bottomY = findBottom(level, placement, tower.x, tower.z);
                if (tower.bottomY == Integer.MIN_VALUE) {
                    tower.height = Integer.MAX_VALUE; // no ground here, never grow
                    continue;
                }
            }
            for (int i = 0; i < height; i++) {
                BlockPos bp = new BlockPos(tower.x, tower.bottomY + i, tower.z);
                BlockState bs = level.getBlockState(bp);
                if (bs.is(Blocks.SCAFFOLDING))
                    continue;
                if (bs.canBeReplaced() && bs.getFluidState().isEmpty()) {
                    if (BuildingUtils.isPosInsideAnyBuilding(false, bp) && !placement.isPosInsideBuilding(bp, 0))
                        break; // never in another building
                    level.setBlockAndUpdate(bp, Blocks.SCAFFOLDING.defaultBlockState());
                }
                // else a building block already stands here: the tower carries on above it
            }
            tower.height = height;
        }
    }

    private static void removeTowers(Level level, List<Tower> towers) {
        for (Tower tower : towers) {
            if (tower.bottomY == Integer.MIN_VALUE)
                continue;
            // top down, so no part of the tower is left floating (which would make it fall and drop items)
            for (int y = tower.bottomY + tower.targetHeight + 1; y >= tower.bottomY; y--) {
                BlockPos bp = new BlockPos(tower.x, y, tower.z);
                if (level.getBlockState(bp).is(Blocks.SCAFFOLDING))
                    level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
            }
        }
    }

    // called when a building is finished, before its road apron is laid
    public static void onBuildingFinished(BuildingPlacement placement) {
        List<Tower> towers = sites.remove(placement.originPos);
        if (towers != null)
            removeTowers(placement.level, towers);
    }

    private static void tick(ServerLevel level) {
        Set<BlockPos> underConstruction = new HashSet<>();
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (placement.isBuilt || !hasConstructionSite(placement) || placement.level != level)
                continue;
            underConstruction.add(placement.originPos);
            growTowers(level, placement, sites.computeIfAbsent(placement.originPos, k -> planTowers(placement)));
        }
        // finished (in case onBuildingFinished was missed) or destroyed buildings
        sites.entrySet().removeIf(entry -> {
            if (underConstruction.contains(entry.getKey()))
                return false;
            removeTowers(level, entry.getValue());
            return true;
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        sites.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        if (++ticks >= CHECK_TICKS) {
            ticks = 0;
            tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
        sites.clear();
    }
}
