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
        public int regrowLeft = MapFeaturesServerEvents.HERD_REGROW_TOTAL; // animals that can still grow back
        public final Set<UUID> members = new HashSet<>();
    }

    // a quarry: its ore blocks (position -> block id), which grow back slowly once mined
    public static class Quarry {
        public BlockPos centre;
        public final List<Long> orePositions = new ArrayList<>();
        public final List<String> oreBlocks = new ArrayList<>();
        public int regrowLeft = MapFeaturesServerEvents.QUARRY_REGROW_TOTAL; // ore blocks that can still grow back
    }

    // ruins: plain blocks until a player's unit comes close, then a building (owner "Ruins") to destroy for its treasure
    public static class RuinSite {
        public BlockPos origin;
        public String building; // building id, eg. reignofnether:ruin_1
        public boolean converted = false;
    }

    public final List<Quarry> quarries = new ArrayList<>();
    public final List<RuinSite> ruins = new ArrayList<>();
    public final Set<Long> scannedChunks = new HashSet<>();
    public final List<BlockPos> fishingSpots = new ArrayList<>();
    public final java.util.Map<Long, Integer> fishingCatchesLeft = new java.util.HashMap<>(); // spot -> catches left
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
        CompoundTag catches = tag.getCompound("fishingCatchesLeft");
        for (String key : catches.getAllKeys())
            data.fishingCatchesLeft.put(Long.parseLong(key), catches.getInt(key));
        data.nextHerdId = Math.max(1, tag.getInt("nextHerdId"));
        ListTag quarries = tag.getList("quarries", Tag.TAG_COMPOUND);
        for (int i = 0; i < quarries.size(); i++) {
            CompoundTag q = quarries.getCompound(i);
            Quarry quarry = new Quarry();
            quarry.centre = BlockPos.of(q.getLong("centre"));
            if (q.contains("regrowLeft"))
                quarry.regrowLeft = q.getInt("regrowLeft");
            for (long l : q.getLongArray("orePositions"))
                quarry.orePositions.add(l);
            ListTag blocks = q.getList("oreBlocks", Tag.TAG_STRING);
            for (int j = 0; j < blocks.size(); j++)
                quarry.oreBlocks.add(blocks.getString(j));
            if (quarry.oreBlocks.size() == quarry.orePositions.size())
                data.quarries.add(quarry);
        }
        ListTag ruins = tag.getList("ruins", Tag.TAG_COMPOUND);
        for (int i = 0; i < ruins.size(); i++) {
            CompoundTag r = ruins.getCompound(i);
            RuinSite ruin = new RuinSite();
            ruin.origin = BlockPos.of(r.getLong("origin"));
            ruin.building = r.getString("building");
            ruin.converted = r.getBoolean("converted");
            data.ruins.add(ruin);
        }
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
            if (h.contains("regrowLeft"))
                herd.regrowLeft = h.getInt("regrowLeft");
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
        CompoundTag catches = new CompoundTag();
        fishingCatchesLeft.forEach((spot, left) -> catches.putInt(Long.toString(spot), left));
        tag.put("fishingCatchesLeft", catches);
        tag.putInt("nextHerdId", nextHerdId);
        ListTag quarryList = new ListTag();
        for (Quarry quarry : quarries) {
            CompoundTag q = new CompoundTag();
            q.putLong("centre", quarry.centre.asLong());
            q.putInt("regrowLeft", quarry.regrowLeft);
            q.put("orePositions", new LongArrayTag(quarry.orePositions));
            ListTag blocks = new ListTag();
            for (String b : quarry.oreBlocks)
                blocks.add(net.minecraft.nbt.StringTag.valueOf(b));
            q.put("oreBlocks", blocks);
            quarryList.add(q);
        }
        tag.put("quarries", quarryList);
        ListTag ruinList = new ListTag();
        for (RuinSite ruin : ruins) {
            CompoundTag r = new CompoundTag();
            r.putLong("origin", ruin.origin.asLong());
            r.putString("building", ruin.building);
            r.putBoolean("converted", ruin.converted);
            ruinList.add(r);
        }
        tag.put("ruins", ruinList);
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
            h.putInt("regrowLeft", herd.regrowLeft);
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
