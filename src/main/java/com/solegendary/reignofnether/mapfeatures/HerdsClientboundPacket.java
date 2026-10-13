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

// where the wild herds live (and what animal), for their icons on the minimap
public class HerdsClientboundPacket {

    public record HerdInfo(BlockPos home, String species) { }

    private final List<HerdInfo> herds;

    public static List<HerdInfo> fromHerds(List<MapFeaturesSaveData.Herd> herds) {
        List<HerdInfo> list = new ArrayList<>();
        for (MapFeaturesSaveData.Herd herd : herds)
            list.add(new HerdInfo(herd.home, herd.species));
        return list;
    }

    public static void sync(List<MapFeaturesSaveData.Herd> herds) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new HerdsClientboundPacket(fromHerds(herds)));
    }

    public static void sync(ServerPlayer player, List<MapFeaturesSaveData.Herd> herds) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new HerdsClientboundPacket(fromHerds(herds)));
    }

    public HerdsClientboundPacket(List<HerdInfo> herds) {
        this.herds = herds;
    }

    public HerdsClientboundPacket(FriendlyByteBuf buffer) {
        this.herds = buffer.readList(buf -> new HerdInfo(buf.readBlockPos(), buf.readUtf()));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(herds, (buf, h) -> {
            buf.writeBlockPos(h.home());
            buf.writeUtf(h.species());
        });
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MapIconsClientEvents.setHerds(herds)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
