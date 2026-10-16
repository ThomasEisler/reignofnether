package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.bot.BotSaveData.BotData;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

// /rts-bot info: what each bot has and what each of its villagers is doing, to see why one is stuck
public class BotInfo {

    public static List<String> describe(ServerLevel level) {
        List<String> lines = new ArrayList<>();
        List<BotData> bots = BotSaveData.getInstance(level).bots;
        if (bots.isEmpty()) {
            lines.add("Aucun bot");
            return lines;
        }
        for (BotData bot : bots) {
            Resources res = ResourcesServerEvents.resourcesList.stream()
                .filter(r -> r.ownerName.equals(bot.name)).findFirst().orElse(null);
            lines.add("§e== " + bot.name + "§r" + (res == null ? "" :
                "  nourriture " + res.food + ", bois " + res.wood + ", minerai " + res.ore) +
                "  pop " + UnitServerEvents.getCurrentPopulation(bot.name) + "/" + BuildingServerEvents.getTotalPopulationSupply(bot.name));

            StringBuilder b = new StringBuilder("Bâtiments :");
            for (BuildingPlacement p : BuildingServerEvents.getBuildings())
                if (bot.name.equals(p.ownerName))
                    b.append(" ").append(p.getBuilding().name).append(p.isBuilt ? "" : " (chantier)")
                        .append(p instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp
                            && !pp.productionQueue.isEmpty() ? " [produit]" : "").append(",");
            lines.add(b.toString());

            List<LivingEntity> units = new ArrayList<>();
            for (LivingEntity le : UnitServerEvents.getAllUnits())
                if (le.isAlive() && bot.name.equals(((Unit) le).getOwnerName()))
                    units.add(le);
            lines.add(BotArmy.describe(bot.name));
            lines.add("Unités : " + units.size());
            for (LivingEntity le : units) {
                BlockPos p = le.blockPosition();
                lines.add(" - " + le.getName().getString() + " (" + p.getX() + " " + p.getY() + " " + p.getZ() + ") : " + activity(le) + carried(le));
            }
        }
        return lines;
    }

    private static String carried(LivingEntity le) {
        Resources r = Resources.getTotalResourcesFromItems(((Unit) le).getItems());
        return r.getTotalValue() > 0 ? ", porte " + r.getTotalValue() : "";
    }

    private static String activity(LivingEntity le) {
        if (!(le instanceof WorkerUnit w))
            return "soldat";
        if (w.getBuildRepairGoal() != null && w.getBuildRepairGoal().getBuildingTarget() != null)
            return "construit " + w.getBuildRepairGoal().getBuildingTarget().getBuilding().name;
        if (le instanceof Unit u && u.getReturnResourcesGoal() != null && u.getReturnResourcesGoal().getBuildingTarget() != null)
            return "rapporte au " + u.getReturnResourcesGoal().getBuildingTarget().getBuilding().name;
        GatherResourcesGoal g = w.getGatherResourceGoal();
        if (g == null)
            return "rien";
        if (g.isFarming())
            return "ferme" + (g.getTargetFarm() != null ? " " + g.getTargetFarm().getBuilding().name : "");
        if (g.getTargetResourceName() == null || g.getTargetResourceName() == com.solegendary.reignofnether.resources.ResourceName.NONE)
            return le instanceof net.minecraft.world.entity.Mob m && !m.getNavigation().isDone() ? "marche" : "rien";
        return "récolte " + g.getTargetResourceName() + (g.isIdle() ? " (ne trouve rien)" : "");
    }
}
