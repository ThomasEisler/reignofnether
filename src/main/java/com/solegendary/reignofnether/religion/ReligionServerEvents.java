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
import net.minecraft.core.BlockPos;
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

// Religion. Step 1: faith (made by temples and town centres), founding a religion at a temple, its imposed
// belief and a second one bought later, the religion of each city, and what the beliefs do.
// Step 2: religions spread every PREACH_INTERVAL: each city pushes its religion into the cities around it, temples
// push their founder's religion in their own city, prophets stood in a city of another religion convert it (in a city
// of their own religion they protect it). A temple, a protecting prophet and the Faithfulness belief each cut the
// pressure of foreign religions. Foreign prophets can be expelled from one's land for faith; prophets near a barbarian
// camp calm it down (no more raids, a small tribute). Step 3 (rites, bots, gauges on screen) comes later.
public class ReligionServerEvents {

    public static final int FOUND_COST = 200;
    public static final int BELIEF_COST = 300;
    public static final int BELIEF_CITIES = 3;          // cities following the religion before a second belief
    public static final double TEMPLE_FAITH = 0.2;      // per second (+1 every 5 s)
    public static final double TOWN_CENTRE_FAITH = 0.05; // per second (+1 every 20 s)
    public static final int MAX_COUNTED_CITIES = 10;    // for the tithe and pilgrimage
    public static final float BUILDER_MONKS = 1.15f, WORK_ETHIC = 1.10f, BLESSED_HARVEST = 1.10f;
    public static final float WISDOM = 0.15f, SAGAS = 0.15f, CRUSADE = 1.10f;

    // step 2: spreading
    public static final int PREACH_INTERVAL = 200;      // ticks (10 s)
    public static final int PRESSURE_RANGE = 260;       // cities this close push their religion
    public static final float CITY_PRESSURE = 1.5f;     // from a city next door (less further away)
    public static final float TEMPLE_PRESSURE = 1.0f;   // a temple, for its owner's religion, in its own city
    public static final float PROPHET_PRESSURE = 5f;    // a prophet preaching in a city
    public static final int PROPHET_RANGE = 28;         // from the city's Town Centre
    public static final float TEMPLE_SHIELD = 0.5f, FAITHFUL_SHIELD = 0.5f, PROPHET_SHIELD = 0.25f; // foreign pressure kept
    public static final int FREE_PROPHETS = 2, PROPHETS_PER_CITIES = 3, MAX_PROPHETS = 6;
    public static final int PROPHET_FAITH = 80;         // for each prophet after the first FREE_PROPHETS
    public static final int EXPEL_FAITH = 60, EXPEL_COOLDOWN = 20 * 60 * 3, EXPEL_BAN = 20 * 60 * 5;
    public static final int CALM_RANGE = 30;            // prophets this close to a barbarian camp calm it
    public static final float CALM_PER_PROPHET = 4f;    // per PREACH_INTERVAL: 25 intervals (~4 min) for one prophet

    private static final Map<String, Long> lastExpel = new HashMap<>();      // civ -> game time
    private static final Map<Integer, Map<String, Long>> bans = new HashMap<>(); // prophet id -> civ whose land it can't enter -> until
    private static int preachRound = 0;

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

    // ---- prophets ---------------------------------------------------------------------------------------------------

    public static List<ProphetUnit> prophetsOf(String civ) {
        List<ProphetUnit> list = new ArrayList<>();
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
            if (le instanceof ProphetUnit p && p.isAlive() && p.getOwnerName().equals(civ))
                list.add(p);
        return list;
    }

    public static int allowedProphets(boolean clientSide, String civ) {
        Religion r = Religions.foundedBy(clientSide, civ);
        if (r == null)
            return 0;
        return Math.min(MAX_PROPHETS, FREE_PROPHETS + Religions.followerCities(clientSide, r) / PROPHETS_PER_CITIES);
    }

    // why a temple can't start a prophet now, or null
    @Nullable
    public static String prophetError(String civ) {
        if (Religions.foundedBy(false, civ) == null)
            return "Fondez d'abord une religion";
        int queued = 0;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (isTemple(b) && b.ownerName.equals(civ) && b instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)
                for (var ap : pp.productionQueue)
                    if (ap.item instanceof ProphetProd)
                        queued++;
        int allowed = allowedProphets(false, civ);
        if (prophetsOf(civ).size() + queued >= allowed)
            return "Vous avez déjà vos " + allowed + " prophètes (1 de plus pour " + PROPHETS_PER_CITIES + " villes de votre religion)";
        if (data().prophetsTrained.getOrDefault(civ, 0) + queued >= FREE_PROPHETS && Religions.faith(false, civ) < PROPHET_FAITH)
            return "Il faut " + PROPHET_FAITH + " de foi pour ce prophète";
        return null;
    }

    public static void onProphetTrained(String civ) {
        int trained = data().prophetsTrained.getOrDefault(civ, 0);
        if (trained >= FREE_PROPHETS)
            data().faith.merge(civ, (double) -PROPHET_FAITH, Double::sum);
        data().prophetsTrained.put(civ, trained + 1);
        changed();
    }

    // the city a prophet stands in (near its Town Centre), or null
    @Nullable
    private static BuildingPlacement cityOfProphet(ProphetUnit p, List<BuildingPlacement> cities) {
        for (BuildingPlacement c : cities)
            if (c.centrePos.distToCenterSqr(p.position()) < PROPHET_RANGE * PROPHET_RANGE)
                return c;
        return null;
    }

    private static boolean isBanned(ProphetUnit p, String landOwner) {
        Map<String, Long> b = bans.get(p.getId());
        return b != null && b.getOrDefault(landOwner, 0L) > serverLevel.getGameTime();
    }

    // a foreign prophet on one's land goes home, can't come back for EXPEL_BAN; costs faith, once every EXPEL_COOLDOWN
    public static void expel(String civ, int prophetId) {
        if (!(serverLevel.getEntity(prophetId) instanceof ProphetUnit p) || !p.isAlive() || p.getOwnerName().equals(civ))
            return;
        String land = com.solegendary.reignofnether.territory.Territory.ownerAt(new ArrayList<>(BuildingServerEvents.getBuildings()),
            PlayerServerEvents::isRTSPlayer, p.getX(), p.getZ());
        if (!civ.equals(land)) {
            HudClientboundPacket.showTempMessageI18n(civ, "Ce prophète n'est pas sur vos terres");
            return;
        }
        long now = serverLevel.getGameTime();
        long wait = lastExpel.getOrDefault(civ, -EXPEL_COOLDOWN * 2L) + EXPEL_COOLDOWN - now;
        if (wait > 0) {
            HudClientboundPacket.showTempMessageI18n(civ, "Prochaine expulsion possible dans " + (wait / 20 + 1) + " s");
            return;
        }
        if (!spendFaith(civ, EXPEL_FAITH))
            return;
        lastExpel.put(civ, now);
        bans.computeIfAbsent(p.getId(), k -> new HashMap<>()).put(civ, now + EXPEL_BAN);
        sendHome(p);
        com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents.change(p.getOwnerName(), civ, -10, "vous avez expulsé son prophète");
        tell(p.getOwnerName(), civ + " a expulsé votre prophète de ses terres (pas de retour avant 5 min).");
        HudClientboundPacket.showTempMessageI18n(civ, "Prophète expulsé");
    }

    // back to its owner's nearest temple (or Town Centre)
    private static void sendHome(ProphetUnit p) {
        BuildingPlacement home = null;
        double best = Double.MAX_VALUE;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.ownerName.equals(p.getOwnerName()) && (isTemple(b) || b.getBuilding().isCapitol)) {
                double d = b.centrePos.distToCenterSqr(p.position());
                if (d < best) { best = d; home = b; }
            }
        if (home != null) {
            com.solegendary.reignofnether.unit.interfaces.Unit.fullResetBehaviours(p);
            p.getMoveGoal().setMoveTarget(home.getClosestGroundPos(p.getOnPos(), 2));
        }
    }

    private static void tell(String civ, String text) {
        var player = serverLevel.getServer().getPlayerList().getPlayerByName(civ);
        if (player != null)
            player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
    }

    // ---- spreading (every PREACH_INTERVAL) ----------------------------------------------------------------------------

    private static void spread() {
        if (!Religions.anyFounded(false))
            return;
        preachRound++;
        List<BuildingPlacement> cities = Religions.cities(false);
        Map<Long, Religion> current = new HashMap<>();
        for (BuildingPlacement c : cities)
            current.put(c.originPos.asLong(), Religions.religionOfCity(false, c));
        Map<Long, Map<Religion, Float>> push = new HashMap<>();
        Set<Long> protectedCities = new HashSet<>();
        Set<Long> withTemple = new HashSet<>();

        // neighbouring cities
        for (BuildingPlacement c : cities)
            for (BuildingPlacement d : cities) {
                Religion rd = current.get(d.originPos.asLong());
                if (c == d || rd == null)
                    continue;
                double dist = Math.sqrt(c.centrePos.distSqr(d.centrePos));
                if (dist < PRESSURE_RANGE)
                    push.computeIfAbsent(c.originPos.asLong(), k -> new EnumMap<>(Religion.class))
                        .merge(rd, (float) (CITY_PRESSURE * (1 - dist / PRESSURE_RANGE)), Float::sum);
            }
        // temples, in their own city, for their owner's religion
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (!isTemple(b) || !b.isBuilt)
                continue;
            BuildingPlacement city = Religions.cityAt(false, b.centrePos.getX(), b.centrePos.getZ());
            if (city == null)
                continue;
            withTemple.add(city.originPos.asLong());
            Religion r = Religions.foundedBy(false, b.ownerName);
            if (r != null)
                push.computeIfAbsent(city.originPos.asLong(), k -> new EnumMap<>(Religion.class)).merge(r, TEMPLE_PRESSURE, Float::sum);
        }
        // prophets
        Set<String> preachedAt = new HashSet<>(); // "cityOwner>prophetOwner", for diplomacy
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits()) {
            if (!(le instanceof ProphetUnit p) || !p.isAlive())
                continue;
            Religion r = Religions.foundedBy(false, p.getOwnerName());
            BuildingPlacement city = cityOfProphet(p, cities);
            if (r == null || city == null)
                continue;
            if (isBanned(p, city.ownerName)) {
                sendHome(p);
                continue;
            }
            long key = city.originPos.asLong();
            if (current.get(key) == r)
                protectedCities.add(key);
            push.computeIfAbsent(key, k -> new EnumMap<>(Religion.class)).merge(r, PROPHET_PRESSURE, Float::sum);
            if (!city.ownerName.equals(p.getOwnerName()) && current.get(key) != r)
                preachedAt.add(city.ownerName + ">" + p.getOwnerName());
            if (serverLevel.getRandom().nextInt(3) == 0)
                for (var pl : serverLevel.players())
                    serverLevel.sendParticles(pl, net.minecraft.core.particles.ParticleTypes.ENCHANT, true, p.getX(), p.getY() + 2.2, p.getZ(), 8, 0.4, 0.3, 0.4, 0.4);
        }
        // apply, with the shields against foreign religions
        for (BuildingPlacement c : cities) {
            long key = c.originPos.asLong();
            Map<Religion, Float> pushes = push.get(key);
            if (pushes == null)
                continue;
            Religion here = current.get(key);
            Map<Religion, Float> gauges = data().cities.computeIfAbsent(key, k -> new EnumMap<>(Religion.class));
            for (Map.Entry<Religion, Float> e : pushes.entrySet()) {
                float amount = e.getValue();
                if (here != null && e.getKey() != here) {
                    if (withTemple.contains(key)) amount *= TEMPLE_SHIELD;
                    if (Religions.hasBelief(false, here, Belief.FAITHFULNESS)) amount *= FAITHFUL_SHIELD;
                    if (protectedCities.contains(key)) amount *= PROPHET_SHIELD;
                }
                add(gauges, e.getKey(), amount);
            }
            Religion now = Religions.religionOfCity(false, c);
            if (now != here && now != null)
                broadcast(now.symbol + " " + cityName(c) + " (" + c.ownerName + ") suit désormais " + article(now) + now.frName + ".", ChatFormatting.LIGHT_PURPLE);
        }
        // preaching at a bot's at peace annoys it (every 30 s)
        if (preachRound % 3 == 0)
            for (String k : preachedAt) {
                String[] ab = k.split(">", 2);
                com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents.change(ab[0], ab[1], -1, "ses prophètes prêchent chez lui");
            }
        calmBarbarians();
        changed();
    }

    // more followers for one religion, taken from the others when the city is full (100 in all)
    private static void add(Map<Religion, Float> gauges, Religion r, float amount) {
        float value = Math.min(100f, gauges.getOrDefault(r, 0f) + amount);
        gauges.put(r, value);
        float others = 0;
        for (Map.Entry<Religion, Float> e : gauges.entrySet())
            if (e.getKey() != r) others += e.getValue();
        if (value + others > 100f && others > 0) {
            float scale = (100f - value) / others;
            gauges.replaceAll((k, v) -> k == r ? v : v * scale);
        }
        gauges.values().removeIf(v -> v < 0.1f);
    }

    private static String cityName(BuildingPlacement c) {
        String name = com.solegendary.reignofnether.nation.CitySaveData.getInstance(serverLevel).names.get(c.originPos.asLong());
        return name != null ? name : "Une ville";
    }

    // ---- barbarians -------------------------------------------------------------------------------------------------

    public static boolean isCalmed(BlockPos campOrigin) {
        return serverLevel != null && data().calm.getOrDefault(campOrigin.asLong(), 0f) >= 100f;
    }

    private static void calmBarbarians() {
        for (BlockPos origin : com.solegendary.reignofnether.barbarian.BarbarianServerEvents.campOrigins()) {
            long key = origin.asLong();
            BuildingPlacement camp = null;
            for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                if (b.originPos.equals(origin)) { camp = b; break; }
            if (camp == null)
                continue;
            Map<String, Integer> near = new HashMap<>();
            for (LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
                if (le instanceof ProphetUnit p && p.isAlive() && Religions.foundedBy(false, p.getOwnerName()) != null &&
                    camp.centrePos.distToCenterSqr(p.position()) < CALM_RANGE * CALM_RANGE)
                    near.merge(p.getOwnerName(), 1, Integer::sum);
            float before = data().calm.getOrDefault(key, 0f);
            if (near.isEmpty() || before >= 100f)
                continue;
            String calmer = near.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            float after = Math.min(100f, before + CALM_PER_PROPHET * near.values().stream().mapToInt(Integer::intValue).sum());
            data().calm.put(key, after);
            if (after >= 100f) {
                data().calmedBy.put(key, calmer);
                broadcast("☮ Les prophètes de " + calmer + " ont apaisé un camp barbare : il ne lance plus de raids.", ChatFormatting.LIGHT_PURPLE);
            }
        }
        data().calm.keySet().retainAll(com.solegendary.reignofnether.barbarian.BarbarianServerEvents.campOrigins().stream().map(BlockPos::asLong).toList());
        data().calmedBy.keySet().retainAll(data().calm.keySet());
    }

    // every minute: a calmed camp's tribute to the civilisation that calmed it
    private static void payTributes() {
        data().calmedBy.forEach((camp, civ) -> {
            if (PlayerServerEvents.isRTSPlayer(civ))
                ResourcesServerEvents.addSubtractResources(new Resources(civ, 25, 25, 0));
        });
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
        data().prophetsTrained.clear();
        data().calm.clear();
        data().calmedBy.clear();
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
        if (t % 1200 == 0) {
            payFounders();
            payTributes();
        }
        if (t % PREACH_INTERVAL == 0)
            spread();
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
