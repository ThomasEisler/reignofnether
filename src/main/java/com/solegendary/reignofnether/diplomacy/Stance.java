package com.solegendary.reignofnether.diplomacy;

// between two civilisations: at war they fight, at peace they leave each other alone, allies also share
// gates, healing and the closer city distance
public enum Stance {
    WAR("Guerre", 0xE04040),
    PEACE("Paix", 0xE0D060),
    ALLIANCE("Alliance", 0x50D050);

    public final String frName;
    public final int colour;

    Stance(String frName, int colour) {
        this.frName = frName;
        this.colour = colour;
    }
}
