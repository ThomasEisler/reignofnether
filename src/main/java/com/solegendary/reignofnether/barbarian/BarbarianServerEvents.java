package com.solegendary.reignofnether.barbarian;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.survival.spawners.WaveSpawner;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.Random;
import java.util.Set;

// Barbarians: an AI faction hostile to everyone. While enabled (/rts-barbarians on), camps appear on the map away from
// players' bases once they have a finished barracks, grow through 3 levels over time, keep a garrison that patrols around them and attacks anyone coming
// close, send raids against the nearest player buildings from level 2, and give loot to whoever destroys them.
public class BarbarianServerEvents {

    public static final String OWNER_NAME = "Barbarians";

    private static final int TICK_INTERVAL = 20;
    // camps only come once a player has had a finished barracks for this long, then one more each time this passes,
    // up to one camp per such player plus one
    public static final int CAMP_SPAWN_INTERVAL = 20 * 60 * 5;
    public static final int EXTRA_CAMPS = 1;
    public static final int CAMP_UPGRADE_INTERVAL = 20 * 60 * 10; // a camp gains a level every 10 minutes
    public static final int NEW_CAMP_LEVEL_INTERVAL = 20 * 60 * 15; // new camps start one level higher every 15 minutes
    public static final int RAID_INTERVAL = 20 * 60 * 6; // from level 2, a raid every 6 minutes
    public static final int UNIT_SPAWN_INTERVAL = 20 * 30; // a camp replaces one missing defender every 30 seconds
    public static final int[] GARRISON_SIZE = { 3, 5, 8 }; // per camp level
    public static final int[] LOOT_PER_RESOURCE = { 100, 200, 350 }; // food, wood and ore given for destroying a camp
    public static final int PATROL_RADIUS = 10;
    public static final int AGGRO_RANGE = 18; // enemies this close to a camp are attacked by its garrison
    public static final int HOME_RANGE = 32; // units further than this from their camp count as raiders
    public static final int LOOT_RANGE = 40; // the owner of the nearest unit this close to a destroyed camp gets the loot

    // secondary buildings around the main one: slot -> building and direction (east, west, north, south),
    // and the camp level (index) from which each slot is built
    private static final Building[] SLOT_BUILDINGS = { Buildings.BARBARIAN_TENT, Buildings.BARBARIAN_TENT, Buildings.BARBARIAN_TOWER, Buildings.BARBARIAN_TENT };
    private static final int[][] SLOT_DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, -1 }, { 0, 1 } };
    private static final int[] SLOT_MIN_LEVEL = { 0, 1, 1, 2 };
    public static final int SLOT_DISTANCE = 13; // from the camp's centre to a secondary building's centre
    public static final int OUTBUILDING_RANGE = 24; // secondary buildings this close belong to the camp (after a restart)

    // ramparts from level 2: a square of walls around the camp with a gate in the middle of each side
    public static final int WALL_RADIUS = 23;
    public static final int GATE_HALF_WIDTH = 2;
    public static final int WALL_COLUMNS_PER_TICK = 6; // walls go up progressively

    private static class Camp {
        final BlockPos origin;
        int level;
        long ticksAtLevel = 0;
        long ticksToRaid = RAID_INTERVAL;
        long ticksToSpawn = 0;
        final boolean[] slotTried = new boolean[SLOT_BUILDINGS.length];
        final List<BlockPos> outbuildings = new ArrayList<>();
        int wallTier = 0; // 0 = none, 1 = wooden palisade, 2 = stone wall
        final Deque<BlockPos> wallQueue = new ArrayDeque<>(); // ground positions of wall columns still to build
        Camp(BlockPos origin, int level) {
            this.origin = origin;
            this.level = level;
        }
    }

    private static final Random random = new Random();
    private static final Map<BlockPos, Camp> camps = new HashMap<>();
    private static final Map<Integer, BlockPos> homes = new HashMap<>(); // defender entity id -> its camp
    private static final Set<Integer> raiders = new HashSet<>();
    private static ServerLevel serverLevel = null;
    private static long ticksToNextCamp = CAMP_SPAWN_INTERVAL;
    private static int ticks = 0;

    public static boolean isEnabled() {
        return serverLevel != null && BarbarianSaveData.getInstance(serverLevel).enabled;
    }

    public static void setEnabled(boolean enabled) {
        if (serverLevel == null)
            return;
        BarbarianSaveData data = BarbarianSaveData.getInstance(serverLevel);
        data.enabled = enabled;
        data.save();
        ticksToNextCamp = CAMP_SPAWN_INTERVAL;
    }

    private static boolean isBarbarian(String ownerName) {
        return OWNER_NAME.equals(ownerName);
    }

    // a player (not barbarians, survival enemies or neutral)
    private static boolean isPlayerOwner(String ownerName) {
        return !ownerName.isBlank() && !isBarbarian(ownerName) && !SurvivalServerEvents.ENEMY_OWNER_NAME.equals(ownerName);
    }

    @Nullable
    private static BuildingPlacement getCampPlacement(BlockPos origin) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.originPos.equals(origin) && placement.getBuilding() == Buildings.BARBARIAN_CAMP)
                return placement;
        return null;
    }

    // places a camp at the given ground position; level index 0-2
    @Nullable
    public static BuildingPlacement placeCamp(BlockPos groundPos, int levelIndex) {
        BuildingPlacement placement = BuildingServerEvents.placeBuilding(Buildings.BARBARIAN_CAMP, groundPos.above(),
            Rotation.values()[random.nextInt(4)], OWNER_NAME, new int[] {}, false, false, true, true);
        if (placement == null)
            return null;
        placement.selfBuilding = true;
        BuildingUtils.clearBuildingArea(placement);
        int level = Math.max(0, Math.min(levelIndex, 2));
        if (level > 0)
            placement.applyLevel(level);
        camps.put(placement.originPos, new Camp(placement.originPos, level));
        return placement;
    }

    // remembers since when each player has a finished barracks; returns how many have had one for long enough
    private static int updateBarracksOwners(BarbarianSaveData data) {
        Set<String> owners = new HashSet<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding() == Buildings.BARRACKS && b.isBuilt && isPlayerOwner(b.ownerName))
                owners.add(b.ownerName);
        data.barracksSince.keySet().removeIf(player -> !owners.contains(player));
        for (String owner : owners)
            data.barracksSince.putIfAbsent(owner, data.ticksEnabled);
        int ready = 0;
        for (long since : data.barracksSince.values())
            if (data.ticksEnabled - since >= CAMP_SPAWN_INTERVAL)
                ready++;
        return ready;
    }

    private static void trySpawnNewCamp(ServerLevel level, int playersReady) {
        if (playersReady == 0 || camps.size() >= playersReady + EXTRA_CAMPS)
            return;
        // 70-100 blocks away from players' buildings (and other camps, which count as buildings there)
        List<BlockPos> spawns = WaveSpawner.getValidSpawnPoints(1, level, false, 8);
        if (spawns.isEmpty())
            return;
        long ticksEnabled = BarbarianSaveData.getInstance(level).ticksEnabled;
        placeCamp(spawns.get(0), (int) (ticksEnabled / NEW_CAMP_LEVEL_INTERVAL));
        ticksToNextCamp = CAMP_SPAWN_INTERVAL;
    }

    private static boolean isOutbuilding(Building building) {
        for (Building b : SLOT_BUILDINGS)
            if (b == building)
                return true;
        return false;
    }

    // places the secondary buildings the camp's level calls for, each centred SLOT_DISTANCE away in its direction
    private static void ensureOutbuildings(ServerLevel level, BuildingPlacement main, Camp camp) {
        for (int slot = 0; slot < SLOT_BUILDINGS.length; slot++) {
            if (camp.slotTried[slot] || camp.level < SLOT_MIN_LEVEL[slot])
                continue;
            camp.slotTried[slot] = true;
            int cx = main.centrePos.getX() + SLOT_DIRECTIONS[slot][0] * SLOT_DISTANCE;
            int cz = main.centrePos.getZ() + SLOT_DIRECTIONS[slot][1] * SLOT_DISTANCE;
            int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            // measure the building to centre it on the slot
            Building building = SLOT_BUILDINGS[slot];
            BuildingPlacement probe = BuildingUtils.getNewBuildingPlacement(building, level, new BlockPos(cx, cy, cz),
                Rotation.NONE, OWNER_NAME, false);
            if (probe == null)
                continue;
            BlockPos origin = new BlockPos(cx - (probe.centrePos.getX() - cx), cy, cz - (probe.centrePos.getZ() - cz));
            if (!level.getFluidState(origin.below()).isEmpty())
                continue;
            BuildingPlacement placement = BuildingServerEvents.placeBuilding(building, origin, Rotation.NONE, OWNER_NAME,
                new int[] {}, false, false, true, true);
            if (placement == null)
                continue;
            placement.selfBuilding = true;
            BuildingUtils.clearBuildingArea(placement);
            camp.outbuildings.add(placement.originPos);
        }
    }

    // secondary buildings of camps placed before a restart
    private static void adoptOutbuildings(Camp camp, BuildingPlacement main) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!isBarbarian(b.ownerName) || !isOutbuilding(b.getBuilding()) || camp.outbuildings.contains(b.originPos))
                continue;
            if (b.centrePos.distSqr(main.centrePos) <= OUTBUILDING_RANGE * OUTBUILDING_RANGE)
                camp.outbuildings.add(b.originPos);
        }
        for (int slot = 0; slot < SLOT_BUILDINGS.length; slot++)
            if (camp.level >= SLOT_MIN_LEVEL[slot])
                camp.slotTried[slot] = true;
    }

    // ground block of a column, ignoring trees and plants; null over water
    @Nullable
    private static BlockPos getGround(ServerLevel level, int x, int z) {
        BlockPos bp = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        for (int i = 0; i < 16; i++) {
            BlockState bs = level.getBlockState(bp);
            if (!bs.getFluidState().isEmpty())
                return null;
            if (!bs.is(BlockTags.LOGS) && !bs.is(BlockTags.LEAVES) && !bs.canBeReplaced() && !isWallBlock(bs))
                return bp;
            bp = bp.below();
        }
        return null;
    }

    private static boolean isWallBlock(BlockState bs) {
        return bs.is(Blocks.SPRUCE_LOG) || bs.is(Blocks.STONE_BRICKS) || bs.is(Blocks.COBBLESTONE) ||
            bs.is(Blocks.MOSSY_STONE_BRICKS) || bs.is(Blocks.COBBLESTONE_WALL);
    }

    private static void planWalls(BuildingPlacement main, Camp camp, int tier) {
        camp.wallTier = tier;
        camp.wallQueue.clear();
        int x0 = main.centrePos.getX(), z0 = main.centrePos.getZ();
        for (int d = -WALL_RADIUS; d <= WALL_RADIUS; d++) {
            if (Math.abs(d) <= GATE_HALF_WIDTH)
                continue; // gates in the middle of each side
            camp.wallQueue.add(new BlockPos(x0 + d, 0, z0 - WALL_RADIUS));
            camp.wallQueue.add(new BlockPos(x0 + WALL_RADIUS, 0, z0 + d));
            camp.wallQueue.add(new BlockPos(x0 - d, 0, z0 + WALL_RADIUS));
            camp.wallQueue.add(new BlockPos(x0 - WALL_RADIUS, 0, z0 - d));
        }
    }

    // builds the next few wall columns: a jagged wooden palisade (tier 1) or a stone wall with crenels (tier 2),
    // replacing a lower tier wall where it stands
    private static void buildWalls(ServerLevel level, Camp camp) {
        for (int i = 0; i < WALL_COLUMNS_PER_TICK && !camp.wallQueue.isEmpty(); i++) {
            BlockPos column = camp.wallQueue.poll();
            BlockPos ground = getGround(level, column.getX(), column.getZ());
            if (ground == null)
                continue;
            long hash = Mth.getSeed(column.getX(), 21, column.getZ());
            int height = camp.wallTier == 1 ? 3 + (int) Math.floorMod(hash >> 16, 2L) : 4;
            for (int h = 1; h <= height + 1; h++) {
                BlockPos bp = ground.above(h);
                BlockState current = level.getBlockState(bp);
                boolean free = (current.canBeReplaced() || current.is(BlockTags.LEAVES) || isWallBlock(current)) &&
                    current.getFluidState().isEmpty();
                if (!free || BuildingUtils.isPosInsideAnyBuilding(false, bp))
                    break;
                BlockState state;
                if (h > height)
                    state = Blocks.AIR.defaultBlockState(); // clears the old, taller palisade top if any
                else if (camp.wallTier == 1)
                    state = Blocks.SPRUCE_LOG.defaultBlockState();
                else if (h == height)
                    state = (column.getX() + column.getZ()) % 2 == 0 ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState();
                else
                    state = switch ((int) Math.floorMod(hash >> 20, 4L)) {
                        case 0 -> Blocks.COBBLESTONE.defaultBlockState();
                        case 1 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                        default -> Blocks.STONE_BRICKS.defaultBlockState();
                    };
                if (h > height && !isWallBlock(current))
                    break;
                level.setBlockAndUpdate(bp, state);
            }
        }
    }

    private static List<EntityType<? extends Mob>> getUnitTypes(int level) {
        List<EntityType<? extends Mob>> types = new ArrayList<>(List.of(
            EntityRegistrar.MILITIA_UNIT.get(), EntityRegistrar.VINDICATOR_UNIT.get()));
        if (level >= 1)
            types.add(EntityRegistrar.PILLAGER_UNIT.get());
        if (level >= 2) {
            types.add(EntityRegistrar.VINDICATOR_UNIT.get());
            if (random.nextInt(4) == 0)
                types.add(EntityRegistrar.RAVAGER_UNIT.get());
        }
        return types;
    }

    private static void spawnDefender(ServerLevel level, BuildingPlacement placement, Camp camp) {
        List<EntityType<? extends Mob>> types = getUnitTypes(camp.level);
        BlockPos near = placement.centrePos.offset(random.nextInt(-6, 7), 0, random.nextInt(-6, 7));
        BlockPos ground = placement.getClosestGroundPos(near, 2);
        if (ground == null)
            return;
        Entity entity = UnitServerEvents.spawnMob(types.get(random.nextInt(types.size())), level, ground, OWNER_NAME);
        if (entity != null)
            homes.put(entity.getId(), camp.origin);
    }

    private static void order(LivingEntity entity, UnitAction action, BlockPos target) {
        UnitAction a = action == UnitAction.ATTACK_MOVE && !(entity instanceof AttackerUnit) ? UnitAction.MOVE : action;
        UnitServerEvents.addActionItem(OWNER_NAME, a, -1, new int[] { entity.getId() }, target, new BlockPos(0, 0, 0));
    }

    private static boolean isBusy(LivingEntity entity) {
        return ((Unit) entity).getTargetGoal().getTarget() != null || !((Mob) entity).getNavigation().isDone();
    }

    @Nullable
    private static BuildingPlacement getNearestPlayerBuilding(Entity entity) {
        BuildingPlacement nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!isPlayerOwner(b.ownerName) || b.getBuilding().invulnerable)
                continue;
            double dist = b.centrePos.distToCenterSqr(entity.position());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = b;
            }
        }
        return nearest;
    }

    @Nullable
    private static LivingEntity getNearestPlayerUnit(BlockPos pos, int range) {
        LivingEntity nearest = null;
        double nearestDist = (double) range * range;
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!entity.isAlive() || !isPlayerOwner(((Unit) entity).getOwnerName()))
                continue;
            double dist = entity.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private static void onCampDestroyed(Camp camp) {
        ticksToNextCamp = Math.max(ticksToNextCamp, CAMP_SPAWN_INTERVAL); // a respite before a new camp replaces it
        LivingEntity looter = getNearestPlayerUnit(camp.origin, LOOT_RANGE);
        if (looter != null) {
            String owner = ((Unit) looter).getOwnerName();
            int loot = Math.round(LOOT_PER_RESOURCE[camp.level] * com.solegendary.reignofnether.nation.Nation.barbarianLoot(false, owner));
            ResourcesServerEvents.addSubtractResources(new Resources(owner, loot, loot, loot));
            HudClientboundPacket.showTempMessageI18n(owner, "hud.reignofnether.barbarian_camp_destroyed");
        }
        // its secondary buildings collapse with it (the walls stay as ruins)
        for (BlockPos origin : camp.outbuildings)
            for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                if (b.originPos.equals(origin))
                    b.destroyRandomBlocks(b.getMaxHealth() * 10);
        // the survivors of the camp go on a rampage
        for (Map.Entry<Integer, BlockPos> entry : homes.entrySet())
            if (entry.getValue().equals(camp.origin))
                raiders.add(entry.getKey());
        homes.values().removeIf(origin -> origin.equals(camp.origin));
    }

    // units that lost track of their camp (eg. after a restart) join the nearest one, or raid if there is none close
    private static void adoptStrayUnits() {
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            int id = entity.getId();
            if (!entity.isAlive() || !isBarbarian(((Unit) entity).getOwnerName()) || homes.containsKey(id) || raiders.contains(id))
                continue;
            Camp nearest = null;
            double nearestDist = (double) HOME_RANGE * HOME_RANGE;
            for (Camp camp : camps.values()) {
                double dist = entity.distanceToSqr(camp.origin.getX(), camp.origin.getY(), camp.origin.getZ());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = camp;
                }
            }
            if (nearest != null)
                homes.put(id, nearest.origin);
            else
                raiders.add(id);
        }
    }

    private static void tick(ServerLevel level) {
        BarbarianSaveData data = BarbarianSaveData.getInstance(level);
        if (data.enabled) {
            data.ticksEnabled += TICK_INTERVAL;
            data.setDirty();
            int playersReady = updateBarracksOwners(data);
            // the first camp comes as soon as a barracks has stood long enough (the countdown waits at 0 until then),
            // the next ones one interval apart
            ticksToNextCamp -= TICK_INTERVAL;
            if (ticksToNextCamp <= 0)
                trySpawnNewCamp(level, playersReady);
        }
        // camps placed before a restart
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.getBuilding() == Buildings.BARBARIAN_CAMP && !camps.containsKey(placement.originPos)) {
                Camp camp = new Camp(placement.originPos, placement.getLevelIndex());
                adoptOutbuildings(camp, placement);
                camps.put(placement.originPos, camp);
            }

        // forget dead units
        homes.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        raiders.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        adoptStrayUnits();

        for (Camp camp : new ArrayList<>(camps.values())) {
            BuildingPlacement placement = getCampPlacement(camp.origin);
            if (placement == null) {
                camps.remove(camp.origin);
                onCampDestroyed(camp);
                continue;
            }
            tickCamp(level, placement, camp, data.enabled);
        }
        tickRaiders(level);
    }

    private static void tickCamp(ServerLevel level, BuildingPlacement placement, Camp camp, boolean enabled) {
        camp.level = placement.getLevelIndex();
        List<LivingEntity> garrison = new ArrayList<>();
        for (Map.Entry<Integer, BlockPos> entry : homes.entrySet())
            if (entry.getValue().equals(camp.origin) && level.getEntity(entry.getKey()) instanceof LivingEntity le)
                garrison.add(le);

        if (placement.isBuilt) {
            camp.outbuildings.removeIf(origin -> BuildingServerEvents.getBuildings().stream().noneMatch(b -> b.originPos.equals(origin)));
            ensureOutbuildings(level, placement, camp);
            int wallTier = camp.level >= 2 ? 2 : camp.level >= 1 ? 1 : 0;
            if (wallTier > camp.wallTier)
                planWalls(placement, camp, wallTier);
            buildWalls(level, camp);
        }
        if (enabled && placement.isBuilt) {
            // grow
            camp.ticksAtLevel += TICK_INTERVAL;
            if (camp.level < 2 && camp.ticksAtLevel >= CAMP_UPGRADE_INTERVAL) {
                camp.ticksAtLevel = 0;
                placement.applyLevel(camp.level + 1);
                camp.level = placement.getLevelIndex();
            }
            // replace fallen defenders
            camp.ticksToSpawn -= TICK_INTERVAL;
            if (garrison.size() < GARRISON_SIZE[camp.level] && camp.ticksToSpawn <= 0) {
                camp.ticksToSpawn = UNIT_SPAWN_INTERVAL;
                spawnDefender(level, placement, camp);
            }
            // raids
            if (camp.level >= 1) {
                camp.ticksToRaid -= TICK_INTERVAL;
                if (camp.ticksToRaid <= 0 && garrison.size() >= 2) {
                    camp.ticksToRaid = RAID_INTERVAL;
                    for (int i = 0; i < garrison.size() / 2; i++) {
                        LivingEntity raider = garrison.get(i);
                        homes.remove(raider.getId());
                        raiders.add(raider.getId());
                        BuildingPlacement target = getNearestPlayerBuilding(raider);
                        if (target != null)
                            order(raider, UnitAction.ATTACK_MOVE, target.getClosestGroundPos(raider.getOnPos(), 1));
                    }
                }
            }
        }
        // defend: anyone coming close to the camp is attacked
        LivingEntity intruder = getNearestPlayerUnit(placement.centrePos, AGGRO_RANGE);
        for (LivingEntity defender : garrison) {
            if (!homes.containsKey(defender.getId()))
                continue;
            if (intruder != null) {
                if (((Unit) defender).getTargetGoal().getTarget() == null)
                    order(defender, UnitAction.ATTACK_MOVE, intruder.getOnPos());
            } else if (!isBusy(defender) && random.nextInt(6) == 0) {
                // patrol around the camp, or walk back to it
                BlockPos spot = placement.centrePos.offset(
                    random.nextInt(-PATROL_RADIUS, PATROL_RADIUS + 1), 0, random.nextInt(-PATROL_RADIUS, PATROL_RADIUS + 1));
                BlockPos ground = placement.getClosestGroundPos(spot, 2);
                if (ground != null)
                    order(defender, UnitAction.MOVE, ground);
            }
        }
    }

    private static void tickRaiders(ServerLevel level) {
        for (int id : new ArrayList<>(raiders)) {
            if (!(level.getEntity(id) instanceof LivingEntity raider) || isBusy(raider))
                continue;
            BuildingPlacement target = getNearestPlayerBuilding(raider);
            if (target != null)
                order(raider, UnitAction.ATTACK_MOVE, target.getClosestGroundPos(raider.getOnPos(), 1));
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        camps.clear();
        homes.clear();
        raiders.clear();
        ticksToNextCamp = CAMP_SPAWN_INTERVAL;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        if (++ticks >= TICK_INTERVAL) {
            ticks = 0;
            tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }
}
