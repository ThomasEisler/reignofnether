package com.solegendary.reignofnether.trade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import javax.annotation.Nullable;
import java.util.UUID;

// a trade route opened in a market: from the market's city to another city, run by one merchant
public class TradeRoute {
    public final BlockPos market;   // market origin
    public final int slot;          // 0, or 1 with the Grand Market
    public final String owner;
    public final BlockPos target;   // Town Centre origin of the city traded with
    @Nullable public UUID merchant = null;
    public boolean outbound = true;
    public String state = "";       // shown in the market: why it is stopped, or empty

    public TradeRoute(BlockPos market, int slot, String owner, BlockPos target) {
        this.market = market;
        this.slot = slot;
        this.owner = owner;
        this.target = target;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("market", market.asLong());
        t.putInt("slot", slot);
        t.putString("owner", owner);
        t.putLong("target", target.asLong());
        if (merchant != null)
            t.putUUID("merchant", merchant);
        t.putBoolean("outbound", outbound);
        return t;
    }

    public static TradeRoute load(CompoundTag t) {
        TradeRoute r = new TradeRoute(BlockPos.of(t.getLong("market")), t.getInt("slot"), t.getString("owner"), BlockPos.of(t.getLong("target")));
        if (t.hasUUID("merchant"))
            r.merchant = t.getUUID("merchant");
        r.outbound = t.getBoolean("outbound");
        return r;
    }
}
