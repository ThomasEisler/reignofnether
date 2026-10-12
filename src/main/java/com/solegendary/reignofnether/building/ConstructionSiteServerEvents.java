package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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

// Buildings under construction look like real building sites: scaffolding columns of random heights stand
// here and there around them, and are taken down when the building is finished or destroyed.
// The layout is deterministic per position, so it can be found again after a server restart.
public class ConstructionSiteServerEvents {

    private static final int CHECK_TICKS = 20;
    private static final int CLUSTER_SIZE = 3; // scaffolding comes in groups of columns this wide
    private static final int CLUSTER_CHANCE = 40; // % of perimeter groups with scaffolding
    private static final int MIN_HEIGHT = 2;
    private static final int MAX_GROUND_OFFSET = 3; // skip columns whose ground is this far from the building's base

    // building origin -> scaffolding blocks placed for it, bottom to top for each column
    private static final Map<BlockPos, List<BlockPos>> sites = new HashMap<>();
    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    private static boolean hasConstructionSite(BuildingPlacement placement) {
        return !(placement.getBuilding() instanceof AbstractBridge) && !placement.ownerName.isEmpty();
    }

    private static List<BlockPos> placeScaffolding(ServerLevel level, BuildingPlacement placement) {
        List<BlockPos> placed = new ArrayList<>();
        int minX = placement.minCorner.getX() - 1, maxX = placement.maxCorner.getX() + 1;
        int minZ = placement.minCorner.getZ() - 1, maxZ = placement.maxCorner.getZ() + 1;
        int baseY = placement.originPos.getY();
        int buildingHeight = placement.maxCorner.getY() - baseY;
        int maxHeight = Math.max(MIN_HEIGHT + 1, (int) (buildingHeight * 0.8f));

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ)
                    continue; // only the ring just outside the footprint
                long clusterHash = Mth.getSeed(Math.floorDiv(x, CLUSTER_SIZE), 7, Math.floorDiv(z, CLUSTER_SIZE));
                if (Math.floorMod(clusterHash >> 16, 100L) >= CLUSTER_CHANCE)
                    continue;
                long hash = Mth.getSeed(x, 13, z);
                int height = MIN_HEIGHT + (int) Math.floorMod(hash >> 16, (long) (maxHeight - MIN_HEIGHT + 1));

                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, groundY - 1, z);
                if (Math.abs(groundY - baseY) > MAX_GROUND_OFFSET || !level.getFluidState(ground).isEmpty() ||
                    !level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP))
                    continue;
                for (int i = 0; i < height; i++) {
                    BlockPos bp = new BlockPos(x, groundY + i, z);
                    BlockState bs = level.getBlockState(bp);
                    if (bs.is(Blocks.SCAFFOLDING)) {
                        placed.add(bp); // already there, eg. after a server restart
                        continue;
                    }
                    if (!bs.canBeReplaced() || !bs.getFluidState().isEmpty() ||
                        BuildingUtils.isPosInsideAnyBuilding(false, bp))
                        break;
                    level.setBlockAndUpdate(bp, Blocks.SCAFFOLDING.defaultBlockState());
                    placed.add(bp);
                }
            }
        }
        return placed;
    }

    private static void removeScaffolding(Level level, List<BlockPos> blocks) {
        // top down, so no column is left floating (which would make it fall and drop items)
        for (int i = blocks.size() - 1; i >= 0; i--) {
            BlockPos bp = blocks.get(i);
            if (level.getBlockState(bp).is(Blocks.SCAFFOLDING))
                level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
        }
    }

    // called when a building is finished, before its road apron is laid
    public static void onBuildingFinished(BuildingPlacement placement) {
        List<BlockPos> blocks = sites.remove(placement.originPos);
        if (blocks != null)
            removeScaffolding(placement.level, blocks);
    }

    private static void tick(ServerLevel level) {
        Set<BlockPos> underConstruction = new HashSet<>();
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (placement.isBuilt || !hasConstructionSite(placement) || placement.level != level)
                continue;
            underConstruction.add(placement.originPos);
            if (!sites.containsKey(placement.originPos))
                sites.put(placement.originPos, placeScaffolding(level, placement));
        }
        // finished (in case onBuildingFinished was missed) or destroyed buildings
        sites.entrySet().removeIf(entry -> {
            if (underConstruction.contains(entry.getKey()))
                return false;
            removeScaffolding(level, entry.getValue());
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
