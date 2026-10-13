package com.solegendary.reignofnether.barbarian;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// tells clients whether barbarians are on and how hard they are (shown on the barbarians button)
public class BarbarianClientboundPacket {

    private final boolean enabled;
    private final BarbarianDifficulty difficulty;

    public static void sync(boolean enabled, BarbarianDifficulty difficulty) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new BarbarianClientboundPacket(enabled, difficulty));
    }

    public static void sync(ServerPlayer player, boolean enabled, BarbarianDifficulty difficulty) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BarbarianClientboundPacket(enabled, difficulty));
    }

    public BarbarianClientboundPacket(boolean enabled, BarbarianDifficulty difficulty) {
        this.enabled = enabled;
        this.difficulty = difficulty;
    }

    public BarbarianClientboundPacket(FriendlyByteBuf buffer) {
        this.enabled = buffer.readBoolean();
        this.difficulty = buffer.readEnum(BarbarianDifficulty.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeEnum(difficulty);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BarbarianClientEvents.setSettings(enabled, difficulty)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
