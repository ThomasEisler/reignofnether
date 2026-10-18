package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

// the whole religion state, sent to everyone when it changes (it is small)
public class ReligionClientboundPacket {

    private final CompoundTag tag;

    public static void sendAll(ReligionSaveData data) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new ReligionClientboundPacket(data.save(new CompoundTag())));
    }

    public ReligionClientboundPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public ReligionClientboundPacket(FriendlyByteBuf buf) {
        this.tag = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(tag);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReligionClient.set(tag)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
