package com.solegendary.reignofnether.rampart;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RampartSaveData extends SavedData {

    public final Map<Long, List<RampartShapes.Column>> shapes = new HashMap<>();

    private static RampartSaveData create() {
        return new RampartSaveData();
    }

    @Nonnull
    public static RampartSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return create();
        return server.overworld().getDataStorage()
            .computeIfAbsent(RampartSaveData::load, RampartSaveData::create, "saved-rampart-data");
    }

    public static RampartSaveData load(CompoundTag tag) {
        RampartSaveData data = create();
        for (String key : tag.getAllKeys()) {
            int[] values = tag.getIntArray(key);
            List<RampartShapes.Column> columns = new ArrayList<>();
            for (int i = 0; i + 3 < values.length; i += 4)
                columns.add(new RampartShapes.Column(values[i], values[i + 1], values[i + 2], values[i + 3]));
            try {
                data.shapes.put(Long.parseLong(key), columns);
            } catch (NumberFormatException ignored) { }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        shapes.forEach((origin, columns) -> {
            int[] values = new int[columns.size() * 4];
            for (int i = 0; i < columns.size(); i++) {
                RampartShapes.Column c = columns.get(i);
                values[i * 4] = c.x();
                values[i * 4 + 1] = c.y();
                values[i * 4 + 2] = c.z();
                values[i * 4 + 3] = c.height();
            }
            tag.putIntArray(Long.toString(origin), values);
        });
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
