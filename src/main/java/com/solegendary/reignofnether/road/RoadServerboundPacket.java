package com.solegendary.reignofnether.road;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// client asks the server to build a road between two of its buildings; the owner is always the sender
public class RoadServerboundPacket {

    private final BlockPos fromPos;
    private final BlockPos toPos;
    private final RoadType type;

    public static void buildRoad(BlockPos fromPos, BlockPos toPos, RoadType type) {
        PacketHandler.INSTANCE.sendToServer(new RoadServerboundPacket(fromPos, toPos, type));
    }

    public RoadServerboundPacket(BlockPos fromPos, BlockPos toPos, RoadType type) {
        this.fromPos = fromPos;
        this.toPos = toPos;
        this.type = type;
    }

    public RoadServerboundPacket(FriendlyByteBuf buffer) {
        this.fromPos = buffer.readBlockPos();
        this.toPos = buffer.readBlockPos();
        this.type = buffer.readEnum(RoadType.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.fromPos);
        buffer.writeBlockPos(this.toPos);
        buffer.writeEnum(this.type);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("RoadServerboundPacket: Sender was null");
                return;
            }
            RoadServerEvents.tryBuildRoad(player.serverLevel(), player.getName().getString(), fromPos, toPos, type);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
