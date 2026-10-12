package com.solegendary.reignofnether.road;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

import java.util.ArrayList;
import java.util.List;

// A road between two buildings (the network's nodes), identified by their origin positions.
public class RoadConnection {
    public final BlockPos from;
    public final BlockPos to;
    public final String ownerName;
    public final RoadType type;
    public final List<BlockPos> blocks; // road surface blocks placed in the world
    public final int length; // centre line length, used for costs

    public RoadConnection(BlockPos from, BlockPos to, String ownerName, RoadType type, List<BlockPos> blocks, int length) {
        this.from = from;
        this.to = to;
        this.ownerName = ownerName;
        this.type = type;
        this.blocks = blocks;
        this.length = length;
    }

    public RoadConnection withType(RoadType newType) {
        return new RoadConnection(from, to, ownerName, newType, blocks, length);
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
        tag.put("blocks", new LongArrayTag(blocks.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.putInt("length", length);
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
            blocks,
            tag.contains("length") ? tag.getInt("length") : blocks.size() / 3
        );
    }
}
