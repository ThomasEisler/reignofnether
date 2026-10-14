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
// Step 3 (development): the other buildings in a set order (barracks, library, blacksmith, watchtowers, market...),
// technologies researched one after another, Town Centre and barracks levels, then new cities (up to MAX_CITIES)
// 110-150 blocks from the first one, each training its own villagers.
public class BotServerEvents {

    private static final int TICK_INTERVAL = 40; // a bot thinks every 2 seconds
    public static final int START_MIN_DIST = 220, START_MAX_DIST = 320; // from the other civilisations' cities
    public static final int BARBARIAN_DIST = 100;
    public static final int MAX_VILLAGERS = 18;
    public static final double FOOD_SHARE = 0.35, ORE_SHARE = 0.2; // the rest gathers wood
    public static final int WORKERS_PER_FARM = 2;
    public static final int RESOURCE_RANGE = 48; // how far from the Town Centre villagers are sent to gather
    public static final int VILLAGERS_PER_NEW_CITY = 8; // more villagers allowed for each city after the first
    public static final int MAX_CITIES = 3;
    public static final int NEW_CITY_VILLAGERS = 16; // villagers needed before founding another city
    public static final int NEW_CITY_MIN_DIST = 110, NEW_CITY_MAX_DIST = 150;
    public static final int FOOD_RESERVE = 100; // kept for villagers when paying for research

    // buildings wanted, in order: building, how many, villagers needed first (locked ones wait for their technology)
    private record Wish(Building building, int count, int villagers) { }
    private static final List<Wish> WISHES = List.of(
        new Wish(Buildings.BARRACKS, 1, 8),
        new Wish(Buildings.LIBRARY, 1, 10),
        new Wish(Buildings.BLACKSMITH, 1, 12),
        new Wish(Buildings.WATCHTOWER, 2, 12),
        new Wish(Buildings.VILLAGER_MARKET, 1, 14),
        new Wish(Buildings.BARRACKS, 2, 20),
        new Wish(Buildings.WATCHTOWER, 4, 22)
    );
    // technologies, in the order a bot researches them
    private static final List<com.solegendary.reignofnether.tech.Tech> RESEARCH = List.of(
        com.solegendary.reignofnether.tech.Tech.MASONRY, com.solegendary.reignofnether.tech.Tech.CARRY_BAGS,
        com.solegendary.reignofnether.tech.Tech.COMMERCE, com.solegendary.reignofnether.tech.Tech.ARCHITECTURE,
        com.solegendary.reignofnether.tech.Tech.ARCHERY, com.solegendary.reignofnether.tech.Tech.METALLURGY,
        com.solegendary.reignofnether.tech.Tech.BACKPACKS, com.solegendary.reignofnether.tech.Tech.TRADE,
        com.solegendary.reignofnether.tech.Tech.MYSTICISM, com.solegendary.reignofnether.tech.Tech.ENGINEERING,
        com.solegendary.reignofnether.tech.Tech.FORTIFICATIONS, com.solegendary.reignofnether.tech.Tech.COMMAND);

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

    // idle = not building and either not gathering or gathering with nothing found for a while (eg. the resource
    // it was sent to is out of reach). Walking doesn't count: a villager stuck on a path it can't finish was
    // never handed a new job before, which left the whole bot standing still.
    private static boolean isIdle(LivingEntity le) {
        if (isBuilding(le))
            return false;
        if (gatherType(le) == ResourceName.NONE)
            return true;
        return le instanceof WorkerUnit w && w.getGatherResourceGoal() != null
            && !w.getGatherResourceGoal().isFarming() && w.getGatherResourceGoal().isIdle();
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
        List<BuildingPlacement> cities = buildings.stream().filter(b -> b.getBuilding().isCapitol && b.isBuilt).toList();
        int maxVillagers = MAX_VILLAGERS + VILLAGERS_PER_NEW_CITY * (cities.size() - 1);

        // more villagers, from every city
        int queued = 0;
        for (BuildingPlacement city : cities) {
            if (villagers.size() + queued >= maxVillagers || pop + queued >= supply)
                break;
            if (city instanceof ProductionPlacement pp && pp.productionQueue.isEmpty() && startProduction(bot.name, pp, ProductionItems.VILLAGER))
                queued++;
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

        // development: buildings, technologies, levels, new cities
        if (!building)
            building = develop(level, bot.name, villagers, buildings, cities, builderPool);
        research(bot.name, buildings);
        upgradeLevels(bot.name, buildings, villagers.size(), maxVillagers);
        if (!building && cities.size() < MAX_CITIES && villagers.size() >= NEW_CITY_VILLAGERS && buildings.stream().noneMatch(b -> b.isCapitol && !b.isBuilt))
            foundCity(level, bot.name, capital, villagers);

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
            // searched from a random spot around the Town Centre, so a villager that couldn't reach its last
            // resource isn't sent straight back to the same one
            BlockPos home = nearestCity(cities, v.blockPosition(), capital).centrePos;
            BlockPos from = home.offset(random.nextInt(33) - 16, 0, random.nextInt(33) - 16);
            Optional<BlockPos> target = ResourceIndex.get(level).findClosest(level, from, RESOURCE_RANGE, type, bp -> true);
            if (target.isEmpty() && type == ResourceName.ORE)
                target = ResourceIndex.get(level).findClosest(level, from, RESOURCE_RANGE, type = ResourceName.WOOD, bp -> true);
            if (target.isPresent()) {
                order(bot.name, UnitAction.MOVE, List.of(v), target.get());
                if (type == ResourceName.ORE)
                    ore++;
            }
        }
    }

    private static boolean startProduction(String name, ProductionPlacement pp, com.solegendary.reignofnether.building.production.ProductionItem item) {
        if (!pp.startProductionItem(item))
            return false;
        BuildingProductionClientboundPacket.startProduction(name, pp.originPos,
            ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString());
        return true;
    }

    private static BuildingPlacement nearestCity(List<BuildingPlacement> cities, BlockPos pos, BuildingPlacement fallback) {
        return cities.stream().min(Comparator.comparingDouble(c -> c.centrePos.distSqr(pos))).orElse(fallback);
    }

    // ---- development (step 3) -----------------------------------------------------------------------------------

    // the first wish not met yet (and not locked by a technology) is built near one of the cities
    private static boolean develop(ServerLevel level, String name, List<LivingEntity> villagers, List<BuildingPlacement> buildings,
                                   List<BuildingPlacement> cities, List<LivingEntity> builderPool) {
        if (cities.isEmpty() || builderPool.isEmpty())
            return false;
        for (Wish wish : WISHES) {
            if (villagers.size() < wish.villagers())
                break;
            long have = buildings.stream().filter(b -> b.getBuilding() == wish.building()).count();
            if (have >= wish.count())
                continue;
            if (!com.solegendary.reignofnether.tech.Tech.canBuild(false, name, wish.building()))
                continue; // waits for its technology, the next wish may go first
            BuildingPlacement city = cities.get(random.nextInt(cities.size()));
            return placeNear(level, name, wish.building(), city.centrePos, 12, 28, nearest(builderPool, city.centrePos, 2)) != null;
        }
        return false;
    }

    // next technology in the list, once the library stands and it can be paid (keeping food for villagers)
    private static void research(String name, List<BuildingPlacement> buildings) {
        if (!com.solegendary.reignofnether.tech.TechServerEvents.hasLibrary(name) || com.solegendary.reignofnether.tech.TechServerEvents.isResearching(name))
            return;
        for (var tech : RESEARCH) {
            if (com.solegendary.reignofnether.tech.TechServerEvents.hasTech(name, tech))
                continue;
            if (!tech.getPrerequisites().stream().allMatch(t -> com.solegendary.reignofnether.tech.TechServerEvents.hasTech(name, t)))
                continue;
            if (com.solegendary.reignofnether.resources.ResourcesServerEvents.canAfford(name, ResourceName.FOOD, tech.food + FOOD_RESERVE) &&
                com.solegendary.reignofnether.resources.ResourcesServerEvents.canAfford(name, ResourceName.WOOD, tech.wood) &&
                com.solegendary.reignofnether.resources.ResourcesServerEvents.canAfford(name, ResourceName.ORE, tech.ore))
                com.solegendary.reignofnether.tech.TechServerEvents.startResearch(name, tech);
            return; // waits for this one rather than skipping to a cheaper one
        }
    }

    // Town Centres and barracks go up a level when the village is big enough (not while training villagers)
    private static void upgradeLevels(String name, List<BuildingPlacement> buildings, int villagers, int maxVillagers) {
        for (BuildingPlacement b : buildings) {
            if (!b.isBuilt || !(b instanceof ProductionPlacement pp) || !pp.productionQueue.isEmpty())
                continue;
            int lvl = b.getLevelIndex();
            com.solegendary.reignofnether.building.production.ProductionItem next = null;
            if (b.getBuilding() == Buildings.TOWN_CENTRE && villagers >= Math.min(14, maxVillagers))
                next = lvl == 0 ? ProductionItems.TOWN_CENTRE_LEVEL_2 : lvl == 1 ? ProductionItems.TOWN_CENTRE_LEVEL_3 : null;
            else if (b.getBuilding() == Buildings.BARRACKS)
                next = lvl == 0 ? ProductionItems.BARRACKS_LEVEL_2 : lvl == 1 ? ProductionItems.BARRACKS_LEVEL_3 : null;
            if (next == null || !com.solegendary.reignofnether.tech.Tech.hasTech(false, name,
                    com.solegendary.reignofnether.tech.Tech.getRequiredForLevel(b.getBuilding(), lvl + 1)))
                continue;
            if (next.canAfford(pp))
                startProduction(name, pp, next);
        }
    }

    // a new city some way from the first one, built by the 3 nearest villagers
    private static void foundCity(ServerLevel level, String name, BuildingPlacement capital, List<LivingEntity> villagers) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = random.nextInt(NEW_CITY_MIN_DIST, NEW_CITY_MAX_DIST + 1);
            int x = capital.centrePos.getX() + (int) (Math.cos(angle) * dist), z = capital.centrePos.getZ() + (int) (Math.sin(angle) * dist);
            if (Cities.getPlacementError(level, name, x, z) != null || !level.hasChunkAt(new BlockPos(x, 0, z)))
                continue;
            BlockPos ground = ground(level, x, z);
            if (!isGoodLand(level, ground))
                continue;
            if (placeNear(level, name, Buildings.TOWN_CENTRE, ground, 0, 4, nearest(villagers, ground, 3)) != null)
                return;
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
