package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

// the trade routes, for the market buttons
public class TradeClientboundPacket {

    public record RouteInfo(BlockPos market, int slot, String owner, BlockPos target, boolean hasMerchant, String state) { }

    private final List<RouteInfo> routes;

    public static void sendAll(List<RouteInfo> routes) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new TradeClientboundPacket(routes));
    }

    public TradeClientboundPacket(List<RouteInfo> routes) {
        this.routes = routes;
    }

    public TradeClientboundPacket(FriendlyByteBuf buf) {
        this.routes = buf.readList(b -> new RouteInfo(b.readBlockPos(), b.readInt(), b.readUtf(), b.readBlockPos(), b.readBoolean(), b.readUtf()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(routes, (b, r) -> {
            b.writeBlockPos(r.market());
            b.writeInt(r.slot());
            b.writeUtf(r.owner());
            b.writeBlockPos(r.target());
            b.writeBoolean(r.hasMerchant());
            b.writeUtf(r.state());
        });
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TradeClientEvents.routes = routes));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
