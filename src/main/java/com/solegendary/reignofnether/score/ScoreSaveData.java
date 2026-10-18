package com.solegendary.reignofnether.score;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

// what can't be seen any more on the map: enemy units killed and enemy buildings destroyed, by civilisation
public class ScoreSaveData extends SavedData {

    public final Map<String, Integer> kills = new HashMap<>();
    public final Map<String, Integer> destroyed = new HashMap<>();
    public final Map<String, Integer> trade = new HashMap<>(); // emeralds earned by trade routes

    @Nonnull
    public static ScoreSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new ScoreSaveData();
        return server.overworld().getDataStorage().computeIfAbsent(ScoreSaveData::load, ScoreSaveData::new, "saved-score-data");
    }

    public static ScoreSaveData load(CompoundTag tag) {
        ScoreSaveData data = new ScoreSaveData();
        CompoundTag k = tag.getCompound("kills");
        for (String n : k.getAllKeys()) data.kills.put(n, k.getInt(n));
        CompoundTag d = tag.getCompound("destroyed");
        for (String n : d.getAllKeys()) data.destroyed.put(n, d.getInt(n));
        CompoundTag t = tag.getCompound("trade");
        for (String n : t.getAllKeys()) data.trade.put(n, t.getInt(n));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag k = new CompoundTag();
        kills.forEach(k::putInt);
        tag.put("kills", k);
        CompoundTag d = new CompoundTag();
        destroyed.forEach(d::putInt);
        tag.put("destroyed", d);
        CompoundTag t = new CompoundTag();
        trade.forEach(t::putInt);
        tag.put("trade", t);
        return tag;
    }
}
