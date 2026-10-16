package com.solegendary.reignofnether.diplomacy;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.player.PlayerClientEvents;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// what the server last said about wars, bot opinions and proposals
public class DiplomacyClient {

    private static final Set<String> wars = new HashSet<>();
    private static List<DiplomacyClientboundPacket.OpinionEntry> opinions = new ArrayList<>();
    private static List<Proposal> proposals = new ArrayList<>();

    public static void set(List<String> w, List<DiplomacyClientboundPacket.OpinionEntry> o, List<Proposal> p) {
        wars.clear();
        wars.addAll(w);
        opinions = o;
        proposals = p;
    }

    public static Stance getStance(String a, String b) {
        if (AlliancesClient.isAllied(a, b))
            return Stance.ALLIANCE;
        if (!PlayerClientEvents.isRTSPlayer(a) || !PlayerClientEvents.isRTSPlayer(b) || wars.contains(DiplomacySaveData.key(a, b)))
            return Stance.WAR;
        return Stance.PEACE;
    }

    public static boolean atPeace(String a, String b) {
        return a != null && b != null && !a.isBlank() && !b.isBlank() && !a.equals(b) && getStance(a, b) == Stance.PEACE;
    }

    @Nullable
    public static DiplomacyClientboundPacket.OpinionEntry getOpinion(String bot, String other) {
        for (var o : opinions)
            if (o.bot().equals(bot) && o.other().equals(other))
                return o;
        return null;
    }

    @Nullable
    public static Proposal getProposal(String from, String to) {
        for (Proposal p : proposals)
            if (p.from().equals(from) && p.to().equals(to))
                return p;
        return null;
    }
}
