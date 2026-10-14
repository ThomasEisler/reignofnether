package com.solegendary.reignofnether.exploration;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

// chunks each player has explored (seen at least once), plus the on/off switch of the clouds
public class ExplorationSaveData extends SavedData {

    public final Map<String, LongOpenHashSet> explored = new HashMap<>();
    public boolean enabled = true;

    @Nonnull
    public static ExplorationSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new ExplorationSaveData();
        return server.overworld().getDataStorage()
            .computeIfAbsent(ExplorationSaveData::load, ExplorationSaveData::new, "saved-exploration-data");
    }

    public static ExplorationSaveData load(CompoundTag tag) {
        ExplorationSaveData data = new ExplorationSaveData();
        data.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        CompoundTag players = tag.getCompound("players");
        for (String name : players.getAllKeys())
            data.explored.put(name, new LongOpenHashSet(players.getLongArray(name)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);
        CompoundTag players = new CompoundTag();
        explored.forEach((name, chunks) -> players.put(name, new LongArrayTag(chunks.toLongArray())));
        tag.put("players", players);
        return tag;
    }
}
