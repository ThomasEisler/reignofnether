package com.solegendary.reignofnether.diplomacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// wars, alliances (the original mod forgot them on restart) and what each bot thinks of the others
public class DiplomacySaveData extends SavedData {

    public static class Opinion {
        public int value = 0;
        public String reason = ""; // the last thing that changed it the wrong way, given when the bot says no
    }

    public final Map<String, Long> wars = new HashMap<>(); // pair key -> game time the war started
    public final Set<String> alliances = new HashSet<>(); // pair keys
    public final Map<String, Map<String, Opinion>> opinions = new HashMap<>(); // bot -> other -> opinion

    public static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public static String[] split(String key) {
        return key.split("\\|", 2);
    }

    @Nonnull
    public static DiplomacySaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return new DiplomacySaveData();
        return server.overworld().getDataStorage().computeIfAbsent(DiplomacySaveData::load, DiplomacySaveData::new, "saved-diplomacy-data");
    }

    public static DiplomacySaveData load(CompoundTag tag) {
        DiplomacySaveData data = new DiplomacySaveData();
        ListTag wars = tag.getList("wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < wars.size(); i++)
            data.wars.put(wars.getCompound(i).getString("pair"), wars.getCompound(i).getLong("since"));
        ListTag alliances = tag.getList("alliances", Tag.TAG_STRING);
        for (int i = 0; i < alliances.size(); i++)
            data.alliances.add(alliances.getString(i));
        ListTag opinions = tag.getList("opinions", Tag.TAG_COMPOUND);
        for (int i = 0; i < opinions.size(); i++) {
            CompoundTag o = opinions.getCompound(i);
            Opinion op = new Opinion();
            op.value = o.getInt("value");
            op.reason = o.getString("reason");
            data.opinions.computeIfAbsent(o.getString("bot"), k -> new HashMap<>()).put(o.getString("other"), op);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag wars = new ListTag();
        this.wars.forEach((pair, since) -> {
            CompoundTag w = new CompoundTag();
            w.putString("pair", pair);
            w.putLong("since", since);
            wars.add(w);
        });
        tag.put("wars", wars);
        ListTag alliances = new ListTag();
        for (String pair : this.alliances)
            alliances.add(net.minecraft.nbt.StringTag.valueOf(pair));
        tag.put("alliances", alliances);
        ListTag opinions = new ListTag();
        this.opinions.forEach((bot, map) -> map.forEach((other, op) -> {
            CompoundTag o = new CompoundTag();
            o.putString("bot", bot);
            o.putString("other", other);
            o.putInt("value", op.value);
            o.putString("reason", op.reason);
            opinions.add(o);
        }));
        tag.put("opinions", opinions);
        return tag;
    }
}
