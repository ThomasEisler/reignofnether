package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a market's open route button clicked: close that route
public class TradeServerboundPacket {

    private final BlockPos market;
    private final int slot;

    public static void close(BlockPos market, int slot) {
        PacketHandler.INSTANCE.sendToServer(new TradeServerboundPacket(market, slot));
    }

    public TradeServerboundPacket(BlockPos market, int slot) {
        this.market = market;
        this.slot = slot;
    }

    public TradeServerboundPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(market);
        buf.writeInt(slot);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            BuildingPlacement b = BuildingUtils.findBuilding(false, market);
            if (player != null && b != null)
                TradeServerEvents.closeRoute(player.getName().getString(), b, slot);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
