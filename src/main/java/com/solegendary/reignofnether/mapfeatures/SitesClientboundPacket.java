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

// quarries and ruins still standing, for their icons on the minimap
public class SitesClientboundPacket {

    private final List<BlockPos> quarries;
    private final List<BlockPos> ruins;

    private static SitesClientboundPacket of(MapFeaturesSaveData data) {
        List<BlockPos> quarries = new ArrayList<>(), ruins = new ArrayList<>();
        for (MapFeaturesSaveData.Quarry q : data.quarries)
            quarries.add(q.centre);
        for (MapFeaturesSaveData.RuinSite r : data.ruins)
            ruins.add(r.origin.offset(4, 0, 4));
        return new SitesClientboundPacket(quarries, ruins);
    }

    public static void sync(MapFeaturesSaveData data) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), of(data));
    }

    public static void sync(ServerPlayer player, MapFeaturesSaveData data) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), of(data));
    }

    public SitesClientboundPacket(List<BlockPos> quarries, List<BlockPos> ruins) {
        this.quarries = quarries;
        this.ruins = ruins;
    }

    public SitesClientboundPacket(FriendlyByteBuf buffer) {
        this.quarries = buffer.readList(FriendlyByteBuf::readBlockPos);
        this.ruins = buffer.readList(FriendlyByteBuf::readBlockPos);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(quarries, FriendlyByteBuf::writeBlockPos);
        buffer.writeCollection(ruins, FriendlyByteBuf::writeBlockPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MapIconsClientEvents.setSites(quarries, ruins)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
