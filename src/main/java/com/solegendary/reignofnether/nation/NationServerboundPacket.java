package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a player picks their nation
public class NationServerboundPacket {

    private final Nation nation;

    public static void chooseNation(Nation nation) {
        PacketHandler.INSTANCE.sendToServer(new NationServerboundPacket(nation));
    }

    public NationServerboundPacket(Nation nation) {
        this.nation = nation;
    }

    public NationServerboundPacket(FriendlyByteBuf buffer) {
        this.nation = buffer.readEnum(Nation.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(nation);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null)
                NationServerEvents.chooseNation(player.getName().getString(), nation);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
