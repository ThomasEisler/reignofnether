package com.solegendary.reignofnether.building.buildings.placements;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

// Young trees on the sawmill's podzol spots: an empty spot gets a sapling (an azalea bush, which never grows on its
// own), which becomes a small tree (2 logs and a leafy top) after a while. Workers cut the logs like any tree.
public class SawmillPlacement extends FarmPlacement {

    public static final int PLANT_SECONDS = 15;  // empty spot -> sapling
    public static final int GROW_SECONDS = 100;  // sapling -> tree
    public static final int TREE_LOGS = 2;
    private static final int TICK_INTERVAL = 20;

    private final List<BlockPos> spots = new ArrayList<>(); // where a tree's bottom log stands
    private final int[] spotTicks;

    public SawmillPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName, ArrayList<BuildingBlock> blocks) {
        super(building, level, originPos, rotation, ownerName, blocks, false);
        for (BuildingBlock bb : blocks)
            if (bb.getBlockState().is(Blocks.PODZOL))
                spots.add(bb.getBlockPos().above());
        spotTicks = new int[spots.size()];
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (tickLevel.isClientSide() || !isBuilt || tickAge % TICK_INTERVAL != 0)
            return;
        for (int i = 0; i < spots.size(); i++) {
            BlockPos bp = spots.get(i);
            BlockState bs = tickLevel.getBlockState(bp);
            if (bs.isAir()) {
                clearLeftovers(tickLevel, bp);
                spotTicks[i] += TICK_INTERVAL;
                if (spotTicks[i] >= PLANT_SECONDS * 20) {
                    tickLevel.setBlockAndUpdate(bp, Blocks.AZALEA.defaultBlockState());
                    spotTicks[i] = 0;
                }
            } else if (bs.is(Blocks.AZALEA)) {
                spotTicks[i] += TICK_INTERVAL;
                if (spotTicks[i] >= GROW_SECONDS * 20 && tickLevel.getBlockState(bp.above()).isAir()) {
                    growTree(tickLevel, bp);
                    spotTicks[i] = 0;
                }
            } else {
                spotTicks[i] = 0;
            }
        }
    }

    private static void growTree(Level level, BlockPos bp) {
        for (int y = 0; y < TREE_LOGS; y++)
            level.setBlockAndUpdate(bp.above(y), Blocks.OAK_LOG.defaultBlockState());
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockPos top = bp.above(TREE_LOGS);
        for (BlockPos p : List.of(top, top.above(), top.north(), top.south(), top.east(), top.west(),
                top.below().north(), top.below().south(), top.below().east(), top.below().west()))
            if (level.getBlockState(p).isAir())
                level.setBlockAndUpdate(p, leaves);
    }

    // leaves (and fallen bits) left floating once the logs have been cut
    private static void clearLeftovers(Level level, BlockPos bp) {
        BlockPos top = bp.above(TREE_LOGS);
        for (BlockPos p : List.of(top, top.above(), top.north(), top.south(), top.east(), top.west(),
                top.below(), top.below().north(), top.below().south(), top.below().east(), top.below().west()))
            if (level.getBlockState(p).is(Blocks.OAK_LEAVES) && level.getBlockState(p).getValue(BlockStateProperties.PERSISTENT))
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public void destroy(ServerLevel serverLevel) {
        super.destroy(serverLevel);
        for (BlockPos bp : spots) {
            for (int y = 0; y < TREE_LOGS; y++)
                if (serverLevel.getBlockState(bp.above(y)).is(Blocks.OAK_LOG) || serverLevel.getBlockState(bp.above(y)).is(Blocks.AZALEA))
                    serverLevel.setBlock(bp.above(y), Blocks.AIR.defaultBlockState(), 3);
            clearLeftovers(serverLevel, bp);
        }
    }
}
