package com.solegendary.reignofnether.road;

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

// server sends a summary of every road (not their blocks) so clients can preview road orders and their costs
public class RoadClientboundPacket {

    public record RoadInfo(BlockPos from, BlockPos to, String ownerName, RoadType type, boolean underConstruction, int length) {
        public boolean connects(BlockPos buildingOrigin) {
            return from.equals(buildingOrigin) || to.equals(buildingOrigin);
        }
    }

    private final List<RoadInfo> roads;

    private static List<RoadInfo> toInfos(List<RoadConnection> connections) {
        List<RoadInfo> infos = new ArrayList<>();
        for (RoadConnection c : connections)
            infos.add(new RoadInfo(c.from, c.to, c.ownerName, c.type, c.isUnderConstruction(), c.length));
        return infos;
    }

    public static void syncRoads(List<RoadConnection> connections) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new RoadClientboundPacket(toInfos(connections)));
    }

    public static void syncRoads(ServerPlayer player, List<RoadConnection> connections) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new RoadClientboundPacket(toInfos(connections)));
    }

    public RoadClientboundPacket(List<RoadInfo> roads) {
        this.roads = roads;
    }

    public RoadClientboundPacket(FriendlyByteBuf buffer) {
        this.roads = buffer.readList(buf -> new RoadInfo(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(),
            buf.readEnum(RoadType.class), buf.readBoolean(), buf.readInt()));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(this.roads, (buf, road) -> {
            buf.writeBlockPos(road.from());
            buf.writeBlockPos(road.to());
            buf.writeUtf(road.ownerName());
            buf.writeEnum(road.type());
            buf.writeBoolean(road.underConstruction());
            buf.writeInt(road.length());
        });
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RoadClientEvents.setRoads(roads)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
