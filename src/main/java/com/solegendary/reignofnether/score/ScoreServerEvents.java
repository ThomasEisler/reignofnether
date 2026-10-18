package com.solegendary.reignofnether.score;

import com.solegendary.reignofnether.barbarian.BarbarianServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.rampart.RampartBuilding;
import com.solegendary.reignofnether.religion.Religion;
import com.solegendary.reignofnether.religion.Religions;
import com.solegendary.reignofnether.tech.Tech;
import com.solegendary.reignofnether.tech.TechServerEvents;
import com.solegendary.reignofnether.territory.Territory;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

// Ranking instead of a victory: points in every domain, worked out every 10 s, shown holding Tab (ScoreClientEvents)
// and with /rts-classement. /rts-fin ends the game when the players decide: final ranking and the winner for all.
//   territory: 10 per city, 1 per 50 cells (2 x 2 blocks) of land
//   population: 1 per villager, 2 per other unit
//   technologies: 10 per technology
//   military: 2 per enemy unit killed (barbarians too), 5 per enemy building destroyed
//   religion: 20 for founding one, 5 per city following it (anywhere)
//   economy: 2 per finished building (not walls or bridges), 1 per 200 resources in stock, 1 per 20 emeralds of trade
public class ScoreServerEvents {

    private static ServerLevel serverLevel = null;
    private static List<Score> scores = new ArrayList<>();

    private static ScoreSaveData data() {
        return ScoreSaveData.getInstance(serverLevel);
    }

    public static List<Score> getScores() {
        return scores;
    }

    private static boolean isCiv(String name) {
        return name != null && !name.isBlank() && PlayerServerEvents.isRTSPlayer(name);
    }

    // ---- what is counted as it happens ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (serverLevel == null || evt.getEntity().level().isClientSide() || !(evt.getEntity() instanceof Unit victim) ||
            !(evt.getSource().getEntity() instanceof Unit killer))
            return;
        String k = killer.getOwnerName(), v = victim.getOwnerName();
        if (!isCiv(k) || k.equals(v) || !(isCiv(v) || BarbarianServerEvents.OWNER_NAME.equals(v)))
            return;
        data().kills.merge(k, 1, Integer::sum);
        data().setDirty();
    }

    // emeralds earned by trade routes (trade/TradeServerEvents)
    public static void onTrade(String civ, int emeralds) {
        if (serverLevel == null)
            return;
        data().trade.merge(civ, emeralds, Integer::sum);
        data().setDirty();
    }

    // called by BuildingPlacement.destroy
    public static void onBuildingDestroyed(BuildingPlacement building) {
        if (serverLevel == null || !(building.lastAttacker instanceof Unit attacker))
            return;
        String k = attacker.getOwnerName(), v = building.ownerName;
        if (!isCiv(k) || k.equals(v) || !(isCiv(v) || BarbarianServerEvents.OWNER_NAME.equals(v)) ||
            building.getBuilding() instanceof RampartBuilding)
            return;
        data().destroyed.merge(k, 1, Integer::sum);
        data().setDirty();
    }

    // ---- working out the points -------------------------------------------------------------------------------------

    private static void update() {
        List<BuildingPlacement> buildings = new ArrayList<>(BuildingServerEvents.getBuildings());
        Map<String, Integer> cells = new HashMap<>();
        Territory.grid(buildings, ScoreServerEvents::isCiv).values().forEach(o -> cells.merge(o, 1, Integer::sum));
        List<Score> list = new ArrayList<>();
        for (RTSPlayer p : PlayerServerEvents.rtsPlayers) {
            String civ = p.name;
            int cities = 0, built = 0;
            for (BuildingPlacement b : buildings) {
                if (!b.ownerName.equals(civ))
                    continue;
                if (b.getBuilding().isCapitol) cities++;
                if (b.isBuilt && !(b.getBuilding() instanceof RampartBuilding) && !(b.getBuilding() instanceof AbstractBridge)) built++;
            }
            int villagers = 0, others = 0;
            for (LivingEntity le : UnitServerEvents.getAllUnits())
                if (le.isAlive() && civ.equals(((Unit) le).getOwnerName())) {
                    if (le instanceof WorkerUnit) villagers++; else others++;
                }
            int techs = 0;
            for (Tech t : Tech.values())
                if (TechServerEvents.hasTech(civ, t)) techs++;
            Religion r = Religions.foundedBy(false, civ);
            int religion = r == null ? 0 : 20 + 5 * Religions.followerCities(false, r);
            int stock = 0;
            for (var res : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
                if (res.ownerName.equals(civ)) stock = res.food + res.wood + res.ore;
            list.add(new Score(civ,
                10 * cities + cells.getOrDefault(civ, 0) / 50,
                villagers + 2 * others,
                10 * techs,
                2 * data().kills.getOrDefault(civ, 0) + 5 * data().destroyed.getOrDefault(civ, 0),
                religion,
                2 * built + stock / 200 + data().trade.getOrDefault(civ, 0) / 20));
        }
        list.sort(Comparator.comparingInt(Score::total).reversed());
        scores = list;
        ScoreClientboundPacket.sendAll(list);
    }

    // ---- commands ---------------------------------------------------------------------------------------------------

    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        int rank = 1;
        for (Score s : scores)
            lines.add(rank++ + ". " + s.civ() + " — " + s.total() + " pts (territoire " + s.territory() + ", population " + s.population() +
                ", technologies " + s.technology() + ", militaire " + s.military() + ", religion " + s.religion() + ", économie " + s.economy() + ")");
        if (lines.isEmpty())
            lines.add("Pas encore de classement");
        return lines;
    }

    // the end, when the players decide: the final ranking for everyone and the winner in big letters
    public static void endGame() {
        update();
        if (scores.isEmpty())
            return;
        Score winner = scores.get(0);
        broadcast("══ Fin de la partie : classement final ══", ChatFormatting.GOLD);
        for (String line : describe())
            broadcast(line, ChatFormatting.YELLOW);
        broadcast("🏆 " + winner.civ() + " l'emporte avec " + winner.total() + " points !", ChatFormatting.GOLD);
        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("🏆 " + winner.civ()).withStyle(ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("l'emporte avec " + winner.total() + " points")));
        }
    }

    private static void broadcast(String text, ChatFormatting colour) {
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(colour), false);
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-classement").executes(ctx -> {
            update();
            for (String line : describe())
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            return 1;
        }));
        evt.getDispatcher().register(Commands.literal("rts-fin").requires(s -> s.hasPermission(2)).executes(ctx -> {
            endGame();
            return 1;
        }));
    }

    // new match (/rts-reset): counts start again
    public static void reset() {
        if (serverLevel == null)
            return;
        data().kills.clear();
        data().destroyed.clear();
        data().trade.clear();
        data().setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END && serverLevel != null && serverLevel.getGameTime() % 200 == 0)
            update();
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
