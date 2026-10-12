package com.solegendary.reignofnether.road;

import java.util.LinkedHashSet;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Serverside road network: buildings are the nodes and RoadConnections the edges.
// Road blocks are indexed by position so lookups (eg. unit speed bonus) never scan the world.
public class RoadServerEvents {

    private static final List<RoadConnection> connections = new ArrayList<>();
    private static final Map<Long, RoadConnection> roadBlocks = new HashMap<>();
    private static ServerLevel serverLevel = null;
    private static int saveTicks = 0;

    public static List<RoadConnection> getConnections() {
        return connections;
    }

    @Nullable
    public static RoadType getRoadTypeAt(BlockPos bp) {
        RoadConnection connection = roadBlocks.get(bp.asLong());
        return connection != null ? connection.type : null;
    }

    public static int getWoodCost(RoadType type, int length) {
        return type.woodPerLength * length;
    }

    public static int getOreCost(RoadType type, int length) {
        return type.orePerLength * length;
    }

    // serverside validation of a player's request to build a road between two of their buildings
    public static boolean tryBuildRoad(ServerLevel level, String ownerName, BlockPos fromPos, BlockPos toPos, RoadType type) {
        BuildingPlacement from = BuildingUtils.findBuilding(false, fromPos);
        BuildingPlacement to = BuildingUtils.findBuilding(false, toPos);

        String error = null;
        if (from == null || to == null || from == to)
            error = "hud.reignofnether.road_invalid_buildings";
        else if (!from.ownerName.equals(ownerName) || !to.ownerName.equals(ownerName))
            error = "hud.reignofnether.road_not_owned";
        else if (!from.isBuilt || !to.isBuilt)
            error = "hud.reignofnether.road_not_built";
        if (error != null) {
            HudClientboundPacket.showTempMessageI18n(ownerName, error);
            return false;
        }
        // connecting two already connected buildings upgrades their road to the next tier instead
        RoadConnection existing = getConnection(from.originPos, to.originPos);
        if (existing != null)
            return tryUpgradeRoad(level, ownerName, existing, from, to);

        RoadPathTracer.Result path = RoadPathTracer.trace(level, from, to);
        if (path.errorKey != null) {
            HudClientboundPacket.showTempMessageI18n(ownerName, path.errorKey);
            return false;
        }
        if (!payForRoad(ownerName, type, path.length))
            return false;

        // the road plus an irregular apron around each building it connects, which joins up with it
        Set<BlockPos> roadBlocksToPlace = new LinkedHashSet<>(path.blocks);
        roadBlocksToPlace.addAll(RoadPathTracer.traceApron(level, from));
        roadBlocksToPlace.addAll(RoadPathTracer.traceApron(level, to));
        roadBlocksToPlace.removeIf(bp -> roadBlocks.containsKey(bp.asLong()));
        RoadConnection connection = new RoadConnection(from.originPos, to.originPos, ownerName, type, new ArrayList<>(roadBlocksToPlace), path.length);
        placeRoadBlocks(level, connection);
        addConnection(connection);
        adaptBuildingFloor(level, from);
        adaptBuildingFloor(level, to);
        saveRoads(level);
        return true;
    }

    private static boolean tryUpgradeRoad(ServerLevel level, String ownerName, RoadConnection existing,
                                          BuildingPlacement from, BuildingPlacement to) {
        if (existing.type.ordinal() >= RoadType.values().length - 1) {
            HudClientboundPacket.showTempMessageI18n(ownerName, "hud.reignofnether.road_max_level");
            return false;
        }
        RoadType nextType = RoadType.values()[existing.type.ordinal() + 1];
        if (!payForRoad(ownerName, nextType, existing.length))
            return false;

        RoadConnection upgraded = existing.withType(nextType);
        connections.remove(existing);
        addConnection(upgraded);
        // only replace blocks that are still road, in case something was built over part of it since
        BlockState roadState = nextType.block.defaultBlockState();
        for (BlockPos bp : upgraded.blocks)
            if (RoadType.isRoadBlock(level.getBlockState(bp)))
                level.setBlockAndUpdate(bp, roadState);
        adaptBuildingFloor(level, from);
        adaptBuildingFloor(level, to);
        saveRoads(level);
        return true;
    }

    private static boolean payForRoad(String ownerName, RoadType type, int length) {
        int wood = getWoodCost(type, length);
        int ore = getOreCost(type, length);
        boolean canAffordWood = ResourcesServerEvents.canAfford(ownerName, ResourceName.WOOD, wood);
        boolean canAffordOre = ResourcesServerEvents.canAfford(ownerName, ResourceName.ORE, ore);
        if (!canAffordWood || !canAffordOre) {
            ResourcesClientboundPacket.warnInsufficientResources(ownerName, true, canAffordWood, canAffordOre, true);
            return false;
        }
        ResourcesServerEvents.addSubtractResources(new Resources(ownerName, 0, -wood, -ore));
        return true;
    }

    private static void placeRoadBlocks(ServerLevel level, RoadConnection connection) {
        BlockState roadState = connection.type.block.defaultBlockState();
        for (BlockPos bp : connection.blocks) {
            level.setBlockAndUpdate(bp, roadState);
            // clear grass, flowers, snow layers etc. sitting on the road
            BlockPos above = bp.above();
            if (level.getBlockState(above).canBeReplaced() && level.getBlockState(above).getFluidState().isEmpty())
                level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
        }
    }

    // material for a building's road floor tiles: the best road connected to it, or null if it has no road
    @Nullable
    public static RoadType getFloorType(BlockPos buildingOrigin) {
        RoadType best = null;
        for (RoadConnection connection : connections)
            if (connection.connects(buildingOrigin) && (best == null || connection.type.ordinal() > best.ordinal()))
                best = connection.type;
        return best;
    }

    // replaces the building's placed road floor tiles (dirt path in its structure) and the road apron around it
    // with the material of the best road connected to it
    public static void adaptBuildingFloor(ServerLevel level, BuildingPlacement placement) {
        RoadType type = getFloorType(placement.originPos);
        if (type == null)
            return;
        BlockState roadState = type.block.defaultBlockState();
        for (BuildingBlock block : placement.getBlocks())
            if (RoadType.isRoadFloorMarker(block.getBlockState()) && block.isPlaced(level))
                level.setBlockAndUpdate(block.getBlockPos(), roadState);
        for (BlockPos bp : RoadPathTracer.traceApron(level, placement))
            if (RoadType.isRoadBlock(level.getBlockState(bp)))
                level.setBlockAndUpdate(bp, roadState);
    }

    // a dirt path apron around a newly finished building, inviting the player to connect it by road;
    // it takes the road's material once a road reaches the building
    public static void placeApron(ServerLevel level, BuildingPlacement placement) {
        RoadType type = getFloorType(placement.originPos);
        BlockState state = (type != null ? type.block : RoadType.PATH.block).defaultBlockState();
        for (BlockPos bp : RoadPathTracer.traceApron(level, placement)) {
            if (RoadType.isRoadBlock(level.getBlockState(bp)))
                continue;
            level.setBlockAndUpdate(bp, state);
            BlockPos above = bp.above();
            if (level.getBlockState(above).canBeReplaced() && level.getBlockState(above).getFluidState().isEmpty())
                level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
        }
    }

    private static void addConnection(RoadConnection connection) {
        connections.add(connection);
        for (BlockPos bp : connection.blocks)
            roadBlocks.put(bp.asLong(), connection);
    }

    @Nullable
    public static RoadConnection getConnection(BlockPos buildingA, BlockPos buildingB) {
        for (RoadConnection connection : connections)
            if (connection.connects(buildingA) && connection.connects(buildingB))
                return connection;
        return null;
    }

    // all building origins reachable by road from the given building (including itself)
    public static Set<BlockPos> getConnectedBuildings(BlockPos buildingOrigin) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(buildingOrigin);
        queue.add(buildingOrigin);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (RoadConnection connection : connections) {
                if (connection.connects(current)) {
                    BlockPos other = connection.getOtherEnd(current);
                    if (visited.add(other))
                        queue.add(other);
                }
            }
        }
        return visited;
    }

    public static boolean areConnected(BlockPos buildingA, BlockPos buildingB) {
        return getConnectedBuildings(buildingA).contains(buildingB);
    }

    public static boolean hasAnyRoad(BlockPos buildingOrigin) {
        for (RoadConnection connection : connections)
            if (connection.connects(buildingOrigin))
                return true;
        return false;
    }

    // the network loses an edge when one of its buildings no longer exists; the road blocks stay in the world
    private static void removeConnectionsToMissingBuildings() {
        Set<BlockPos> origins = new HashSet<>();
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            origins.add(placement.originPos);
        connections.removeIf(c -> !origins.contains(c.from) || !origins.contains(c.to));
        roadBlocks.values().removeIf(c -> !connections.contains(c));
    }

    private static void saveRoads(ServerLevel level) {
        RoadSaveData data = RoadSaveData.getInstance(level);
        data.connections.clear();
        data.connections.addAll(connections);
        data.save();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        connections.clear();
        roadBlocks.clear();
        if (serverLevel != null)
            RoadSaveData.getInstance(serverLevel).connections.forEach(RoadServerEvents::addConnection);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        saveTicks += 1;
        if (saveTicks >= 600) {
            saveTicks = 0;
            int before = connections.size();
            removeConnectionsToMissingBuildings();
            if (connections.size() != before)
                saveRoads(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        if (serverLevel != null)
            saveRoads(serverLevel);
        serverLevel = null;
    }
}
