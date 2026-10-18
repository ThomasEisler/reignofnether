package com.solegendary.reignofnether.religion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

// faith of each civilisation, who founded which religion, second beliefs, and each city's followers per religion
public class ReligionSaveData extends SavedData {

    public final Map<String, Double> faith = new HashMap<>();
    public final Map<Religion, String> founders = new EnumMap<>(Religion.class);
    public final Map<Religion, Belief> secondBeliefs = new EnumMap<>(Religion.class);
    public final Map<Long, Map<Religion, Float>> cities = new HashMap<>(); // city origin -> religion -> 0..100

    @Nonnull
    public static ReligionSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new ReligionSaveData();
        return server.overworld().getDataStorage().computeIfAbsent(ReligionSaveData::load, ReligionSaveData::new, "saved-religion-data");
    }

    public static ReligionSaveData load(CompoundTag tag) {
        ReligionSaveData data = new ReligionSaveData();
        CompoundTag faith = tag.getCompound("faith");
        for (String k : faith.getAllKeys())
            data.faith.put(k, faith.getDouble(k));
        CompoundTag founders = tag.getCompound("founders");
        for (String k : founders.getAllKeys())
            try { data.founders.put(Religion.valueOf(k), founders.getString(k)); } catch (IllegalArgumentException ignored) { }
        CompoundTag beliefs = tag.getCompound("beliefs");
        for (String k : beliefs.getAllKeys())
            try { data.secondBeliefs.put(Religion.valueOf(k), Belief.valueOf(beliefs.getString(k))); } catch (IllegalArgumentException ignored) { }
        CompoundTag cities = tag.getCompound("cities");
        for (String k : cities.getAllKeys()) {
            CompoundTag c = cities.getCompound(k);
            Map<Religion, Float> gauges = new EnumMap<>(Religion.class);
            for (String r : c.getAllKeys())
                try { gauges.put(Religion.valueOf(r), c.getFloat(r)); } catch (IllegalArgumentException ignored) { }
            try { data.cities.put(Long.parseLong(k), gauges); } catch (NumberFormatException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag faith = new CompoundTag();
        this.faith.forEach(faith::putDouble);
        tag.put("faith", faith);
        CompoundTag founders = new CompoundTag();
        this.founders.forEach((r, f) -> founders.putString(r.name(), f));
        tag.put("founders", founders);
        CompoundTag beliefs = new CompoundTag();
        this.secondBeliefs.forEach((r, b) -> beliefs.putString(r.name(), b.name()));
        tag.put("beliefs", beliefs);
        CompoundTag cities = new CompoundTag();
        this.cities.forEach((origin, gauges) -> {
            CompoundTag c = new CompoundTag();
            gauges.forEach((r, v) -> c.putFloat(r.name(), v));
            cities.put(Long.toString(origin), c);
        });
        tag.put("cities", cities);
        return tag;
    }
}
