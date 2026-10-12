package com.solegendary.reignofnether.road;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// Road tiers. Costs are per block of road length (along its centre line), so longer roads cost more.
public enum RoadType {
    PATH(Blocks.DIRT_PATH, 1, 0, 0.15f),
    PAVED(Blocks.COBBLESTONE, 1, 1, 0.25f),
    ADVANCED(Blocks.POLISHED_ANDESITE, 0, 3, 0.35f);

    public final Block block;
    public final int woodPerLength;
    public final int orePerLength;
    public final float speedBonus; // movement speed multiplier bonus for units on this road (0.15 = +15%)

    // in building structures, dirt path floor blocks are "road floor" tiles that take the material of the
    // best road connected to the building
    public static boolean isRoadFloorMarker(BlockState bs) {
        return bs.is(Blocks.DIRT_PATH);
    }

    public static boolean isRoadBlock(BlockState bs) {
        for (RoadType type : values())
            if (bs.is(type.block))
                return true;
        return false;
    }

    RoadType(Block block, int woodPerLength, int orePerLength, float speedBonus) {
        this.block = block;
        this.woodPerLength = woodPerLength;
        this.orePerLength = orePerLength;
        this.speedBonus = speedBonus;
    }
}
