package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.rampart.RampartBuilding.Family;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.tech.Tech;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

// A player's rampart once built: click a segment to turn it into a tower or a gate (of the wall's level), or to take
// the whole wall (every segment joined to it, towers and gates included) up a level: palisade -> stone (Masonry)
// -> reinforced stone (Fortifications). Each changed segment becomes a building site again, built by the nearest
// workers; the old blocks stay standing until replaced. The price is that of the new level, segment by segment, so a
// long wall, or one with towers and gates, costs more.
public class RampartUpgrades {

    public static final int MAX_LEVEL = 3;
    public static final int WORKER_RANGE = 80; // workers this close come and build
    public static final int WORKERS_SENT = 3;

    public static RampartBuilding get(Family family, int level) {
        return switch (family) {
            case WALL -> level <= 1 ? Buildings.PALISADE : level == 2 ? Buildings.STONE_RAMPART : Buildings.REINFORCED_RAMPART;
            case GATE -> level <= 1 ? Buildings.WOODEN_GATE : level == 2 ? Buildings.IRON_GATE : Buildings.REINFORCED_GATE;
            case TOWER -> level <= 1 ? Buildings.WOODEN_RAMPART_TOWER : level == 2 ? Buildings.RAMPART_TOWER : Buildings.REINFORCED_RAMPART_TOWER;
        };
    }

    public static String levelName(int level) {
        return switch (level) {
            case 1 -> "palissade";
            case 2 -> "pierre";
            default -> "pierre renforcée";
        };
    }

    public static String cost(int wood, int ore) {
        return wood + " bois" + (ore > 0 ? ", " + ore + " minerai" : "");
    }

    // ---- the whole wall ---------------------------------------------------------------------------------------------

    // every segment of the same owner joined to this one (columns side by side), this one first
    public static List<BuildingPlacement> wall(Level level, BuildingPlacement start) {
        Map<Long, BuildingPlacement> cells = new HashMap<>();
        for (BuildingPlacement b : BuildingUtils.getBuildingsList(level.isClientSide())) {
            if (!(b.getBuilding() instanceof RampartBuilding) || !b.ownerName.equals(start.ownerName))
                continue;
            List<RampartShapes.Column> columns = RampartShapes.get(level, b.originPos);
            if (columns != null)
                for (RampartShapes.Column c : columns)
                    cells.put(BlockPos.asLong(c.x(), 0, c.z()), b);
        }
        List<BuildingPlacement> found = new ArrayList<>(List.of(start));
        Set<BuildingPlacement> seen = new HashSet<>(found);
        int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int i = 0; i < found.size(); i++) {
            List<RampartShapes.Column> columns = RampartShapes.get(level, found.get(i).originPos);
            if (columns == null)
                continue;
            for (RampartShapes.Column c : columns)
                for (int[] s : sides) {
                    BuildingPlacement next = cells.get(BlockPos.asLong(c.x() + s[0], 0, c.z() + s[1]));
                    if (next != null && seen.add(next))
                        found.add(next);
                }
        }
        return found;
    }

    public record Plan(List<BuildingPlacement> segments, int level, int wood, int ore, int towers, int gates, @Nullable String error) {
        public boolean isValid() { return error == null; }
    }

    private static Plan error(String error) {
        return new Plan(List.of(), 0, 0, 0, 0, 0, error);
    }

    // segment cost at a level (walls by length, gates and towers each)
    private static int[] segmentCost(Level level, BuildingPlacement b, RampartBuilding target) {
        List<RampartShapes.Column> columns = RampartShapes.get(level, b.originPos);
        int n = columns == null ? 1 : columns.size();
        return new int[] { RampartLine.woodCost(target, n), RampartLine.oreCost(target, n) };
    }

    // the wall goes up to the level after its lowest segment's
    public static Plan upgradePlan(Level level, BuildingPlacement clicked) {
        List<BuildingPlacement> wall = wall(level, clicked);
        int lowest = MAX_LEVEL;
        for (BuildingPlacement b : wall)
            lowest = Math.min(lowest, ((RampartBuilding) b.getBuilding()).kind.level);
        if (lowest >= MAX_LEVEL)
            return error("Le mur est déjà au niveau maximum");
        int target = lowest + 1;
        RampartBuilding targetWall = get(Family.WALL, target);
        if (!Tech.canBuild(level.isClientSide(), clicked.ownerName, targetWall)) {
            Tech tech = Tech.getRequiredFor(targetWall);
            return error("Il faut d'abord rechercher : " + (tech != null ? tech.displayName : "?"));
        }
        List<BuildingPlacement> segments = new ArrayList<>();
        int wood = 0, ore = 0, towers = 0, gates = 0;
        for (BuildingPlacement b : wall) {
            RampartBuilding rb = (RampartBuilding) b.getBuilding();
            if (rb.kind.level >= target)
                continue;
            if (!b.isBuilt)
                return error("Attendez que tout le mur soit construit");
            int[] c = segmentCost(level, b, get(rb.kind.family, target));
            wood += c[0];
            ore += c[1];
            if (rb.isTower()) towers++;
            if (rb.isGate()) gates++;
            segments.add(b);
        }
        return new Plan(segments, target, wood, ore, towers, gates, null);
    }

    // ---- one segment ------------------------------------------------------------------------------------------------

    @Nullable
    public static String gateError(Level level, BuildingPlacement b) {
        List<RampartShapes.Column> columns = RampartShapes.get(level, b.originPos);
        if (columns == null || columns.size() < 3)
            return "Morceau trop court pour une porte";
        boolean sameX = columns.stream().allMatch(c -> c.x() == columns.get(0).x());
        boolean sameZ = columns.stream().allMatch(c -> c.z() == columns.get(0).z());
        if (!sameX && !sameZ)
            return "Une porte se pose sur un morceau de mur droit";
        return null;
    }

    public static int[] gateCost(BuildingPlacement b) {
        RampartBuilding gate = get(Family.GATE, ((RampartBuilding) b.getBuilding()).kind.level);
        return new int[] { RampartLine.woodCost(gate, 1), RampartLine.oreCost(gate, 1) };
    }

    public static int[] towerCost(BuildingPlacement b) {
        RampartBuilding tower = get(Family.TOWER, ((RampartBuilding) b.getBuilding()).kind.level);
        return new int[] { RampartLine.woodCost(tower, 1), RampartLine.oreCost(tower, 1) };
    }

    // ---- doing it (server) ------------------------------------------------------------------------------------------

    public static void upgrade(ServerLevel level, BuildingPlacement clicked) {
        Plan plan = upgradePlan(level, clicked);
        if (!plan.isValid()) {
            HudClientboundPacket.showTempMessageI18n(clicked.ownerName, plan.error());
            return;
        }
        if (!pay(clicked.ownerName, plan.wood(), plan.ore()))
            return;
        int[] builders = nearestWorkers(level, clicked.ownerName, clicked.centrePos);
        boolean queue = false;
        for (BuildingPlacement b : plan.segments())
            if (replace(level, b, get(((RampartBuilding) b.getBuilding()).kind.family, plan.level()), builders, queue) != null)
                queue = true;
        noWorkersWarning(clicked.ownerName, builders);
    }

    public static void makeGate(ServerLevel level, BuildingPlacement b) {
        String error = gateError(level, b);
        if (error != null) {
            HudClientboundPacket.showTempMessageI18n(b.ownerName, error);
            return;
        }
        int[] c = gateCost(b);
        if (!pay(b.ownerName, c[0], c[1]))
            return;
        int[] builders = nearestWorkers(level, b.ownerName, b.centrePos);
        replace(level, b, get(Family.GATE, ((RampartBuilding) b.getBuilding()).kind.level), builders, false);
        noWorkersWarning(b.ownerName, builders);
    }

    // a 3 x 3 tower round the middle of the segment, cutting the wall around it (paid there)
    public static void makeTower(ServerLevel level, BuildingPlacement b) {
        List<RampartShapes.Column> columns = RampartShapes.get(level, b.originPos);
        if (columns == null || columns.isEmpty())
            return;
        RampartShapes.Column mid = columns.get(columns.size() / 2);
        BlockPos centre = new BlockPos(mid.x(), mid.y(), mid.z());
        int[] builders = nearestWorkers(level, b.ownerName, b.centrePos);
        if (builders.length == 0) {
            HudClientboundPacket.showTempMessageI18n(b.ownerName, "Il faut des ouvriers près du mur pour construire une tour");
            return;
        }
        RampartServerEvents.buildPlayerRampart(level, b.ownerName, get(Family.TOWER, ((RampartBuilding) b.getBuilding()).kind.level),
            centre, centre, builders);
    }

    private static boolean pay(String owner, int wood, int ore) {
        boolean w = ResourcesServerEvents.canAfford(owner, ResourceName.WOOD, wood);
        boolean o = ResourcesServerEvents.canAfford(owner, ResourceName.ORE, ore);
        if (!w || !o) {
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, w, o, true);
            return false;
        }
        ResourcesServerEvents.addSubtractResources(new Resources(owner, 0, -wood, -ore));
        return true;
    }

    // the segment becomes a building site of another kind, on the same columns (heights of the new kind)
    @Nullable
    private static BuildingPlacement replace(ServerLevel level, BuildingPlacement old, RampartBuilding next, int[] builders, boolean queue) {
        List<RampartShapes.Column> columns = RampartShapes.get(level, old.originPos);
        if (columns == null || columns.isEmpty())
            return null;
        List<RampartShapes.Column> reshaped = new ArrayList<>();
        for (RampartShapes.Column c : columns)
            reshaped.add(new RampartShapes.Column(c.x(), c.y(), c.z(), next.columnHeight(c.x(), c.z())));
        BuildingServerEvents.getBuildings().remove(old);
        com.solegendary.reignofnether.building.BuildingClientboundPacket.removeBuilding(old.originPos);
        return RampartServerEvents.placeSegment(level, next, old.ownerName, reshaped, builders, false, queue);
    }

    // the owner's nearest workers, those not already building first
    private static int[] nearestWorkers(ServerLevel level, String owner, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        List<LivingEntity> workers = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le.isAlive() && le instanceof WorkerUnit && ((Unit) le).getOwnerName().equals(owner) &&
                le.distanceToSqr(at) < WORKER_RANGE * WORKER_RANGE)
                workers.add(le);
        workers.sort(Comparator.comparing((LivingEntity le) -> isBuilding(le) ? 1 : 0).thenComparing(le -> le.distanceToSqr(at)));
        return workers.stream().limit(WORKERS_SENT).mapToInt(LivingEntity::getId).toArray();
    }

    private static boolean isBuilding(LivingEntity le) {
        return le instanceof WorkerUnit w && w.getBuildRepairGoal() != null && w.getBuildRepairGoal().getBuildingTarget() != null;
    }

    private static void noWorkersWarning(String owner, int[] builders) {
        if (builders.length == 0)
            HudClientboundPacket.showTempMessageI18n(owner, "Aucun ouvrier proche : envoyez-en sur le chantier");
    }
}
