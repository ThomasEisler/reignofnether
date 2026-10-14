package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.barbarian.BarbarianServerEvents;
import com.solegendary.reignofnether.bot.BotSaveData.BotData;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingProductionClientboundPacket;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.nation.Cities;
import com.solegendary.reignofnether.nation.Nation;
import com.solegendary.reignofnether.nation.NationServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ResourceIndex;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.survival.spawners.WaveSpawner;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;

// Computer-run civilisations ("bots"), so a game can have 4 civilisations with only 1 or 2 people.
// Step 1 (arrival): a free nation, a free area far from every city, a Town Centre built by its first villagers.
// Step 2 (economy): villagers made up to a target, shared out between food (farms), wood and ore; houses before the
// population is full, farms as it grows, a stockpile by the forest; unfinished buildings always get a builder.
public class BotServerEvents {

    private static final int TICK_INTERVAL = 40; // a bot thinks every 2 seconds
    public static final int START_MIN_DIST = 220, START_MAX_DIST = 320; // from the other civilisations' cities
    public static final int BARBARIAN_DIST = 100;
    public static final int MAX_VILLAGERS = 18;
    public static final double FOOD_SHARE = 0.35, ORE_SHARE = 0.2; // the rest gathers wood
    public static final int WORKERS_PER_FARM = 2;
    public static final int RESOURCE_RANGE = 48; // how far from the Town Centre villagers are sent to gather

    private static final Random random = new Random();
    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    private static BotSaveData data() {
        return BotSaveData.getInstance(serverLevel);
    }

    public static boolean isBot(String name) {
        return serverLevel != null && data().bots.stream().anyMatch(b -> b.name.equals(name));
    }

    // ---- arrival ------------------------------------------------------------------------------------------------

    // adds a bot civilisation; returns a message for whoever asked
    public static String addBot() {
        if (serverLevel == null)
            return "Pas de partie en cours";
        List<Nation> free = new ArrayList<>();
        for (Nation n : Nation.values())
            if (!NationServerEvents.isTaken(n) && !PlayerServerEvents.isRTSPlayer(n.displayName))
                free.add(n);
        if (free.isEmpty())
            return "Plus de nation libre pour un bot";
        Nation nation = free.get(random.nextInt(free.size()));
        String name = nation.displayName;
        BlockPos start = findStart(serverLevel, name);
        if (start == null)
            return "Pas de place libre trouvée pour une nouvelle civilisation";
        PlayerServerEvents.startRTSBot(name, Vec3.atCenterOf(start.above()), Factions.VILLAGERS);
        if (!PlayerServerEvents.isRTSPlayer(name))
            return "Impossible d'ajouter le bot (aucun joueur connecté ?)";
        NationServerEvents.chooseNation(name, nation);
        BotData bot = new BotData();
        bot.name = name;
        bot.start = start;
        data().bots.add(bot);
        data().setDirty();
        return name + " (bot) s'installe en " + start.getX() + ", " + start.getZ();
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static boolean isGoodLand(ServerLevel level, BlockPos ground) {
        return level.getBlockState(ground).getFluidState().isEmpty() && level.getBlockState(ground.above()).getFluidState().isEmpty() &&
            !level.getBlockState(ground).is(net.minecraft.tags.BlockTags.LEAVES) && WaveSpawner.getYVariance(level, ground, 8) < 3;
    }

    // a free area: far enough from every city (and barbarian camps), not too far from the others either
    @Nullable
    private static BlockPos findStart(ServerLevel level, String name) {
        List<BlockPos> refs = new ArrayList<>();
        for (BuildingPlacement city : Cities.getCities(level))
            refs.add(city.centrePos);
        if (refs.isEmpty())
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
                refs.add(p.blockPosition());
        if (refs.isEmpty())
            return null;
        for (int attempt = 0; attempt < 250; attempt++) {
            BlockPos ref = refs.get(random.nextInt(refs.size()));
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = random.nextInt(START_MIN_DIST, START_MAX_DIST + 1);
            int x = ref.getX() + (int) (Math.cos(angle) * dist), z = ref.getZ() + (int) (Math.sin(angle) * dist);
            if (Cities.getPlacementError(level, name, x, z) != null)
                continue;
            boolean nearOther = false;
            for (BlockPos other : refs)
                if (other.distToCenterSqr(x, other.getY(), z) < (double) START_MIN_DIST * START_MIN_DIST)
                    nearOther = true;
            for (BotData b : data().bots)
                if (b.start.distToCenterSqr(x, b.start.getY(), z) < (double) START_MIN_DIST * START_MIN_DIST)
                    nearOther = true;
            for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                if (BarbarianServerEvents.OWNER_NAME.equals(b.ownerName) && b.centrePos.distToCenterSqr(x, b.centrePos.getY(), z) < BARBARIAN_DIST * BARBARIAN_DIST)
                    nearOther = true;
            if (nearOther)
                continue;
            BlockPos ground = ground(level, x, z);
            if (isGoodLand(level, ground))
                return ground;
        }
        return null;
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static List<LivingEntity> getUnits(String name) {
        List<LivingEntity> units = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le.isAlive() && name.equals(((Unit) le).getOwnerName()))
                units.add(le);
        return units;
    }

    private static List<BuildingPlacement> getBuildings(String name) {
        List<BuildingPlacement> list = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (name.equals(b.ownerName))
                list.add(b);
        return list;
    }

    private static void order(String name, UnitAction action, List<LivingEntity> units, BlockPos target) {
        if (units.isEmpty())
            return;
        int[] ids = units.stream().mapToInt(LivingEntity::getId).toArray();
        UnitServerEvents.addActionItem(name, action, -1, ids, target, new BlockPos(0, 0, 0));
    }

    private static boolean isBuilding(LivingEntity le) {
        return le instanceof WorkerUnit w && w.getBuildRepairGoal() != null && w.getBuildRepairGoal().getBuildingTarget() != null;
    }

    private static ResourceName gatherType(LivingEntity le) {
        if (!(le instanceof WorkerUnit w) || w.getGatherResourceGoal() == null)
            return ResourceName.NONE;
        GatherResourcesGoal g = w.getGatherResourceGoal();
        if (g.isFarming())
            return ResourceName.FOOD;
        ResourceName r = g.getTargetResourceName();
        return r != null ? r : ResourceName.NONE;
    }

    private static boolean isIdle(LivingEntity le) {
        return !isBuilding(le) && gatherType(le) == ResourceName.NONE && ((Mob) le).getNavigation().isDone();
    }

    private static int[] size(Building building, Level level) {
        int sx = 0, sz = 0;
        for (var bb : building.getRelativeBlockData(level)) {
            sx = Math.max(sx, bb.getBlockPos().getX() + 1);
            sz = Math.max(sz, bb.getBlockPos().getZ() + 1);
        }
        return new int[] { sx, sz };
    }

    // places a building somewhere between minR and maxR blocks from centre, built by the given villagers
    @Nullable
    private static BuildingPlacement placeNear(ServerLevel level, String name, Building building, BlockPos centre,
                                               int minR, int maxR, List<LivingEntity> builders) {
        int[] s = size(building, level);
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int r = minR + random.nextInt(Math.max(1, maxR - minR + 1));
            int cx = centre.getX() + (int) (Math.cos(angle) * r), cz = centre.getZ() + (int) (Math.sin(angle) * r);
            BlockPos g = ground(level, cx, cz);
            BlockPos origin = new BlockPos(cx - s[0] / 2, g.getY() + 1, cz - s[1] / 2);
            Rotation rotation = Rotation.NONE;
            if (BuildingValidators.getPlacementValidityError(level, building, origin, name, rotation, false, false, true) != null)
                continue;
            int[] ids = builders.stream().mapToInt(LivingEntity::getId).toArray();
            BuildingPlacement placed = BuildingServerEvents.placeBuilding(building, origin, rotation, name, ids, false, false, false, true);
            if (placed != null)
                return placed;
        }
        return null;
    }

    private static List<LivingEntity> nearest(List<LivingEntity> from, BlockPos pos, int count) {
        List<LivingEntity> sorted = new ArrayList<>(from);
        sorted.sort(Comparator.comparingDouble(le -> le.distanceToSqr(Vec3.atCenterOf(pos))));
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    // ---- thinking -----------------------------------------------------------------------------------------------

    private static void think(ServerLevel level, BotData bot) {
        bot.ticksAlive += TICK_INTERVAL;
        List<LivingEntity> units = getUnits(bot.name);
        List<LivingEntity> villagers = units.stream().filter(u -> u instanceof WorkerUnit).toList();
        List<BuildingPlacement> buildings = getBuildings(bot.name);
        BuildingPlacement capital = buildings.stream().filter(b -> b.getBuilding().isCapitol).findFirst().orElse(null);

        // arrival: the Town Centre, near where the bot came in
        if (capital == null) {
            if (bot.capitalPlaced || villagers.isEmpty())
                return; // lost its last city: nothing more to do for now
            BuildingPlacement tc = placeNear(level, bot.name, Buildings.TOWN_CENTRE, bot.start, 0, 10, villagers);
            if (tc == null && bot.ticksAlive > 20 * 60) // can't find a valid spot for a minute: put it down anyway
                tc = BuildingServerEvents.placeBuilding(Buildings.TOWN_CENTRE, bot.start.above(), Rotation.NONE, bot.name,
                    villagers.stream().mapToInt(LivingEntity::getId).toArray(), false, false, true, true);
            if (tc != null) {
                bot.capitalPlaced = true;
                data().setDirty();
            }
            return;
        }

        // unfinished buildings always have a builder
        List<LivingEntity> free = new ArrayList<>(villagers.stream().filter(BotServerEvents::isIdle).toList());
        for (BuildingPlacement b : buildings) {
            if (b.isBuilt)
                continue;
            boolean hasBuilder = villagers.stream().anyMatch(v -> v instanceof WorkerUnit w && w.getBuildRepairGoal() != null &&
                w.getBuildRepairGoal().getBuildingTarget() == b);
            if (hasBuilder)
                continue;
            List<LivingEntity> pool = !free.isEmpty() ? free : villagers.stream().filter(v -> !isBuilding(v)).toList();
            List<LivingEntity> builders = nearest(pool, b.centrePos, b.isCapitol ? 3 : 1);
            order(bot.name, UnitAction.BUILD_REPAIR, builders, b.centrePos);
            free.removeAll(builders);
        }
        if (!capital.isBuilt)
            return;

        int pop = UnitServerEvents.getCurrentPopulation(bot.name);
        int supply = BuildingServerEvents.getTotalPopulationSupply(bot.name);

        // more villagers
        if (villagers.size() < MAX_VILLAGERS && pop < supply && capital instanceof ProductionPlacement pp && pp.productionQueue.isEmpty()) {
            if (pp.startProductionItem(ProductionItems.VILLAGER))
                BuildingProductionClientboundPacket.startProduction(bot.name, capital.originPos,
                    ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.VILLAGER).toString());
        }

        boolean building = buildings.stream().anyMatch(b -> !b.isBuilt);
        List<LivingEntity> builderPool = !free.isEmpty() ? free : villagers.stream().filter(v -> !isBuilding(v)).toList();
        // a house before the population is full
        if (!building && supply - pop <= 2 && supply < UnitServerEvents.maxPopulation) {
            if (placeNear(level, bot.name, Buildings.VILLAGER_HOUSE, capital.centrePos, 10, 26, nearest(builderPool, capital.centrePos, 1)) != null)
                building = true;
        }
        // farms as the village grows
        long farms = buildings.stream().filter(b -> b.getBuilding() == Buildings.WHEAT_FARM).count();
        if (!building && villagers.size() >= 5 && farms < Math.min(4, villagers.size() / 5 + 1)) {
            if (placeNear(level, bot.name, Buildings.WHEAT_FARM, capital.centrePos, 10, 24, nearest(builderPool, capital.centrePos, 1)) != null)
                building = true;
        }
        // a stockpile by the nearest forest
        boolean hasStockpile = buildings.stream().anyMatch(b -> b.getBuilding() == Buildings.OAK_STOCKPILE);
        if (!building && !hasStockpile && villagers.size() >= 6) {
            Optional<BlockPos> wood = ResourceIndex.get(level).findClosest(level, capital.centrePos, RESOURCE_RANGE, ResourceName.WOOD, bp -> true);
            if (wood.isPresent() && wood.get().distSqr(capital.centrePos) > 20 * 20)
                placeNear(level, bot.name, Buildings.OAK_STOCKPILE, wood.get(), 4, 10, nearest(builderPool, wood.get(), 1));
        }

        // share out the idle villagers: food (farms), ore, wood
        free = new ArrayList<>(villagers.stream().filter(BotServerEvents::isIdle).toList());
        if (free.isEmpty())
            return;
        int food = 0, ore = 0;
        for (LivingEntity v : villagers) {
            ResourceName r = gatherType(v);
            if (r == ResourceName.FOOD) food++;
            else if (r == ResourceName.ORE) ore++;
        }
        List<BuildingPlacement> farmList = buildings.stream().filter(b -> b.getBuilding() == Buildings.WHEAT_FARM && b.isBuilt).toList();
        for (LivingEntity v : free) {
            int n = villagers.size();
            if (food < Math.round(n * FOOD_SHARE) && !farmList.isEmpty()) {
                BuildingPlacement farm = farmList.stream().min(Comparator.comparingLong(f -> villagers.stream()
                    .filter(w -> w instanceof WorkerUnit wu && wu.getGatherResourceGoal() != null && wu.getGatherResourceGoal().getTargetFarm() == f).count())).get();
                long workers = villagers.stream().filter(w -> w instanceof WorkerUnit wu && wu.getGatherResourceGoal() != null &&
                    wu.getGatherResourceGoal().getTargetFarm() == farm).count();
                if (workers < WORKERS_PER_FARM) {
                    order(bot.name, UnitAction.FARM, List.of(v), farm.centrePos);
                    food++;
                    continue;
                }
            }
            ResourceName type = ore < Math.round(n * ORE_SHARE) ? ResourceName.ORE : ResourceName.WOOD;
            Optional<BlockPos> target = ResourceIndex.get(level).findClosest(level, v.blockPosition(), RESOURCE_RANGE, type, bp -> true);
            if (target.isEmpty() && type == ResourceName.ORE)
                target = ResourceIndex.get(level).findClosest(level, v.blockPosition(), RESOURCE_RANGE, type = ResourceName.WOOD, bp -> true);
            if (target.isPresent()) {
                order(bot.name, UnitAction.MOVE, List.of(v), target.get());
                if (type == ResourceName.ORE)
                    ore++;
            }
        }
    }

    // ---- events -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || ++ticks < TICK_INTERVAL)
            return;
        ticks = 0;
        for (BotData bot : new ArrayList<>(data().bots)) {
            if (!PlayerServerEvents.isRTSPlayer(bot.name)) { // removed (eg. /rts-reset)
                data().bots.remove(bot);
                data().setDirty();
                continue;
            }
            think(serverLevel, bot);
        }
        data().setDirty();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }
}
