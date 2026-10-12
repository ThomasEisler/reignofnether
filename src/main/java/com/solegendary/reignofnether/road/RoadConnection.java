package com.solegendary.reignofnether.road;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// A road between two buildings (the network's nodes), identified by their origin positions.
// Roads are built (and upgraded) block by block by workers, in the order of the blocks list.
public class RoadConnection {
    public final BlockPos from;
    public final BlockPos to;
    public final String ownerName;
    public RoadType type; // tier of the blocks already in place
    @Nullable public RoadType upgradeTo; // tier being built over the existing road, if an upgrade is in progress
    public final List<BlockPos> blocks; // road surface blocks, in building order (from's apron, road, to's apron)
    public final int length; // centre line length, used for costs
    public int built; // number of blocks of the list placed so far (for the current construction or upgrade)

    public RoadConnection(BlockPos from, BlockPos to, String ownerName, RoadType type, @Nullable RoadType upgradeTo,
                          List<BlockPos> blocks, int length, int built) {
        this.from = from;
        this.to = to;
        this.ownerName = ownerName;
        this.type = type;
        this.upgradeTo = upgradeTo;
        this.blocks = blocks;
        this.length = length;
        this.built = built;
    }

    public boolean isUnderConstruction() {
        return upgradeTo != null || built < blocks.size();
    }

    // the tier the workers are currently placing
    public RoadType getTypeInProgress() {
        return upgradeTo != null ? upgradeTo : type;
    }

    // where workers should stand to place the next block
    @Nullable
    public BlockPos getNextBlock() {
        return built < blocks.size() ? blocks.get(built) : null;
    }

    public boolean connects(BlockPos buildingOrigin) {
        return from.equals(buildingOrigin) || to.equals(buildingOrigin);
    }

    public BlockPos getOtherEnd(BlockPos buildingOrigin) {
        return from.equals(buildingOrigin) ? to : from;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("from", from.asLong());
        tag.putLong("to", to.asLong());
        tag.putString("ownerName", ownerName);
        tag.putString("type", type.name());
        if (upgradeTo != null)
            tag.putString("upgradeTo", upgradeTo.name());
        tag.put("blocks", new LongArrayTag(blocks.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.putInt("length", length);
        tag.putInt("built", built);
        return tag;
    }

    public static RoadConnection load(CompoundTag tag) {
        List<BlockPos> blocks = new ArrayList<>();
        for (long l : tag.getLongArray("blocks"))
            blocks.add(BlockPos.of(l));
        return new RoadConnection(
            BlockPos.of(tag.getLong("from")),
            BlockPos.of(tag.getLong("to")),
            tag.getString("ownerName"),
            RoadType.valueOf(tag.getString("type")),
            tag.contains("upgradeTo") ? RoadType.valueOf(tag.getString("upgradeTo")) : null,
            blocks,
            tag.contains("length") ? tag.getInt("length") : blocks.size() / 3,
            // roads saved before workers built them were placed instantly
            tag.contains("built") ? tag.getInt("built") : blocks.size()
        );
    }
}
