package com.solegendary.reignofnether.barbarian;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class BarbarianSaveData extends SavedData {

    public boolean enabled = true; // on by default, /rts-barbarians off to disable
    public BarbarianDifficulty difficulty = BarbarianDifficulty.NORMAL;
    public long ticksEnabled = 0; // game time the barbarians have been active, makes new camps stronger
    // player -> ticksEnabled value when their first barracks was seen finished (removed if they lose all of them)
    public final Map<String, Long> barracksSince = new HashMap<>();

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
        data.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        data.ticksEnabled = tag.getLong("ticksEnabled");
        if (tag.contains("difficulty"))
            try { data.difficulty = BarbarianDifficulty.valueOf(tag.getString("difficulty")); } catch (IllegalArgumentException ignored) { }
        CompoundTag barracks = tag.getCompound("barracksSince");
        for (String player : barracks.getAllKeys())
            data.barracksSince.put(player, barracks.getLong(player));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);
        tag.putLong("ticksEnabled", ticksEnabled);
        tag.putString("difficulty", difficulty.name());
        CompoundTag barracks = new CompoundTag();
        barracksSince.forEach(barracks::putLong);
        tag.put("barracksSince", barracks);
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
