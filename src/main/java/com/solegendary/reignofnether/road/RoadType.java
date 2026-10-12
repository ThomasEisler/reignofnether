package com.solegendary.reignofnether.road;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// Road tiers, each a random-looking mix of blocks. Costs are per block of road length (along its centre line),
// so longer roads cost more.
public enum RoadType {
    PATH(List.of(Blocks.GRAVEL, Blocks.COARSE_DIRT), 1, 0, 0.15f),
    PAVED(List.of(Blocks.ANDESITE, Blocks.GRAVEL), 1, 1, 0.25f),
    ADVANCED(List.of(Blocks.ANDESITE, Blocks.STONE_BRICKS, Blocks.STONE), 0, 3, 0.35f);

    public final List<Block> blocks;
    public final int woodPerLength;
    public final int orePerLength;
    public final float speedBonus; // movement speed multiplier bonus for units on this road (0.15 = +15%)

    RoadType(List<Block> blocks, int woodPerLength, int orePerLength, float speedBonus) {
        this.blocks = blocks;
        this.woodPerLength = woodPerLength;
        this.orePerLength = orePerLength;
        this.speedBonus = speedBonus;
    }

    // the block of the mix used at a position; deterministic so rebuilding a road gives the same pattern
    public BlockState getStateAt(BlockPos bp) {
        long hash = Mth.getSeed(bp.getX(), 0, bp.getZ());
        return blocks.get((int) Math.floorMod(hash >> 16, (long) blocks.size())).defaultBlockState();
    }

    // in building structures, dirt path floor blocks are "road floor" tiles that take the material of the
    // best road connected to the building
    public static boolean isRoadFloorMarker(BlockState bs) {
        return bs.is(Blocks.DIRT_PATH);
    }

    // any block a road or road floor can be made of (dirt path included for floors placed before roads had mixes)
    public static boolean isRoadBlock(BlockState bs) {
        if (bs.is(Blocks.DIRT_PATH))
            return true;
        for (RoadType type : values())
            for (Block block : type.blocks)
                if (bs.is(block))
                    return true;
        return false;
    }
}
