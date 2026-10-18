package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.barbarian.BarbarianServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingProductionClientboundPacket;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.*;

// Religion for computer-run civilisations (step 3): once a temple stands (BotServerEvents builds one after Mysticism)
// the bot founds a religion when it has the faith, buys a second belief, trains its prophets and sends them out:
// first to its own cities that follow another religion, then to calm the barbarian camps near home, then to convert
// its neighbours' cities (not those at war, prophets would die on the way). A harvest festival when faith piles up.
public class ReligionBot {

    public static final int THINK_TICKS = 20 * 10;
    public static final int MISSION_RANGE = 400; // from its capital
    public static final int FESTIVAL_FAITH = 160;

    private static final Random random = new Random();
    private static final Map<Integer, BlockPos> missions = new HashMap<>(); // prophet id -> where it was sent

    public static void think(String name, long ticksAlive, List<BuildingPlacement> buildings, BuildingPlacement capital) {
        if (ticksAlive % THINK_TICKS != 0)
            return;
        List<BuildingPlacement> temples = buildings.stream().filter(b -> b.isBuilt && ReligionServerEvents.isTemple(b)).toList();
        if (temples.isEmpty())
            return;
        Religion mine = Religions.foundedBy(false, name);
        if (mine == null) {
            List<Religion> free = Arrays.stream(Religion.values()).filter(r -> !ReligionServerEvents.data().founders.containsKey(r)).toList();
            if (!free.isEmpty() && Religions.faith(false, name) >= ReligionServerEvents.FOUND_COST)
                ReligionServerEvents.found(name, free.get(random.nextInt(free.size())), temples.get(0));
            festival(name, temples);
            return;
        }
        // second belief
        if (!ReligionServerEvents.data().secondBeliefs.containsKey(mine) &&
            Religions.followerCities(false, mine) >= ReligionServerEvents.BELIEF_CITIES &&
            Religions.faith(false, name) >= ReligionServerEvents.BELIEF_COST) {
            List<Belief> free = Religions.freeBeliefs(false);
            if (!free.isEmpty())
                ReligionServerEvents.buyBelief(name, free.get(random.nextInt(free.size())));
        }
        // prophets: train them, then send them out
        if (ReligionServerEvents.prophetError(name) == null)
            for (BuildingPlacement t : temples)
                if (t instanceof ProductionPlacement pp && pp.productionQueue.isEmpty() && pp.startProductionItem(ProductionItems.PROPHET)) {
                    BuildingProductionClientboundPacket.startProduction(name, pp.originPos,
                        ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.PROPHET).toString());
                    break;
                }
        sendProphets(name, mine, capital);
        festival(name, temples);
    }

    private static void festival(String name, List<BuildingPlacement> temples) {
        if (Religions.faith(false, name) >= FESTIVAL_FAITH && ReligionServerEvents.riteReady(name, Rite.HARVEST))
            ReligionServerEvents.performRite(name, Rite.HARVEST, temples.get(random.nextInt(temples.size())));
    }

    private static void sendProphets(String name, Religion mine, BuildingPlacement capital) {
        List<ProphetUnit> prophets = ReligionServerEvents.prophetsOf(name);
        missions.keySet().removeIf(id -> prophets.stream().noneMatch(p -> p.getId() == id));
        List<BlockPos> targets = targets(name, mine, capital);
        Set<BlockPos> taken = new HashSet<>();
        for (ProphetUnit p : prophets) {
            BlockPos mission = missions.get(p.getId());
            if (mission != null && targets.contains(mission)) {
                taken.add(mission);
                continue; // still worth it: keep going (or keep preaching)
            }
            BlockPos next = targets.stream().filter(t -> !taken.contains(t)).findFirst().orElse(null);
            if (next == null)
                continue;
            taken.add(next);
            missions.put(p.getId(), next);
            UnitServerEvents.addActionItem(name, UnitAction.MOVE, -1, new int[] { p.getId() }, next.offset(3, 0, 3), new BlockPos(0, 0, 0));
        }
    }

    // where prophets are wanted, most urgent first
    private static List<BlockPos> targets(String name, Religion mine, BuildingPlacement capital) {
        List<BlockPos> list = new ArrayList<>();
        List<BuildingPlacement> cities = Religions.cities(false);
        // own cities following another religion (or none)
        for (BuildingPlacement c : cities)
            if (c.ownerName.equals(name) && Religions.religionOfCity(false, c) != mine)
                list.add(c.centrePos);
        // barbarian camps near home, not calmed yet
        for (BlockPos camp : BarbarianServerEvents.campOrigins()) {
            BuildingPlacement b = find(camp);
            if (b != null && !ReligionServerEvents.isCalmed(camp) && b.centrePos.distSqr(capital.centrePos) < (double) MISSION_RANGE * MISSION_RANGE)
                list.add(b.centrePos);
        }
        // neighbours' cities of another religion, nearest first, never at war
        List<BuildingPlacement> foreign = new ArrayList<>();
        for (BuildingPlacement c : cities)
            if (!c.ownerName.equals(name) && !DiplomacyServerEvents.atWar(name, c.ownerName) &&
                Religions.religionOfCity(false, c) != mine && c.centrePos.distSqr(capital.centrePos) < (double) MISSION_RANGE * MISSION_RANGE)
                foreign.add(c);
        foreign.sort(Comparator.comparingDouble(c -> c.centrePos.distSqr(capital.centrePos)));
        for (BuildingPlacement c : foreign)
            list.add(c.centrePos);
        return list;
    }

    @Nullable
    private static BuildingPlacement find(BlockPos origin) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.originPos.equals(origin))
                return b;
        return null;
    }
}
