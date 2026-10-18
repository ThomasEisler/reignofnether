package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.building.BuildRange;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.tech.Tech;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// A player's rampart drawn between two points: the columns it covers (following the ground), what it costs and
// whether it can be built. Computed the same way for the preview and by the server.
public class RampartLine {

    public static final int MAX_COLUMNS = 48;
    public static final int GATE_WIDTH = 5;
    public static final int SEGMENT_LENGTH = 5;

    public record Plan(List<RampartShapes.Column> columns, int wood, int ore, @Nullable String error) {
        public boolean isValid() { return error == null; }
    }

    // price per column for walls, per gate for gates
    public static int woodCost(RampartBuilding b, int columns) {
        return switch (b.kind) {
            case PALISADE -> 4 * columns;
            case STONE -> 3 * columns;
            case REINFORCED -> 2 * columns;
            case WOODEN_GATE -> 80;
            case IRON_GATE, REINFORCED_GATE -> 60;
            case WOODEN_TOWER -> 120;
            case TOWER, REINFORCED_TOWER -> 100;
        };
    }

    public static int oreCost(RampartBuilding b, int columns) {
        return switch (b.kind) {
            case PALISADE, WOODEN_GATE, WOODEN_TOWER -> 0;
            case STONE -> 4 * columns;
            case REINFORCED -> 7 * columns;
            case IRON_GATE -> 100;
            case REINFORCED_GATE -> 180;
            case TOWER -> 150;
            case REINFORCED_TOWER -> 250;
        };
    }

    // x, z of the line's columns, joined side by side (never only by a corner, units could squeeze through)
    public static List<int[]> lineColumns(BlockPos start, BlockPos end, boolean gate) {
        return lineColumns(start, end, gate, false);
    }

    public static List<int[]> lineColumns(BlockPos start, BlockPos end, boolean gate, boolean tower) {
        if (tower) { // 3 x 3 around the clicked block, the centre in the middle of the list
            List<int[]> cols = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++)
                    cols.add(new int[] { start.getX() + dx, start.getZ() + dz });
            return cols;
        }
        int dx = end.getX() - start.getX(), dz = end.getZ() - start.getZ();
        List<int[]> cols = new ArrayList<>();
        if (gate) { // always GATE_WIDTH wide, straight along the main direction
            boolean alongX = Math.abs(dx) >= Math.abs(dz);
            int step = (alongX ? dx : dz) < 0 ? -1 : 1;
            for (int i = 0; i < GATE_WIDTH; i++)
                cols.add(alongX ? new int[] { start.getX() + i * step, start.getZ() } : new int[] { start.getX(), start.getZ() + i * step });
            return cols;
        }
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int prevX = start.getX(), prevZ = start.getZ();
        cols.add(new int[] { prevX, prevZ });
        for (int i = 1; i <= steps && cols.size() < MAX_COLUMNS; i++) {
            int x = start.getX() + Math.round((float) dx * i / steps);
            int z = start.getZ() + Math.round((float) dz * i / steps);
            if (x != prevX && z != prevZ)
                cols.add(new int[] { x, prevZ });
            cols.add(new int[] { x, z });
            prevX = x;
            prevZ = z;
        }
        while (cols.size() > MAX_COLUMNS)
            cols.remove(cols.size() - 1);
        return cols;
    }

    public static Plan plan(Level level, String ownerName, RampartBuilding building, BlockPos start, BlockPos end) {
        if (!Tech.canBuild(level.isClientSide(), ownerName, building)) {
            Tech tech = Tech.getRequiredFor(building);
            return new Plan(List.of(), 0, 0, "Il faut d'abord rechercher : " + (tech != null ? tech.displayName : "?"));
        }
        List<RampartShapes.Column> columns = new ArrayList<>();
        for (int[] xz : lineColumns(start, end, building.isGate(), building.isTower())) {
            BlockPos ground = getGround(level, xz[0], xz[1]);
            if (ground == null) {
                if (building.isGate() || building.isTower())
                    return new Plan(List.of(), 0, 0, "Pas de porte sur l'eau");
                continue; // a wall stops at water
            }
            if (BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), ground.above())) {
                if (building.isTower()) { // a tower can stand on one of the player's walls, which is cut around it
                    var other = BuildingUtils.findBuilding(level.isClientSide(), ground.above());
                    if (other == null || !(other.getBuilding() instanceof RampartBuilding rb) || rb.isGate() || rb.isTower() ||
                        !other.ownerName.equals(ownerName))
                        return new Plan(List.of(), 0, 0, "Un bâtiment est dans le passage");
                    ground = new BlockPos(xz[0], other.minCorner.getY() - 1, xz[1]);
                    BlockPos g2 = getGround(level, xz[0], xz[1]);
                    if (g2 != null) ground = g2;
                    columns.add(new RampartShapes.Column(xz[0], ground.getY(), xz[1], building.columnHeight(xz[0], xz[1])));
                    continue;
                }
                if (building.isGate())
                    return new Plan(List.of(), 0, 0, "Un bâtiment est dans le passage");
                continue; // joins an existing wall or building
            }
            if (BuildRange.hasAnyBuilding(level, ownerName) &&
                !BuildRange.isFootprintInRange(level, ownerName, xz[0], xz[1], xz[0], xz[1]))
                return new Plan(List.of(), 0, 0, "Trop loin de vos bâtiments");
            columns.add(new RampartShapes.Column(xz[0], ground.getY(), xz[1], building.columnHeight(xz[0], xz[1])));
        }
        if (columns.isEmpty())
            return new Plan(List.of(), 0, 0, "Rien à construire ici");
        return new Plan(columns, woodCost(building, columns.size()), oreCost(building, columns.size()), null);
    }

    // a wall through several points (the player's turns): each stretch planned as above, columns joined, one price
    public static Plan planPath(Level level, String ownerName, RampartBuilding building, List<BlockPos> points) {
        if (points.size() < 2)
            return points.isEmpty() ? new Plan(List.of(), 0, 0, "Rien à construire ici") : plan(level, ownerName, building, points.get(0), points.get(0));
        List<RampartShapes.Column> columns = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i + 1 < points.size(); i++) {
            Plan leg = plan(level, ownerName, building, points.get(i), points.get(i + 1));
            if (!leg.isValid()) {
                if (leg.error().equals("Rien à construire ici"))
                    continue; // eg. a stretch along an existing wall
                return new Plan(List.of(), 0, 0, (points.size() > 2 ? "Tronçon " + (i + 1) + " : " : "") + leg.error());
            }
            for (RampartShapes.Column c : leg.columns())
                if (seen.add(BlockPos.asLong(c.x(), 0, c.z())))
                    columns.add(c);
        }
        if (columns.isEmpty())
            return new Plan(List.of(), 0, 0, "Rien à construire ici");
        return new Plan(columns, woodCost(building, columns.size()), oreCost(building, columns.size()), null);
    }

    // splits the plan into segments (buildings) of a few columns; a gate is one segment; gaps start a new segment
    public static List<List<RampartShapes.Column>> segments(RampartBuilding building, List<RampartShapes.Column> columns) {
        List<List<RampartShapes.Column>> segments = new ArrayList<>();
        if (building.isGate() || building.isTower()) {
            segments.add(columns);
            return segments;
        }
        List<RampartShapes.Column> current = new ArrayList<>();
        RampartShapes.Column prev = null;
        for (RampartShapes.Column c : columns) {
            boolean adjacent = prev != null && Math.abs(c.x() - prev.x()) + Math.abs(c.z() - prev.z()) == 1;
            if (!current.isEmpty() && (!adjacent || current.size() >= SEGMENT_LENGTH)) {
                segments.add(current);
                current = new ArrayList<>();
            }
            current.add(c);
            prev = c;
        }
        if (!current.isEmpty())
            segments.add(current);
        return segments;
    }

    // ground block of a column, ignoring plants, trees and rampart blocks; null over water
    @Nullable
    public static BlockPos getGround(Level level, int x, int z) {
        BlockPos bp = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        for (int i = 0; i < 24; i++) {
            BlockState bs = level.getBlockState(bp);
            if (!bs.getFluidState().isEmpty())
                return null;
            if (!bs.is(BlockTags.LOGS) && !bs.is(BlockTags.LEAVES) && !bs.canBeReplaced() && !isRampartBlock(bs))
                return bp;
            bp = bp.below();
        }
        return null;
    }

    public static boolean isRampartBlock(BlockState bs) {
        return bs.is(BlockRegistrar.PALISADE.get()) || bs.is(BlockRegistrar.PALISADE_TOP.get()) ||
            bs.is(BlockRegistrar.STONE_RAMPART.get()) || bs.is(BlockRegistrar.REINFORCED_RAMPART.get()) || bs.getBlock() instanceof RampartGateBlock;
    }
}
