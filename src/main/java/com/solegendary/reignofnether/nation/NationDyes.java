package com.solegendary.reignofnether.nation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;

// Buildings in their nation's colour: white wool (and white carpets and banners) in a building's model is put down
// in the dye closest to its owner's nation colour, so tents and flags show whose they are.
// Barbarians get black; players without a nation keep the white.
public class NationDyes {

    public static DyeColor dyeOf(Nation nation) {
        return switch (nation) {
            case FRANCE -> DyeColor.BLUE;
            case ENGLAND -> DyeColor.RED;
            case SPAIN -> DyeColor.YELLOW;
            case HRE -> DyeColor.BROWN;
            case ROME -> DyeColor.PURPLE;
            case PORTUGAL -> DyeColor.GREEN;
            case RUSSIA -> DyeColor.ORANGE;
            case SWEDEN -> DyeColor.LIGHT_BLUE;
        };
    }

    // the block to put down instead, or the same one
    public static BlockState recolour(BlockState state, @Nullable Nation nation) {
        return nation == null ? state : recolour(state, dyeOf(nation));
    }

    public static BlockState recolour(BlockState state, DyeColor dye) {
        String kind;
        if (state.is(Blocks.WHITE_WOOL)) kind = "wool";
        else if (state.is(Blocks.WHITE_CARPET)) kind = "carpet";
        else if (state.is(Blocks.WHITE_BANNER)) kind = "banner";
        else if (state.is(Blocks.WHITE_WALL_BANNER)) kind = "wall_banner";
        else return state;
        if (dye == DyeColor.WHITE)
            return state;
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", dye.getName() + "_" + kind));
        BlockState out = block.defaultBlockState();
        for (Property<?> p : state.getProperties())
            out = copy(state, out, p); // banner rotation and facing
        return out;
    }

    // white wool, carpet or banner in the world where the building wants another colour of the same thing
    public static boolean isStillWhite(BlockState inWorld, BlockState wanted) {
        if (inWorld.getBlock() == wanted.getBlock() || recolour(inWorld, DyeColor.BLACK) == inWorld)
            return false;
        return (inWorld.is(BlockTags.WOOL) && wanted.is(BlockTags.WOOL)) ||
            (inWorld.is(BlockTags.WOOL_CARPETS) && wanted.is(BlockTags.WOOL_CARPETS)) ||
            (inWorld.is(BlockTags.BANNERS) && wanted.is(BlockTags.BANNERS));
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }
}
