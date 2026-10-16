package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Step 5: how many bots a game has and how strong they are (bots button in the start menu, or /rts-bot nombre and
// /rts-bot difficulte), saved with the world. Missing bots arrive one by one once a player has a Town Centre, so
// they settle away from the players; raising the number mid-game adds bots, lowering it removes none.
public class BotSettings {

    public static final int MAX_BOTS = 3;
    private static final int CHECK_INTERVAL = 100; // 5 s between two arrivals
    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    private static BotSaveData data() {
        return BotSaveData.getInstance(serverLevel);
    }

    public static BotDifficulty difficulty() {
        if (serverLevel == null)
            return BotDifficulty.NORMAL;
        BotDifficulty d = BotDifficulty.fromFr(data().difficulty);
        return d != null ? d : BotDifficulty.NORMAL;
    }

    public static int wanted() {
        return serverLevel == null ? 0 : data().wanted;
    }

    public static void set(int wanted, BotDifficulty difficulty) {
        if (serverLevel == null)
            return;
        data().wanted = Math.max(0, Math.min(MAX_BOTS, wanted));
        data().difficulty = difficulty.name();
        data().setDirty();
        BotClientboundPacket.sync(data().wanted, difficulty);
    }

    // a player with a Town Centre: the game has started and bots can settle away from it
    private static boolean gameStarted() {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding().isCapitol && PlayerServerEvents.isRTSPlayer(b.ownerName) && !BotServerEvents.isBot(b.ownerName))
                return true;
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || ++ticks < CHECK_INTERVAL)
            return;
        ticks = 0;
        if (data().bots.size() >= data().wanted || !gameStarted())
            return;
        String msg = BotServerEvents.addBot();
        if (data().bots.isEmpty())
            return;
        BotSaveData.BotData bot = data().bots.get(data().bots.size() - 1);
        if (!msg.startsWith(bot.name))
            return; // no room found: tries again later
        int bonus = difficulty().startBonus;
        if (bonus > 0)
            ResourcesServerEvents.addSubtractResources(new Resources(bot.name, bonus, bonus, bonus));
        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers())
            p.sendSystemMessage(Component.literal("Une nouvelle civilisation arrive : " + bot.name));
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (serverLevel != null && evt.getEntity() instanceof ServerPlayer p)
            BotClientboundPacket.sync(p, data().wanted, difficulty());
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
