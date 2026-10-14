package com.solegendary.reignofnether.exploration;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// explored chunks of the receiving player: the whole list (replace = true) or only the new ones
public class ExplorationClientboundPacket {

    private final boolean enabled;
    private final boolean replace;
    private final long[] chunks;

    public static void send(ServerPlayer player, boolean enabled, boolean replace, long[] chunks) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new ExplorationClientboundPacket(enabled, replace, chunks));
    }

    public ExplorationClientboundPacket(boolean enabled, boolean replace, long[] chunks) {
        this.enabled = enabled;
        this.replace = replace;
        this.chunks = chunks;
    }

    public ExplorationClientboundPacket(FriendlyByteBuf buffer) {
        this.enabled = buffer.readBoolean();
        this.replace = buffer.readBoolean();
        this.chunks = buffer.readLongArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeBoolean(replace);
        buffer.writeLongArray(chunks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ExplorationClientEvents.receive(enabled, replace, chunks)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
