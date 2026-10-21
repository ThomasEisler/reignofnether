package com.solegendary.reignofnether.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

// the computer-run civilisations of this world
public class BotSaveData extends SavedData {

    public static class BotData {
        public String name;
        public BlockPos start;
        public boolean capitalPlaced = false;
        public long ticksAlive = 0;
    }

    public final List<BotData> bots = new ArrayList<>();
    public int wanted = 0; // bots the game should have (start menu button)
    public String difficulty = "NORMAL";
    public boolean sunkenRaised = false; // bot buildings sunk by the 21/10/2026 repair were put back up

    @Nonnull
    public static BotSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new BotSaveData();
        return server.overworld().getDataStorage().computeIfAbsent(BotSaveData::load, BotSaveData::new, "saved-bot-data");
    }

    public static BotSaveData load(CompoundTag tag) {
        BotSaveData data = new BotSaveData();
        ListTag list = tag.getList("bots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            BotData bot = new BotData();
            bot.name = b.getString("name");
            bot.start = BlockPos.of(b.getLong("start"));
            bot.capitalPlaced = b.getBoolean("capitalPlaced");
            bot.ticksAlive = b.getLong("ticksAlive");
            data.bots.add(bot);
        }
        data.wanted = tag.getInt("wanted");
        data.sunkenRaised = tag.getBoolean("sunkenRaised");
        if (tag.contains("difficulty"))
            data.difficulty = tag.getString("difficulty");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BotData bot : bots) {
            CompoundTag b = new CompoundTag();
            b.putString("name", bot.name);
            b.putLong("start", bot.start.asLong());
            b.putBoolean("capitalPlaced", bot.capitalPlaced);
            b.putLong("ticksAlive", bot.ticksAlive);
            list.add(b);
        }
        tag.put("bots", list);
        tag.putInt("wanted", wanted);
        tag.putString("difficulty", difficulty);
        tag.putBoolean("sunkenRaised", sunkenRaised);
        return tag;
    }
}
