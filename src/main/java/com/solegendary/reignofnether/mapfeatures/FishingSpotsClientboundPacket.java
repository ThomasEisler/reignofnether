package com.solegendary.reignofnether.mapfeatures;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// the fishing spots, so clients can show their bubbles (and check where a port can go)
public class FishingSpotsClientboundPacket {

    private final List<BlockPos> spots;

    public static void sync(List<BlockPos> spots) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new FishingSpotsClientboundPacket(new ArrayList<>(spots)));
    }

    public static void sync(ServerPlayer player, List<BlockPos> spots) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new FishingSpotsClientboundPacket(new ArrayList<>(spots)));
    }

    public FishingSpotsClientboundPacket(List<BlockPos> spots) {
        this.spots = spots;
    }

    public FishingSpotsClientboundPacket(FriendlyByteBuf buffer) {
        this.spots = buffer.readList(FriendlyByteBuf::readBlockPos);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(spots, FriendlyByteBuf::writeBlockPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FishingClientEvents.setSpots(spots)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
