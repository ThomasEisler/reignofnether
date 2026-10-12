package com.solegendary.reignofnether.barbarian;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

public class BarbarianSaveData extends SavedData {

    public boolean enabled = false;
    public long ticksEnabled = 0; // game time the barbarians have been active, makes new camps stronger

    private static BarbarianSaveData create() {
        return new BarbarianSaveData();
    }

    @Nonnull
    public static BarbarianSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return create();
        return server.overworld().getDataStorage()
            .computeIfAbsent(BarbarianSaveData::load, BarbarianSaveData::create, "saved-barbarian-data");
    }

    public static BarbarianSaveData load(CompoundTag tag) {
        BarbarianSaveData data = create();
        data.enabled = tag.getBoolean("enabled");
        data.ticksEnabled = tag.getLong("ticksEnabled");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);
        tag.putLong("ticksEnabled", ticksEnabled);
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
