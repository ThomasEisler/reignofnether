package com.solegendary.reignofnether.exploration;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

// Exploration: every chunk a player's units or buildings have seen (within their sight range) is explored for good.
// The rest of the map stays under clouds for that player (ExplorationClientEvents). Only players' own things
// explore; bots and barbarians don't need it. Explorers (ExplorerUnit) see much further than other units.
public class ExplorationServerEvents {

    private static final int UPDATE_TICKS = 20;

    private static ServerLevel serverLevel = null;
    private static ExplorationSaveData data = null;
    private static int ticks = 0;

    // true when the owner may act there: explored, or exploration not running for them (clouds off, bots)
    public static boolean isExploredBy(String owner, int blockX, int blockZ) {
        if (data == null || !data.enabled)
            return true;
        LongOpenHashSet set = data.explored.get(owner);
        return set == null || set.isEmpty() || set.contains(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
    }

    public static boolean isEnabled() {
        return data == null || data.enabled;
    }

    public static void setEnabled(boolean enabled) {
        if (data == null)
            return;
        data.enabled = enabled;
        data.setDirty();
        syncAll();
    }

    // the fog of war is switched on its own (/rts-brouillard on|off), saved with the world
    public static boolean isFogWanted() {
        return data == null || data.fogEnabled;
    }

    public static void setFogWanted(boolean value) {
        if (data == null)
            return;
        data.fogEnabled = value;
        data.setDirty();
        com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.setEnabled(value);
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent evt) {
        evt.getDispatcher().register(net.minecraft.commands.Commands.literal("rts-brouillard")
            .requires(source -> source.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("on").executes(ctx -> {
                setFogWanted(true);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Brouillard de guerre activé"), true);
                return 1;
            }))
            .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> {
                setFogWanted(false);
                ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Brouillard de guerre désactivé"), true);
                return 1;
            })));
    }

    // everyone's map goes back under the clouds
    public static void reset() {
        if (data == null)
            return;
        data.explored.clear();
        data.setDirty();
        syncAll();
    }

    private static void syncAll() {
        if (serverLevel == null)
            return;
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers())
            sync(player);
    }

    private static void sync(ServerPlayer player) {
        LongOpenHashSet chunks = data.explored.get(player.getName().getString());
        ExplorationClientboundPacket.send(player, data.enabled, true, chunks == null ? new long[0] : chunks.toLongArray());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        data = serverLevel != null ? ExplorationSaveData.getInstance(serverLevel) : null;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
        data = null;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (data != null && evt.getEntity() instanceof ServerPlayer player)
            sync(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || data == null || !data.enabled)
            return;
        if (++ticks < UPDATE_TICKS)
            return;
        ticks = 0;

        Map<String, ServerPlayer> online = new HashMap<>();
        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers())
            online.put(p.getName().getString(), p);
        if (online.isEmpty())
            return;

        Map<String, LongArrayList> added = new HashMap<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Unit unit && online.containsKey(unit.getOwnerName()) && le.level() == serverLevel)
                explore(unit.getOwnerName(), le.getBlockX(), le.getBlockZ(), unit.getSightRange(), added);
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (online.containsKey(b.ownerName))
                explore(b.ownerName, b.centrePos.getX(), b.centrePos.getZ(), Math.max(b.getSightRange(), 24), added);

        if (added.isEmpty())
            return;
        data.setDirty();
        added.forEach((name, chunks) ->
            ExplorationClientboundPacket.send(online.get(name), true, false, chunks.toLongArray()));
    }

    // marks every chunk touched by the circle of radius r around (x, z)
    private static void explore(String owner, int x, int z, int r, Map<String, LongArrayList> added) {
        LongOpenHashSet set = data.explored.computeIfAbsent(owner, k -> new LongOpenHashSet());
        int cx0 = (x - r) >> 4, cx1 = (x + r) >> 4, cz0 = (z - r) >> 4, cz1 = (z + r) >> 4;
        long r2 = (long) r * r;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                // distance from (x, z) to the closest point of the chunk
                int nx = Math.max(cx << 4, Math.min(x, (cx << 4) + 15));
                int nz = Math.max(cz << 4, Math.min(z, (cz << 4) + 15));
                long dx = nx - x, dz = nz - z;
                if (dx * dx + dz * dz > r2)
                    continue;
                long key = ChunkPos.asLong(cx, cz);
                if (set.add(key))
                    added.computeIfAbsent(owner, k -> new LongArrayList()).add(key);
            }
        }
    }
}
