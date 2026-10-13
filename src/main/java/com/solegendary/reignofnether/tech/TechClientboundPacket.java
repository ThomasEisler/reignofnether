package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

// a player's technologies and current research, sent to that player
public class TechClientboundPacket {

    private final Set<Tech> done;
    private final int current; // ordinal, -1 for none
    private final int ticksLeft;

    public static void sync(String playerName, TechServerEvents.PlayerTech state) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player != null)
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new TechClientboundPacket(
                state.done, state.current != null ? state.current.ordinal() : -1, state.ticksLeft));
    }

    public TechClientboundPacket(Set<Tech> done, int current, int ticksLeft) {
        this.done = done.isEmpty() ? EnumSet.noneOf(Tech.class) : EnumSet.copyOf(done);
        this.current = current;
        this.ticksLeft = ticksLeft;
    }

    public TechClientboundPacket(FriendlyByteBuf buffer) {
        this.done = EnumSet.noneOf(Tech.class);
        int n = buffer.readVarInt();
        for (int i = 0; i < n; i++)
            this.done.add(buffer.readEnum(Tech.class));
        this.current = buffer.readInt();
        this.ticksLeft = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(done.size());
        for (Tech t : done)
            buffer.writeEnum(t);
        buffer.writeInt(current);
        buffer.writeInt(ticksLeft);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            TechClientEvents.setState(done, current >= 0 ? Tech.values()[current] : null, ticksLeft)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
