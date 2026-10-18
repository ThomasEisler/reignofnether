package com.solegendary.reignofnether.religion;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;

// Religion, step 1: faith (made by temples and town centres), founding a religion at a temple, its imposed
// belief and a second one bought later, the religion of each city, and what the beliefs do.
// Step 2 (pressure between cities, prophets) and step 3 (rites, bots, diplomacy) come later.
public class ReligionServerEvents {

    public static final int FOUND_COST = 200;
    public static final int BELIEF_COST = 300;
    public static final int BELIEF_CITIES = 3;          // cities following the religion before a second belief
    public static final double TEMPLE_FAITH = 0.2;      // per second (+1 every 5 s)
    public static final double TOWN_CENTRE_FAITH = 0.05; // per second (+1 every 20 s)
    public static final int MAX_COUNTED_CITIES = 10;    // for the tithe and pilgrimage
    public static final float BUILDER_MONKS = 1.15f, WORK_ETHIC = 1.10f, BLESSED_HARVEST = 1.10f;
    public static final float WISDOM = 0.15f, SAGAS = 0.15f, CRUSADE = 1.10f;

    private static ServerLevel serverLevel = null;
    private static boolean needsSync = true;

    public static ReligionSaveData data() {
        return serverLevel == null ? new ReligionSaveData() : ReligionSaveData.getInstance(serverLevel);
    }

    private static void changed() {
        data().setDirty();
        needsSync = true;
    }

    // ---- faith ------------------------------------------------------------------------------------------------------

    public static boolean isTemple(BuildingPlacement b) {
        return b.getBuilding() instanceof Temple;
    }

    public static void addFaith(String civ, double amount) {
        data().faith.merge(civ, amount, Double::sum);
        changed();
    }

    private static boolean spendFaith(String civ, int amount) {
        if (Religions.faith(false, civ) < amount) {
            HudClientboundPacket.showTempMessageI18n(civ, "Pas assez de foi (" + amount + " nécessaires)");
            return false;
        }
        data().faith.merge(civ, (double) -amount, Double::sum);
        changed();
        return true;
    }

    // every second
    private static void makeFaith() {
        Map<String, Double> made = new HashMap<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!b.isBuilt || b.ownerName.isBlank())
                continue;
            if (isTemple(b))
                made.merge(b.ownerName, TEMPLE_FAITH, Double::sum);
            else if (b.getBuilding().isCapitol)
                made.merge(b.ownerName, TOWN_CENTRE_FAITH, Double::sum);
        }
        made.forEach((civ, amount) -> data().faith.merge(civ, amount, Double::sum));
        if (!made.isEmpty())
            data().setDirty();
    }

    // every minute: the founders' tithe (emeralds) and pilgrimage (faith), for each city following their religion
    private static void payFounders() {
        for (Map.Entry<Religion, String> e : data().founders.entrySet()) {
            int cities = Math.min(MAX_COUNTED_CITIES, Religions.followerCities(false, e.getKey()));
            if (cities == 0)
                continue;
            if (Religions.hasBelief(false, e.getKey(), Belief.TITHE))
                ResourcesServerEvents.addSubtractResources(new Resources(e.getValue(), 0, 0, 0, cities));
            if (Religions.hasBelief(false, e.getKey(), Belief.PILGRIMAGE))
                addFaith(e.getValue(), cities);
        }
    }

    // ---- founding and beliefs ---------------------------------------------------------------------------------------

    public static void found(String civ, Religion religion, BuildingPlacement temple) {
        if (!temple.isBuilt || !temple.ownerName.equals(civ) || !isTemple(temple))
            return;
        if (Religions.foundedBy(false, civ) != null) {
            HudClientboundPacket.showTempMessageI18n(civ, "Vous avez déjà fondé une religion");
            return;
        }
        if (data().founders.containsKey(religion)) {
            HudClientboundPacket.showTempMessageI18n(civ, religion.frName + " a déjà été fondé par " + data().founders.get(religion));
            return;
        }
        BuildingPlacement city = Religions.cityAt(false, temple.centrePos.getX(), temple.centrePos.getZ());
        if (city == null) {
            HudClientboundPacket.showTempMessageI18n(civ, "Le temple doit être sur les terres d'une de vos villes");
            return;
        }
        if (!spendFaith(civ, FOUND_COST))
            return;
        data().founders.put(religion, civ);
        // the temple's city follows it at once
        Map<Religion, Float> gauges = data().cities.computeIfAbsent(city.originPos.asLong(), k -> new EnumMap<>(Religion.class));
        gauges.replaceAll((r, v) -> v * 0.5f);
        gauges.put(religion, 100f);
        changed();
        broadcast(religion.symbol + " " + civ + " fonde " + article(religion) + religion.frName + " (" + religion.belief.frName + ") !", ChatFormatting.LIGHT_PURPLE);
        for (var p : serverLevel.players())
            SoundClientboundPacket.playSoundForPlayer(SoundAction.CHAT, p.getName().getString());
    }

    private static String article(Religion r) {
        return switch (r) {
            case ORTHODOXY, ISLAM, HINDUISM -> "l'";
            default -> "le ";
        };
    }

    public static void buyBelief(String civ, Belief belief) {
        Religion religion = Religions.foundedBy(false, civ);
        if (religion == null) {
            HudClientboundPacket.showTempMessageI18n(civ, "Fondez d'abord une religion");
            return;
        }
        if (data().secondBeliefs.containsKey(religion)) {
            HudClientboundPacket.showTempMessageI18n(civ, "Votre religion a déjà sa 2e croyance");
            return;
        }
        if (!Religions.freeBeliefs(false).contains(belief)) {
            HudClientboundPacket.showTempMessageI18n(civ, "Cette croyance est déjà prise");
            return;
        }
        if (Religions.followerCities(false, religion) < BELIEF_CITIES) {
            HudClientboundPacket.showTempMessageI18n(civ, "Il faut " + BELIEF_CITIES + " villes qui suivent votre religion");
            return;
        }
        if (!spendFaith(civ, BELIEF_COST))
            return;
        data().secondBeliefs.put(religion, belief);
        changed();
        broadcast(religion.symbol + " " + religion.frName + " (" + civ + ") adopte la croyance " + belief.frName + ".", ChatFormatting.LIGHT_PURPLE);
    }

    // ---- what beliefs do (the other hooks call these) ---------------------------------------------------------------

    // construction speed of a building
    public static float buildSpeed(BuildingPlacement b) {
        return Religions.beliefAt(false, b.centrePos.getX(), b.centrePos.getZ(), Belief.BUILDER_MONKS) ? BUILDER_MONKS : 1f;
    }

    public static float gatherSpeed(LivingEntity worker) {
        return Religions.beliefAt(false, worker.getX(), worker.getZ(), Belief.WORK_ETHIC) ? WORK_ETHIC : 1f;
    }

    public static float farmSpeed(BuildingPlacement farm) {
        return Religions.beliefAt(farm.getLevel().isClientSide(), farm.centrePos.getX(), farm.centrePos.getZ(), Belief.BLESSED_HARVEST) ? BLESSED_HARVEST : 1f;
    }

    // research ticks go this many times faster
    public static float researchSpeed(String civ) {
        return 1f + WISDOM * Religions.civShare(false, civ, Belief.HOUSES_OF_WISDOM);
    }

    public static boolean compassion(LivingEntity priest) {
        return Religions.beliefAt(false, priest.getX(), priest.getZ(), Belief.COMPASSION);
    }

    @SubscribeEvent
    public static void onEntityDamaged(LivingDamageEvent evt) {
        if (serverLevel == null || evt.getEntity().level().isClientSide() || !Religions.anyFounded(false))
            return;
        float mult = 1f;
        if (evt.getSource().getEntity() instanceof Unit attacker) {
            if (attacker instanceof HeroUnit)
                mult *= 1f + SAGAS * Religions.civShare(false, attacker.getOwnerName(), Belief.SAGAS);
            // crusade: the founder's soldiers on the land of a city of another religion
            if (!(attacker instanceof WorkerUnit) && Religions.founderHas(false, attacker.getOwnerName(), Belief.CRUSADE)) {
                Religion here = Religions.religionAt(false, evt.getEntity().getX(), evt.getEntity().getZ());
                BuildingPlacement city = Religions.cityAt(false, evt.getEntity().getX(), evt.getEntity().getZ());
                if (city != null && here != Religions.foundedBy(false, attacker.getOwnerName()))
                    mult *= CRUSADE;
            }
        }
        if (evt.getEntity() instanceof HeroUnit hero)
            mult /= 1f + SAGAS * Religions.civShare(false, ((Unit) hero).getOwnerName(), Belief.SAGAS);
        if (mult != 1f)
            evt.setAmount(evt.getAmount() * mult);
    }

    // ---- cities -----------------------------------------------------------------------------------------------------

    // forget the followers of cities that no longer exist
    private static void forgetLostCities() {
        Set<Long> alive = new HashSet<>();
        for (BuildingPlacement c : Religions.cities(false))
            alive.add(c.originPos.asLong());
        if (data().cities.keySet().retainAll(alive))
            changed();
    }

    // new match (/rts-reset): no religions any more
    public static void reset() {
        if (serverLevel == null)
            return;
        data().faith.clear();
        data().founders.clear();
        data().secondBeliefs.clear();
        data().cities.clear();
        changed();
    }

    // ---- messages and info ------------------------------------------------------------------------------------------

    private static void broadcast(String text, ChatFormatting colour) {
        if (serverLevel != null)
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(colour), false);
    }

    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Religion r : Religion.values()) {
            String founder = data().founders.get(r);
            if (founder == null)
                continue;
            Belief second = data().secondBeliefs.get(r);
            lines.add(r.symbol + " " + r.frName + " (" + founder + ") : " + r.belief.frName + (second != null ? ", " + second.frName : "") +
                " — " + Religions.followerCities(false, r) + " ville(s)");
        }
        if (lines.isEmpty())
            lines.add("Aucune religion fondée");
        for (var p : PlayerServerEvents.rtsPlayers)
            lines.add("Foi de " + p.name + " : " + Religions.faith(false, p.name));
        return lines;
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-religion")
            .executes(ctx -> {
                for (String line : describe())
                    ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                return 1;
            })
            .then(Commands.literal("foi").requires(s -> s.hasPermission(2))
                .then(Commands.argument("n", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
                    addFaith(ctx.getSource().getTextName(), IntegerArgumentType.getInteger(ctx, "n"));
                    return 1;
                }))));
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        long t = serverLevel.getGameTime();
        if (t % 20 == 0)
            makeFaith();
        if (t % 1200 == 0)
            payFounders();
        if (t % 200 == 0)
            forgetLostCities();
        if (needsSync || t % 40 == 0) { // faith moves all the time: every 2 s
            needsSync = false;
            ReligionClientboundPacket.sendAll(data());
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        needsSync = true;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        needsSync = true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }

    @Nullable
    public static ServerLevel level() {
        return serverLevel;
    }
}
