package com.solegendary.reignofnether.tech;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class TechSaveData extends SavedData {

    public final Map<String, TechServerEvents.PlayerTech> players = new HashMap<>();

    private static TechSaveData create() {
        return new TechSaveData();
    }

    @Nonnull
    public static TechSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return create();
        return server.overworld().getDataStorage()
            .computeIfAbsent(TechSaveData::load, TechSaveData::create, "saved-tech-data");
    }

    public static TechSaveData load(CompoundTag tag) {
        TechSaveData data = create();
        for (String player : tag.getAllKeys())
            data.players.put(player, TechServerEvents.PlayerTech.load(tag.getCompound(player)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        players.forEach((player, state) -> tag.put(player, state.save()));
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
