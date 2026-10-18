package com.solegendary.reignofnether.score;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

// the ranking, every 10 s
public class ScoreClientboundPacket {

    private final List<Score> scores;

    public static void sendAll(List<Score> scores) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new ScoreClientboundPacket(scores));
    }

    public ScoreClientboundPacket(List<Score> scores) {
        this.scores = scores;
    }

    public ScoreClientboundPacket(FriendlyByteBuf buf) {
        this.scores = buf.readList(b -> new Score(b.readUtf(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(scores, (b, s) -> {
            b.writeUtf(s.civ());
            b.writeInt(s.territory());
            b.writeInt(s.population());
            b.writeInt(s.technology());
            b.writeInt(s.military());
            b.writeInt(s.religion());
            b.writeInt(s.economy());
        });
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ScoreClientEvents.scores = scores));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
