package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.CatapultUnit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Getting through enemy ramparts. Any unit that attacks buildings can break a gate; walls and towers can only be
// attacked by siege units (catapults, ravagers, iron golems). A unit whose way is barred by an enemy rampart (it stops
// moving short of where it was sent) attacks it by itself: the rampart in its way if it may, or else the nearest gate
// of that wall; once through, it goes on to where it was sent.
public class RampartSiege {

    private static final int CHECK_INTERVAL = 20; // ticks
    private static final int STUCK_CHECKS = 2; // checks without moving before a unit counts as held up
    private static final double MIN_PROGRESS = 0.5; // blocks per check
    private static final int LINE_RANGE = 48; // how far along its way a unit looks for the rampart barring it
    private static final int GATE_SEARCH_RANGE = 64; // from the wall, for a gate to break instead

    private record Resume(BlockPos target, boolean attackMove) {}

    private static int ticks = 0;
    private static final Map<Integer, Vec3> lastPos = new HashMap<>();
    private static final Map<Integer, Integer> stuckChecks = new HashMap<>();
    private static final Map<Integer, Resume> resumes = new HashMap<>();

    public static boolean isSiegeUnit(Object unit) {
        return unit instanceof CatapultUnit || unit instanceof RavagerUnit || unit instanceof IronGolemUnit;
    }

    // a rampart wall segment or tower (not a gate)
    public static boolean isWall(BuildingPlacement placement) {
        return placement.getBuilding() instanceof RampartBuilding rb && !rb.isGate();
    }

    // only siege units may attack walls and towers
    public static boolean mayAttack(Object unit, BuildingPlacement placement) {
        return placement == null || !isWall(placement) || isSiegeUnit(unit);
    }

    public static boolean mayAttack(Object unit, Level level, BlockPos bp) {
        return mayAttack(unit, BuildingUtils.findBuilding(level.isClientSide(), bp));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ++ticks < CHECK_INTERVAL)
            return;
        ticks = 0;
        List<BuildingPlacement> ramparts = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding() instanceof RampartBuilding && b.isBuilt)
                ramparts.add(b);

        Map<Integer, Vec3> seen = new HashMap<>();
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!entity.isAlive() || !(entity instanceof AttackerUnit attacker) || !(entity instanceof Unit unit) ||
                !attacker.canAttackBuildings())
                continue;
            int id = entity.getId();
            seen.put(id, entity.position());
            boolean busy = unit.getTargetGoal().getTarget() != null || hasBuildingTarget(attacker);

            Resume resume = resumes.get(id);
            if (resume != null) {
                if (busy)
                    continue; // still breaking its way through
                resumes.remove(id);
                if (attacker.getAttackMoveTarget() == null && unit.getMoveGoal().getMoveTarget() == null) {
                    if (resume.attackMove())
                        attacker.setAttackMoveTarget(resume.target());
                    else
                        unit.setMoveTarget(resume.target());
                }
                continue;
            }
            BlockPos target = attacker.getAttackMoveTarget() != null ? attacker.getAttackMoveTarget() : unit.getMoveGoal().getMoveTarget();
            if (busy || target == null || ramparts.isEmpty() || horizontalDist(entity.position(), target) <= 3) {
                stuckChecks.remove(id);
                continue;
            }
            Vec3 before = lastPos.get(id);
            boolean moved = before == null || before.distanceTo(entity.position()) >= MIN_PROGRESS;
            int stuck = moved ? 0 : stuckChecks.getOrDefault(id, 0) + 1;
            stuckChecks.put(id, stuck);
            if (stuck < STUCK_CHECKS)
                continue;

            BuildingPlacement barring = findBarring(entity, unit.getOwnerName(), target, ramparts);
            if (barring == null)
                continue;
            if (!mayAttack(entity, barring))
                barring = nearestGate(barring, unit.getOwnerName(), ramparts);
            if (barring == null)
                continue;
            boolean attackMove = attacker.getAttackMoveTarget() != null;
            unit.getMoveGoal().stopMoving();
            attacker.setAttackMoveTarget(null);
            attacker.setAttackBuildingTarget(barring.centrePos, false);
            resumes.put(id, new Resume(target, attackMove));
            stuckChecks.remove(id);
        }
        lastPos.clear();
        lastPos.putAll(seen);
        stuckChecks.keySet().retainAll(seen.keySet());
        resumes.keySet().retainAll(seen.keySet());
    }

    private static boolean isEnemy(String unitOwner, BuildingPlacement rampart) {
        return !unitOwner.equals(rampart.ownerName) && !AlliancesServerEvents.isAllied(unitOwner, rampart.ownerName) &&
            !com.solegendary.reignofnether.diplomacy.Diplomacy.atPeace(false, unitOwner, rampart.ownerName);
    }

    // the first enemy rampart on the straight line from the unit to where it's going (or one it's pushing against)
    @Nullable
    private static BuildingPlacement findBarring(LivingEntity entity, String owner, BlockPos target, List<BuildingPlacement> ramparts) {
        Vec3 from = entity.position();
        Vec3 dir = new Vec3(target.getX() + 0.5 - from.x, 0, target.getZ() + 0.5 - from.z);
        double length = Math.min(dir.length(), LINE_RANGE);
        if (length < 0.01)
            return null;
        dir = dir.normalize();
        for (double t = 0; t <= length; t += 0.5) {
            double x = from.x + dir.x * t, z = from.z + dir.z * t;
            for (BuildingPlacement r : ramparts)
                if (isEnemy(owner, r) && contains2D(r, x, z, t < 2 ? 1 : 0))
                    return r;
        }
        return null;
    }

    private static boolean contains2D(BuildingPlacement b, double x, double z, int margin) {
        return x >= b.minCorner.getX() - margin && x < b.maxCorner.getX() + 1 + margin &&
            z >= b.minCorner.getZ() - margin && z < b.maxCorner.getZ() + 1 + margin;
    }

    @Nullable
    private static BuildingPlacement nearestGate(BuildingPlacement wall, String owner, List<BuildingPlacement> ramparts) {
        BuildingPlacement best = null;
        double bestDist = GATE_SEARCH_RANGE * GATE_SEARCH_RANGE;
        for (BuildingPlacement r : ramparts) {
            if (isWall(r) || !r.ownerName.equals(wall.ownerName) || !isEnemy(owner, r))
                continue;
            double d = r.centrePos.distSqr(wall.centrePos);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    private static boolean hasBuildingTarget(AttackerUnit attacker) {
        if (attacker.getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal g)
            return g.getBuildingTarget() != null;
        if (attacker.getAttackBuildingGoal() instanceof RangedAttackBuildingGoal<?> g)
            return g.getBuildingTarget() != null;
        return false;
    }

    private static double horizontalDist(Vec3 pos, BlockPos target) {
        double dx = target.getX() + 0.5 - pos.x, dz = target.getZ() + 0.5 - pos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
