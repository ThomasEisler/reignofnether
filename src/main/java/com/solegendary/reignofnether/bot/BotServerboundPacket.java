package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a player changed the number of bots or their difficulty with the button
public class BotServerboundPacket {

    private final int wanted;
    private final BotDifficulty difficulty;

    public static void send(int wanted, BotDifficulty difficulty) {
        PacketHandler.INSTANCE.sendToServer(new BotServerboundPacket(wanted, difficulty));
    }

    public BotServerboundPacket(int wanted, BotDifficulty difficulty) {
        this.wanted = wanted;
        this.difficulty = difficulty;
    }

    public BotServerboundPacket(FriendlyByteBuf buffer) {
        this.wanted = buffer.readInt();
        this.difficulty = buffer.readEnum(BotDifficulty.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(wanted);
        buffer.writeEnum(difficulty);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() == null || !ctx.get().getSender().hasPermissions(2))
                return;
            BotSettings.set(wanted, difficulty);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
