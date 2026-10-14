package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

// which player plays which nation; one nation per player, saved with the world, cleared by /rts-reset
public class NationServerEvents {

    private static final Map<String, Nation> nations = new HashMap<>();
    private static ServerLevel serverLevel = null;

    @Nullable
    public static Nation getNation(String playerName) {
        return nations.get(playerName);
    }

    public static void chooseNation(String playerName, Nation nation) {
        for (Map.Entry<String, Nation> entry : nations.entrySet()) {
            if (entry.getValue() == nation && !entry.getKey().equals(playerName)) {
                NationClientboundPacket.sync(nations); // the client's list was out of date
                return;
            }
        }
        nations.put(playerName, nation);
        save();
        NationClientboundPacket.sync(nations);
        PlayerServerEvents.sendMessageToAllPlayers(playerName + " joue " + nation.displayName + ", dirigée par " + nation.leader);
    }

    public static void clearNations() {
        nations.clear();
        save();
        NationClientboundPacket.sync(nations);
        CityServerEvents.clear(); // a new game: city names start again from the capital
    }

    private static void save() {
        if (serverLevel == null)
            return;
        NationSaveData data = NationSaveData.getInstance(serverLevel);
        data.nations.clear();
        data.nations.putAll(nations);
        data.save();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        nations.clear();
        if (serverLevel != null)
            nations.putAll(NationSaveData.getInstance(serverLevel).nations);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer player)
            NationClientboundPacket.sync(player, nations);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        save();
        serverLevel = null;
    }
}
