package com.solegendary.reignofnether.barbarian;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a player changed the barbarians setting with the button (off, or on at a difficulty)
public class BarbarianServerboundPacket {

    private final boolean enabled;
    private final BarbarianDifficulty difficulty;

    public static void send(boolean enabled, BarbarianDifficulty difficulty) {
        PacketHandler.INSTANCE.sendToServer(new BarbarianServerboundPacket(enabled, difficulty));
    }

    public BarbarianServerboundPacket(boolean enabled, BarbarianDifficulty difficulty) {
        this.enabled = enabled;
        this.difficulty = difficulty;
    }

    public BarbarianServerboundPacket(FriendlyByteBuf buffer) {
        this.enabled = buffer.readBoolean();
        this.difficulty = buffer.readEnum(BarbarianDifficulty.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeEnum(difficulty);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() == null)
                return;
            BarbarianServerEvents.setDifficulty(difficulty);
            BarbarianServerEvents.setEnabled(enabled);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
