package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.VillagerMarket;
import com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.road.RoadConnection;
import com.solegendary.reignofnether.road.RoadServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Trade routes, run from markets: a market button (one route per market, two with the Grand Market), then a click on
// a city linked to the market's city by a finished royal road. The route gets a merchant (paid for, with two pack
// llamas) who goes back and forth on its own; each time it reaches the other city it brings in emeralds (spent at the
// market for resources): more the longer the road, twice as much with a foreign city, which also gets a share. A dead
// merchant is replaced after a while; a war with that city, or a lost road, stops the route until things are back.
// Clicking the route's button again closes it.
public class TradeServerEvents {

    public static final int BASE_EMERALDS = 12;         // per delivery...
    public static final int BLOCKS_PER_EMERALD = 15;    // ...plus 1 for this many blocks between the cities
    public static final float FOREIGN_MULT = 2f;        // trading with another civilisation's city
    public static final float PARTNER_SHARE = 0.25f;    // what that civilisation gets on top
    public static final int MERCHANT_FOOD = 100, MERCHANT_WOOD = 75;
    public static final int REPLACE_TICKS = 20 * 45;    // a new merchant this long after one dies
    public static final int ARRIVE_RANGE = 10;          // from the Town Centre's centre
    public static final int LLAMAS = 2;

    private static ServerLevel serverLevel = null;
    private static boolean needsSync = true;

    public static TradeSaveData data() {
        return TradeSaveData.getInstance(serverLevel);
    }

    // ---- markets and cities --------------------------------------------------------------------------------------

    public static int slotsOf(BuildingPlacement market) {
        return !market.isBuilt ? 0 : market.getUpgradeLevel() >= 1 ? 2 : 1;
    }

    @Nullable
    private static BuildingPlacement buildingAt(BlockPos origin) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.originPos.equals(origin))
                return b;
        return null;
    }

    // the city a market belongs to: the nearest Town Centre of its owner on whose land it stands
    @Nullable
    public static BuildingPlacement homeCity(boolean clientSide, BuildingPlacement market) {
        BuildingPlacement city = com.solegendary.reignofnether.religion.Religions.cityAt(clientSide, market.centrePos.getX(), market.centrePos.getZ());
        return city != null && city.ownerName.equals(market.ownerName) ? city : null;
    }

    private static boolean royalRoad(BlockPos a, BlockPos b) {
        RoadConnection c = RoadServerEvents.getConnection(a, b);
        return c != null && c.type.isRoyal() && !c.isUnderConstruction();
    }

    public static int emeraldsPerTrip(BuildingPlacement home, BuildingPlacement target) {
        int dist = (int) Math.sqrt(home.centrePos.distSqr(target.centrePos));
        boolean foreign = !home.ownerName.equals(target.ownerName);
        return Math.round((BASE_EMERALDS + dist / (float) BLOCKS_PER_EMERALD) * (foreign ? FOREIGN_MULT : 1f));
    }

    @Nullable
    private static TradeRoute route(BlockPos market, int slot) {
        for (TradeRoute r : data().routes)
            if (r.market.equals(market) && r.slot == slot)
                return r;
        return null;
    }

    // ---- opening and closing (market buttons) --------------------------------------------------------------------

    public static void openRoute(String owner, BuildingPlacement market, int slot, BlockPos clicked) {
        if (!market.ownerName.equals(owner) || !(market.getBuilding() instanceof VillagerMarket) || slot >= slotsOf(market))
            return;
        if (route(market.originPos, slot) != null) {
            closeRoute(owner, market, slot);
            return;
        }
        BuildingPlacement target = com.solegendary.reignofnether.building.BuildingUtils.findBuilding(false, clicked);
        if (target == null || !target.getBuilding().isCapitol || !target.isBuilt) {
            HudClientboundPacket.showTempMessageI18n(owner, "Cliquez sur l'Hôtel de ville d'une ville");
            return;
        }
        BuildingPlacement home = homeCity(false, market);
        if (home == null) {
            HudClientboundPacket.showTempMessageI18n(owner, "Ce marché n'est sur les terres d'aucune de vos villes");
            return;
        }
        if (home == target) {
            HudClientboundPacket.showTempMessageI18n(owner, "C'est la ville de ce marché : choisissez une autre ville");
            return;
        }
        if (!target.ownerName.equals(owner) && DiplomacyServerEvents.atWar(owner, target.ownerName)) {
            HudClientboundPacket.showTempMessageI18n(owner, "Pas de commerce avec une civilisation en guerre avec vous");
            return;
        }
        if (!royalRoad(home.originPos, target.originPos)) {
            HudClientboundPacket.showTempMessageI18n(owner, "Il faut d'abord une route royale terminée entre ces deux villes");
            return;
        }
        for (TradeRoute r : data().routes)
            if (r.market.equals(market.originPos) && r.target.equals(target.originPos)) {
                HudClientboundPacket.showTempMessageI18n(owner, "Ce marché commerce déjà avec cette ville");
                return;
            }
        TradeRoute r = new TradeRoute(market.originPos, slot, owner, target.originPos);
        data().routes.add(r);
        data().setDirty();
        needsSync = true;
        HudClientboundPacket.showTempMessageI18n(owner, "Route commerciale ouverte : environ " + emeraldsPerTrip(home, target) + " émeraudes par voyage");
    }

    public static void closeRoute(String owner, BuildingPlacement market, int slot) {
        TradeRoute r = route(market.originPos, slot);
        if (r == null || !r.owner.equals(owner))
            return;
        removeMerchant(r);
        data().routes.remove(r);
        data().setDirty();
        needsSync = true;
        HudClientboundPacket.showTempMessageI18n(owner, "Route commerciale fermée");
    }

    // ---- the merchant ------------------------------------------------------------------------------------------------

    @Nullable
    private static MerchantUnit merchantOf(TradeRoute r) {
        if (r.merchant == null)
            return null;
        Entity e = serverLevel.getEntity(r.merchant);
        return e instanceof MerchantUnit m && m.isAlive() ? m : null;
    }

    private static void removeMerchant(TradeRoute r) {
        MerchantUnit m = merchantOf(r);
        if (m != null) {
            removeLlamas(m);
            m.discard();
        }
        r.merchant = null;
    }

    // a new merchant at the market, paid for (it waits if the owner can't pay yet)
    private static void hireMerchant(TradeRoute r, BuildingPlacement market) {
        if (!ResourcesServerEvents.canAfford(r.owner, ResourceName.FOOD, MERCHANT_FOOD) ||
            !ResourcesServerEvents.canAfford(r.owner, ResourceName.WOOD, MERCHANT_WOOD)) {
            r.state = "en attente de " + MERCHANT_FOOD + " nourriture et " + MERCHANT_WOOD + " bois pour un marchand";
            return;
        }
        if (!(market instanceof ProductionPlacement pp))
            return;
        Entity e = pp.produceUnit(serverLevel, EntityRegistrar.MERCHANT_UNIT.get(), r.owner, false);
        if (!(e instanceof MerchantUnit m))
            return;
        ResourcesServerEvents.addSubtractResources(new Resources(r.owner, -MERCHANT_FOOD, -MERCHANT_WOOD, 0));
        r.merchant = m.getUUID();
        r.outbound = true;
        r.state = "";
        data().setDirty();
    }

    private static void goTo(MerchantUnit m, BuildingPlacement city) {
        BlockPos dest = city.getClosestGroundPos(m.getOnPos(), 2);
        if (!dest.equals(m.getMoveGoal().getMoveTarget()))
            m.getMoveGoal().setMoveTarget(dest);
    }

    // every second
    private static void tickRoutes() {
        long now = serverLevel.getGameTime();
        for (TradeRoute r : new ArrayList<>(data().routes)) {
            BuildingPlacement market = buildingAt(r.market);
            if (market == null || !market.ownerName.equals(r.owner) || !PlayerServerEvents.isRTSPlayer(r.owner)) {
                removeMerchant(r); // market gone: the route goes with it
                data().routes.remove(r);
                needsSync = true;
                continue;
            }
            BuildingPlacement home = homeCity(false, market), target = buildingAt(r.target);
            String stop = null;
            if (home == null || target == null || !target.getBuilding().isCapitol)
                stop = "ville perdue";
            else if (!royalRoad(home.originPos, target.originPos))
                stop = "route royale coupée";
            else if (!target.ownerName.equals(r.owner) && DiplomacyServerEvents.atWar(r.owner, target.ownerName))
                stop = "en guerre avec " + target.ownerName;
            MerchantUnit m = merchantOf(r);
            if (stop != null) { // suspended: the merchant waits at home
                if (!stop.equals(r.state)) { r.state = stop; needsSync = true; }
                if (m != null && home != null)
                    goTo(m, home);
                continue;
            }
            if (m == null) {
                if (r.merchant != null) { // died: a new one in a while
                    r.merchant = null;
                    r.nextMerchant = now + REPLACE_TICKS;
                    r.state = "marchand tué, un autre arrive bientôt";
                    needsSync = true;
                }
                if (now >= r.nextMerchant) {
                    String before = r.state;
                    hireMerchant(r, market);
                    if (!before.equals(r.state) || r.merchant != null) needsSync = true;
                }
                continue;
            }
            if (!r.state.isEmpty()) { r.state = ""; needsSync = true; }
            BuildingPlacement dest = r.outbound ? target : home;
            if (dest.centrePos.distToCenterSqr(m.position()) < ARRIVE_RANGE * ARRIVE_RANGE) {
                if (r.outbound)
                    deliver(r, home, target);
                r.outbound = !r.outbound;
                data().setDirty();
            }
            goTo(m, r.outbound ? target : home); // also brings it back on its way after any other order
        }
    }

    private static void deliver(TradeRoute r, BuildingPlacement home, BuildingPlacement target) {
        int emeralds = emeraldsPerTrip(home, target);
        ResourcesServerEvents.addSubtractResources(new Resources(r.owner, 0, 0, 0, emeralds));
        com.solegendary.reignofnether.score.ScoreServerEvents.onTrade(r.owner, emeralds);
        if (!target.ownerName.equals(r.owner) && PlayerServerEvents.isRTSPlayer(target.ownerName)) {
            int share = Math.max(1, Math.round(emeralds * PARTNER_SHARE));
            ResourcesServerEvents.addSubtractResources(new Resources(target.ownerName, 0, 0, 0, share));
            com.solegendary.reignofnether.score.ScoreServerEvents.onTrade(target.ownerName, share);
            DiplomacyServerEvents.change(target.ownerName, r.owner, 1, ""); // a bot likes trading
        }
        HudClientboundPacket.showTempMessageI18n(r.owner, "Commerce : +" + emeralds + " émeraudes");
    }

    // a merchant left over (route closed while it was saved, or another bug): it goes
    public static void tickMerchant(MerchantUnit m) {
        keepLlamas(m);
        if (m.tickCount > 100 && data().routes.stream().noneMatch(r -> m.getUUID().equals(r.merchant))) {
            removeLlamas(m);
            m.discard();
        }
    }

    // ---- the llamas --------------------------------------------------------------------------------------------------

    @Nullable
    private static Llama llama(ServerLevel level, UUID id) {
        Entity e = level.getEntity(id);
        return e instanceof Llama l && l.isAlive() ? l : null;
    }

    // two pack llamas on a lead behind it: put back if lost or left behind
    private static void keepLlamas(MerchantUnit m) {
        if (!(m.level() instanceof ServerLevel level))
            return;
        m.llamas.removeIf(id -> llama(level, id) == null);
        while (m.llamas.size() < LLAMAS) {
            Llama l = EntityType.LLAMA.create(level);
            if (l == null)
                return;
            l.moveTo(m.getX() - 1.5, m.getY(), m.getZ() - 1.5, m.getYRot(), 0);
            l.setChest(true);
            l.setTamed(true);
            l.setPersistenceRequired();
            l.setInvulnerable(true);
            l.setVariant(Llama.Variant.values()[level.getRandom().nextInt(Llama.Variant.values().length)]);
            level.addFreshEntity(l);
            m.llamas.add(l.getUUID());
        }
        for (UUID id : m.llamas) {
            Llama l = llama(level, id);
            if (l == null)
                continue;
            if (l.distanceToSqr(m) > 64)
                l.teleportTo(m.getX() - 1.5, m.getY(), m.getZ() - 1.5);
            if (l.getLeashHolder() != m)
                l.setLeashedTo(m, true);
        }
    }

    public static void removeLlamas(MerchantUnit m) {
        if (!(m.level() instanceof ServerLevel level))
            return;
        for (UUID id : m.llamas) {
            Llama l = llama(level, id);
            if (l != null)
                l.discard();
        }
        m.llamas.clear();
    }

    // ---- syncing -----------------------------------------------------------------------------------------------------

    private static void sync() {
        needsSync = false;
        List<TradeClientboundPacket.RouteInfo> list = new ArrayList<>();
        for (TradeRoute r : data().routes)
            list.add(new TradeClientboundPacket.RouteInfo(r.market, r.slot, r.owner, r.target, r.merchant != null, r.state));
        TradeClientboundPacket.sendAll(list);
    }

    // new match (/rts-reset)
    public static void reset() {
        if (serverLevel == null)
            return;
        data().routes.clear();
        data().setDirty();
        needsSync = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        long t = serverLevel.getGameTime();
        if (t % 20 == 0)
            tickRoutes();
        if (needsSync || t % 200 == 0)
            sync();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        needsSync = true;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        needsSync = true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }
}
