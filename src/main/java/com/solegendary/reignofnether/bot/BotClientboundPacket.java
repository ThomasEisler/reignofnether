package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// tells clients how many bots the game has and how strong they are (shown on the bots button)
public class BotClientboundPacket {

    private final int wanted;
    private final BotDifficulty difficulty;

    public static void sync(int wanted, BotDifficulty difficulty) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new BotClientboundPacket(wanted, difficulty));
    }

    public static void sync(ServerPlayer player, int wanted, BotDifficulty difficulty) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BotClientboundPacket(wanted, difficulty));
    }

    public BotClientboundPacket(int wanted, BotDifficulty difficulty) {
        this.wanted = wanted;
        this.difficulty = difficulty;
    }

    public BotClientboundPacket(FriendlyByteBuf buffer) {
        this.wanted = buffer.readInt();
        this.difficulty = buffer.readEnum(BotDifficulty.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(wanted);
        buffer.writeEnum(difficulty);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BotClientEvents.setSettings(wanted, difficulty)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
