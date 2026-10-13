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
// players' bases once they have a finished barracks, grow through 5 levels over time (Campement, Repaire, Fort,
// Forteresse, Citadelle), keep a garrison that patrols around them and attacks anyone coming close, send raids against
// the nearest player buildings from level 2, and give loot to whoever destroys them. Each level adds secondary
// buildings with their own role (see the SLOTS table): campfire, loot store, forge, alarm horn, cages, stable,
// ravager pen and the chief's tent.
public class BarbarianServerEvents {

    public static final String OWNER_NAME = "Barbarians";

    private static final int TICK_INTERVAL = 20;
    // camps only come once a player has had a finished barracks for this long, then one more each time this passes,
    // up to one camp per such player plus one
    // camp timings and garrison sizes depend on the difficulty: see BarbarianDifficulty
    public static final int[] LOOT_PER_RESOURCE = { 100, 200, 300, 400, 500 }; // food, wood and ore given for destroying a camp
    public static final int PATROL_RADIUS = 10;
    public static final int AGGRO_RANGE = 18; // enemies this close to a camp are attacked by its garrison
    public static final int HOME_RANGE = 32; // units further than this from their camp count as raiders
    public static final int LOOT_RANGE = 40; // the owner of the nearest unit this close to a destroyed camp gets the loot

    // secondary buildings around the main one: building, camp level (index) from which it is built, direction (degrees,
    // 0 = east, 90 = south) and distance from the camp's centre to its centre. An inner ring of 8, then an outer one.
    private record Slot(Building building, int minLevel, double angle, int distance) { }
    private static final Slot[] SLOTS = {
        new Slot(Buildings.BARBARIAN_TENT, 0, 0, 13),
        new Slot(Buildings.BARBARIAN_TENT, 0, 180, 13),
        new Slot(Buildings.BARBARIAN_CAMPFIRE, 0, 270, 13),    // heals barbarians around it
        new Slot(Buildings.BARBARIAN_TOWER, 1, 90, 13),
        new Slot(Buildings.BARBARIAN_LOOT, 1, 315, 13),        // raids steal resources and pile them here
        new Slot(Buildings.BARBARIAN_FORGE, 2, 135, 13),       // stronger, armoured barbarians
        new Slot(Buildings.BARBARIAN_ALARM, 2, 225, 13),       // the whole garrison (and a neighbour camp) answers
        new Slot(Buildings.BARBARIAN_CAGES, 3, 45, 13),        // villagers captured in raids
        new Slot(Buildings.BARBARIAN_STABLE, 3, 22.5, 20),     // cavaliers
        new Slot(Buildings.BARBARIAN_RAVAGER_PEN, 4, 202.5, 20), // ravagers
        new Slot(Buildings.BARBARIAN_CHIEF_TENT, 4, 112.5, 20),  // the barbarian chief
    };
    public static final int OUTBUILDING_RANGE = 26; // secondary buildings this close belong to the camp (after a restart)

    public static final int CAMPFIRE_RANGE = 10; // barbarians this close to a campfire heal
    public static final int STEAL_RANGE = 8; // raiders this close to a player's building steal from them
    public static final int STEAL_AMOUNT = 10; // of each resource, now and then
    public static final int MAX_PRISONERS = 8;
    public static final int START_PRISONERS = 2;
    public static final int ALERT_RANGE_NO_HORN = 12; // without an alarm horn only the defenders this close react
    public static final int ALARM_COOLDOWN = 20 * 60;
    public static final int ALARM_HELP_RANGE = 140; // a neighbour camp this close sends help when the horn sounds
    public static final int CHIEF_RESPAWN = 20 * 60 * 5;
    public static final int CHIEF_DEFEAT_EFFECT = 20 * 60 * 3; // the garrison is weakened this long when its chief falls

    // ramparts: a closed rough circle of wall segments (buildings units can attack) around the camp, with 2 gates that
    // slide open for the barbarians. They only come at level 3 (Fort) and are rebuilt further out as the camp grows
    public static final int[] WALL_RADIUS = { 0, 0, 23, 31, 35 }; // average radius per camp level
    public static final double WALL_RADIUS_VARIATION = 3; // how far the wall wanders in and out
    public static final int GATE_HALF_WIDTH = 2; // gates are 5 blocks wide
    public static final int GATES = 2;
    public static final int SEGMENT_LENGTH = 5; // columns per wall segment
    public static final int SEGMENTS_PER_TICK = 2; // walls go up progressively

    private static class Camp {
        final BlockPos origin;
        int level;
        long ticksAtLevel = 0;
        long ticksToRaid = diff().raidInterval;
        long ticksToSpawn = 0;
        final boolean[] slotTried = new boolean[SLOTS.length];
        final List<BlockPos> outbuildings = new ArrayList<>();
        final Map<BlockPos, Building> outbuildingTypes = new HashMap<>();
        final Map<BlockPos, BlockPos> outbuildingCentres = new HashMap<>();
        final int[] stolen = new int[3]; // food, wood, ore piled in the loot store
        int prisoners = 0; // villagers in the cages
        long alarmCooldown = 0;
        int chiefId = -1;
        long ticksToChief = 0;
        int wallRadius = 0;
        int wallTier = 0; // 0 = none, 1 = wooden palisade, 2 = stone wall
        boolean wallTierChecked = false; // after a restart, the tier is read from the walls standing
        final Deque<List<int[]>> wallQueue = new ArrayDeque<>(); // segments still to place: x, z of their columns, last one is {isGate}
        Camp(BlockPos origin, int level) {
            this.origin = origin;
            this.level = level;
        }
    }

    private static final Random random = new Random();
    private static final Map<BlockPos, Camp> camps = new HashMap<>();
    private static final Map<Integer, BlockPos> homes = new HashMap<>(); // defender entity id -> its camp
    private static final Set<Integer> raiders = new HashSet<>();
    private static final Map<Integer, BlockPos> raiderCamps = new HashMap<>(); // raider -> the camp that sent it
    private static final Map<String, Long> lastStealMessage = new HashMap<>();
    private static ServerLevel serverLevel = null;
    private static long ticksToNextCamp = BarbarianDifficulty.NORMAL.campSpawnInterval;
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
        ticksToNextCamp = diff().campSpawnInterval;
        BarbarianClientboundPacket.sync(data.enabled, data.difficulty);
    }

    public static void setDifficulty(BarbarianDifficulty difficulty) {
        if (serverLevel == null)
            return;
        BarbarianSaveData data = BarbarianSaveData.getInstance(serverLevel);
        data.difficulty = difficulty;
        data.save();
        ticksToNextCamp = Math.min(ticksToNextCamp, difficulty.campSpawnInterval);
        BarbarianClientboundPacket.sync(data.enabled, data.difficulty);
    }

    public static BarbarianDifficulty diff() {
        return serverLevel == null ? BarbarianDifficulty.NORMAL : BarbarianSaveData.getInstance(serverLevel).difficulty;
    }

    @SubscribeEvent
    public static void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BarbarianSaveData data = BarbarianSaveData.getInstance(player.level());
            BarbarianClientboundPacket.sync(player, data.enabled, data.difficulty);
        }
    }

    private static boolean isBarbarian(String ownerName) {
        return OWNER_NAME.equals(ownerName);
    }

    // also used client side, to draw barbarians with their own look
    public static boolean isBarbarian(Entity entity) {
        return entity instanceof Unit unit && OWNER_NAME.equals(unit.getOwnerName());
    }

    // a player (not barbarians, survival enemies or neutral)
    private static boolean isPlayerOwner(String ownerName) {
        return !ownerName.isBlank() && !isBarbarian(ownerName) && !SurvivalServerEvents.ENEMY_OWNER_NAME.equals(ownerName) &&
            !com.solegendary.reignofnether.mapfeatures.MapFeaturesServerEvents.RUINS_OWNER.equals(ownerName);
    }

    @Nullable
    private static BuildingPlacement getCampPlacement(BlockPos origin) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.originPos.equals(origin) && placement.getBuilding() == Buildings.BARBARIAN_CAMP)
                return placement;
        return null;
    }

    // places a camp at the given ground position; level index 0-4
    @Nullable
    public static BuildingPlacement placeCamp(BlockPos groundPos, int levelIndex) {
        BuildingPlacement placement = BuildingServerEvents.placeBuilding(Buildings.BARBARIAN_CAMP, groundPos.above(),
            Rotation.values()[random.nextInt(4)], OWNER_NAME, new int[] {}, false, false, true, true);
        if (placement == null)
            return null;
        placement.selfBuilding = true;
        BuildingUtils.clearBuildingArea(placement);
        int level = Math.max(0, Math.min(levelIndex, 4));
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
            if (data.ticksEnabled - since >= diff().campSpawnInterval)
                ready++;
        return ready;
    }

    // camps grow big (walls up to ~40 blocks across), so they come well away from players' buildings and from each other
    public static final int CAMP_MIN_DIST = 130, CAMP_MAX_DIST = 170; // from the nearest player building
    public static final int CAMP_SPACING = 110; // between two camps

    // a fairly flat spot on dry land, CAMP_MIN_DIST to CAMP_MAX_DIST from the players' buildings
    @Nullable
    private static BlockPos findCampSpot(ServerLevel level) {
        List<BuildingPlacement> playerBuildings = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (isPlayerOwner(b.ownerName))
                playerBuildings.add(b);
        if (playerBuildings.isEmpty())
            return null;
        for (int attempt = 0; attempt < 150; attempt++) {
            BuildingPlacement from = playerBuildings.get(random.nextInt(playerBuildings.size()));
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = random.nextInt(CAMP_MIN_DIST, CAMP_MAX_DIST + 1);
            int x = from.centrePos.getX() + (int) (Math.cos(angle) * dist);
            int z = from.centrePos.getZ() + (int) (Math.sin(angle) * dist);
            boolean tooClose = false;
            for (BuildingPlacement b : playerBuildings)
                if (b.centrePos.distToCenterSqr(x, b.centrePos.getY(), z) < (double) CAMP_MIN_DIST * CAMP_MIN_DIST)
                    tooClose = true;
            for (Camp camp : camps.values())
                if (camp.origin.distToCenterSqr(x, camp.origin.getY(), z) < (double) CAMP_SPACING * CAMP_SPACING)
                    tooClose = true;
            if (tooClose)
                continue;
            BlockPos ground = getGround(level, x, z);
            if (ground == null || level.getBlockState(ground).is(BlockTags.LEAVES) ||
                BuildingUtils.isPosInsideAnyBuilding(false, ground.above()) ||
                WaveSpawner.getYVariance(level, ground, 8) >= 4)
                continue;
            return ground;
        }
        return null;
    }

    private static void trySpawnNewCamp(ServerLevel level, int playersReady) {
        if (playersReady == 0 || camps.size() >= playersReady + diff().extraCamps)
            return;
        BlockPos spot = findCampSpot(level);
        if (spot == null)
            return;
        // the clock for stronger camps starts when the first camp could come (5 min after the first barracks),
        // not when the world was made: the first camp is always level 1, then they grow from there
        BarbarianSaveData data = BarbarianSaveData.getInstance(level);
        long firstBarracks = data.barracksSince.values().stream().mapToLong(Long::longValue).min().orElse(data.ticksEnabled);
        long sinceCampsBegan = Math.max(0, data.ticksEnabled - firstBarracks - diff().campSpawnInterval);
        placeCamp(spot, (int) Math.min(4, sinceCampsBegan / diff().newCampLevelInterval));
        ticksToNextCamp = diff().campSpawnInterval;
    }

    private static boolean isOutbuilding(Building building) {
        for (Slot slot : SLOTS)
            if (slot.building() == building)
                return true;
        return false;
    }

    // a secondary building of this type stands (finished) around the camp
    @Nullable
    private static BuildingPlacement getOutbuilding(Camp camp, Building building) {
        for (BlockPos origin : camp.outbuildings)
            if (camp.outbuildingTypes.get(origin) == building)
                for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                    if (b.originPos.equals(origin) && b.isBuilt)
                        return b;
        return null;
    }

    private static boolean has(Camp camp, Building building) {
        return getOutbuilding(camp, building) != null;
    }

    private static void addOutbuilding(Camp camp, BuildingPlacement b) {
        camp.outbuildings.add(b.originPos);
        camp.outbuildingTypes.put(b.originPos, b.getBuilding());
        camp.outbuildingCentres.put(b.originPos, b.centrePos);
        if (b.getBuilding() == Buildings.BARBARIAN_CAGES)
            camp.prisoners = Math.max(camp.prisoners, START_PRISONERS);
    }

    // places the secondary buildings the camp's level calls for, each centred on its slot
    private static void ensureOutbuildings(ServerLevel level, BuildingPlacement main, Camp camp) {
        for (int slot = 0; slot < SLOTS.length; slot++) {
            if (camp.slotTried[slot] || camp.level < SLOTS[slot].minLevel())
                continue;
            camp.slotTried[slot] = true;
            double angle = Math.toRadians(SLOTS[slot].angle());
            int cx = main.centrePos.getX() + (int) Math.round(Math.cos(angle) * SLOTS[slot].distance());
            int cz = main.centrePos.getZ() + (int) Math.round(Math.sin(angle) * SLOTS[slot].distance());
            int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            // measure the building to centre it on the slot
            Building building = SLOTS[slot].building();
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
            addOutbuilding(camp, placement);
        }
    }

    // secondary buildings of camps placed before a restart
    private static void adoptOutbuildings(Camp camp, BuildingPlacement main) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!isBarbarian(b.ownerName) || !isOutbuilding(b.getBuilding()) || camp.outbuildings.contains(b.originPos))
                continue;
            if (b.centrePos.distSqr(main.centrePos) <= OUTBUILDING_RANGE * OUTBUILDING_RANGE)
                addOutbuilding(camp, b);
        }
        for (int slot = 0; slot < SLOTS.length; slot++)
            if (camp.level >= SLOTS[slot].minLevel())
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
            bs.is(Blocks.MOSSY_STONE_BRICKS) || bs.is(Blocks.COBBLESTONE_WALL) ||
            bs.is(com.solegendary.reignofnether.registrars.BlockRegistrar.PALISADE.get()) ||
            bs.is(com.solegendary.reignofnether.registrars.BlockRegistrar.PALISADE_TOP.get()) ||
            bs.is(com.solegendary.reignofnether.registrars.BlockRegistrar.STONE_RAMPART.get()) ||
            bs.getBlock() instanceof com.solegendary.reignofnether.rampart.RampartGateBlock;
    }

    // the wall's radius in each direction: a few slow waves with phases from the camp's position, so the circle looks
    // hand-made and the stone wall is rebuilt exactly where the palisade stood
    private static double wallRadius(double angle, double[] phases, int radius) {
        return radius + WALL_RADIUS_VARIATION * (0.55 * Math.sin(2 * angle + phases[0]) +
            0.3 * Math.sin(3 * angle + phases[1]) + 0.15 * Math.sin(5 * angle + phases[2]));
    }

    // none for levels 1 and 2, a palisade at level 3, stone from level 4
    private static int wallTierFor(int campLevel) {
        return campLevel >= 3 ? 2 : campLevel >= 2 ? 1 : 0;
    }

    private static void planWalls(ServerLevel level, BuildingPlacement main, Camp camp, int tier) {
        int radius = WALL_RADIUS[camp.level];
        // a stone wall replaces the palisade, or a wider wall the old one: the old segments go first
        if (camp.wallTier > 0)
            for (BuildingPlacement b : getCampWalls(camp))
                com.solegendary.reignofnether.rampart.RampartServerEvents.removeSegment(level, b);
        camp.wallTier = tier;
        camp.wallRadius = radius;
        camp.wallQueue.clear();
        int x0 = main.centrePos.getX(), z0 = main.centrePos.getZ();
        Random rand = new Random(Mth.getSeed(x0, 7, z0));
        double[] phases = { rand.nextDouble() * Math.PI * 2, rand.nextDouble() * Math.PI * 2, rand.nextDouble() * Math.PI * 2 };
        // 2 gates roughly opposite each other
        double gateStart = rand.nextDouble() * Math.PI * 2;
        double[] gates = new double[GATES];
        for (int g = 0; g < GATES; g++)
            gates[g] = gateStart + g * Math.PI * 2 / GATES + (rand.nextDouble() - 0.5) * 0.6;

        // the closed ring of columns, in order, with no diagonal-only joins (units could squeeze through)
        List<int[]> ring = new ArrayList<>(); // x, z, gate index or -1
        Set<Long> seen = new HashSet<>();
        int prevX = Integer.MIN_VALUE, prevZ = 0;
        int steps = (int) (Math.PI * 2 * (radius + WALL_RADIUS_VARIATION) * 3);
        for (int i = 0; i <= steps; i++) {
            double angle = i * Math.PI * 2 / steps;
            double r = wallRadius(angle, phases, radius);
            int gate = -1;
            for (int g = 0; g < GATES; g++) {
                double diff = Math.abs(Math.atan2(Math.sin(angle - gates[g]), Math.cos(angle - gates[g])));
                if (diff * r <= GATE_HALF_WIDTH + 0.5)
                    gate = g;
            }
            int x = x0 + (int) Math.round(Math.cos(angle) * r), z = z0 + (int) Math.round(Math.sin(angle) * r);
            if (prevX != Integer.MIN_VALUE && x != prevX && z != prevZ && seen.add(BlockPos.asLong(x, 0, prevZ)))
                ring.add(new int[] { x, prevZ, gate });
            if (seen.add(BlockPos.asLong(x, 0, z)))
                ring.add(new int[] { x, z, gate });
            prevX = x;
            prevZ = z;
        }
        // start right after a gate so no segment wraps around
        int startIdx = 0;
        for (int i = 0; i < ring.size(); i++)
            if (ring.get(i)[2] == 0 && ring.get((i + 1) % ring.size())[2] != 0) {
                startIdx = (i + 1) % ring.size();
                break;
            }
        // cut into segments: each gate is one segment, walls are cut every SEGMENT_LENGTH columns
        List<int[]> current = new ArrayList<>();
        int currentGate = -2;
        for (int k = 0; k < ring.size(); k++) {
            int[] col = ring.get((startIdx + k) % ring.size());
            boolean isGate = col[2] >= 0;
            boolean newSegment = current.isEmpty() || (isGate ? col[2] != currentGate : currentGate >= 0 || current.size() >= SEGMENT_LENGTH);
            if (newSegment && !current.isEmpty()) {
                current.add(new int[] { currentGate >= 0 ? 1 : 0 });
                camp.wallQueue.add(current);
                current = new ArrayList<>();
            }
            current.add(new int[] { col[0], col[1] });
            currentGate = isGate ? col[2] : -1;
        }
        if (!current.isEmpty()) {
            current.add(new int[] { currentGate >= 0 ? 1 : 0 });
            camp.wallQueue.add(current);
        }
    }

    // the wall segments and gates standing around a camp
    private static List<BuildingPlacement> getCampWalls(Camp camp) {
        List<BuildingPlacement> walls = new ArrayList<>();
        int range = WALL_RADIUS[WALL_RADIUS.length - 1] + (int) WALL_RADIUS_VARIATION + 4;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding() instanceof com.solegendary.reignofnether.rampart.RampartBuilding && isBarbarian(b.ownerName) &&
                b.centrePos.distSqr(camp.origin) <= range * range)
                walls.add(b);
        return walls;
    }

    // places the next few planned segments, building themselves; a segment waits while an old one still stands there
    private static void buildWalls(ServerLevel level, Camp camp) {
        for (int i = 0; i < SEGMENTS_PER_TICK && !camp.wallQueue.isEmpty(); i++) {
            List<int[]> segment = camp.wallQueue.poll();
            boolean isGate = segment.get(segment.size() - 1)[0] == 1;
            com.solegendary.reignofnether.rampart.RampartBuilding building = camp.wallTier == 1
                ? (isGate ? Buildings.WOODEN_GATE : Buildings.PALISADE)
                : (isGate ? Buildings.IRON_GATE : Buildings.STONE_RAMPART);
            List<com.solegendary.reignofnether.rampart.RampartShapes.Column> columns = new ArrayList<>();
            for (int c = 0; c < segment.size() - 1; c++) {
                int x = segment.get(c)[0], z = segment.get(c)[1];
                BlockPos ground = getGround(level, x, z);
                if (ground == null || BuildingUtils.isPosInsideAnyBuilding(false, ground.above()))
                    continue; // water or another building: a gap
                columns.add(new com.solegendary.reignofnether.rampart.RampartShapes.Column(x, ground.getY(), z, building.columnHeight(x, z)));
            }
            if (columns.isEmpty())
                continue;
            com.solegendary.reignofnether.rampart.RampartShapes.Column first = columns.get(0);
            BlockPos origin = new BlockPos(first.x(), first.y(), first.z());
            if (BuildingServerEvents.getBuildings().stream().anyMatch(b -> b.originPos.equals(origin))) {
                camp.wallQueue.add(segment); // the old segment is being removed, try again later
                continue;
            }
            com.solegendary.reignofnether.rampart.RampartServerEvents.placeSegment(level, building, OWNER_NAME, columns, new int[] {}, true);
        }
    }

    private static List<EntityType<? extends Mob>> getUnitTypes(Camp camp) {
        List<EntityType<? extends Mob>> types = new ArrayList<>(List.of(
            EntityRegistrar.MILITIA_UNIT.get(), EntityRegistrar.VINDICATOR_UNIT.get()));
        if (camp.level >= 1)
            types.add(EntityRegistrar.PILLAGER_UNIT.get());
        if (camp.level >= 2)
            types.add(EntityRegistrar.VINDICATOR_UNIT.get());
        if (camp.level >= 3 && has(camp, Buildings.BARBARIAN_STABLE))
            types.add(EntityRegistrar.HORSE_UNIT.get()); // cavaliers
        if (camp.level >= 4 && has(camp, Buildings.BARBARIAN_RAVAGER_PEN))
            types.add(EntityRegistrar.RAVAGER_UNIT.get());
        return types;
    }

    private static void spawnDefender(ServerLevel level, BuildingPlacement placement, Camp camp) {
        List<EntityType<? extends Mob>> types = getUnitTypes(camp);
        BlockPos near = placement.centrePos.offset(random.nextInt(-6, 7), 0, random.nextInt(-6, 7));
        BlockPos ground = placement.getClosestGroundPos(near, 2);
        if (ground == null)
            return;
        EntityType<? extends Mob> type = types.get(random.nextInt(types.size()));
        Entity entity = UnitServerEvents.spawnMob(type, level, ground, OWNER_NAME);
        if (entity == null)
            return;
        homes.put(entity.getId(), camp.origin);
        // cavaliers: a barbarian on a war horse (the horse is the one given orders)
        if (entity instanceof com.solegendary.reignofnether.unit.units.villagers.HorseUnit horse) {
            horse.equipBarbarianHorse();
            Entity rider = UnitServerEvents.spawnMob(EntityRegistrar.VINDICATOR_UNIT.get(), level, ground, OWNER_NAME);
            if (rider != null)
                rider.startRiding(horse, true);
        }
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
        ticksToNextCamp = Math.max(ticksToNextCamp, diff().campSpawnInterval); // a respite before a new camp replaces it
        LivingEntity looter = getNearestPlayerUnit(camp.origin, LOOT_RANGE);
        if (looter != null) {
            String owner = ((Unit) looter).getOwnerName();
            int loot = Math.round(LOOT_PER_RESOURCE[camp.level] * com.solegendary.reignofnether.nation.Nation.barbarianLoot(false, owner));
            ResourcesServerEvents.addSubtractResources(new Resources(owner, loot, loot, loot));
            HudClientboundPacket.showTempMessageI18n(owner, "hud.reignofnether.barbarian_camp_destroyed");
        }
        giveBackStolen(camp, camp.origin);
        freePrisoners(serverLevel, camp, camp.origin);
        if (serverLevel != null) {
            BarbarianSaveData data = BarbarianSaveData.getInstance(serverLevel);
            if (data.campStores.remove(camp.origin.asLong()) != null)
                data.setDirty();
        }
        // its secondary buildings and walls collapse with it
        for (BlockPos origin : camp.outbuildings)
            for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                if (b.originPos.equals(origin))
                    b.destroyRandomBlocks(b.getMaxHealth() * 10);
        for (BuildingPlacement wall : getCampWalls(camp))
            wall.destroyRandomBlocks(wall.getMaxHealth() * 10);
        camp.wallQueue.clear();
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
            if (!entity.isAlive() || !isBarbarian(((Unit) entity).getOwnerName()) || homes.containsKey(id) || raiders.contains(id) ||
                entity.isPassenger())
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
            if (nearest != null) {
                homes.put(id, nearest.origin);
                if (isChief(entity) && nearest.chiefId < 0)
                    nearest.chiefId = id;
            } else
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
                int[] store = data.campStores.get(placement.originPos.asLong()); // what it had stolen and its prisoners
                if (store != null) {
                    camp.stolen[0] = store[0];
                    camp.stolen[1] = store[1];
                    camp.stolen[2] = store[2];
                    camp.prisoners = store[3];
                }
                adoptOutbuildings(camp, placement);
                camps.put(placement.originPos, camp);
            }

        // forget dead units
        homes.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        raiders.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
        raiderCamps.keySet().removeIf(id -> !raiders.contains(id));
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
        saveCampStores(data);
    }

    // stolen resources and prisoners are kept with the world, per camp (written only when they change; a camp's entry
    // goes when the camp is destroyed, so camps not loaded back yet after a restart keep theirs)
    private static void saveCampStores(BarbarianSaveData data) {
        for (Camp camp : camps.values()) {
            int[] values = { camp.stolen[0], camp.stolen[1], camp.stolen[2], camp.prisoners };
            if (!java.util.Arrays.equals(values, data.campStores.get(camp.origin.asLong()))) {
                data.campStores.put(camp.origin.asLong(), values);
                data.setDirty();
            }
        }
    }

    private static void tickCamp(ServerLevel level, BuildingPlacement placement, Camp camp, boolean enabled) {
        camp.level = placement.getLevelIndex();
        List<LivingEntity> garrison = new ArrayList<>(); // the chief isn't counted: he never raids and isn't replaced
        LivingEntity chief = camp.chiefId >= 0 && level.getEntity(camp.chiefId) instanceof LivingEntity c && c.isAlive() ? c : null;
        for (Map.Entry<Integer, BlockPos> entry : homes.entrySet())
            if (entry.getValue().equals(camp.origin) && entry.getKey() != camp.chiefId && level.getEntity(entry.getKey()) instanceof LivingEntity le)
                garrison.add(le);

        if (placement.isBuilt) {
            // secondary buildings that fell: the loot store gives back what was stolen, the cages free the prisoners
            for (BlockPos origin : new ArrayList<>(camp.outbuildings))
                if (BuildingServerEvents.getBuildings().stream().noneMatch(b -> b.originPos.equals(origin))) {
                    onOutbuildingLost(level, camp, camp.outbuildingTypes.get(origin), camp.outbuildingCentres.getOrDefault(origin, origin));
                    camp.outbuildings.remove(origin);
                    camp.outbuildingTypes.remove(origin);
                    camp.outbuildingCentres.remove(origin);
                }
            ensureOutbuildings(level, placement, camp);
            if (!camp.wallTierChecked) { // walls standing from before a restart
                camp.wallTierChecked = true;
                for (BuildingPlacement wall : getCampWalls(camp)) {
                    var kind = ((com.solegendary.reignofnether.rampart.RampartBuilding) wall.getBuilding()).kind;
                    int tier = kind == com.solegendary.reignofnether.rampart.RampartBuilding.Kind.STONE ||
                        kind == com.solegendary.reignofnether.rampart.RampartBuilding.Kind.IRON_GATE ? 2 : 1;
                    camp.wallTier = Math.max(camp.wallTier, tier);
                    camp.wallRadius = WALL_RADIUS[camp.level];
                }
            }
            int wallTier = wallTierFor(camp.level);
            if (wallTier > camp.wallTier || (wallTier > 0 && WALL_RADIUS[camp.level] > camp.wallRadius))
                planWalls(level, placement, camp, wallTier);
            buildWalls(level, camp);
        }
        if (enabled && placement.isBuilt) {
            // grow
            camp.ticksAtLevel += TICK_INTERVAL;
            if (camp.level < 4 && camp.ticksAtLevel >= diff().campUpgradeInterval) {
                camp.ticksAtLevel = 0;
                placement.applyLevel(camp.level + 1);
                camp.level = placement.getLevelIndex();
            }
            // replace fallen defenders
            camp.ticksToSpawn -= TICK_INTERVAL;
            if (garrison.size() < diff().garrisonSize[camp.level] && camp.ticksToSpawn <= 0) {
                camp.ticksToSpawn = diff().unitSpawnInterval;
                spawnDefender(level, placement, camp);
            }
            // the chief, while his tent stands
            BuildingPlacement chiefTent = getOutbuilding(camp, Buildings.BARBARIAN_CHIEF_TENT);
            if (chief == null && camp.chiefId >= 0) {
                onChiefKilled(level, camp, garrison);
            } else if (chief == null && chiefTent != null) {
                camp.ticksToChief -= TICK_INTERVAL;
                if (camp.ticksToChief <= 0)
                    chief = spawnChief(level, chiefTent, camp);
            }
            // raids
            if (camp.level >= 1) {
                camp.ticksToRaid -= TICK_INTERVAL;
                if (camp.ticksToRaid <= 0 && garrison.size() >= 2) {
                    camp.ticksToRaid = diff().raidInterval;
                    for (int i = 0; i < garrison.size() / 2; i++) {
                        LivingEntity raider = garrison.get(i);
                        homes.remove(raider.getId());
                        raiders.add(raider.getId());
                        raiderCamps.put(raider.getId(), camp.origin);
                        BuildingPlacement target = getNearestPlayerBuilding(raider);
                        if (target != null)
                            order(raider, UnitAction.ATTACK_MOVE, target.getClosestGroundPos(raider.getOnPos(), 1));
                    }
                    garrison.removeIf(le -> raiders.contains(le.getId()));
                }
            }
        }
        List<LivingEntity> everyone = new ArrayList<>(garrison);
        if (chief != null)
            everyone.add(chief);
        for (Map.Entry<Integer, BlockPos> entry : raiderCamps.entrySet())
            if (entry.getValue().equals(camp.origin) && level.getEntity(entry.getKey()) instanceof LivingEntity le && le.isAlive())
                everyone.add(le);
        tickCampfire(camp, everyone);
        tickForge(camp, everyone);

        // defend: anyone coming close to the camp is attacked. With an alarm horn the whole garrison answers (and a
        // neighbour camp sends help); without one, only the defenders close to the intruder notice.
        camp.alarmCooldown -= TICK_INTERVAL;
        LivingEntity intruder = getNearestPlayerUnit(placement.centrePos, AGGRO_RANGE);
        boolean horn = has(camp, Buildings.BARBARIAN_ALARM);
        if (intruder != null && horn && camp.alarmCooldown <= 0) {
            camp.alarmCooldown = ALARM_COOLDOWN;
            soundAlarm(level, placement, camp, intruder);
        }
        List<LivingEntity> defenders = new ArrayList<>(garrison);
        if (chief != null)
            defenders.add(chief);
        for (LivingEntity defender : defenders) {
            if (!homes.containsKey(defender.getId()))
                continue;
            if (intruder != null && (horn || defender.distanceTo(intruder) <= ALERT_RANGE_NO_HORN)) {
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

    // ---- secondary buildings' roles ----------------------------------------------------------------------------

    // campfire: barbarians around it slowly heal
    private static void tickCampfire(Camp camp, List<LivingEntity> units) {
        BuildingPlacement fire = getOutbuilding(camp, Buildings.BARBARIAN_CAMPFIRE);
        if (fire == null)
            return;
        for (LivingEntity le : units)
            if (le.getHealth() < le.getMaxHealth() && le.distanceToSqr(fire.centrePos.getCenter()) <= CAMPFIRE_RANGE * CAMPFIRE_RANGE)
                le.heal(1);
    }

    // forge: while it stands the camp's barbarians hit harder, take less damage and wear a mail helmet
    private static void tickForge(Camp camp, List<LivingEntity> units) {
        boolean forge = has(camp, Buildings.BARBARIAN_FORGE);
        for (LivingEntity le : units) {
            if (le.getId() == camp.chiefId)
                continue;
            var head = le.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            if (forge) {
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, TICK_INTERVAL * 3, 0, false, false));
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, TICK_INTERVAL * 3, 0, false, false));
                if (head.isEmpty() && !(le instanceof com.solegendary.reignofnether.unit.units.villagers.HorseUnit) &&
                    !(le instanceof com.solegendary.reignofnether.unit.units.villagers.RavagerUnit))
                    le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHAINMAIL_HELMET));
            } else if (head.is(net.minecraft.world.item.Items.CHAINMAIL_HELMET)) {
                le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    // alarm horn: a horn call, and the nearest other camp sends half of its garrison
    private static void soundAlarm(ServerLevel level, BuildingPlacement placement, Camp camp, LivingEntity intruder) {
        level.playSound(null, placement.centrePos, net.minecraft.sounds.SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(),
            net.minecraft.sounds.SoundSource.HOSTILE, 16f, 0.9f);
        Camp nearest = null;
        double nearestDist = (double) ALARM_HELP_RANGE * ALARM_HELP_RANGE;
        for (Camp other : camps.values()) {
            double dist = other.origin.distSqr(camp.origin);
            if (other != camp && dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        if (nearest == null)
            return;
        List<LivingEntity> helpers = new ArrayList<>();
        for (Map.Entry<Integer, BlockPos> entry : homes.entrySet())
            if (entry.getValue().equals(nearest.origin) && entry.getKey() != nearest.chiefId &&
                level.getEntity(entry.getKey()) instanceof LivingEntity le && le.isAlive())
                helpers.add(le);
        for (int i = 0; i < helpers.size() / 2; i++)
            order(helpers.get(i), UnitAction.ATTACK_MOVE, intruder.getOnPos());
    }

    // the chief: a strong vindicator with a golden helmet, back 5 minutes after he falls while his tent stands
    @Nullable
    private static LivingEntity spawnChief(ServerLevel level, BuildingPlacement tent, Camp camp) {
        BlockPos ground = tent.getClosestGroundPos(tent.centrePos, 4);
        if (ground == null)
            return null;
        Entity entity = UnitServerEvents.spawnMob(EntityRegistrar.VINDICATOR_UNIT.get(), level, ground, OWNER_NAME);
        if (!(entity instanceof LivingEntity chief))
            return null;
        var health = chief.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null)
            health.setBaseValue(health.getBaseValue() * 5);
        chief.setHealth(chief.getMaxHealth());
        var damage = chief.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get());
        if (damage != null)
            damage.setBaseValue(damage.getBaseValue() * 2);
        chief.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HELMET));
        chief.setCustomName(net.minecraft.network.chat.Component.literal("Chef barbare"));
        camp.chiefId = chief.getId();
        homes.put(chief.getId(), camp.origin);
        return chief;
    }

    private static boolean isChief(LivingEntity le) {
        return le.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.GOLDEN_HELMET);
    }

    // the chief fell: his warriors lose heart for a while
    private static void onChiefKilled(ServerLevel level, Camp camp, List<LivingEntity> garrison) {
        camp.chiefId = -1;
        camp.ticksToChief = CHIEF_RESPAWN;
        for (LivingEntity le : garrison) {
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, CHIEF_DEFEAT_EFFECT, 1));
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, CHIEF_DEFEAT_EFFECT, 0));
        }
        LivingEntity witness = getNearestPlayerUnit(camp.origin, LOOT_RANGE * 2);
        if (witness != null)
            HudClientboundPacket.showTempMessageI18n(((Unit) witness).getOwnerName(), "Le chef barbare est tombé ! Ses guerriers faiblissent.");
    }

    @Nullable
    private static String getNearestPlayerName(BlockPos pos) {
        LivingEntity unit = getNearestPlayerUnit(pos, LOOT_RANGE);
        return unit != null ? ((Unit) unit).getOwnerName() : null;
    }

    private static void giveBackStolen(Camp camp, BlockPos pos) {
        if (camp.stolen[0] + camp.stolen[1] + camp.stolen[2] <= 0)
            return;
        String owner = getNearestPlayerName(pos);
        if (owner != null) {
            ResourcesServerEvents.addSubtractResources(new Resources(owner, camp.stolen[0], camp.stolen[1], camp.stolen[2]));
            HudClientboundPacket.showTempMessageI18n(owner, "Réserve barbare pillée : vous récupérez " + camp.stolen[0] +
                " nourriture, " + camp.stolen[1] + " bois et " + camp.stolen[2] + " minerai.");
        }
        camp.stolen[0] = camp.stolen[1] = camp.stolen[2] = 0;
    }

    private static void freePrisoners(ServerLevel level, Camp camp, BlockPos pos) {
        if (camp.prisoners <= 0)
            return;
        String owner = getNearestPlayerName(pos);
        if (owner != null) {
            for (int i = 0; i < camp.prisoners; i++)
                UnitServerEvents.spawnMob(EntityRegistrar.VILLAGER_UNIT.get(), level, pos, owner);
            HudClientboundPacket.showTempMessageI18n(owner, camp.prisoners + " villageois libérés des cages barbares rejoignent votre peuple !");
        }
        camp.prisoners = 0;
    }

    private static void onOutbuildingLost(ServerLevel level, Camp camp, @Nullable Building building, BlockPos centre) {
        if (building == Buildings.BARBARIAN_LOOT)
            giveBackStolen(camp, centre);
        else if (building == Buildings.BARBARIAN_CAGES)
            freePrisoners(level, camp, centre);
    }

    @Nullable
    private static Camp getCampOf(int unitId) {
        BlockPos origin = homes.containsKey(unitId) ? homes.get(unitId) : raiderCamps.get(unitId);
        return origin != null ? camps.get(origin) : null;
    }

    // raiders: attack the nearest player building; near one, those of a camp with a loot store steal resources
    private static void tickRaiders(ServerLevel level) {
        for (int id : new ArrayList<>(raiders)) {
            if (!(level.getEntity(id) instanceof LivingEntity raider))
                continue;
            BuildingPlacement target = getNearestPlayerBuilding(raider);
            Camp camp = getCampOf(id);
            if (target != null && camp != null && random.nextInt(4) == 0 &&
                target.centrePos.distToCenterSqr(raider.position()) <= (double) (STEAL_RANGE + 4) * (STEAL_RANGE + 4) &&
                has(camp, Buildings.BARBARIAN_LOOT))
                steal(camp, target.ownerName);
            if (isBusy(raider))
                continue;
            if (target != null)
                order(raider, UnitAction.ATTACK_MOVE, target.getClosestGroundPos(raider.getOnPos(), 1));
        }
    }

    private static void steal(Camp camp, String owner) {
        Resources res = ResourcesServerEvents.resourcesList.stream().filter(r -> r.ownerName.equals(owner)).findFirst().orElse(null);
        if (res == null)
            return;
        int food = Math.min(STEAL_AMOUNT, Math.max(0, res.food));
        int wood = Math.min(STEAL_AMOUNT, Math.max(0, res.wood));
        int ore = Math.min(STEAL_AMOUNT, Math.max(0, res.ore));
        if (food + wood + ore <= 0)
            return;
        ResourcesServerEvents.addSubtractResources(new Resources(owner, -food, -wood, -ore));
        camp.stolen[0] += food;
        camp.stolen[1] += wood;
        camp.stolen[2] += ore;
        long now = serverLevel != null ? serverLevel.getGameTime() : 0;
        if (now - lastStealMessage.getOrDefault(owner, -100000L) > 20 * 30) {
            lastStealMessage.put(owner, now);
            HudClientboundPacket.showTempMessageI18n(owner, "Des barbares pillent vos réserves !");
        }
    }

    // a barbarian of a camp with cages kills a player's villager: it is taken prisoner instead
    @SubscribeEvent
    public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide() ||
            !(evt.getEntity() instanceof com.solegendary.reignofnether.unit.units.villagers.VillagerUnit villager) ||
            !isPlayerOwner(villager.getOwnerName()) || !(evt.getSource().getEntity() instanceof LivingEntity killer) ||
            !isBarbarian(killer))
            return;
        Camp camp = getCampOf(killer.getId());
        if (camp == null || camp.prisoners >= MAX_PRISONERS || !has(camp, Buildings.BARBARIAN_CAGES))
            return;
        camp.prisoners++;
        HudClientboundPacket.showTempMessageI18n(villager.getOwnerName(), "Un de vos villageois a été capturé par les barbares !");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        camps.clear();
        homes.clear();
        raiders.clear();
        raiderCamps.clear();
        ticksToNextCamp = diff().campSpawnInterval;
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
