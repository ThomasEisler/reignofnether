package com.solegendary.reignofnether.religion;

import net.minecraft.nbt.CompoundTag;

// what the server last said about religions (faith, founders, beliefs, cities)
public class ReligionClient {
    public static ReligionSaveData data = new ReligionSaveData();

    public static void set(CompoundTag tag) {
        data = ReligionSaveData.load(tag);
    }
}
