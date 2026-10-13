package com.solegendary.reignofnether.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class NationSaveData extends SavedData {

    public final Map<String, Nation> nations = new HashMap<>(); // player name -> nation

    private static NationSaveData create() {
        return new NationSaveData();
    }

    @Nonnull
    public static NationSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return create();
        return server.overworld().getDataStorage()
            .computeIfAbsent(NationSaveData::load, NationSaveData::create, "saved-nation-data");
    }

    public static NationSaveData load(CompoundTag tag) {
        NationSaveData data = create();
        for (String player : tag.getAllKeys()) {
            try {
                data.nations.put(player, Nation.valueOf(tag.getString(player)));
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        nations.forEach((player, nation) -> tag.putString(player, nation.name()));
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
