package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// the shape of a rampart segment, sent before the segment itself so the client can build its blocks
public class RampartShapeClientboundPacket {

    private final BlockPos origin;
    private final List<RampartShapes.Column> columns;

    // to one player, or everyone if null
    public static void send(@Nullable ServerPlayer player, BlockPos origin, List<RampartShapes.Column> columns) {
        RampartShapeClientboundPacket packet = new RampartShapeClientboundPacket(origin, columns);
        if (player != null)
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        else
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    public RampartShapeClientboundPacket(BlockPos origin, List<RampartShapes.Column> columns) {
        this.origin = origin;
        this.columns = columns;
    }

    public RampartShapeClientboundPacket(FriendlyByteBuf buffer) {
        this.origin = buffer.readBlockPos();
        int n = buffer.readVarInt();
        this.columns = new ArrayList<>();
        for (int i = 0; i < n; i++)
            columns.add(new RampartShapes.Column(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readVarInt()));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(origin);
        buffer.writeVarInt(columns.size());
        for (RampartShapes.Column c : columns) {
            buffer.writeInt(c.x());
            buffer.writeInt(c.y());
            buffer.writeInt(c.z());
            buffer.writeVarInt(c.height());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        // handled on the main thread, in order, before the segment's own packet
        RampartShapes.setClientShape(origin, columns);
        ctx.get().setPacketHandled(true);
        return true;
    }
}
