package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Rampart segments and gates: placing them, and gates that slide open for their owner's (and allies') units and
// close behind them. Everyone else has to break through.
public class RampartServerEvents {

    private static final int GATE_TICK_INTERVAL = 2; // a gate slides one column per step
    public static final int MAX_PATH_POINTS = 32;
    private static final double GATE_OPEN_RANGE = 6; // from the gate's centre

    // how many columns of each gate are open, by gate origin
    private static final Map<BlockPos, Integer> gateOpenColumns = new HashMap<>();
    private static int ticks = 0;

    // places a segment: `columns` are ground positions (in order along the wall), each with its height
    @Nullable
    public static BuildingPlacement placeSegment(ServerLevel level, RampartBuilding building, String ownerName,
                                                 List<RampartShapes.Column> columns, int[] builderIds, boolean selfBuilding) {
        return placeSegment(level, building, ownerName, columns, builderIds, selfBuilding, false);
    }

    // queue: the builders build it after what they're already doing (shift-queue)
    @Nullable
    public static BuildingPlacement placeSegment(ServerLevel level, RampartBuilding building, String ownerName,
                                                 List<RampartShapes.Column> columns, int[] builderIds, boolean selfBuilding, boolean queue) {
        if (columns.isEmpty())
            return null;
        RampartShapes.Column first = columns.get(0);
        BlockPos origin = new BlockPos(first.x(), first.y(), first.z());
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.originPos.equals(origin))
                return null;
        RampartShapes.put(level, origin, columns);
        BuildingPlacement placement = BuildingServerEvents.placeBuilding(building, origin, Rotation.NONE, ownerName,
            builderIds, queue, false, true, true);
        if (placement != null && selfBuilding)
            placement.selfBuilding = true;
        return placement;
    }

    // a player's rampart: checked again here, paid for, then cut into segments the workers build one after another
    public static void buildPlayerRampart(ServerLevel level, String ownerName, RampartBuilding building,
                                          BlockPos start, BlockPos end, int[] builderIds) {
        buildPlayerRampart(level, ownerName, building, List.of(start, end), builderIds);
    }

    // the same through several points (a wall with turns, drawn point by point)
    public static void buildPlayerRampart(ServerLevel level, String ownerName, RampartBuilding building,
                                          List<BlockPos> points, int[] builderIds) {
        if (points.isEmpty() || points.size() > MAX_PATH_POINTS)
            return;
        for (int i = 0; i + 1 < points.size(); i++)
            if (points.get(i).distSqr(points.get(i + 1)) > (RampartLine.MAX_COLUMNS + 4) * (RampartLine.MAX_COLUMNS + 4))
                return;
        List<Integer> builders = new ArrayList<>();
        for (int id : builderIds)
            if (level.getEntity(id) instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit &&
                level.getEntity(id) instanceof Unit unit && unit.getOwnerName().equals(ownerName))
                builders.add(id);
        if (builders.isEmpty()) {
            com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(ownerName, "Il faut des ouvriers pour construire un rempart");
            return;
        }
        RampartLine.Plan plan = building.isGate() || building.isTower()
            ? RampartLine.plan(level, ownerName, building, points.get(0), points.get(points.size() - 1))
            : RampartLine.planPath(level, ownerName, building, points);
        if (!plan.isValid()) {
            com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(ownerName, plan.error());
            return;
        }
        boolean wood = com.solegendary.reignofnether.resources.ResourcesServerEvents.canAfford(ownerName, com.solegendary.reignofnether.resources.ResourceName.WOOD, plan.wood());
        boolean ore = com.solegendary.reignofnether.resources.ResourcesServerEvents.canAfford(ownerName, com.solegendary.reignofnether.resources.ResourceName.ORE, plan.ore());
        if (!wood || !ore) {
            com.solegendary.reignofnether.resources.ResourcesClientboundPacket.warnInsufficientResources(ownerName, true, wood, ore, true);
            return;
        }
        com.solegendary.reignofnether.resources.ResourcesServerEvents.addSubtractResources(
            new com.solegendary.reignofnether.resources.Resources(ownerName, 0, -plan.wood(), -plan.ore()));
        int[] ids = builders.stream().mapToInt(Integer::intValue).toArray();
        if (building.isTower())
            cutWallsUnder(level, ownerName, plan.columns());
        boolean queue = false;
        for (List<RampartShapes.Column> segment : RampartLine.segments(building, plan.columns())) {
            if (placeSegment(level, building, ownerName, segment, ids, false, queue) != null)
                queue = true;
        }
    }

    // a tower set into walls: the wall segments under it are split, their blocks staying where they are; the
    // remaining parts become new (already built) segments on either side
    private static void cutWallsUnder(ServerLevel level, String ownerName, List<RampartShapes.Column> footprint) {
        Set<Long> cells = new HashSet<>();
        for (RampartShapes.Column c : footprint)
            cells.add(BlockPos.asLong(c.x(), 0, c.z()));
        List<BuildingPlacement> cut = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!(b.getBuilding() instanceof RampartBuilding rb) || rb.isGate() || rb.isTower() || !b.ownerName.equals(ownerName))
                continue;
            List<RampartShapes.Column> columns = RampartShapes.get(level, b.originPos);
            if (columns != null && columns.stream().anyMatch(c -> cells.contains(BlockPos.asLong(c.x(), 0, c.z()))))
                cut.add(b);
        }
        for (BuildingPlacement b : cut) {
            List<RampartShapes.Column> columns = RampartShapes.get(level, b.originPos);
            BuildingServerEvents.getBuildings().remove(b);
            com.solegendary.reignofnether.building.BuildingClientboundPacket.removeBuilding(b.originPos);
            List<RampartShapes.Column> left = new ArrayList<>();
            for (RampartShapes.Column c : columns)
                if (!cells.contains(BlockPos.asLong(c.x(), 0, c.z())))
                    left.add(c);
            for (List<RampartShapes.Column> part : RampartLine.segments((RampartBuilding) b.getBuilding(), left))
                placeSegment(level, (RampartBuilding) b.getBuilding(), ownerName, part, new int[] {}, false);
        }
    }

    // removes a segment without a fight (eg. replaced by a better one): its blocks vanish and the building goes
    public static void removeSegment(ServerLevel level, BuildingPlacement placement) {
        for (BuildingBlock block : placement.getBlocks())
            if (placement.isPosPartOfBuilding(block.getBlockPos(), true))
                level.setBlock(block.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
    }

    public static void syncShapes(@Nullable ServerPlayer player) {
        RampartShapes.getServerShapes().forEach((origin, columns) ->
            RampartShapeClientboundPacket.send(player, BlockPos.of(origin), columns));
    }

    private static boolean isFriendly(String unitOwner, String gateOwner) {
        return unitOwner.equals(gateOwner) || AlliancesServerEvents.isAllied(unitOwner, gateOwner);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ++ticks < GATE_TICK_INTERVAL)
            return;
        ticks = 0;
        Set<BlockPos> seen = new HashSet<>();
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (!(placement.getBuilding() instanceof RampartBuilding rb) || !rb.isGate() || !placement.isBuilt)
                continue;
            Level level = placement.getLevel();
            List<RampartShapes.Column> columns = RampartShapes.get(level, placement.originPos);
            if (columns == null)
                continue;
            seen.add(placement.originPos);

            boolean friendNear = false;
            for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                if (entity.isAlive() && entity instanceof Unit unit && isFriendly(unit.getOwnerName(), placement.ownerName) &&
                    entity.position().distanceToSqr(placement.centrePos.getCenter()) <= GATE_OPEN_RANGE * GATE_OPEN_RANGE) {
                    friendNear = true;
                    break;
                }
            }
            int open = gateOpenColumns.getOrDefault(placement.originPos, 0);
            if (friendNear && open < columns.size()) {
                setColumnOpen(level, columns.get(open), true);
                open++;
            } else if (!friendNear && open > 0) {
                RampartShapes.Column column = columns.get(open - 1);
                if (isColumnClear(level, column)) { // never shut on someone
                    setColumnOpen(level, column, false);
                    open--;
                }
            }
            gateOpenColumns.put(placement.originPos, open);
        }
        gateOpenColumns.keySet().retainAll(seen);
    }

    private static boolean isColumnClear(Level level, RampartShapes.Column c) {
        AABB box = new AABB(c.x(), c.y() + 1, c.z(), c.x() + 1, c.y() + 1 + c.height(), c.z() + 1);
        return level.getEntitiesOfClass(LivingEntity.class, box).isEmpty();
    }

    private static void setColumnOpen(Level level, RampartShapes.Column c, boolean open) {
        for (int h = 1; h <= c.height(); h++) {
            BlockPos bp = new BlockPos(c.x(), c.y() + h, c.z());
            BlockState bs = level.getBlockState(bp);
            if (bs.getBlock() instanceof RampartGateBlock && bs.getValue(RampartGateBlock.OPEN) != open)
                level.setBlock(bp, bs.setValue(RampartGateBlock.OPEN, open), 3);
        }
    }

    // forget shapes of segments that no longer exist
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        Set<Long> existing = new HashSet<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding() instanceof RampartBuilding)
                existing.add(b.originPos.asLong());
        RampartShapes.getServerShapes().keySet().retainAll(existing);
        RampartShapes.save(evt.getServer().overworld());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt) {
        RampartShapes.onServerStopped();
        gateOpenColumns.clear();
    }
}
