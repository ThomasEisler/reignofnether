package com.solegendary.reignofnether.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

// city names (Town Centre origin -> name) and how many cities each player has founded (names aren't reused)
public class CitySaveData extends SavedData {

    public final Map<Long, String> names = new HashMap<>();
    public final Map<String, Integer> founded = new HashMap<>();

    @Nonnull
    public static CitySaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new CitySaveData();
        return server.overworld().getDataStorage().computeIfAbsent(CitySaveData::load, CitySaveData::new, "saved-city-data");
    }

    public static CitySaveData load(CompoundTag tag) {
        CitySaveData data = new CitySaveData();
        CompoundTag names = tag.getCompound("names");
        for (String key : names.getAllKeys())
            data.names.put(Long.parseLong(key), names.getString(key));
        CompoundTag founded = tag.getCompound("founded");
        for (String key : founded.getAllKeys())
            data.founded.put(key, founded.getInt(key));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag names = new CompoundTag();
        this.names.forEach((pos, name) -> names.putString(Long.toString(pos), name));
        tag.put("names", names);
        CompoundTag founded = new CompoundTag();
        this.founded.forEach(founded::putInt);
        tag.put("founded", founded);
        return tag;
    }
}
