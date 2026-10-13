package com.solegendary.reignofnether.mapfeatures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Things found on the map when chunks are first looked at: fishing spots and wild herds. Saved with the world.
public class MapFeaturesSaveData extends SavedData {

    public static class Herd {
        public int id;
        public String species; // entity type id, eg. minecraft:cow
        public BlockPos home;
        public int step = 0; // which point of its migration circle it heads to
        public int radius;
        public int targetSize;
        public long ticksToRegrow = 0;
        public final Set<UUID> members = new HashSet<>();
    }

    public final Set<Long> scannedChunks = new HashSet<>();
    public final List<BlockPos> fishingSpots = new ArrayList<>();
    public final List<Herd> herds = new ArrayList<>();
    public int nextHerdId = 1;

    private static MapFeaturesSaveData create() {
        return new MapFeaturesSaveData();
    }

    @Nonnull
    public static MapFeaturesSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return create();
        return server.overworld().getDataStorage()
            .computeIfAbsent(MapFeaturesSaveData::load, MapFeaturesSaveData::create, "saved-map-features-data");
    }

    public static MapFeaturesSaveData load(CompoundTag tag) {
        MapFeaturesSaveData data = create();
        for (long l : tag.getLongArray("scannedChunks"))
            data.scannedChunks.add(l);
        for (long l : tag.getLongArray("fishingSpots"))
            data.fishingSpots.add(BlockPos.of(l));
        data.nextHerdId = Math.max(1, tag.getInt("nextHerdId"));
        ListTag herds = tag.getList("herds", Tag.TAG_COMPOUND);
        for (int i = 0; i < herds.size(); i++) {
            CompoundTag h = herds.getCompound(i);
            Herd herd = new Herd();
            herd.id = h.getInt("id");
            herd.species = h.getString("species");
            herd.home = BlockPos.of(h.getLong("home"));
            herd.step = h.getInt("step");
            herd.radius = h.getInt("radius");
            herd.targetSize = h.getInt("targetSize");
            herd.ticksToRegrow = h.getLong("ticksToRegrow");
            ListTag members = h.getList("members", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < members.size(); j++)
                herd.members.add(net.minecraft.nbt.NbtUtils.loadUUID(members.get(j)));
            data.herds.add(herd);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("scannedChunks", new LongArrayTag(new ArrayList<>(scannedChunks)));
        tag.put("fishingSpots", new LongArrayTag(fishingSpots.stream().map(BlockPos::asLong).toList()));
        tag.putInt("nextHerdId", nextHerdId);
        ListTag list = new ListTag();
        for (Herd herd : herds) {
            CompoundTag h = new CompoundTag();
            h.putInt("id", herd.id);
            h.putString("species", herd.species);
            h.putLong("home", herd.home.asLong());
            h.putInt("step", herd.step);
            h.putInt("radius", herd.radius);
            h.putInt("targetSize", herd.targetSize);
            h.putLong("ticksToRegrow", herd.ticksToRegrow);
            ListTag members = new ListTag();
            for (UUID uuid : herd.members)
                members.add(net.minecraft.nbt.NbtUtils.createUUID(uuid));
            h.put("members", members);
            list.add(h);
        }
        tag.put("herds", list);
        return tag;
    }
}
