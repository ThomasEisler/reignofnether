package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UnitFormations {

    // given a list of entities that were issued a move command to moveBp, find the best pairings of entities and
    // positions that allows each entity the best chance to reach said position without bumping into each other

    public static <T extends LivingEntity> List<Pair<LivingEntity, BlockPos>> getMoveFormation(Level level, List<T> units, BlockPos moveBp) {

        // 1. create a list of final BPs the units should move into, closest approximation of a square
        //    with moveBp at the approximate centre by spiralling outward
        ArrayList<BlockPos> bps = new ArrayList<>(List.of(moveBp));
        BlockPos lastBp = bps.get(0);

        if (units.size() == 3) {
            // for specifically size 3 just use a 3v1 line
            bps.add(bps.get(0).west());
            bps.add(bps.get(0).east());
        } else {
            Direction dir = Direction.NORTH;
            double edgeLength = 1.1f;
            int edgeBlocksLeft = 0;
            for (int i = 0; i < units.size() - 1; i++) {
                BlockPos nextBp = switch (dir) {
                    case NORTH -> lastBp.north();
                    case SOUTH -> lastBp.south();
                    case WEST -> lastBp.west();
                    case EAST -> lastBp.east();
                    default -> null;
                };
                bps.add(nextBp);
                lastBp = nextBp;
                edgeBlocksLeft -= 1;
                if (edgeBlocksLeft <= 0) {
                    edgeLength += 0.5;
                    edgeBlocksLeft = (int) Math.floor(edgeLength);
                    dir = switch (dir) {
                        case NORTH -> Direction.WEST;
                        case WEST -> Direction.SOUTH;
                        case SOUTH -> Direction.EAST;
                        case EAST -> Direction.NORTH;
                        default -> null;
                    };
                }
            }
        }

        // 2. pair each unit to each square based on min/max x/z positions
        //    eg. the north-west-most unit should go to the north-west-most square
        units.sort(Comparator.comparing(u -> u.getOnPos().getX()));
        units.sort(Comparator.comparing(u -> u.getOnPos().getZ()));
        bps.sort(Comparator.comparing(Vec3i::getX));
        bps.sort(Comparator.comparing(Vec3i::getZ));

        // entityId/BlockPos pairs
        List<Pair<LivingEntity, BlockPos>> formation = new ArrayList<>();

        for (int i = 0; i < units.size(); i++) {
            formation.add(new Pair<>(units.get(i), bps.get(i)));
        }
        return formation;
    }


    // ---- military formations ------------------------------------------------------------------------------------
    // Soldiers moved together line up in ranks facing the way they march, instead of crowding round the point:
    // fighters on foot in front, riders behind them, archers and crossbowmen next, siege engines and the rest at
    // the back. Each rank is centred on the target; units keep their side (left ones go left) so they don't cross.
    // The whole group walks at the pace of its slowest unit so the ranks hold together on the way.

    public static final double SPACING = 2.0; // blocks between two units, along a rank and between ranks
    public static final int MIN_SOLDIERS = 2;

    private enum Role { MELEE, RIDER, RANGED, SIEGE, OTHER }

    public static boolean isSoldier(LivingEntity le) {
        return le instanceof AttackerUnit && !(le instanceof WorkerUnit) &&
            !(le instanceof com.solegendary.reignofnether.unit.units.villagers.ExplorerUnit);
    }

    // enough soldiers among them to march in formation
    public static boolean useMilitaryFormation(List<? extends LivingEntity> units) {
        return units.stream().filter(UnitFormations::isSoldier).count() >= MIN_SOLDIERS;
    }

    private static Role role(LivingEntity le) {
        if (!isSoldier(le))
            return Role.OTHER;
        if (le instanceof com.solegendary.reignofnether.unit.units.villagers.CatapultUnit)
            return Role.SIEGE;
        if (le.isPassenger() || le.isVehicle() || le instanceof net.minecraft.world.entity.animal.horse.AbstractHorse)
            return Role.RIDER;
        if (le instanceof RangedAttackerUnit)
            return Role.RANGED;
        return Role.MELEE;
    }

    public static List<Pair<LivingEntity, BlockPos>> getMilitaryFormation(Level level, List<? extends LivingEntity> units, BlockPos target) {
        List<Pair<LivingEntity, BlockPos>> formation = new ArrayList<>();
        if (units.isEmpty())
            return formation;
        // marching direction: from the middle of the group to the target
        double cx = 0, cz = 0;
        for (LivingEntity le : units) { cx += le.getX(); cz += le.getZ(); }
        cx /= units.size();
        cz /= units.size();
        double fx = target.getX() + 0.5 - cx, fz = target.getZ() + 0.5 - cz;
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1) { fx = 0; fz = 1; } else { fx /= len; fz /= len; }
        final double fwdX = fx, fwdZ = fz, rightX = -fz, rightZ = fx;
        final double ocx = cx, ocz = cz;

        int width = Math.max(2, (int) Math.ceil(Math.sqrt(units.size() * 1.6)));
        // ranks, front to back: each role starts a new rank; within a role the units furthest forward go first
        List<List<LivingEntity>> ranks = new ArrayList<>();
        for (Role role : Role.values()) {
            List<LivingEntity> group = new ArrayList<>();
            for (LivingEntity le : units)
                if (role(le) == role)
                    group.add(le);
            group.sort(Comparator.comparingDouble((LivingEntity le) -> -((le.getX() - ocx) * fwdX + (le.getZ() - ocz) * fwdZ)));
            for (int i = 0; i < group.size(); i += width)
                ranks.add(new ArrayList<>(group.subList(i, Math.min(group.size(), i + width))));
        }
        for (int r = 0; r < ranks.size(); r++) {
            List<LivingEntity> rank = ranks.get(r);
            // keep their side: sorted left to right
            rank.sort(Comparator.comparingDouble((LivingEntity le) -> (le.getX() - ocx) * rightX + (le.getZ() - ocz) * rightZ));
            double depth = ((ranks.size() - 1) / 2.0 - r) * SPACING; // the front rank ahead of the target
            for (int i = 0; i < rank.size(); i++) {
                double side = (i - (rank.size() - 1) / 2.0) * SPACING;
                double x = target.getX() + 0.5 + fwdX * depth + rightX * side;
                double z = target.getZ() + 0.5 + fwdZ * depth + rightZ * side;
                formation.add(new Pair<>(rank.get(i), ground(level, target, (int) Math.floor(x), (int) Math.floor(z))));
            }
        }
        return formation;
    }

    // the walkable spot at x, z near the target's height (the target itself over water or a cliff)
    private static BlockPos ground(Level level, BlockPos target, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, target.getY(), z)))
            return new BlockPos(x, target.getY(), z);
        // the client only keeps MOTION_BLOCKING up to date (leaves included): step down through the trees
        int y = level.getHeight(level.isClientSide() ? Heightmap.Types.MOTION_BLOCKING : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int i = 0; i < 24 && isTree(level.getBlockState(new BlockPos(x, y, z))); i++)
            y--;
        BlockPos bp = new BlockPos(x, y, z);
        if (Math.abs(y - target.getY()) > 6 || !level.getFluidState(bp).isEmpty())
            return target;
        return bp;
    }

    private static boolean isTree(net.minecraft.world.level.block.state.BlockState bs) {
        return bs.is(net.minecraft.tags.BlockTags.LEAVES) || bs.is(net.minecraft.tags.BlockTags.LOGS);
    }

    // how fast a unit actually walks, to make the group keep the pace of its slowest
    public static double walkSpeed(LivingEntity le) {
        LivingEntity mover = le.getVehicle() instanceof LivingEntity v ? v : le;
        double base = mover.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float mod = mover instanceof Unit u ? u.getSpeedModifier() : 1f;
        if (mod < 1f) mod = (float) Math.sqrt(mod);
        return base * mod;
    }

    // everyone slows down to the slowest one's pace (never speeds up)
    public static void syncSpeeds(List<? extends LivingEntity> units) {
        double slowest = Double.MAX_VALUE;
        for (LivingEntity le : units)
            slowest = Math.min(slowest, walkSpeed(le));
        for (LivingEntity le : units)
            if (le instanceof Unit u && u.getMoveGoal() != null) {
                double own = walkSpeed(le);
                u.getMoveGoal().formationSpeed = own > 0 ? (float) Math.min(1, slowest / own) : 1f;
            }
    }
}
