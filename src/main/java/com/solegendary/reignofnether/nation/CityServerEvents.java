package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

// names each new Town Centre after the next city of its owner's nation; forgets the names of fallen cities
public class CityServerEvents {

    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    public static void onCityPlaced(BuildingPlacement placement) {
        if (serverLevel == null || placement.ownerName.isBlank())
            return;
        CitySaveData data = CitySaveData.getInstance(serverLevel);
        if (data.names.containsKey(placement.originPos.asLong()))
            return;
        int index = data.founded.getOrDefault(placement.ownerName, 0);
        data.founded.put(placement.ownerName, index + 1);
        data.names.put(placement.originPos.asLong(), Cities.getName(NationServerEvents.getNation(placement.ownerName), index));
        data.setDirty();
        CityNamesClientboundPacket.sync(data.names);
    }

    public static void clear() {
        if (serverLevel == null)
            return;
        CitySaveData data = CitySaveData.getInstance(serverLevel);
        data.founded.clear();
        data.setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || ++ticks < 100)
            return;
        ticks = 0;
        CitySaveData data = CitySaveData.getInstance(serverLevel);
        Set<Long> standing = new HashSet<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (Cities.isCity(b.getBuilding()))
                standing.add(b.originPos.asLong());
        if (data.names.keySet().removeIf(pos -> !standing.contains(pos))) {
            data.setDirty();
            CityNamesClientboundPacket.sync(data.names);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (serverLevel != null && evt.getEntity() instanceof ServerPlayer player)
            CityNamesClientboundPacket.sync(player, CitySaveData.getInstance(serverLevel).names);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }
}
