package com.solegendary.reignofnether.road;

import java.util.concurrent.ConcurrentHashMap;
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
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
    // read from pathfinding worker threads, hence concurrent
    private static final Map<Long, RoadConnection> roadBlocks = new ConcurrentHashMap<>();
    private static ServerLevel serverLevel = null;
    private static int saveTicks = 0;

    // workers building roads: worker entity id -> road, and the spot it was last sent to (so a different move
    // target means the player gave it another order)
    private static final Map<Integer, RoadConnection> workerRoads = new HashMap<>();
    private static final Map<Integer, BlockPos> workerTargets = new HashMap<>();
    public static final int WORKERS_SENT = 3; // idle workers sent automatically when a road is ordered
    public static final int WORKERS_SEARCH_RANGE = 64; // how far those workers can be
    public static final int AUTO_JOIN_RANGE = 12; // idle workers this close to a road site join in by themselves
    public static final int WORK_RANGE = 3; // distance from the next block at which a worker can place it
    public static final int BLOCKS_PER_WORKER = 1; // blocks placed by each worker every WORK_TICKS
    public static final int WORK_TICKS = 10;

    public static List<RoadConnection> getConnections() {
        return connections;
    }

    public static boolean isRoadPos(int x, int y, int z) {
        return roadBlocks.containsKey(BlockPos.asLong(x, y, z));
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

        // an irregular apron around each building it connects, joined up by the road; built in that order
        Set<BlockPos> roadBlocksToPlace = new LinkedHashSet<>(RoadPathTracer.traceApron(level, from));
        roadBlocksToPlace.addAll(path.blocks);
        roadBlocksToPlace.addAll(RoadPathTracer.traceApron(level, to));
        roadBlocksToPlace.removeIf(bp -> roadBlocks.containsKey(bp.asLong()));
        RoadConnection connection = new RoadConnection(from.originPos, to.originPos, ownerName, type, null,
            new ArrayList<>(roadBlocksToPlace), path.length, 0);
        addConnection(connection);
        startConstruction(level, connection);
        return true;
    }

    private static boolean tryUpgradeRoad(ServerLevel level, String ownerName, RoadConnection existing,
                                          BuildingPlacement from, BuildingPlacement to) {
        if (existing.type.ordinal() >= RoadType.values().length - 1) {
            HudClientboundPacket.showTempMessageI18n(ownerName, "hud.reignofnether.road_max_level");
            return false;
        }
        if (existing.isUnderConstruction()) {
            HudClientboundPacket.showTempMessageI18n(ownerName, "hud.reignofnether.road_under_construction");
            return false;
        }
        RoadType nextType = RoadType.values()[existing.type.ordinal() + 1];
        if (!payForRoad(ownerName, nextType, existing.length))
            return false;
        existing.upgradeTo = nextType;
        existing.built = 0;
        startConstruction(level, existing);
        return true;
    }

    private static void startConstruction(ServerLevel level, RoadConnection connection) {
        if (connection.blocks.isEmpty()) {
            finishConstruction(level, connection);
            return;
        }
        int sent = sendIdleWorkers(level, connection, WORKERS_SENT, WORKERS_SEARCH_RANGE);
        if (sent == 0)
            HudClientboundPacket.showTempMessageI18n(connection.ownerName, "hud.reignofnether.road_no_workers");
        saveRoads(level);
        syncToClients();
    }

    // assigns up to [max] of the owner's idle workers within [range] of the road's next block, closest first
    private static int sendIdleWorkers(ServerLevel level, RoadConnection connection, int max, int range) {
        BlockPos next = connection.getNextBlock();
        if (next == null)
            return 0;
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity.level() != level || !entity.isAlive() || !(entity instanceof WorkerUnit worker) ||
                workerRoads.containsKey(entity.getId()) || !((Unit) entity).getOwnerName().equals(connection.ownerName) ||
                !WorkerUnit.isIdle(worker) || entity.distanceToSqr(next.getX(), next.getY(), next.getZ()) > range * range)
                continue;
            candidates.add(entity);
        }
        candidates.sort((a, b) -> Double.compare(
            a.distanceToSqr(next.getX(), next.getY(), next.getZ()), b.distanceToSqr(next.getX(), next.getY(), next.getZ())));
        int sent = 0;
        for (LivingEntity entity : candidates) {
            if (sent >= max)
                break;
            workerRoads.put(entity.getId(), connection);
            workerTargets.remove(entity.getId());
            sent++;
        }
        return sent;
    }

    private static void releaseWorker(int entityId) {
        workerRoads.remove(entityId);
        workerTargets.remove(entityId);
    }

    // the player gave the worker another order since we last sent it somewhere
    private static boolean isBusyElsewhere(Mob mob, WorkerUnit worker) {
        BlockPos moveTarget = ((Unit) mob).getMoveGoal().getMoveTarget();
        return worker.getBuildRepairGoal().getBuildingTarget() != null ||
            worker.getGatherResourceGoal().isGathering() ||
            worker.getGatherResourceGoal().isFarming() ||
            ((Unit) mob).getTargetGoal().getTarget() != null ||
            (moveTarget != null && !moveTarget.equals(workerTargets.get(mob.getId())));
    }

    private static void tickRoadWorkers(ServerLevel level) {
        for (RoadConnection connection : connections)
            if (connection.isUnderConstruction())
                sendIdleWorkers(level, connection, Integer.MAX_VALUE, AUTO_JOIN_RANGE);

        Set<RoadConnection> finished = new HashSet<>();
        for (Map.Entry<Integer, RoadConnection> entry : new ArrayList<>(workerRoads.entrySet())) {
            int id = entry.getKey();
            RoadConnection connection = entry.getValue();
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !(entity instanceof WorkerUnit worker) || !mob.isAlive() ||
                !connections.contains(connection) || !connection.isUnderConstruction() || isBusyElsewhere(mob, worker)) {
                releaseWorker(id);
                continue;
            }
            BlockPos next = connection.getNextBlock();
            if (next == null)
                continue;
            double dx = mob.getX() - (next.getX() + 0.5), dz = mob.getZ() - (next.getZ() + 0.5);
            if (dx * dx + dz * dz <= WORK_RANGE * WORK_RANGE) {
                mob.getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 1, next.getZ() + 0.5);
                mob.swing(InteractionHand.MAIN_HAND);
                for (int i = 0; i < BLOCKS_PER_WORKER && connection.isUnderConstruction(); i++)
                    if (placeNextBlock(level, connection))
                        finished.add(connection);
            } else {
                BlockPos sentTo = workerTargets.get(id);
                BlockPos moveTarget = ((Unit) mob).getMoveGoal().getMoveTarget();
                if (moveTarget == null || sentTo == null || sentTo.distSqr(next.above()) > WORK_RANGE * WORK_RANGE) {
                    workerTargets.put(id, next.above());
                    ((Unit) mob).setMoveTarget(next.above());
                }
            }
        }
        for (RoadConnection connection : finished)
            finishConstruction(level, connection);
    }

    // places the road's next block; returns true if that was the last one
    private static boolean placeNextBlock(ServerLevel level, RoadConnection connection) {
        BlockPos bp = connection.getNextBlock();
        if (bp == null)
            return false;
        if (connection.upgradeTo != null) {
            // only replace blocks that are still road, in case something was built over part of it since
            if (RoadType.isRoadBlock(level.getBlockState(bp)))
                level.setBlockAndUpdate(bp, connection.upgradeTo.getStateAt(bp));
        } else {
            placeRoadBlock(level, bp, connection.type);
            roadBlocks.put(bp.asLong(), connection);
        }
        connection.built++;
        return connection.built >= connection.blocks.size();
    }

    private static void finishConstruction(ServerLevel level, RoadConnection connection) {
        if (connection.upgradeTo != null) {
            connection.type = connection.upgradeTo;
            connection.upgradeTo = null;
        }
        connection.built = connection.blocks.size();
        workerRoads.values().removeIf(c -> c == connection);
        workerTargets.keySet().removeIf(id -> !workerRoads.containsKey(id));
        for (BlockPos origin : List.of(connection.from, connection.to)) {
            BuildingPlacement placement = BuildingUtils.findBuilding(false, origin);
            if (placement != null)
                adaptBuildingFloor(level, placement);
        }
        saveRoads(level);
        syncToClients();
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

    private static void placeRoadBlock(ServerLevel level, BlockPos bp, RoadType type) {
        level.setBlockAndUpdate(bp, type.getStateAt(bp));
        // clear grass, flowers, snow layers etc. sitting on the road
        BlockPos above = bp.above();
        if (level.getBlockState(above).canBeReplaced() && level.getBlockState(above).getFluidState().isEmpty())
            level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
    }

    // material for a building's road floor tiles: the best road connected to it, or null if it has no road
    @Nullable
    public static RoadType getFloorType(BlockPos buildingOrigin) {
        RoadType best = null;
        for (RoadConnection connection : connections)
            if (connection.connects(buildingOrigin) && isOpen(connection) && (best == null || connection.type.ordinal() > best.ordinal()))
                best = connection.type;
        return best;
    }

    // a road counts once fully built; an upgrade in progress keeps the previous tier until done
    private static boolean isOpen(RoadConnection connection) {
        return connection.upgradeTo != null || connection.built >= connection.blocks.size();
    }

    // replaces the building's placed road floor tiles (dirt path in its structure) and the road apron around it
    // with the material of the best road connected to it
    public static void adaptBuildingFloor(ServerLevel level, BuildingPlacement placement) {
        RoadType type = getFloorType(placement.originPos);
        if (type == null)
            return;
        for (BuildingBlock block : placement.getBlocks())
            if (RoadType.isRoadFloorMarker(block.getBlockState()) && block.isPlaced(level))
                level.setBlockAndUpdate(block.getBlockPos(), type.getStateAt(block.getBlockPos()));
        for (BlockPos bp : RoadPathTracer.traceApron(level, placement))
            if (RoadType.isRoadBlock(level.getBlockState(bp)))
                level.setBlockAndUpdate(bp, type.getStateAt(bp));
    }

    // a path apron around a newly finished building, inviting the player to connect it by road;
    // it takes the road's material once a road reaches the building
    public static void placeApron(ServerLevel level, BuildingPlacement placement) {
        RoadType floorType = getFloorType(placement.originPos);
        RoadType type = floorType != null ? floorType : RoadType.PATH;
        for (BlockPos bp : RoadPathTracer.traceApron(level, placement)) {
            if (RoadType.isRoadBlock(level.getBlockState(bp)))
                continue;
            level.setBlockAndUpdate(bp, type.getStateAt(bp));
            BlockPos above = bp.above();
            if (level.getBlockState(above).canBeReplaced() && level.getBlockState(above).getFluidState().isEmpty())
                level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
        }
    }

    private static void addConnection(RoadConnection connection) {
        connections.add(connection);
        int registered = connection.upgradeTo != null ? connection.blocks.size() : connection.built;
        for (int i = 0; i < registered && i < connection.blocks.size(); i++)
            roadBlocks.put(connection.blocks.get(i).asLong(), connection);
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
        workerRoads.values().removeIf(c -> !connections.contains(c));
        workerTargets.keySet().removeIf(id -> !workerRoads.containsKey(id));
    }

    // tells every client which roads exist (for the road preview and costs)
    public static void syncToClients() {
        RoadClientboundPacket.syncRoads(connections);
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
        workerRoads.clear();
        workerTargets.clear();
        if (serverLevel != null)
            RoadSaveData.getInstance(serverLevel).connections.forEach(RoadServerEvents::addConnection);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        saveTicks += 1;
        if (saveTicks % WORK_TICKS == 0)
            tickRoadWorkers(serverLevel);
        if (saveTicks >= 600) {
            saveTicks = 0;
            int before = connections.size();
            removeConnectionsToMissingBuildings();
            if (connections.size() != before) {
                saveRoads(serverLevel);
                syncToClients();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer player)
            RoadClientboundPacket.syncRoads(player, connections);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        if (serverLevel != null)
            saveRoads(serverLevel);
        serverLevel = null;
    }
}
