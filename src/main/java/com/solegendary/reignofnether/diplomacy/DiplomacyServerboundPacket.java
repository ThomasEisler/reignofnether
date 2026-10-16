package com.solegendary.reignofnether.diplomacy;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// a button of the diplomacy screen: "guerre", "paix", "alliance", "rompre", "echange", "accepter" or "refuser"
public class DiplomacyServerboundPacket {

    private final String what, target;
    private final int food, wood, ore;

    public static void send(String what, String target) {
        send(what, target, 0, 0, 0);
    }

    public static void send(String what, String target, int food, int wood, int ore) {
        PacketHandler.INSTANCE.sendToServer(new DiplomacyServerboundPacket(what, target, food, wood, ore));
    }

    public DiplomacyServerboundPacket(String what, String target, int food, int wood, int ore) {
        this.what = what;
        this.target = target;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
    }

    public DiplomacyServerboundPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(what);
        buf.writeUtf(target);
        buf.writeInt(food);
        buf.writeInt(wood);
        buf.writeInt(ore);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;
            String me = player.getName().getString();
            String error = switch (what) {
                case "accepter" -> DiplomacyServerEvents.answer(me, target, true);
                case "refuser" -> DiplomacyServerEvents.answer(me, target, false);
                default -> DiplomacyServerEvents.request(me, target, what, food, wood, ore);
            };
            if (error != null)
                player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
