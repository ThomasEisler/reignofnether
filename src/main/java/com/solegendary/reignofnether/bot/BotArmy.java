package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barbarian.BarbarianServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.ExplorerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

// Step 4 (army): a bot trains soldiers in its barracks (and iron golems at the blacksmith), keeps them by its
// Town Centre, sends them all against any enemy that comes near its buildings, and, once the army is big enough,
// attacks the nearest enemy: barbarian camps first, other civilisations (bots and players) after a peace period.
// Army size, peace time, first attack and time between attacks depend on the difficulty (BotDifficulty).
// The attack goes building after building of the same enemy, and the army comes home when it has lost too much.
public class BotArmy {

    public static final int DEFENCE_RANGE = 36; // enemies this close to one of its buildings are attacked
    public static final int RALLY_DIST = 14; // soldiers wait this far from the Town Centre
    public static final int MAX_ATTACK_SIZE = 16;
    public static final int MAX_ATTACK_DIST = 450;
    public static final float RETREAT_SHARE = 0.35f; // comes home when only this share of the attackers is left
    public static final int FOOD_KEPT = 50; // for villagers, while the village still grows
    public static final int MAX_GOLEMS = 2;

    private static class State {
        @Nullable BlockPos target; // building being attacked
        @Nullable String enemy;
        final Set<Integer> attackers = new HashSet<>();
        int sent = 0;
        long nextAttack = 0;
        int attacks = 0;
        int trained = 0;
        boolean defending = false;
    }
    private static final Map<String, State> states = new HashMap<>();

    public static void think(String name, long ticksAlive, List<LivingEntity> units, int villagers, int maxVillagers,
                             List<BuildingPlacement> buildings, BuildingPlacement capital) {
        State st = states.computeIfAbsent(name, n -> new State());
        List<LivingEntity> army = units.stream().filter(BotArmy::isSoldier).toList();

        train(name, st, army, villagers, maxVillagers, buildings);

        if (defend(name, st, army, buildings))
            return;
        if (st.target != null) {
            pursue(name, st, army, ticksAlive, capital);
            return;
        }
        if (ticksAlive >= st.nextAttack && army.size() >= attackSize(st))
            startAttack(name, st, army, ticksAlive, capital);
        else
            rally(name, army, capital);
    }

    public static boolean isSoldier(LivingEntity le) {
        return le instanceof AttackerUnit && !(le instanceof WorkerUnit) && !(le instanceof ExplorerUnit);
    }

    private static int attackSize(State st) {
        return Math.min(MAX_ATTACK_SIZE, BotSettings.difficulty().firstAttackSize + 2 * st.attacks);
    }

    // ---- training -----------------------------------------------------------------------------------------------

    private static void train(String name, State st, List<LivingEntity> army, int villagers, int maxVillagers,
                              List<BuildingPlacement> buildings) {
        if (army.size() >= BotSettings.difficulty().maxArmy)
            return;
        int pop = UnitServerEvents.getCurrentPopulation(name);
        int supply = BuildingServerEvents.getTotalPopulationSupply(name);
        // 2 population places stay free for villagers while the village grows
        int spare = supply - pop - (villagers < maxVillagers ? 2 : 0);
        for (BuildingPlacement b : buildings) {
            if (spare <= 0)
                return;
            if (!b.isBuilt || !(b instanceof ProductionPlacement pp) || !pp.productionQueue.isEmpty())
                continue;
            ProductionItem item = null;
            if (b.getBuilding() == Buildings.BARRACKS) {
                // 2 vindicators, a pillager, then a cavalier when it can
                int turn = st.trained % 4;
                item = turn == 2 ? ProductionItems.PILLAGER : turn == 3 ? ProductionItems.CAVALIER : ProductionItems.VINDICATOR;
            } else if (b.getBuilding() == Buildings.BLACKSMITH && army.size() >= 6 &&
                    army.stream().filter(le -> le instanceof com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit).count() < MAX_GOLEMS)
                item = ProductionItems.IRON_GOLEM;
            if (item == null || !canPay(name, item, villagers < maxVillagers))
                continue;
            if (start(name, pp, item) || (item == ProductionItems.CAVALIER && start(name, pp, ProductionItems.VINDICATOR))) {
                st.trained++;
                spare--;
            }
        }
    }

    private static boolean canPay(String name, ProductionItem item, boolean keepFood) {
        ResourceCost c = item.getCost(false, name);
        return ResourcesServerEvents.canAfford(name, ResourceName.FOOD, c.food + (keepFood ? FOOD_KEPT : 0)) &&
            ResourcesServerEvents.canAfford(name, ResourceName.WOOD, c.wood + BotServerEvents.WOOD_RESERVE) &&
            ResourcesServerEvents.canAfford(name, ResourceName.ORE, c.ore);
    }

    private static boolean start(String name, ProductionPlacement pp, ProductionItem item) {
        if (!pp.startProductionItem(item))
            return false;
        com.solegendary.reignofnether.building.BuildingProductionClientboundPacket.startProduction(name, pp.originPos,
            com.solegendary.reignofnether.api.ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString());
        return true;
    }

    // ---- enemies ------------------------------------------------------------------------------------------------

    // barbarians, and the other civilisations (players and bots) that aren't allies
    private static boolean isEnemy(String name, String other) {
        if (other == null || other.isEmpty() || other.equals(name))
            return false;
        if (BarbarianServerEvents.OWNER_NAME.equals(other))
            return true;
        return PlayerServerEvents.isRTSPlayer(other) && !AlliancesServerEvents.isAllied(name, other);
    }

    private static boolean defend(String name, State st, List<LivingEntity> army, List<BuildingPlacement> buildings) {
        if (army.isEmpty())
            return false;
        LivingEntity intruder = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!le.isAlive() || !isEnemy(name, ((Unit) le).getOwnerName()))
                continue;
            for (BuildingPlacement b : buildings) {
                double d = le.distanceToSqr(Vec3.atCenterOf(b.centrePos));
                if (d < DEFENCE_RANGE * DEFENCE_RANGE && d < best) {
                    best = d;
                    intruder = le;
                }
            }
        }
        if (intruder == null) {
            st.defending = false;
            return false;
        }
        // the whole army, attackers included (an attack is called off to save the town)
        if (st.target != null)
            endAttack(st, 0);
        // only those not fighting yet, so the ones already at it aren't interrupted every 2 seconds
        order(name, UnitAction.ATTACK_MOVE, army.stream().filter(le -> ((Unit) le).getTargetGoal().getTarget() == null).toList(),
            intruder.blockPosition());
        st.defending = true;
        return true;
    }

    // the enemy building nearest to a point (only from one enemy if given)
    @Nullable
    private static BuildingPlacement nearestEnemyBuilding(String name, @Nullable String enemy, BlockPos from, int maxDist) {
        BuildingPlacement best = null;
        double bestDist = (double) maxDist * maxDist;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (enemy != null ? !enemy.equals(b.ownerName) : !isEnemy(name, b.ownerName))
                continue;
            double d = b.centrePos.distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    // ---- attacks ------------------------------------------------------------------------------------------------

    private static void startAttack(String name, State st, List<LivingEntity> army, long ticksAlive, BuildingPlacement capital) {
        BlockPos home = capital.centrePos;
        // barbarian camps are fair game at any time, the other civilisations only after the peace period
        BuildingPlacement target = nearestEnemyBuilding(name, BarbarianServerEvents.OWNER_NAME, home, MAX_ATTACK_DIST);
        if (ticksAlive >= BotSettings.difficulty().peaceTicks) {
            BuildingPlacement civ = null;
            double best = (double) MAX_ATTACK_DIST * MAX_ATTACK_DIST;
            for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                if (!BarbarianServerEvents.OWNER_NAME.equals(b.ownerName) && isEnemy(name, b.ownerName) && b.centrePos.distSqr(home) < best) {
                    best = b.centrePos.distSqr(home);
                    civ = b;
                }
            if (civ != null && (target == null || civ.centrePos.distSqr(home) < target.centrePos.distSqr(home)))
                target = civ;
        }
        if (target == null) {
            st.nextAttack = ticksAlive + BotSettings.difficulty().attackCooldown;
            return;
        }
        st.target = target.centrePos;
        st.enemy = target.ownerName;
        st.attackers.clear();
        army.forEach(le -> st.attackers.add(le.getId()));
        st.sent = army.size();
        st.attacks++;
        order(name, UnitAction.ATTACK_MOVE, army, target.centrePos);
    }

    private static void pursue(String name, State st, List<LivingEntity> army, long ticksAlive, BuildingPlacement capital) {
        List<LivingEntity> attackers = army.stream().filter(le -> st.attackers.contains(le.getId())).toList();
        if (attackers.size() <= Math.max(1, st.sent * RETREAT_SHARE)) { // beaten: everyone home
            endAttack(st, ticksAlive + BotSettings.difficulty().attackCooldown);
            order(name, UnitAction.MOVE, attackers, rallyPoint(capital));
            return;
        }
        BuildingPlacement target = BuildingServerEvents.getBuildings().stream()
            .filter(b -> b.centrePos.equals(st.target) && b.ownerName.equals(st.enemy)).findFirst().orElse(null);
        if (target == null) { // destroyed: the next building of the same enemy nearby, or home
            target = nearestEnemyBuilding(name, st.enemy, st.target, 60);
            if (target == null) {
                endAttack(st, ticksAlive + BotSettings.difficulty().attackCooldown);
                order(name, UnitAction.MOVE, attackers, rallyPoint(capital));
                return;
            }
            st.target = target.centrePos;
            order(name, UnitAction.ATTACK_MOVE, attackers, target.centrePos);
            return;
        }
        // close enough: the soldiers with nothing to fight hit the building itself
        List<LivingEntity> idle = attackers.stream().filter(le -> le.distanceToSqr(Vec3.atCenterOf(st.target)) < 24 * 24 &&
            ((Unit) le).getTargetGoal().getTarget() == null).toList();
        order(name, UnitAction.ATTACK_BUILDING, idle, target.centrePos);
    }

    private static void endAttack(State st, long nextAttack) {
        st.target = null;
        st.enemy = null;
        st.attackers.clear();
        st.nextAttack = nextAttack;
    }

    // soldiers with nothing to do wait by the Town Centre
    private static void rally(String name, List<LivingEntity> army, BuildingPlacement capital) {
        BlockPos rally = rallyPoint(capital);
        List<LivingEntity> far = army.stream().filter(le -> le.distanceToSqr(Vec3.atCenterOf(rally)) > 12 * 12 &&
            ((Unit) le).getTargetGoal().getTarget() == null && le instanceof net.minecraft.world.entity.Mob m && m.getNavigation().isDone()).toList();
        order(name, UnitAction.MOVE, far, rally);
    }

    private static BlockPos rallyPoint(BuildingPlacement capital) {
        return capital.centrePos.offset(RALLY_DIST, 0, RALLY_DIST);
    }

    private static void order(String name, UnitAction action, List<LivingEntity> units, BlockPos target) {
        if (units.isEmpty())
            return;
        int[] ids = units.stream().mapToInt(LivingEntity::getId).toArray();
        UnitServerEvents.addActionItem(name, action, -1, ids, target, new BlockPos(0, 0, 0));
    }

    // for /rts-bot info
    public static String describe(String name) {
        State st = states.get(name);
        if (st == null)
            return "Armée : rien encore";
        if (st.defending)
            return "Armée : défend la ville";
        if (st.target != null)
            return "Armée : attaque " + st.enemy + " en " + st.target.getX() + " " + st.target.getZ() + " (" + st.sent + " partis)";
        return "Armée : attend (attaque n° " + (st.attacks + 1) + " à " + attackSize(st) + " soldats)";
    }
}
