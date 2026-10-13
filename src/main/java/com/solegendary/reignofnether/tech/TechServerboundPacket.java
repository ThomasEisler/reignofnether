package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a player starts or cancels a research
public class TechServerboundPacket {

    private final boolean start;
    private final Tech tech;

    public static void start(Tech tech) {
        PacketHandler.INSTANCE.sendToServer(new TechServerboundPacket(true, tech));
    }

    public static void cancel() {
        PacketHandler.INSTANCE.sendToServer(new TechServerboundPacket(false, Tech.MASONRY));
    }

    public TechServerboundPacket(boolean start, Tech tech) {
        this.start = start;
        this.tech = tech;
    }

    public TechServerboundPacket(FriendlyByteBuf buffer) {
        this.start = buffer.readBoolean();
        this.tech = buffer.readEnum(Tech.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(start);
        buffer.writeEnum(tech);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;
            if (start)
                TechServerEvents.startResearch(player.getName().getString(), tech);
            else
                TechServerEvents.cancelResearch(player.getName().getString());
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
