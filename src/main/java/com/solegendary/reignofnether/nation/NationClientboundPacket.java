package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

// every player's nation, sent to clients
public class NationClientboundPacket {

    private final Map<String, Nation> nations;

    public static void sync(Map<String, Nation> nations) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new NationClientboundPacket(new HashMap<>(nations)));
    }

    public static void sync(ServerPlayer player, Map<String, Nation> nations) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new NationClientboundPacket(new HashMap<>(nations)));
    }

    public NationClientboundPacket(Map<String, Nation> nations) {
        this.nations = nations;
    }

    public NationClientboundPacket(FriendlyByteBuf buffer) {
        this.nations = buffer.readMap(FriendlyByteBuf::readUtf, buf -> buf.readEnum(Nation.class));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeMap(nations, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeEnum);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NationClientEvents.setNations(nations)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
