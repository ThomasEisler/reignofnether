package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a temple button: found a religion, or adopt a second belief
public class ReligionServerboundPacket {

    private final boolean found;
    private final String what; // Religion or Belief name
    private final BlockPos temple;

    public static void found(Religion religion, BlockPos temple) {
        PacketHandler.INSTANCE.sendToServer(new ReligionServerboundPacket(true, religion.name(), temple));
    }

    public static void adopt(Belief belief, BlockPos temple) {
        PacketHandler.INSTANCE.sendToServer(new ReligionServerboundPacket(false, belief.name(), temple));
    }

    public ReligionServerboundPacket(boolean found, String what, BlockPos temple) {
        this.found = found;
        this.what = what;
        this.temple = temple;
    }

    public ReligionServerboundPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(), buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(found);
        buf.writeUtf(what);
        buf.writeBlockPos(temple);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;
            String me = player.getName().getString();
            BuildingPlacement b = BuildingUtils.findBuilding(false, temple);
            if (b == null || !b.ownerName.equals(me) || !(b.getBuilding() instanceof Temple))
                return;
            try {
                if (found)
                    ReligionServerEvents.found(me, Religion.valueOf(what), b);
                else
                    ReligionServerEvents.buyBelief(me, Belief.valueOf(what));
            } catch (IllegalArgumentException ignored) { }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
