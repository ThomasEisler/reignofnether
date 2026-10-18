package com.solegendary.reignofnether.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

// the trade routes opened in markets
public class TradeSaveData extends SavedData {

    public final List<TradeRoute> routes = new ArrayList<>();

    @Nonnull
    public static TradeSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new TradeSaveData();
        return server.overworld().getDataStorage().computeIfAbsent(TradeSaveData::load, TradeSaveData::new, "saved-trade-data");
    }

    public static TradeSaveData load(CompoundTag tag) {
        TradeSaveData data = new TradeSaveData();
        ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
            data.routes.add(TradeRoute.load(list.getCompound(i)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (TradeRoute r : routes)
            list.add(r.save());
        tag.put("routes", list);
        return tag;
    }
}
