package com.solegendary.reignofnether.naval;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

// Ships: Small Ships' ships (a separate mod, optional) steered by one of our units at the helm, its captain.
// Selecting the captain selects the ship; a right click sends it straight there (no path on water).
// Everything that touches Small Ships' classes is in ShipPilot, only ever called when that mod is loaded.
public class NavalServerEvents {

    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("smallships");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        if (isLoaded())
            ShipPilot.allowCaptains();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || !isLoaded() || ++ticks < ShipPilot.TICK_INTERVAL)
            return;
        ticks = 0;
        ShipPilot.tick(serverLevel);
    }

    // /rts-navire: a ship of that kind on the water at that spot, with a captain belonging to ownerName
    public static String spawnShip(ServerLevel level, String type, BlockPos at, String ownerName) {
        if (!isLoaded())
            return "Le mod Small Ships n'est pas installé";
        return ShipPilot.spawnShip(level, type, at, ownerName);
    }
}
