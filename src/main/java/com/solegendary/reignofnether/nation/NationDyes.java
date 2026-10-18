package com.solegendary.reignofnether.nation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;

// Buildings in their nation's colour: white wool (and white carpets and banners) in a building's model is put down
// in the dye closest to its owner's nation colour, so tents and flags show whose they are.
// Barbarians and players without a nation keep the white.
public class NationDyes {

    public static DyeColor dyeOf(Nation nation) {
        return switch (nation) {
            case FRANCE -> DyeColor.BLUE;
            case ENGLAND -> DyeColor.RED;
            case SPAIN -> DyeColor.YELLOW;
            case HRE -> DyeColor.BLACK;
            case ROME -> DyeColor.PURPLE;
            case PORTUGAL -> DyeColor.GREEN;
            case RUSSIA -> DyeColor.WHITE;
            case SWEDEN -> DyeColor.LIGHT_BLUE;
        };
    }

    // the block to put down instead, or the same one
    public static BlockState recolour(BlockState state, @Nullable Nation nation) {
        if (nation == null)
            return state;
        String kind;
        if (state.is(Blocks.WHITE_WOOL)) kind = "wool";
        else if (state.is(Blocks.WHITE_CARPET)) kind = "carpet";
        else if (state.is(Blocks.WHITE_BANNER)) kind = "banner";
        else if (state.is(Blocks.WHITE_WALL_BANNER)) kind = "wall_banner";
        else return state;
        DyeColor dye = dyeOf(nation);
        if (dye == DyeColor.WHITE)
            return state;
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", dye.getName() + "_" + kind));
        BlockState out = block.defaultBlockState();
        for (Property<?> p : state.getProperties())
            out = copy(state, out, p); // banner rotation and facing
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }
}
