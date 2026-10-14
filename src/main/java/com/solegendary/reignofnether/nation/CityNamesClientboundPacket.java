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

// city names (Town Centre origin -> name), shown above each Town Centre
public class CityNamesClientboundPacket {

    private final Map<Long, String> names;

    public static void sync(Map<Long, String> names) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CityNamesClientboundPacket(new HashMap<>(names)));
    }

    public static void sync(ServerPlayer player, Map<Long, String> names) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CityNamesClientboundPacket(new HashMap<>(names)));
    }

    public CityNamesClientboundPacket(Map<Long, String> names) {
        this.names = names;
    }

    public CityNamesClientboundPacket(FriendlyByteBuf buffer) {
        this.names = buffer.readMap(FriendlyByteBuf::readLong, FriendlyByteBuf::readUtf);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeMap(names, FriendlyByteBuf::writeLong, FriendlyByteBuf::writeUtf);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClientEvents.setNames(names)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
