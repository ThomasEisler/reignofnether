package com.solegendary.reignofnether.diplomacy;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

// the whole diplomacy state (wars, bot opinions, pending proposals), sent to everyone when it changes
public class DiplomacyClientboundPacket {

    public record OpinionEntry(String bot, String other, int value, String reason) { }

    private final List<String> wars;
    private final List<OpinionEntry> opinions;
    private final List<Proposal> proposals;

    public static void sendAll(List<String> wars, List<OpinionEntry> opinions, List<Proposal> proposals) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new DiplomacyClientboundPacket(wars, opinions, proposals));
    }

    public DiplomacyClientboundPacket(List<String> wars, List<OpinionEntry> opinions, List<Proposal> proposals) {
        this.wars = wars;
        this.opinions = opinions;
        this.proposals = proposals;
    }

    public DiplomacyClientboundPacket(FriendlyByteBuf buf) {
        this.wars = buf.readList(FriendlyByteBuf::readUtf);
        this.opinions = buf.readList(b -> new OpinionEntry(b.readUtf(), b.readUtf(), b.readInt(), b.readUtf()));
        this.proposals = buf.readList(b -> new Proposal(b.readEnum(Proposal.Kind.class), b.readUtf(), b.readUtf(),
            b.readInt(), b.readInt(), b.readInt(), 0));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(wars, FriendlyByteBuf::writeUtf);
        buf.writeCollection(opinions, (b, o) -> {
            b.writeUtf(o.bot());
            b.writeUtf(o.other());
            b.writeInt(o.value());
            b.writeUtf(o.reason());
        });
        buf.writeCollection(proposals, (b, p) -> {
            b.writeEnum(p.kind());
            b.writeUtf(p.from());
            b.writeUtf(p.to());
            b.writeInt(p.food());
            b.writeInt(p.wood());
            b.writeInt(p.ore());
        });
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DiplomacyClient.set(wars, opinions, proposals)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
