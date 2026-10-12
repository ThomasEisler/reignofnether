package com.solegendary.reignofnether.road;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;

public class RoadSaveData extends SavedData {

    public final ArrayList<RoadConnection> connections = new ArrayList<>();

    private static RoadSaveData create() {
        return new RoadSaveData();
    }

    @Nonnull
    public static RoadSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return create();
        }
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(RoadSaveData::load, RoadSaveData::create, "saved-road-data");
    }

    public static RoadSaveData load(CompoundTag tag) {
        RoadSaveData data = create();
        ListTag ltag = (ListTag) tag.get("connections");
        if (ltag != null) {
            for (Tag ctag : ltag)
                data.connections.add(RoadConnection.load((CompoundTag) ctag));
        }
        ReignOfNether.LOGGER.info("RoadSaveData.load: " + data.connections.size() + " road(s)");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        this.connections.forEach(c -> list.add(c.save()));
        tag.put("connections", list);
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
