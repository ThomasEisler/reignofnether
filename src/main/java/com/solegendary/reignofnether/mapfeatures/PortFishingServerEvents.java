package com.solegendary.reignofnether.mapfeatures;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;

// Fishermen: villagers sent to a Port (right click on it) take a boat from there, sail to the nearest fishing spot
// (bubbles), fish for a while, sail back and bring the catch to the port, again and again.
public class PortFishingServerEvents {

    public static final int MAX_FISHERS_PER_PORT = 3;
    public static final int SAIL_RANGE = 80; // how far (along the water) a fishing spot can be from the port
    public static final int FISHING_TICKS = 20 * 20;
    public static final int CATCHES_PER_TRIP = 4; // taken from the fishing spot
    public static final int FOOD_PER_TRIP = 20;
    public static final double BOAT_SPEED = 0.22; // blocks per tick
    public static final String BOAT_TAG = "rtsFishingBoat";

    private enum State { WALK_TO_PORT, SAIL_OUT, FISH, SAIL_BACK }

    private static class Fisher {
        final int unitId;
        final BlockPos portOrigin;
        State state = State.WALK_TO_PORT;
        BlockPos dock; // water block next to the port where boats come and go
        BlockPos spot;
        List<BlockPos> path = new ArrayList<>(); // water blocks from the dock to the spot
        int waypoint = 0;
        int ticks = 0;
        int stuckTicks = 0;
        Vec3 lastPos = Vec3.ZERO;
        @Nullable Boat boat;
        Fisher(int unitId, BlockPos portOrigin) {
            this.unitId = unitId;
            this.portOrigin = portOrigin;
        }
    }

    private static final Map<Integer, Fisher> fishers = new HashMap<>();
    private static final List<Boat> leftoverBoats = new ArrayList<>(); // boats of fishermen from before a restart
    private static ServerLevel serverLevel = null;
    private static boolean stopping = false; // not while we put a fisherman back on land ourselves

    // ---- orders -------------------------------------------------------------------------------------------------

    public static void assign(Entity unit, BuildingPlacement port) {
        if (!(unit.level() instanceof ServerLevel level) || port.getBuilding() != Buildings.PORT || !port.isBuilt)
            return;
        long count = fishers.values().stream().filter(f -> f.portOrigin.equals(port.originPos)).count();
        if (count >= MAX_FISHERS_PER_PORT) {
            HudClientboundPacket.showTempMessageI18n(((Unit) unit).getOwnerName(), "Ce port a déjà " + MAX_FISHERS_PER_PORT + " pêcheurs");
            return;
        }
        Fisher fisher = new Fisher(unit.getId(), port.originPos);
        if (!planTrip(level, fisher, port)) {
            HudClientboundPacket.showTempMessageI18n(((Unit) unit).getOwnerName(), "Aucun coin de pêche accessible en bateau depuis ce port");
            return;
        }
        fishers.put(unit.getId(), fisher);
        ((Unit) unit).setMoveTarget(fisher.dock);
    }

    // any other order: the fisherman comes back to land and stops
    public static void stop(Entity unit) {
        if (stopping)
            return;
        Fisher fisher = fishers.remove(unit.getId());
        if (fisher != null)
            landFisher(unit, fisher);
    }

    private static void landFisher(Entity unit, Fisher fisher) {
        stopping = true;
        if (unit.getVehicle() instanceof Boat)
            unit.stopRiding();
        if (fisher.boat != null) {
            fisher.boat.discard();
            fisher.boat = null;
        }
        if (fisher.state != State.WALK_TO_PORT && fisher.dock != null) {
            BlockPos land = findLandNear(unit.level(), fisher);
            unit.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
        }
        stopping = false;
    }

    public static boolean isFishing(Entity unit) {
        return fishers.containsKey(unit.getId());
    }

    // ---- planning -----------------------------------------------------------------------------------------------

    @Nullable
    private static BuildingPlacement getPort(BlockPos origin) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.originPos.equals(origin) && b.getBuilding() == Buildings.PORT)
                return b;
        return null;
    }

    private static boolean isSailable(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER) && level.getBlockState(pos.above()).isAir();
    }

    // water blocks touching the port (at the water's surface), the port's side of them being land
    public static List<BlockPos> getDocks(Level level, BuildingPlacement port) {
        List<BlockPos> docks = new ArrayList<>();
        BlockPos min = port.minCorner, max = port.maxCorner;
        for (int y = min.getY() - 2; y <= min.getY(); y++)
            for (int x = min.getX() - 1; x <= max.getX() + 1; x++)
                for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                    boolean edge = x == min.getX() - 1 || x == max.getX() + 1 || z == min.getZ() - 1 || z == max.getZ() + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (edge && isSailable(level, pos))
                        docks.add(pos);
                }
        return docks;
    }

    // the nearest fishing spot reachable by water, and the way there
    private static boolean planTrip(ServerLevel level, Fisher fisher, BuildingPlacement port) {
        List<BlockPos> docks = getDocks(level, port);
        if (docks.isEmpty())
            return false;
        List<BlockPos> spots = new ArrayList<>(MapFeaturesServerEvents.getFishingSpots(false));
        spots.sort(Comparator.comparingDouble(s -> s.distSqr(port.centrePos)));
        for (BlockPos spot : spots) {
            if (spot.distSqr(port.centrePos) > (double) SAIL_RANGE * SAIL_RANGE)
                break;
            BlockPos dock = docks.stream().min(Comparator.comparingDouble(d -> d.distSqr(spot))).get();
            List<BlockPos> path = findWaterPath(level, dock, spot);
            if (path != null) {
                fisher.dock = dock;
                fisher.spot = spot;
                fisher.path = path;
                return true;
            }
        }
        return false;
    }

    // breadth-first search over the water's surface (same level as the dock, give or take one), then every few blocks kept
    @Nullable
    private static List<BlockPos> findWaterPath(Level level, BlockPos from, BlockPos to) {
        Map<Long, Long> cameFrom = new HashMap<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(from);
        cameFrom.put(from.asLong(), Long.MIN_VALUE);
        BlockPos found = null;
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!open.isEmpty() && cameFrom.size() < 40000) {
            BlockPos cur = open.poll();
            if (Math.abs(cur.getX() - to.getX()) <= 2 && Math.abs(cur.getZ() - to.getZ()) <= 2) {
                found = cur;
                break;
            }
            for (int[] d : dirs) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos next = cur.offset(d[0], dy, d[1]);
                    if (Math.abs(next.getY() - from.getY()) > 2 || cameFrom.containsKey(next.asLong()) ||
                        next.distSqr(from) > (double) SAIL_RANGE * SAIL_RANGE * 1.5 || !level.hasChunkAt(next) || !isSailable(level, next))
                        continue;
                    cameFrom.put(next.asLong(), cur.asLong());
                    open.add(next);
                    break;
                }
            }
        }
        if (found == null)
            return null;
        List<BlockPos> full = new ArrayList<>();
        for (long l = found.asLong(); l != Long.MIN_VALUE; l = cameFrom.get(l))
            full.add(BlockPos.of(l));
        Collections.reverse(full);
        List<BlockPos> path = new ArrayList<>();
        for (int i = 0; i < full.size(); i += 3)
            path.add(full.get(i));
        if (!path.get(path.size() - 1).equals(full.get(full.size() - 1)))
            path.add(full.get(full.size() - 1));
        return path;
    }

    private static BlockPos findLandNear(Level level, Fisher fisher) {
        BuildingPlacement port = getPort(fisher.portOrigin);
        BlockPos from = fisher.dock;
        for (int r = 1; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = from.offset(dx, 1, dz);
                    for (int dy = 1; dy >= -1; dy--) {
                        BlockPos q = p.offset(0, dy, 0);
                        if (level.getBlockState(q.below()).isSolid() && level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() &&
                            (port == null || !port.isPosInsideBuilding(q)))
                            return q;
                    }
                }
        return port != null ? port.centrePos : from.above();
    }

    // ---- sailing ------------------------------------------------------------------------------------------------

    private static Boat launchBoat(ServerLevel level, Entity unit, BlockPos at) {
        Boat boat = new Boat(level, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5);
        boat.setVariant(Boat.Type.SPRUCE);
        boat.getPersistentData().putBoolean(BOAT_TAG, true);
        level.addFreshEntity(boat);
        unit.startRiding(boat, true);
        return boat;
    }

    // steer the boat to the next point of its path; true once at the end
    private static boolean sail(Fisher fisher, List<BlockPos> path) {
        Boat boat = fisher.boat;
        if (boat == null || fisher.waypoint >= path.size())
            return true;
        BlockPos wp = path.get(fisher.waypoint);
        Vec3 target = new Vec3(wp.getX() + 0.5, boat.getY(), wp.getZ() + 0.5);
        Vec3 diff = target.subtract(boat.position());
        if (diff.horizontalDistance() < 1.2) {
            fisher.waypoint++;
            return fisher.waypoint >= path.size();
        }
        Vec3 dir = diff.normalize().scale(BOAT_SPEED);
        boat.setDeltaMovement(dir.x, boat.getDeltaMovement().y, dir.z);
        boat.setYRot((float) (Math.toDegrees(Math.atan2(-dir.x, dir.z))) - 90f);
        // stuck on a bank: hop to the waypoint
        if (boat.position().distanceToSqr(fisher.lastPos) < 0.0025) {
            if (++fisher.stuckTicks > 60) {
                boat.setPos(target.x, wp.getY() + 0.9, target.z);
                fisher.stuckTicks = 0;
            }
        } else {
            fisher.stuckTicks = 0;
        }
        fisher.lastPos = boat.position();
        return false;
    }

    private static void tickFisher(ServerLevel level, Fisher fisher, Entity unit) {
        BuildingPlacement port = getPort(fisher.portOrigin);
        if (port == null) { // the port is gone
            stop(unit);
            return;
        }
        switch (fisher.state) {
            case WALK_TO_PORT -> {
                if (unit.distanceToSqr(fisher.dock.getX() + 0.5, fisher.dock.getY() + 1, fisher.dock.getZ() + 0.5) < 3.5 * 3.5) {
                    ((Unit) unit).getMoveGoal().stopMoving();
                    fisher.boat = launchBoat(level, unit, fisher.dock);
                    fisher.state = State.SAIL_OUT;
                    fisher.waypoint = 0;
                } else if (++fisher.ticks % 40 == 0 && ((Mob) unit).getNavigation().isDone()) {
                    ((Unit) unit).setMoveTarget(fisher.dock);
                }
            }
            case SAIL_OUT -> {
                if (fisher.boat == null || !fisher.boat.isAlive() || unit.getVehicle() != fisher.boat) {
                    stop(unit);
                    return;
                }
                if (sail(fisher, fisher.path)) {
                    fisher.boat.setDeltaMovement(Vec3.ZERO);
                    fisher.state = State.FISH;
                    fisher.ticks = 0;
                }
            }
            case FISH -> {
                if (fisher.boat == null || !fisher.boat.isAlive() || unit.getVehicle() != fisher.boat) {
                    stop(unit);
                    return;
                }
                fisher.boat.setDeltaMovement(Vec3.ZERO);
                if (fisher.ticks % 10 == 0)
                    level.sendParticles(ParticleTypes.FISHING, fisher.boat.getX() + 1.5, fisher.boat.getY() + 0.3, fisher.boat.getZ(), 3, 0.3, 0, 0.3, 0.02);
                if (++fisher.ticks >= FISHING_TICKS) {
                    String owner = ((Unit) unit).getOwnerName();
                    int catches = 0;
                    for (int i = 0; i < CATCHES_PER_TRIP; i++)
                        if (MapFeaturesServerEvents.takeCatch(fisher.spot, owner))
                            catches++;
                    fisher.ticks = catches; // what is brought back, in catches
                    List<BlockPos> back = new ArrayList<>(fisher.path);
                    Collections.reverse(back);
                    back.add(fisher.dock);
                    fisher.path = back;
                    fisher.waypoint = 0;
                    fisher.state = State.SAIL_BACK;
                }
            }
            case SAIL_BACK -> {
                if (fisher.boat == null || !fisher.boat.isAlive() || unit.getVehicle() != fisher.boat) {
                    stop(unit);
                    return;
                }
                if (sail(fisher, fisher.path)) {
                    int food = FOOD_PER_TRIP * fisher.ticks / CATCHES_PER_TRIP;
                    if (food > 0)
                        ResourcesServerEvents.addSubtractResources(new Resources(((Unit) unit).getOwnerName(), food, 0, 0));
                    // off again, to the same spot or the next one
                    if (!planTrip(level, fisher, port)) {
                        stop(unit);
                        HudClientboundPacket.showTempMessageI18n(((Unit) unit).getOwnerName(), "Plus de coin de pêche accessible : le pêcheur rentre");
                        return;
                    }
                    fisher.boat.setPos(fisher.dock.getX() + 0.5, fisher.dock.getY() + 0.9, fisher.dock.getZ() + 0.5);
                    fisher.state = State.SAIL_OUT;
                    fisher.waypoint = 0;
                }
            }
        }
    }

    // ---- events -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        for (Boat boat : leftoverBoats) {
            boat.ejectPassengers();
            boat.discard();
        }
        leftoverBoats.clear();
        for (Fisher fisher : new ArrayList<>(fishers.values())) {
            Entity unit = serverLevel.getEntity(fisher.unitId);
            if (unit == null || !unit.isAlive()) {
                if (fisher.boat != null)
                    fisher.boat.discard();
                fishers.remove(fisher.unitId);
                continue;
            }
            tickFisher(serverLevel, fisher, unit);
        }
    }

    // fishing boats saved with the world: their fishermen were forgotten, so the boats go
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof Boat boat && boat.getPersistentData().getBoolean(BOAT_TAG) &&
            fishers.values().stream().noneMatch(f -> f.boat == boat))
            leftoverBoats.add(boat);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        fishers.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
        fishers.clear();
    }
}
