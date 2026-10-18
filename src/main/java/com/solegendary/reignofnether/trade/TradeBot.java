package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingProductionClientboundPacket;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.VillagerMarket;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.road.RoadConnection;
import com.solegendary.reignofnether.road.RoadPathTracer;
import com.solegendary.reignofnether.road.RoadServerEvents;
import com.solegendary.reignofnether.road.RoadType;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;

// Trade for computer-run civilisations: with a market, a royal road from the capital to the nearest city of a
// civilisation at peace (or one of its own), when it can afford it; then a merchant trading along it.
public class TradeBot {

    public static final int THINK_TICKS = 20 * 30;
    public static final int ROAD_RESERVE = 300; // wood and ore kept besides the road's price

    public static void think(ServerLevel level, String name, long ticksAlive, List<BuildingPlacement> buildings, BuildingPlacement capital) {
        if (ticksAlive % THINK_TICKS != 0 || !capital.isBuilt)
            return;
        BuildingPlacement market = buildings.stream().filter(b -> b.isBuilt && b.getBuilding() instanceof VillagerMarket).findFirst().orElse(null);
        if (market == null)
            return;
        // the cities its capital is linked to by a finished royal road
        List<BuildingPlacement> linked = BuildingServerEvents.getBuildings().stream().filter(b -> b.getBuilding().isCapitol && b != capital &&
            isRoyal(RoadServerEvents.getConnection(capital.originPos, b.originPos)) &&
            (b.ownerName.equals(name) || !DiplomacyServerEvents.atWar(name, b.ownerName))).toList();
        if (linked.isEmpty()) {
            buildRoad(level, name, capital);
            return;
        }
        // a merchant, and every idle merchant off to a linked city
        if (TradeServerEvents.merchantError(name) == null && market instanceof ProductionPlacement pp && pp.productionQueue.isEmpty() &&
            pp.startProductionItem(ProductionItems.MERCHANT))
            BuildingProductionClientboundPacket.startProduction(name, pp.originPos,
                ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.MERCHANT).toString());
        for (MerchantUnit m : TradeServerEvents.merchantsOf(name))
            if (m.tradeCity == null)
                TradeServerEvents.startTrade(m, linked.get(m.getRandom().nextInt(linked.size())));
    }

    private static boolean isRoyal(RoadConnection c) {
        return c != null && c.type.isRoyal() && !c.isUnderConstruction();
    }

    // to the nearest city at peace within reach (another civilisation's first: it pays twice as much)
    private static void buildRoad(ServerLevel level, String name, BuildingPlacement capital) {
        boolean underway = RoadServerEvents.getConnections().stream().anyMatch(c -> c.ownerName.equals(name) && c.type.isRoyal() && c.isUnderConstruction());
        if (underway)
            return;
        BuildingPlacement target = BuildingServerEvents.getBuildings().stream()
            .filter(b -> b.getBuilding().isCapitol && b.isBuilt && b != capital && (b.ownerName.equals(name) || !DiplomacyServerEvents.atWar(name, b.ownerName)))
            .filter(b -> Math.max(Math.abs(b.centrePos.getX() - capital.centrePos.getX()), Math.abs(b.centrePos.getZ() - capital.centrePos.getZ())) < RoadPathTracer.ROYAL_MAX_LENGTH)
            .min(Comparator.comparingDouble((BuildingPlacement b) -> (b.ownerName.equals(name) ? 1e9 : 0) + b.centrePos.distSqr(capital.centrePos)))
            .orElse(null);
        if (target == null)
            return;
        int length = (int) Math.sqrt(target.centrePos.distSqr(capital.centrePos)) + 40;
        if (!ResourcesServerEvents.canAfford(name, ResourceName.WOOD, RoadType.ROYAL.woodPerLength * length + ROAD_RESERVE) ||
            !ResourcesServerEvents.canAfford(name, ResourceName.ORE, RoadType.ROYAL.orePerLength * length + ROAD_RESERVE))
            return;
        RoadServerEvents.tryBuildRoad(level, name, capital.originPos, target.originPos, RoadType.ROYAL);
    }
}
