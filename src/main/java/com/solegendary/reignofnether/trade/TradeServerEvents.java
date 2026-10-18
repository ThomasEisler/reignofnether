package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.villagers.VillagerMarket;
import com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.road.RoadConnection;
import com.solegendary.reignofnether.road.RoadServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Llama;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Trade routes: a merchant (trained at a market) right-clicked onto a city linked to one of its owner's cities by a
// finished royal road goes back and forth between the two. Each time it reaches the other city it brings in
// emeralds (spent at the market for resources): more the longer the road, twice as much with a foreign city, which
// also gets a share. A war between the two stops the route; another order to the merchant too.
public class TradeServerEvents {

    public static final int BASE_EMERALDS = 8;          // per delivery...
    public static final int BLOCKS_PER_EMERALD = 25;    // ...plus 1 for this many blocks between the cities
    public static final float FOREIGN_MULT = 2f;        // trading with another civilisation's city
    public static final float PARTNER_SHARE = 0.25f;    // what that civilisation gets on top
    public static final int ARRIVE_RANGE = 10;          // from the Town Centre's centre
    public static final int LLAMAS = 2;

    // ---- how many merchants ----------------------------------------------------------------------------------------

    public static List<MerchantUnit> merchantsOf(String civ) {
        List<MerchantUnit> list = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof MerchantUnit m && m.isAlive() && m.getOwnerName().equals(civ))
                list.add(m);
        return list;
    }

    // one per finished market, one more for each Grand Market
    public static int allowedMerchants(String civ) {
        int n = 0;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.ownerName.equals(civ) && b.isBuilt && b.getBuilding() instanceof VillagerMarket)
                n += b.getUpgradeLevel() >= 1 ? 2 : 1;
        return n;
    }

    @Nullable
    public static String merchantError(String civ) {
        int queued = 0;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.ownerName.equals(civ) && b instanceof ProductionPlacement pp)
                for (var ap : pp.productionQueue)
                    if (ap.item instanceof MerchantProd)
                        queued++;
        int allowed = allowedMerchants(civ);
        if (merchantsOf(civ).size() + queued >= allowed)
            return "Vous avez déjà vos " + allowed + " marchands (1 par marché, 2 avec le Grand marché)";
        return null;
    }

    // ---- starting a route ----------------------------------------------------------------------------------------

    @Nullable
    private static BuildingPlacement cityAt(BlockPos origin) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.originPos.equals(origin) && b.getBuilding().isCapitol)
                return b;
        return null;
    }

    private static boolean royalRoad(BlockPos a, BlockPos b) {
        RoadConnection c = RoadServerEvents.getConnection(a, b);
        return c != null && c.type.isRoyal() && !c.isUnderConstruction();
    }

    // the merchant was right-clicked onto a city
    public static void startTrade(MerchantUnit m, BuildingPlacement target) {
        String owner = m.getOwnerName();
        if (!target.getBuilding().isCapitol || !target.isBuilt)
            return;
        if (!target.ownerName.equals(owner) && DiplomacyServerEvents.atWar(owner, target.ownerName)) {
            HudClientboundPacket.showTempMessageI18n(owner, "Pas de commerce avec une civilisation en guerre avec vous");
            return;
        }
        // home: the owner's city linked to it by a royal road, the nearest to the merchant
        BuildingPlacement home = null;
        double best = Double.MAX_VALUE;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding().isCapitol && b.ownerName.equals(owner) && b != target && royalRoad(b.originPos, target.originPos)) {
                double d = b.centrePos.distToCenterSqr(m.position());
                if (d < best) { best = d; home = b; }
            }
        if (home == null) {
            HudClientboundPacket.showTempMessageI18n(owner, "Il faut une route royale terminée entre une de vos villes et celle-ci");
            return;
        }
        m.homeCity = home.originPos;
        m.tradeCity = target.originPos;
        m.outbound = true;
        goTo(m, target);
        HudClientboundPacket.showTempMessageI18n(owner, "Route commerciale ouverte");
    }

    private static void stopTrade(MerchantUnit m, @Nullable String why) {
        if (why != null)
            HudClientboundPacket.showTempMessageI18n(m.getOwnerName(), why);
        BuildingPlacement home = m.homeCity != null ? cityAt(m.homeCity) : null;
        m.homeCity = null;
        m.tradeCity = null;
        if (home != null)
            goTo(m, home);
    }

    private static final java.util.Map<Integer, BlockPos> expected = new java.util.HashMap<>(); // merchant -> where we sent it

    private static void goTo(MerchantUnit m, BuildingPlacement city) {
        BlockPos dest = city.getClosestGroundPos(m.getOnPos(), 2);
        expected.put(m.getId(), dest);
        m.getMoveGoal().setMoveTarget(dest);
    }

    // ---- every second, for each merchant --------------------------------------------------------------------------

    public static void tickMerchant(MerchantUnit m) {
        keepLlamas(m);
        if (m.tradeCity == null || m.homeCity == null)
            return;
        BuildingPlacement home = cityAt(m.homeCity), trade = cityAt(m.tradeCity);
        if (home == null || trade == null || !home.ownerName.equals(m.getOwnerName()) || !royalRoad(m.homeCity, m.tradeCity)) {
            stopTrade(m, "Route commerciale coupée (ville ou route royale perdue)");
            return;
        }
        if (!trade.ownerName.equals(m.getOwnerName()) && DiplomacyServerEvents.atWar(m.getOwnerName(), trade.ownerName)) {
            stopTrade(m, "Route commerciale coupée par la guerre avec " + trade.ownerName);
            return;
        }
        // another order given by the player ends the route
        BlockPos target = m.getMoveGoal().getMoveTarget(), sent = expected.get(m.getId());
        if (target != null && sent != null && !target.equals(sent)) {
            m.homeCity = null;
            m.tradeCity = null;
            return;
        }
        BuildingPlacement dest = m.outbound ? trade : home;
        if (dest.centrePos.distToCenterSqr(m.position()) < ARRIVE_RANGE * ARRIVE_RANGE) {
            if (m.outbound)
                deliver(m, home, trade);
            m.outbound = !m.outbound;
            goTo(m, m.outbound ? trade : home);
        } else if (target == null) // stopped on the way (stuck, pushed): off again
            goTo(m, dest);
    }

    private static void deliver(MerchantUnit m, BuildingPlacement home, BuildingPlacement trade) {
        String owner = m.getOwnerName();
        boolean foreign = !trade.ownerName.equals(owner);
        int dist = (int) Math.sqrt(home.centrePos.distSqr(trade.centrePos));
        int emeralds = Math.round((BASE_EMERALDS + dist / (float) BLOCKS_PER_EMERALD) * (foreign ? FOREIGN_MULT : 1f));
        ResourcesServerEvents.addSubtractResources(new Resources(owner, 0, 0, 0, emeralds));
        com.solegendary.reignofnether.score.ScoreServerEvents.onTrade(owner, emeralds);
        if (foreign && PlayerServerEvents.isRTSPlayer(trade.ownerName)) {
            int share = Math.max(1, Math.round(emeralds * PARTNER_SHARE));
            ResourcesServerEvents.addSubtractResources(new Resources(trade.ownerName, 0, 0, 0, share));
            com.solegendary.reignofnether.score.ScoreServerEvents.onTrade(trade.ownerName, share);
            DiplomacyServerEvents.change(trade.ownerName, owner, 1, ""); // a bot likes trading
        }
        HudClientboundPacket.showTempMessageI18n(owner, "Commerce : +" + emeralds + " émeraudes");
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
        expected.remove(m.getId());
    }

    // a merchant's order, from UnitActionItem (server): a city clicked starts a route (true = handled); any other
    // order ends the route it was on
    public static boolean onOrder(Unit unit, com.solegendary.reignofnether.unit.UnitAction action, @Nullable BuildingPlacement clicked) {
        if (!(unit instanceof MerchantUnit m) || m.level().isClientSide())
            return false;
        boolean onCity = clicked != null && clicked.getBuilding().isCapitol &&
            (action == com.solegendary.reignofnether.unit.UnitAction.MOVE || action == com.solegendary.reignofnether.unit.UnitAction.ATTACK_BUILDING ||
             action == com.solegendary.reignofnether.unit.UnitAction.BUILD_REPAIR);
        if (!onCity) {
            m.homeCity = null;
            m.tradeCity = null;
            return false;
        }
        startTrade(m, clicked);
        return true;
    }
}
