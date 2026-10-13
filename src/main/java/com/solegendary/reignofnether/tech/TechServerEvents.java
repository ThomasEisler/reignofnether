package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Each player's technologies: what they have, and the one being researched (one at a time). Research only
// progresses while the player owns a finished Library. Saved with the world, cleared by /rts-reset.
public class TechServerEvents {

    public static class PlayerTech {
        public final Set<Tech> done = EnumSet.noneOf(Tech.class);
        @Nullable public Tech current = null;
        public int ticksLeft = 0;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            StringBuilder sb = new StringBuilder();
            for (Tech t : done)
                sb.append(t.name()).append(',');
            tag.putString("done", sb.toString());
            if (current != null)
                tag.putString("current", current.name());
            tag.putInt("ticksLeft", ticksLeft);
            return tag;
        }

        static PlayerTech load(CompoundTag tag) {
            PlayerTech state = new PlayerTech();
            for (String name : tag.getString("done").split(","))
                if (!name.isBlank())
                    try { state.done.add(Tech.valueOf(name)); } catch (IllegalArgumentException ignored) { }
            if (tag.contains("current"))
                try { state.current = Tech.valueOf(tag.getString("current")); } catch (IllegalArgumentException ignored) { }
            state.ticksLeft = tag.getInt("ticksLeft");
            return state;
        }
    }

    private static final int TICK_INTERVAL = 20;
    private static final Map<String, PlayerTech> players = new HashMap<>();
    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    private static PlayerTech get(String player) {
        return players.computeIfAbsent(player, p -> new PlayerTech());
    }

    public static boolean hasTech(String player, Tech tech) {
        PlayerTech state = players.get(player);
        return state != null && state.done.contains(tech);
    }

    public static boolean hasLibrary(String player) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.ownerName.equals(player) && b.isBuilt && b.getBuilding() == Buildings.LIBRARY)
                return true;
        return false;
    }

    public static void startResearch(String player, Tech tech) {
        PlayerTech state = get(player);
        String error = null;
        if (state.done.contains(tech))
            error = "Technologie déjà connue";
        else if (state.current != null)
            error = "Une recherche est déjà en cours";
        else if (!hasLibrary(player))
            error = "Il faut une Bibliothèque terminée pour faire des recherches";
        else
            for (Tech pre : tech.getPrerequisites())
                if (!state.done.contains(pre))
                    error = "Il faut d'abord rechercher : " + pre.displayName;
        if (error != null) {
            HudClientboundPacket.showTempMessageI18n(player, error);
            return;
        }
        boolean food = ResourcesServerEvents.canAfford(player, ResourceName.FOOD, tech.food);
        boolean wood = ResourcesServerEvents.canAfford(player, ResourceName.WOOD, tech.wood);
        boolean ore = ResourcesServerEvents.canAfford(player, ResourceName.ORE, tech.ore);
        if (!food || !wood || !ore) {
            ResourcesClientboundPacket.warnInsufficientResources(player, food, wood, ore, true);
            return;
        }
        ResourcesServerEvents.addSubtractResources(new Resources(player, -tech.food, -tech.wood, -tech.ore));
        state.current = tech;
        state.ticksLeft = tech.seconds * 20;
        save();
        TechClientboundPacket.sync(player, state);
    }

    // the original game's researches a technology gives: their effects (and the game's checks) then apply
    private static void grantResearch(String player, Tech tech) {
        for (String id : tech.grantedResearch) {
            var item = Tech.getProductionItem(id);
            if (item != null && !ResearchServerEvents.playerHasResearch(player, item))
                ResearchServerEvents.addResearch(player, item);
        }
    }

    // cancelling refunds the full cost
    public static void cancelResearch(String player) {
        PlayerTech state = get(player);
        if (state.current == null)
            return;
        Tech tech = state.current;
        ResourcesServerEvents.addSubtractResources(new Resources(player, tech.food, tech.wood, tech.ore));
        state.current = null;
        state.ticksLeft = 0;
        save();
        TechClientboundPacket.sync(player, state);
    }

    public static void clearAll() {
        players.clear();
        save();
        for (ServerPlayer p : serverLevel != null ? serverLevel.getServer().getPlayerList().getPlayers() : java.util.List.<ServerPlayer>of())
            TechClientboundPacket.sync(p.getName().getString(), new PlayerTech());
    }

    private static void save() {
        if (serverLevel == null)
            return;
        TechSaveData data = TechSaveData.getInstance(serverLevel);
        data.players.clear();
        data.players.putAll(players);
        data.save();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || ++ticks < TICK_INTERVAL)
            return;
        ticks = 0;
        for (Map.Entry<String, PlayerTech> entry : players.entrySet()) {
            String player = entry.getKey();
            PlayerTech state = entry.getValue();
            if (state.current == null || !hasLibrary(player))
                continue; // paused without a library
            state.ticksLeft -= ResearchServerEvents.playerHasCheat(player, "warpten") ? TICK_INTERVAL * 10 : TICK_INTERVAL;
            if (state.ticksLeft <= 0) {
                Tech tech = state.current;
                state.done.add(tech);
                grantResearch(player, tech);
                state.current = null;
                state.ticksLeft = 0;
                HudClientboundPacket.showTempMessageI18n(player, "Recherche terminée : " + tech.displayName);
                PlayerServerEvents.sendMessageToAllPlayers(player + " a découvert : " + tech.displayName);
                save();
            }
            TechClientboundPacket.sync(player, state);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        players.clear();
        if (serverLevel != null)
            players.putAll(TechSaveData.getInstance(serverLevel).players);
        players.forEach((player, state) -> state.done.forEach(tech -> grantResearch(player, tech)));
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer player)
            TechClientboundPacket.sync(player.getName().getString(), get(player.getName().getString()));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        save();
        serverLevel = null;
    }
}
