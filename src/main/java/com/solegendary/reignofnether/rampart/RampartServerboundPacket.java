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

// a player builds a rampart through its points (a gate or a tower: start and end) with the given workers
public class RampartServerboundPacket {

    private final ResourceLocation building;
    private final java.util.List<BlockPos> points;
    private final int[] builderIds;

    public static void build(RampartBuilding building, BlockPos start, BlockPos end, int[] builderIds) {
        build(building, java.util.List.of(start, end), builderIds);
    }

    public static void build(RampartBuilding building, java.util.List<BlockPos> points, int[] builderIds) {
        PacketHandler.INSTANCE.sendToServer(new RampartServerboundPacket(
            ReignOfNetherRegistries.BUILDING.getKey(building), points, builderIds));
    }

    public RampartServerboundPacket(ResourceLocation building, java.util.List<BlockPos> points, int[] builderIds) {
        this.building = building;
        this.points = points;
        this.builderIds = builderIds;
    }

    public RampartServerboundPacket(FriendlyByteBuf buffer) {
        this.building = buffer.readResourceLocation();
        this.points = buffer.readList(FriendlyByteBuf::readBlockPos);
        this.builderIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(building);
        buffer.writeCollection(points, FriendlyByteBuf::writeBlockPos);
        buffer.writeVarIntArray(builderIds);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            Building b = ReignOfNetherRegistries.BUILDING.get(building);
            if (player != null && b instanceof RampartBuilding rb)
                RampartServerEvents.buildPlayerRampart(player.serverLevel(), player.getName().getString(), rb, points, builderIds);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
