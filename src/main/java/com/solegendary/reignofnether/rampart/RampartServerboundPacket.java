package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a player builds a rampart (or a gate) from start to end with the given workers
public class RampartServerboundPacket {

    private final ResourceLocation building;
    private final BlockPos start, end;
    private final int[] builderIds;

    public static void build(RampartBuilding building, BlockPos start, BlockPos end, int[] builderIds) {
        PacketHandler.INSTANCE.sendToServer(new RampartServerboundPacket(
            ReignOfNetherRegistries.BUILDING.getKey(building), start, end, builderIds));
    }

    public RampartServerboundPacket(ResourceLocation building, BlockPos start, BlockPos end, int[] builderIds) {
        this.building = building;
        this.start = start;
        this.end = end;
        this.builderIds = builderIds;
    }

    public RampartServerboundPacket(FriendlyByteBuf buffer) {
        this.building = buffer.readResourceLocation();
        this.start = buffer.readBlockPos();
        this.end = buffer.readBlockPos();
        this.builderIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(building);
        buffer.writeBlockPos(start);
        buffer.writeBlockPos(end);
        buffer.writeVarIntArray(builderIds);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            Building b = ReignOfNetherRegistries.BUILDING.get(building);
            if (player != null && b instanceof RampartBuilding rb)
                RampartServerEvents.buildPlayerRampart(player.serverLevel(), player.getName().getString(), rb, start, end, builderIds);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
