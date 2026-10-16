package com.solegendary.reignofnether.diplomacy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.bot.BotSettings;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.diplomacy.DiplomacySaveData.Opinion;
import com.solegendary.reignofnether.nation.Cities;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;

// Diplomacy, Civilization style but simple: 3 stances (war, peace, alliance; peace by default), and for each bot an
// opinion of every other civilisation (-100 to +100) that decides what it does: war under WAR_AT, alliance over
// ALLY_AT. At peace nobody attacks: units don't pick fights and attack orders are refused until war is declared.
// Opinion goes up with gifts, fair trades, a common enemy and being allies; down with units on its territory
// (TERRITORY blocks around its buildings), cities too close, attacks during a war and broken
// agreements; and slowly back towards 0 (and up, out of weariness, in a long war). Bots deal with each other the same way.
public class DiplomacyServerEvents {

    public static final int MIN = -100, MAX = 100;
    public static final int WAR_AT = -50;        // a bot at peace declares war under this
    public static final int BREAK_AT = -10;      // a bot breaks its alliance under this
    public static final int PEACE_AT = -25;      // a bot at war accepts or offers peace over this
    public static final int ALLY_AT = 50;        // a bot accepts an alliance over this
    public static final int OFFER_ALLY_AT = 65;  // and offers one itself over this
    public static final int TRADE_AT = -20;      // a bot refuses to trade under this
    public static final int TERRITORY = 36;      // blocks around a bot's buildings
    public static final int NEIGHBOUR_DIST = 260, FAR_NEIGHBOUR_DIST = 420; // cities this close make a bot uneasy
    public static final int GIFT_PER_POINT = 20; // resources given for +1
    public static final int MAX_GIFT_POINTS = 25;

    private static final int SYNC_INTERVAL = 20 * 10, DECIDE_INTERVAL = 20 * 10;
    private static final int TERRITORY_INTERVAL = 20 * 20, OPINION_INTERVAL = 20 * 30;
    private static final int HIT_COOLDOWN = 20 * 10; // at most -1 every 10 s per pair for attacks
    private static final int MIN_WAR = 20 * 60 * 4;  // a war lasts at least this before a bot makes peace
    private static final int OFFER_COOLDOWN = 20 * 60 * 8; // between two offers of the same bot to a player
    private static final int PROPOSAL_LIFE = 20 * 60 * 2;

    private static ServerLevel serverLevel = null;
    private static boolean restored = false;
    private static boolean needsSync = false;
    private static final Random random = new Random();
    private static final List<Proposal> proposals = new ArrayList<>();
    private static final Map<String, Long> lastHit = new HashMap<>();   // "victim>attacker" -> game time
    private static final Map<String, Long> lastOffer = new HashMap<>(); // "bot>other" -> game time

    private static DiplomacySaveData data() {
        return DiplomacySaveData.getInstance(serverLevel);
    }

    private static long now() {
        return serverLevel == null ? 0 : serverLevel.getGameTime();
    }

    // ---- stances --------------------------------------------------------------------------------------------------

    // only between two civilisations (players or bots); anything else (barbarians, neutral mobs) is always hostile
    public static boolean isCiv(String name) {
        return name != null && !name.isBlank() && PlayerServerEvents.isRTSPlayer(name);
    }

    public static Stance getStance(String a, String b) {
        if (AlliancesServerEvents.isAllied(a, b))
            return Stance.ALLIANCE;
        if (serverLevel == null || !isCiv(a) || !isCiv(b) || data().wars.containsKey(DiplomacySaveData.key(a, b)))
            return Stance.WAR;
        return Stance.PEACE;
    }

    public static boolean atPeace(String a, String b) {
        return a != null && b != null && !a.equals(b) && getStance(a, b) == Stance.PEACE;
    }

    public static boolean atWar(String a, String b) {
        return a != null && b != null && !a.equals(b) && getStance(a, b) == Stance.WAR;
    }

    private static boolean locked() {
        return serverLevel != null && (serverLevel.getGameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES).get() ||
            serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get());
    }

    public static void declareWar(String by, String on) {
        Stance before = getStance(by, on);
        if (before == Stance.WAR)
            return;
        if (before == Stance.ALLIANCE)
            AlliancesServerEvents.removeAlliance(by, on);
        data().wars.put(DiplomacySaveData.key(by, on), now());
        data().setDirty();
        // breaking an alliance is worse than breaking the peace; the victim's allies don't like it either
        change(on, by, before == Stance.ALLIANCE ? -50 : -30, before == Stance.ALLIANCE ?
            "vous avez trahi votre alliance" : "vous lui avez déclaré la guerre");
        for (String ally : new ArrayList<>(AlliancesServerEvents.getAllAllies(on)))
            if (!ally.equals(by))
                change(ally, by, -20, "vous avez attaqué son allié " + on);
        broadcast("⚔ " + by + " déclare la guerre à " + on + " !", ChatFormatting.RED);
        sound(SoundAction.ENEMY, by, on);
        needsSync = true;
    }

    public static void makePeace(String a, String b) {
        if (getStance(a, b) != Stance.WAR)
            return;
        data().wars.remove(DiplomacySaveData.key(a, b));
        data().setDirty();
        broadcast("☮ " + a + " et " + b + " font la paix.", ChatFormatting.YELLOW);
        sound(SoundAction.ALLY, a, b);
        needsSync = true;
    }

    public static void makeAlliance(String a, String b) {
        if (getStance(a, b) == Stance.ALLIANCE)
            return;
        data().wars.remove(DiplomacySaveData.key(a, b));
        AlliancesServerEvents.addAlliance(a, b);
        snapshotAlliances();
        broadcast("🤝 " + a + " et " + b + " s'allient.", ChatFormatting.GREEN);
        sound(SoundAction.ALLY, a, b);
        needsSync = true;
    }

    // alliance -> peace
    public static void breakAlliance(String by, String with) {
        if (getStance(by, with) != Stance.ALLIANCE)
            return;
        AlliancesServerEvents.removeAlliance(by, with);
        snapshotAlliances();
        change(with, by, -25, "vous avez rompu votre alliance");
        broadcast(by + " rompt son alliance avec " + with + " (ils restent en paix).", ChatFormatting.GOLD);
        sound(SoundAction.ENEMY, by, with);
        needsSync = true;
    }

    // ---- opinions -------------------------------------------------------------------------------------------------

    private static Opinion opinionOf(String bot, String other) {
        return data().opinions.computeIfAbsent(bot, k -> new HashMap<>()).computeIfAbsent(other, k -> {
            Opinion o = new Opinion();
            o.value = random.nextInt(-10, 11); // a bit of character
            return o;
        });
    }

    // what a bot thinks of another civilisation (0 for players: they decide for themselves)
    public static int getOpinion(String bot, String other) {
        if (serverLevel == null || !BotServerEvents.isBot(bot))
            return 0;
        return opinionOf(bot, other).value;
    }

    public static String getReason(String bot, String other) {
        if (serverLevel == null || !BotServerEvents.isBot(bot))
            return "";
        return opinionOf(bot, other).reason;
    }

    // only bots keep opinions
    public static void change(String bot, String other, int delta, String reason) {
        if (serverLevel == null || delta == 0 || bot.equals(other) || !BotServerEvents.isBot(bot) || !isCiv(other))
            return;
        Opinion o = opinionOf(bot, other);
        o.value = Math.max(MIN, Math.min(MAX, o.value + delta));
        if (delta < 0 && !reason.isEmpty())
            o.reason = reason;
        data().setDirty();
        needsSync = true;
    }

    public static void setOpinion(String bot, String other, int value) {
        opinionOf(bot, other).value = Math.max(MIN, Math.min(MAX, value));
        data().setDirty();
        needsSync = true;
    }

    private static List<String> civs() {
        List<String> list = new ArrayList<>();
        for (RTSPlayer p : PlayerServerEvents.rtsPlayers)
            list.add(p.name);
        return list;
    }

    // every OPINION_INTERVAL: neighbours, common enemies, allies, and the return towards 0 (good feelings fade
    // every time, grudges only every third time)
    private static int opinionRound = 0;
    private static void updateOpinions() {
        opinionRound++;
        List<BuildingPlacement> cities = Cities.getCities(serverLevel);
        List<String> civs = civs();
        for (String bot : BotServerEvents.botNames()) {
            for (String other : civs) {
                if (other.equals(bot))
                    continue;
                Opinion o = opinionOf(bot, other);
                if (o.value > 0) o.value--;
                else if (o.value < 0 && opinionRound % 3 == 0) o.value++;
                Stance st = getStance(bot, other);
                if (st == Stance.ALLIANCE)
                    change(bot, other, 1, "");
                if (st == Stance.WAR && now() - data().wars.getOrDefault(DiplomacySaveData.key(bot, other), 0L) > MIN_WAR)
                    change(bot, other, 1, ""); // tired of the war
                if (st == Stance.PEACE && (citiesClose(cities, bot, other, NEIGHBOUR_DIST) ||
                        (opinionRound % 2 == 0 && citiesClose(cities, bot, other, FAR_NEIGHBOUR_DIST))))
                    change(bot, other, -1, "vos villes sont trop près des siennes");
                for (String third : civs)
                    if (!third.equals(bot) && !third.equals(other) && atWar(bot, third) && atWar(other, third)) {
                        change(bot, other, 2, "");
                        break;
                    }
            }
        }
        data().setDirty();
        needsSync = true;
    }

    private static boolean citiesClose(List<BuildingPlacement> cities, String a, String b, int dist) {
        for (BuildingPlacement ca : cities)
            if (ca.ownerName.equals(a))
                for (BuildingPlacement cb : cities)
                    if (cb.ownerName.equals(b) && ca.centrePos.distSqr(cb.centrePos) < (double) dist * dist)
                        return true;
        return false;
    }

    // every TERRITORY_INTERVAL: -1 towards each civilisation at peace with units near the bot's buildings
    private static void checkTerritory() {
        for (String bot : BotServerEvents.botNames()) {
            List<BuildingPlacement> buildings = BuildingServerEvents.getBuildings().stream().filter(b -> bot.equals(b.ownerName)).toList();
            if (buildings.isEmpty())
                continue;
            Set<String> intruders = new HashSet<>();
            for (LivingEntity le : UnitServerEvents.getAllUnits()) {
                String owner = ((Unit) le).getOwnerName();
                if (!le.isAlive() || owner.equals(bot) || intruders.contains(owner) || !atPeace(bot, owner))
                    continue;
                for (BuildingPlacement b : buildings)
                    if (le.distanceToSqr(Vec3.atCenterOf(b.centrePos)) < TERRITORY * TERRITORY) {
                        intruders.add(owner);
                        break;
                    }
            }
            for (String owner : intruders)
                change(bot, owner, -1, "vos unités entrent sur son territoire");
        }
    }

    // attacks during a war keep the bot angry
    @SubscribeEvent
    public static void onEntityDamaged(LivingDamageEvent evt) {
        if (serverLevel == null || evt.getEntity().level().isClientSide() || !(evt.getEntity() instanceof Unit victim) ||
            !(evt.getSource().getEntity() instanceof Unit attacker))
            return;
        String v = victim.getOwnerName(), a = attacker.getOwnerName();
        if (v.equals(a) || !BotServerEvents.isBot(v) || !atWar(v, a))
            return;
        String k = v + ">" + a;
        if (now() - lastHit.getOrDefault(k, -HIT_COOLDOWN * 2L) < HIT_COOLDOWN)
            return;
        lastHit.put(k, now());
        change(v, a, -1, "vous attaquez ses unités");
    }

    // ---- bot decisions --------------------------------------------------------------------------------------------

    private static void decide() {
        for (String bot : BotServerEvents.botNames()) {
            boolean canWar = BotServerEvents.ticksAlive(bot) >= BotSettings.difficulty().peaceTicks;
            for (String other : civs()) {
                if (other.equals(bot))
                    continue;
                int op = getOpinion(bot, other);
                boolean otherIsBot = BotServerEvents.isBot(other);
                switch (getStance(bot, other)) {
                    case ALLIANCE -> {
                        if (op <= BREAK_AT)
                            breakAlliance(bot, other);
                    }
                    case PEACE -> {
                        if (op <= WAR_AT && canWar && !locked())
                            declareWar(bot, other);
                        else if (op >= ALLY_AT && otherIsBot && getOpinion(other, bot) >= ALLY_AT && !locked())
                            makeAlliance(bot, other);
                        else if (op >= OFFER_ALLY_AT && !otherIsBot)
                            offer(bot, other, Proposal.Kind.ALLIANCE);
                    }
                    case WAR -> {
                        long since = data().wars.getOrDefault(DiplomacySaveData.key(bot, other), 0L);
                        if (op < PEACE_AT || now() - since < MIN_WAR || locked())
                            break;
                        if (otherIsBot) {
                            if (getOpinion(other, bot) >= PEACE_AT)
                                makePeace(bot, other);
                        } else
                            offer(bot, other, Proposal.Kind.PEACE);
                    }
                }
            }
        }
    }

    // a bot asks a player, at most once every OFFER_COOLDOWN
    private static void offer(String bot, String player, Proposal.Kind kind) {
        String k = bot + ">" + player;
        if (now() - lastOffer.getOrDefault(k, -OFFER_COOLDOWN * 2L) < OFFER_COOLDOWN)
            return;
        lastOffer.put(k, now());
        addProposal(new Proposal(kind, bot, player, 0, 0, 0, now()));
    }

    // a player (or a bot) asks a bot: yes or no right away, with the reason
    private static void answerAsBot(Proposal p) {
        String bot = p.to(), from = p.from();
        int op = getOpinion(bot, from);
        String why = " (opinion " + signed(op) + (getReason(bot, from).isEmpty() ? "" : " : " + getReason(bot, from)) + ")";
        switch (p.kind()) {
            case PEACE -> {
                long since = data().wars.getOrDefault(DiplomacySaveData.key(bot, from), 0L);
                if (now() - since < MIN_WAR / 2)
                    tell(from, bot + " refuse la paix : la guerre vient de commencer.", ChatFormatting.RED);
                else if (op < PEACE_AT)
                    tell(from, bot + " refuse la paix" + why + ".", ChatFormatting.RED);
                else
                    makePeace(bot, from);
            }
            case ALLIANCE -> {
                if (op < ALLY_AT)
                    tell(from, bot + " refuse l'alliance" + why + ". Il faut plus de " + ALLY_AT + ".", ChatFormatting.RED);
                else
                    makeAlliance(bot, from);
            }
            case TRADE -> {
                int given = Math.max(0, p.food()) + Math.max(0, p.wood()) + Math.max(0, p.ore());
                int asked = Math.max(0, -p.food()) + Math.max(0, -p.wood()) + Math.max(0, -p.ore());
                if (asked == 0) { // a gift: always welcome
                    if (transfer(from, bot, p.food(), p.wood(), p.ore())) {
                        change(bot, from, Math.min(MAX_GIFT_POINTS, Math.max(1, given / GIFT_PER_POINT)), "");
                        tell(from, bot + " accepte votre cadeau (opinion " + signed(getOpinion(bot, from)) + ").", ChatFormatting.GREEN);
                    }
                    return;
                }
                float fair = op >= ALLY_AT ? 0.8f : 1f; // friends accept a slightly worse deal
                if (atWar(bot, from))
                    tell(from, bot + " ne commerce pas avec un ennemi.", ChatFormatting.RED);
                else if (op < TRADE_AT)
                    tell(from, bot + " refuse l'échange" + why + ".", ChatFormatting.RED);
                else if (given < asked * fair)
                    tell(from, bot + " refuse : l'échange ne lui est pas assez favorable.", ChatFormatting.RED);
                else if (!canPay(bot, -p.food(), -p.wood(), -p.ore(), true))
                    tell(from, bot + " refuse : il n'a pas assez de ressources.", ChatFormatting.RED);
                else if (transfer(from, bot, p.food(), p.wood(), p.ore())) {
                    change(bot, from, 2 + Math.min(MAX_GIFT_POINTS, Math.max(0, given - asked) / GIFT_PER_POINT), "");
                    tell(from, bot + " accepte l'échange.", ChatFormatting.GREEN);
                }
            }
        }
    }

    // ---- proposals between civilisations --------------------------------------------------------------------------

    // what a player (or the HUD) asks for; returns an error, or null
    @Nullable
    public static String request(String from, String to, String what, int food, int wood, int ore) {
        if (serverLevel == null)
            return "Pas de partie en cours";
        if (!isCiv(from))
            return "Vous ne jouez pas";
        if (!isCiv(to) || from.equals(to))
            return "Civilisation inconnue : " + to;
        Stance st = getStance(from, to);
        boolean changesStance = !what.equals("echange");
        if (changesStance && locked())
            return "Les alliances sont verrouillées dans cette partie";
        switch (what) {
            case "guerre" -> {
                if (st == Stance.WAR)
                    return "Déjà en guerre contre " + to;
                declareWar(from, to);
            }
            case "rompre" -> {
                if (st != Stance.ALLIANCE)
                    return "Pas d'alliance avec " + to;
                breakAlliance(from, to);
            }
            case "paix" -> {
                if (st != Stance.WAR)
                    return "Déjà en paix avec " + to;
                propose(new Proposal(Proposal.Kind.PEACE, from, to, 0, 0, 0, now()));
            }
            case "alliance" -> {
                if (st == Stance.ALLIANCE)
                    return "Déjà allié à " + to;
                if (st == Stance.WAR)
                    return "Faites d'abord la paix avec " + to;
                propose(new Proposal(Proposal.Kind.ALLIANCE, from, to, 0, 0, 0, now()));
            }
            case "echange" -> {
                if (food == 0 && wood == 0 && ore == 0)
                    return "Rien à échanger";
                if (!canPay(from, food, wood, ore, false))
                    return "Vous n'avez pas assez de ressources";
                boolean gift = food >= 0 && wood >= 0 && ore >= 0;
                if (gift && !BotServerEvents.isBot(to)) {
                    if (transfer(from, to, food, wood, ore)) {
                        tell(from, "Envoyé à " + to + " : " + describeTrade(food, wood, ore), ChatFormatting.GREEN);
                        tell(to, from + " vous envoie " + describeTrade(food, wood, ore), ChatFormatting.GREEN);
                    }
                } else
                    propose(new Proposal(Proposal.Kind.TRADE, from, to, food, wood, ore, now()));
            }
            default -> {
                return "Action inconnue : " + what;
            }
        }
        return null;
    }

    private static void propose(Proposal p) {
        if (BotServerEvents.isBot(p.to()))
            answerAsBot(p);
        else {
            addProposal(p);
            tell(p.from(), "Proposition envoyée à " + p.to() + " : " + p.describe() + ".", ChatFormatting.GRAY);
        }
    }

    private static void addProposal(Proposal p) {
        proposals.removeIf(q -> q.from().equals(p.from()) && q.to().equals(p.to()));
        proposals.add(p);
        MutableComponent msg = Component.literal(p.from() + " vous propose " + p.describe() + ". ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal("[Accepter]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rts-diplo accepter " + p.from()))))
            .append(Component.literal(" "))
            .append(Component.literal("[Refuser]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rts-diplo refuser " + p.from()))));
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayerByName(p.to());
        if (player != null)
            player.sendSystemMessage(msg);
        SoundClientboundPacket.playSoundForPlayer(SoundAction.CHAT, p.to());
        needsSync = true;
    }

    @Nullable
    public static String answer(String player, String from, boolean accept) {
        Proposal p = proposals.stream().filter(q -> q.to().equals(player) && q.from().equals(from)).findFirst().orElse(null);
        if (p == null)
            return "Aucune proposition de " + from;
        proposals.remove(p);
        needsSync = true;
        if (!accept) {
            tell(from, player + " refuse " + p.describe() + ".", ChatFormatting.RED);
            if (BotServerEvents.isBot(from))
                change(from, player, -5, "vous avez refusé sa proposition");
            return null;
        }
        switch (p.kind()) {
            case PEACE -> {
                if (getStance(from, player) != Stance.WAR)
                    return "Vous n'êtes plus en guerre";
                makePeace(from, player);
            }
            case ALLIANCE -> {
                if (getStance(from, player) == Stance.WAR)
                    return "Vous êtes en guerre";
                makeAlliance(from, player);
            }
            case TRADE -> {
                if (!canPay(from, p.food(), p.wood(), p.ore(), false) || !canPay(player, -p.food(), -p.wood(), -p.ore(), false))
                    return "L'un de vous n'a plus assez de ressources";
                if (transfer(from, player, p.food(), p.wood(), p.ore())) {
                    tell(from, player + " accepte l'échange.", ChatFormatting.GREEN);
                    tell(player, "Échange fait avec " + from + ".", ChatFormatting.GREEN);
                }
            }
        }
        return null;
    }

    public static List<Proposal> getProposals() {
        return proposals;
    }

    // ---- resources ------------------------------------------------------------------------------------------------

    @Nullable
    private static Resources resourcesOf(String name) {
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                return r;
        return null;
    }

    // can pay the positive amounts (a bot keeps a little for itself)
    private static boolean canPay(String name, int food, int wood, int ore, boolean botReserve) {
        Resources r = resourcesOf(name);
        if (r == null)
            return false;
        int keepFood = botReserve ? 50 : 0, keepWood = botReserve ? BotServerEvents.WOOD_RESERVE : 0;
        return (food <= 0 || r.food >= food + keepFood) && (wood <= 0 || r.wood >= wood + keepWood) && (ore <= 0 || r.ore >= ore);
    }

    // "from" gives the positive amounts to "to" and receives the negative ones
    private static boolean transfer(String from, String to, int food, int wood, int ore) {
        if (!canPay(from, food, wood, ore, false) || !canPay(to, -food, -wood, -ore, false))
            return false;
        ResourcesServerEvents.addSubtractResources(new Resources(from, -food, -wood, -ore));
        ResourcesServerEvents.addSubtractResources(new Resources(to, food, wood, ore));
        SoundClientboundPacket.playSoundForPlayer(SoundAction.CHAT, to);
        return true;
    }

    public static String describeTrade(int food, int wood, int ore) {
        List<String> give = new ArrayList<>(), get = new ArrayList<>();
        if (food != 0) (food > 0 ? give : get).add(Math.abs(food) + " nourriture");
        if (wood != 0) (wood > 0 ? give : get).add(Math.abs(wood) + " bois");
        if (ore != 0) (ore > 0 ? give : get).add(Math.abs(ore) + " minerai");
        if (get.isEmpty())
            return String.join(", ", give);
        if (give.isEmpty())
            return "demande " + String.join(", ", get);
        return String.join(", ", give) + " contre " + String.join(", ", get);
    }

    // ---- messages -------------------------------------------------------------------------------------------------

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    private static void broadcast(String text, ChatFormatting colour) {
        if (serverLevel != null)
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(colour), false);
    }

    private static void tell(String name, String text, ChatFormatting colour) {
        if (serverLevel == null)
            return;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayerByName(name);
        if (player != null)
            player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }

    private static void sound(SoundAction action, String a, String b) {
        SoundClientboundPacket.playSoundForPlayer(action, a);
        SoundClientboundPacket.playSoundForPlayer(action, b);
    }

    // attack orders refused at peace: said once in a while, not for every unit
    private static final Map<String, Long> lastPeaceWarning = new HashMap<>();
    public static void warnAtPeace(String player, String other) {
        if (now() - lastPeaceWarning.getOrDefault(player, -100L) < 60)
            return;
        lastPeaceWarning.put(player, now());
        tell(player, "Vous êtes en paix avec " + other + " : déclarez-lui d'abord la guerre (écran Diplomatie ou /rts-diplo guerre " + other + ").", ChatFormatting.GOLD);
    }

    // for /rts-diplo and /rts-bot info
    public static List<String> describe(String viewer) {
        List<String> lines = new ArrayList<>();
        for (String other : civs()) {
            if (other.equals(viewer))
                continue;
            String line = other + " : " + getStance(viewer, other).frName;
            if (BotServerEvents.isBot(other)) {
                line += ", opinion de vous " + signed(getOpinion(other, viewer));
                if (!getReason(other, viewer).isEmpty())
                    line += " (" + getReason(other, viewer) + ")";
            }
            lines.add(line);
        }
        if (lines.isEmpty())
            lines.add("Aucune autre civilisation");
        return lines;
    }

    // what a bot thinks of the others, for /rts-bot info
    public static List<String> describeBot(String bot) {
        List<String> lines = new ArrayList<>();
        for (String other : civs())
            if (!other.equals(bot))
                lines.add("Diplomatie : " + other + " " + getStance(bot, other).frName + ", opinion " + signed(getOpinion(bot, other)) +
                    (getReason(bot, other).isEmpty() ? "" : " (" + getReason(bot, other) + ")"));
        return lines;
    }

    // ---- saving and syncing ---------------------------------------------------------------------------------------

    private static void snapshotAlliances() {
        Set<String> now = new HashSet<>();
        for (String a : civs())
            for (String b : AlliancesServerEvents.getAllAllies(a))
                now.add(DiplomacySaveData.key(a, b));
        if (!now.equals(data().alliances)) {
            data().alliances.clear();
            data().alliances.addAll(now);
            data().setDirty();
            needsSync = true;
        }
    }

    // after a restart: the alliances the original mod forgot
    private static void restoreAlliances() {
        for (String pair : data().alliances) {
            String[] ab = DiplomacySaveData.split(pair);
            if (ab.length == 2 && isCiv(ab[0]) && isCiv(ab[1]) && !AlliancesServerEvents.isAllied(ab[0], ab[1]))
                AlliancesServerEvents.addAlliance(ab[0], ab[1]);
        }
    }

    // new match (/rts-reset): everyone back to peace, opinions forgotten
    public static void reset() {
        if (serverLevel == null)
            return;
        data().wars.clear();
        data().alliances.clear();
        data().opinions.clear();
        data().setDirty();
        proposals.clear();
        needsSync = true;
    }

    private static void sync() {
        needsSync = false;
        List<DiplomacyClientboundPacket.OpinionEntry> ops = new ArrayList<>();
        for (String bot : BotServerEvents.botNames())
            for (String other : civs())
                if (!other.equals(bot))
                    ops.add(new DiplomacyClientboundPacket.OpinionEntry(bot, other, getOpinion(bot, other), getReason(bot, other)));
        DiplomacyClientboundPacket.sendAll(new ArrayList<>(data().wars.keySet()), ops, new ArrayList<>(proposals));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null)
            return;
        long t = now();
        if (!restored && t % 20 == 0 && !PlayerServerEvents.rtsPlayers.isEmpty()) {
            restored = true;
            restoreAlliances();
        }
        if (t % TERRITORY_INTERVAL == 0)
            checkTerritory();
        if (t % OPINION_INTERVAL == 0)
            updateOpinions();
        if (t % DECIDE_INTERVAL == 0) {
            if (restored)
                snapshotAlliances();
            proposals.removeIf(p -> t - p.time() > PROPOSAL_LIFE || !isCiv(p.from()) || !isCiv(p.to()));
            decide();
        }
        if (needsSync || t % SYNC_INTERVAL == 0)
            sync();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        needsSync = true;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);
        restored = false;
        proposals.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
    }

    // ---- /rts-diplo -----------------------------------------------------------------------------------------------

    private static final SuggestionProvider<CommandSourceStack> CIVS = (ctx, b) -> SharedSuggestionProvider.suggest(civs(), b);

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();
        var root = Commands.literal("rts-diplo").executes(ctx -> {
            String me = ctx.getSource().getTextName();
            for (String line : describe(me))
                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            return 1;
        });
        for (String what : List.of("guerre", "paix", "alliance", "rompre"))
            root.then(Commands.literal(what).then(Commands.argument("civ", StringArgumentType.greedyString()).suggests(CIVS)
                .executes(ctx -> reply(ctx, request(ctx.getSource().getTextName(), StringArgumentType.getString(ctx, "civ"), what, 0, 0, 0)))));
        root.then(Commands.literal("accepter").then(Commands.argument("civ", StringArgumentType.greedyString()).suggests(CIVS)
            .executes(ctx -> reply(ctx, answer(ctx.getSource().getTextName(), StringArgumentType.getString(ctx, "civ"), true)))));
        root.then(Commands.literal("refuser").then(Commands.argument("civ", StringArgumentType.greedyString()).suggests(CIVS)
            .executes(ctx -> reply(ctx, answer(ctx.getSource().getTextName(), StringArgumentType.getString(ctx, "civ"), false)))));
        // for testing: /rts-diplo opinion <valeur> <bot> -> sets what that bot thinks of you
        root.then(Commands.literal("opinion").requires(s -> s.hasPermission(2))
            .then(Commands.argument("valeur", IntegerArgumentType.integer(MIN, MAX))
                .then(Commands.argument("bot", StringArgumentType.greedyString()).suggests(CIVS).executes(ctx -> {
                    String bot = StringArgumentType.getString(ctx, "bot");
                    if (!BotServerEvents.isBot(bot))
                        return reply(ctx, bot + " n'est pas un bot");
                    setOpinion(bot, ctx.getSource().getTextName(), IntegerArgumentType.getInteger(ctx, "valeur"));
                    return reply(ctx, null);
                }))));
        d.register(root);
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, @Nullable String error) {
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }
}
